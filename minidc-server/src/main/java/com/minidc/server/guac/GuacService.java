package com.minidc.server.guac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.enums.LeaseState;
import com.minidc.server.config.AppProperties;
import com.minidc.server.crypto.AesGcmCrypto;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service for generating signed Apache Guacamole remote desktop connection tokens and launch URLs.
 */
@Service
public class GuacService {

    private final LeaseRepository leaseRepository;
    private final NodeRepository nodeRepository;
    private final AesGcmCrypto aesGcmCrypto;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public GuacService(LeaseRepository leaseRepository,
                       NodeRepository nodeRepository,
                       AesGcmCrypto aesGcmCrypto,
                       AppProperties properties,
                       ObjectMapper objectMapper) {
        this.leaseRepository = leaseRepository;
        this.nodeRepository = nodeRepository;
        this.aesGcmCrypto = aesGcmCrypto;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record ConnectResponse(String url) {}

    public ConnectResponse getConnectUrl(UUID leaseId, CurrentUser user) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        if (!lease.getBorrowerId().equals(user.id())) {
            throw new IllegalArgumentException("Forbidden: Only the borrower can connect to this lease VM");
        }

        if (lease.getState() != LeaseState.ACTIVE) {
            throw new IllegalArgumentException("Lease is not ACTIVE (current state: " + lease.getState() + ")");
        }

        Node node = nodeRepository.findById(lease.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + lease.getNodeId()));

        String decryptedPassword = aesGcmCrypto.decrypt(lease.getVmPasswordEnc());
        long expiresEpochMs = lease.getEndsAt() != null ? lease.getEndsAt().toEpochMilli() : System.currentTimeMillis() + 3600_000L;

        Map<String, Object> guacPayload = Map.of(
                "username", user.email(),
                "expires", expiresEpochMs,
                "connections", Map.of(
                        "vm", Map.of(
                                "protocol", "rdp",
                                "parameters", Map.of(
                                        "hostname", node.getAddress() != null ? node.getAddress() : "127.0.0.1",
                                        "port", String.valueOf(lease.getVmPort() != null ? lease.getVmPort() : 3389),
                                        "username", lease.getVmUser() != null ? lease.getVmUser() : "minidc",
                                        "password", decryptedPassword != null ? decryptedPassword : "",
                                        "security", "any",
                                        "ignore-cert", "true",
                                        "resize-method", "display-update",
                                        "enable-wallpaper", "false",
                                        "disable-copy", "true",
                                        "disable-paste", "false"
                                )
                        )
                )
        );

        // TODO(phase-4): Implement HMAC-SHA256 signature, AES-128-CBC encryption, and POST to <guac-base-url>/api/tokens
        String stubToken = "stub-token-" + leaseId;
        String connectUrl = properties.guacBaseUrl() + "/#/client/lease-" + lease.getId() + "?token=" + stubToken;

        return new ConnectResponse(connectUrl);
    }
}
