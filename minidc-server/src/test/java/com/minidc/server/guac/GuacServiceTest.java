package com.minidc.server.guac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.Role;
import com.minidc.server.config.AppProperties;
import com.minidc.server.crypto.AesGcmCrypto;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuacServiceTest {

    private LeaseRepository leaseRepository;
    private NodeRepository nodeRepository;
    private AesGcmCrypto aesGcmCrypto;
    private GuacCrypto guacCrypto;
    private AppProperties properties;
    private ObjectMapper objectMapper;
    private GuacService guacService;

    private final UUID borrowerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();
    private final String secretKey = "00112233445566778899aabbccddeeff";

    @BeforeEach
    void setUp() {
        leaseRepository = mock(LeaseRepository.class);
        nodeRepository = mock(NodeRepository.class);
        aesGcmCrypto = mock(AesGcmCrypto.class);
        guacCrypto = new GuacCrypto();
        objectMapper = new ObjectMapper();
        properties = new AppProperties(
                "jwt-secret-test-32-chars-minimum!!",
                12,
                "data-key-test-32-chars-minimum!",
                "http://localhost:8080/guacamole",
                secretKey,
                1,
                false
        );

        guacService = new GuacService(
                leaseRepository,
                nodeRepository,
                aesGcmCrypto,
                guacCrypto,
                properties,
                objectMapper
        );
    }

    @Test
    void testGetConnectUrlSuccess() throws Exception {
        CurrentUser borrower = new CurrentUser(borrowerId, "borrower@minidc.local", Role.MEMBER);

        Lease lease = new Lease();
        lease.setId(leaseId);
        lease.setBorrowerId(borrowerId);
        lease.setNodeId(nodeId);
        lease.setState(LeaseState.ACTIVE);
        lease.setVmUser("minidc");
        lease.setVmPasswordEnc("encrypted-pw");
        lease.setVmPort(33001);
        lease.setEndsAt(Instant.now().plusSeconds(3600));

        Node node = new Node();
        node.setId(nodeId);
        node.setAddress("192.168.1.100");

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(aesGcmCrypto.decrypt("encrypted-pw")).thenReturn("plain-secret-password");

        GuacService.ConnectResponse response = guacService.getConnectUrl(leaseId, borrower);

        assertNotNull(response);
        assertNotNull(response.url());
        assertTrue(response.url().contains(properties.guacBaseUrl()));
        assertTrue(response.url().contains("lease-" + leaseId));
        assertEquals("lease-" + leaseId, response.connectionId());

        // Verify the fallback or token decrypts properly with the secret key
        assertNotNull(response.token());
        String decryptedJson = guacCrypto.decrypt(response.token(), secretKey);
        JsonNode root = objectMapper.readTree(decryptedJson);

        assertEquals("borrower@minidc.local", root.get("username").asText());
        JsonNode connection = root.get("connections").get("lease-" + leaseId);
        assertNotNull(connection);
        assertEquals("rdp", connection.get("protocol").asText());
        assertEquals("192.168.1.100", connection.get("parameters").get("hostname").asText());
        assertEquals("33001", connection.get("parameters").get("port").asText());
        assertEquals("plain-secret-password", connection.get("parameters").get("password").asText());
    }

    @Test
    void testForbiddenWhenNotBorrower() {
        CurrentUser attacker = new CurrentUser(otherUserId, "attacker@minidc.local", Role.MEMBER);

        Lease lease = new Lease();
        lease.setId(leaseId);
        lease.setBorrowerId(borrowerId);
        lease.setState(LeaseState.ACTIVE);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guacService.getConnectUrl(leaseId, attacker));
        assertTrue(ex.getMessage().contains("Only the borrower can connect"));
    }

    @Test
    void testForbiddenWhenNotActive() {
        CurrentUser borrower = new CurrentUser(borrowerId, "borrower@minidc.local", Role.MEMBER);

        Lease lease = new Lease();
        lease.setId(leaseId);
        lease.setBorrowerId(borrowerId);
        lease.setState(LeaseState.REQUESTED);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guacService.getConnectUrl(leaseId, borrower));
        assertTrue(ex.getMessage().contains("Lease is not ACTIVE"));
    }
}
