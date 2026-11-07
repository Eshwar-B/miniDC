package com.minidc.server.admin;

import com.minidc.server.audit.AuditLog;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.security.CurrentUser;
import com.minidc.server.user.Invite;
import com.minidc.server.user.InviteRepository;
import com.minidc.server.user.User;
import com.minidc.server.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for administrative management of invites, users, nodes, leases, and system audits.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final NodeRepository nodeRepository;
    private final LeaseRepository leaseRepository;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminController(InviteRepository inviteRepository,
                           UserRepository userRepository,
                           NodeRepository nodeRepository,
                           LeaseRepository leaseRepository,
                           AuditService auditService) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.leaseRepository = leaseRepository;
        this.auditService = auditService;
    }

    public record InviteResponse(String code, Instant expiresAt) {}

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse createInvite(@AuthenticationPrincipal CurrentUser user) {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        String code = "INV-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 16).toUpperCase();
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        Invite invite = new Invite(code, user.id(), null, expiresAt);
        inviteRepository.save(invite);
        auditService.log(user.id(), null, null, "INVITE_CREATED", "Generated invite: " + code);

        return new InviteResponse(code, expiresAt);
    }

    @PostMapping("/users/{id}/ban")
    public User banUser(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        target.setBanned(true);
        userRepository.save(target);
        auditService.log(user.id(), null, null, "USER_BANNED", "Banned user: " + target.getEmail());
        return target;
    }

    @GetMapping("/nodes")
    public List<Node> getAllNodes() {
        return nodeRepository.findAll();
    }

    @GetMapping("/leases")
    public List<Lease> getAllLeases() {
        return leaseRepository.findAll();
    }

    @GetMapping("/audit")
    public List<AuditLog> getAuditLogs() {
        return auditService.getRecentLogs();
    }
}
