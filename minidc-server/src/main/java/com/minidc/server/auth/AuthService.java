package com.minidc.server.auth;

import com.minidc.common.enums.Role;
import com.minidc.server.audit.AuditService;
import com.minidc.server.auth.dto.LoginRequest;
import com.minidc.server.auth.dto.RegisterRequest;
import com.minidc.server.auth.dto.TokenResponse;
import com.minidc.server.security.JwtService;
import com.minidc.server.user.Invite;
import com.minidc.server.user.InviteRepository;
import com.minidc.server.user.User;
import com.minidc.server.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing user authentication, registration with invites, and login rate limiting/lockouts.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final com.minidc.server.redis.TokenBlacklistService tokenBlacklistService;
    private final com.minidc.server.config.AppProperties properties;

    private record FailedAttempt(int count, Instant lockedUntil) {}
    private final Map<String, FailedAttempt> failureCache = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository,
                       InviteRepository inviteRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       com.minidc.server.redis.TokenBlacklistService tokenBlacklistService,
                       com.minidc.server.config.AppProperties properties) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.properties = properties;
    }

    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            tokenBlacklistService.blacklistToken(token, java.time.Duration.ofHours(properties.jwtHours()));
        }
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        Invite invite = inviteRepository.findByCode(request.inviteCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code: " + request.inviteCode()));

        if (invite.isUsed()) {
            throw new IllegalArgumentException("Invite code already used: " + request.inviteCode());
        }
        if (invite.isExpired()) {
            throw new IllegalArgumentException("Invite code has expired: " + request.inviteCode());
        }

        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                Role.MEMBER,
                false,
                Instant.now()
        );
        userRepository.save(user);

        invite.setUsedBy(userId);
        inviteRepository.save(invite);

        auditService.log(userId, null, null, "USER_REGISTERED", "Registered user: " + user.getEmail());

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenResponse(token);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        Instant now = Instant.now();

        FailedAttempt attempt = failureCache.get(email);
        if (attempt != null && attempt.lockedUntil() != null && now.isBefore(attempt.lockedUntil())) {
            long remainingSec = ChronoUnit.SECONDS.between(now, attempt.lockedUntil());
            throw new IllegalArgumentException("Account locked due to consecutive failed attempts. Try again in " + remainingSec + " seconds.");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int count = (attempt != null ? attempt.count() : 0) + 1;
            Instant lockedUntil = count >= 5 ? now.plus(5, ChronoUnit.MINUTES) : null;
            failureCache.put(email, new FailedAttempt(count, lockedUntil));
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (user.isBanned()) {
            throw new IllegalArgumentException("User account is banned");
        }

        failureCache.remove(email);
        auditService.log(user.getId(), null, null, "USER_LOGIN", "Login successful: " + user.getEmail());

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenResponse(token);
    }
}
