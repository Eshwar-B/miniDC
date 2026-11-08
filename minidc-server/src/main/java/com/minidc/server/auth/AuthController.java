package com.minidc.server.auth;

import com.minidc.server.auth.dto.LoginRequest;
import com.minidc.server.auth.dto.RegisterRequest;
import com.minidc.server.auth.dto.TokenResponse;
import com.minidc.server.security.CurrentUser;
import com.minidc.server.user.User;
import com.minidc.server.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication actions including registration, login, and identity lookup.
 */
@RestController
@RequestMapping
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/api/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@org.springframework.web.bind.annotation.RequestHeader(value = org.springframework.http.HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        authService.logout(authHeader);
    }

    @GetMapping("/api/me")
    public User getCurrentUser(@AuthenticationPrincipal CurrentUser currentUser) {
        // TODO(phase-2): Implement password reset, email verification, and token refresh endpoints
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentUser.id()));
    }
}
