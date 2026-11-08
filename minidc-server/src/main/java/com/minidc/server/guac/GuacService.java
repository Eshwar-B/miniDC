package com.minidc.server.guac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.enums.LeaseState;
import com.minidc.server.config.AppProperties;
import com.minidc.server.crypto.AesGcmCrypto;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating signed Apache Guacamole remote desktop connection tokens and launch URLs.
 * Implements HMAC-SHA256 signature + AES-128-CBC encryption and exchange with Guacamole's auth-json API.
 */
@Service
public class GuacService {

    private static final Logger log = LoggerFactory.getLogger(GuacService.class);

    private final LeaseRepository leaseRepository;
    private final NodeRepository nodeRepository;
    private final AesGcmCrypto aesGcmCrypto;
    private final GuacCrypto guacCrypto;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GuacService(LeaseRepository leaseRepository,
                       NodeRepository nodeRepository,
                       AesGcmCrypto aesGcmCrypto,
                       GuacCrypto guacCrypto,
                       AppProperties properties,
                       ObjectMapper objectMapper) {
        this.leaseRepository = leaseRepository;
        this.nodeRepository = nodeRepository;
        this.aesGcmCrypto = aesGcmCrypto;
        this.guacCrypto = guacCrypto;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public record ConnectResponse(String url, String token, String connectionId) {
        public ConnectResponse(String url) {
            this(url, null, null);
        }
    }

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
        String connectionId = "lease-" + lease.getId();

        Map<String, Object> connectionParams = Map.ofEntries(
                Map.entry("hostname", node.getAddress() != null ? node.getAddress() : "127.0.0.1"),
                Map.entry("port", String.valueOf(lease.getVmPort() != null ? lease.getVmPort() : 3389)),
                Map.entry("username", lease.getVmUser() != null ? lease.getVmUser() : "minidc"),
                Map.entry("password", decryptedPassword != null ? decryptedPassword : ""),
                Map.entry("security", "any"),
                Map.entry("ignore-cert", "true"),
                Map.entry("resize-method", "display-update"),
                Map.entry("enable-wallpaper", "false"),
                Map.entry("disable-copy", "false"),
                Map.entry("disable-paste", "false"),
                Map.entry("enable-audio", "true")
        );

        Map<String, Object> guacPayload = Map.of(
                "username", user.email(),
                "expires", expiresEpochMs,
                "connections", Map.of(
                        connectionId, Map.of(
                                "protocol", "rdp",
                                "parameters", connectionParams
                        )
                )
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(guacPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Guacamole payload JSON", e);
        }

        // Encrypt and sign payload with HMAC-SHA256 and AES-128-CBC
        String encryptedData = guacCrypto.encrypt(jsonPayload, properties.guacJsonSecret());

        // Exchange encrypted payload with Guacamole's /api/tokens REST endpoint
        String authToken = null;
        try {
            String formBody = "data=" + URLEncoder.encode(encryptedData, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.guacBaseUrl() + "/api/tokens"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.hasNonNull("authToken")) {
                    authToken = root.get("authToken").asText();
                    log.info("Obtained authenticated Guacamole session token for lease {}", leaseId);
                }
            } else {
                log.warn("Guacamole /api/tokens returned HTTP status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.debug("Guacamole server at {} unreachable, utilizing signed data token fallback: {}",
                    properties.guacBaseUrl(), e.getMessage());
        }

        String finalToken = authToken != null ? authToken : encryptedData;
        String connectUrl = properties.guacBaseUrl() + "/#/client/" + connectionId + "?token=" + finalToken;

        return new ConnectResponse(connectUrl, finalToken, connectionId);
    }
}
