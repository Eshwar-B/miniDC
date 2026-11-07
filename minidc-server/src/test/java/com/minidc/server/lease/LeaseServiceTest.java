package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.NodeStatus;
import com.minidc.common.enums.Role;
import com.minidc.server.audit.AuditService;
import com.minidc.server.config.AppProperties;
import com.minidc.server.crypto.AesGcmCrypto;
import com.minidc.server.lease.dto.CreateLeaseRequest;
import com.minidc.server.lease.dto.LeaseResponse;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.Offer;
import com.minidc.server.offer.OfferRepository;
import com.minidc.server.user.User;
import com.minidc.server.user.UserRepository;
import com.minidc.server.ws.AgentCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests validating lease creation business rules, capacity guards, and offer constraints.
 */
@ExtendWith(MockitoExtension.class)
class LeaseServiceTest {

    @Mock
    private LeaseRepository leaseRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private AesGcmCrypto aesGcmCrypto;
    @Mock
    private AgentCommandSender agentCommandSender;

    private LeaseService leaseService;
    private final AppProperties properties = new AppProperties(
            "secret12345678901234567890123456",
            12,
            "Y2hhbmdlLW1lLWNoYW5nZS1tZS1jaGFuZ2UtbWUtMzI=",
            "http://localhost:8080/guacamole",
            "secret",
            1,
            true
    );

    private final UUID borrowerId = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaseService = new LeaseService(
                leaseRepository,
                offerRepository,
                nodeRepository,
                userRepository,
                auditService,
                aesGcmCrypto,
                agentCommandSender,
                properties
        );
    }

    private User createBorrower(boolean banned) {
        return new User(borrowerId, "borrower@example.com", "hash", "Borrower", Role.MEMBER, banned, Instant.now());
    }

    private Offer createOffer(boolean autoApprove) {
        return new Offer(offerId, nodeId, 8192, 4, 100, 24, null, null, autoApprove, null, true);
    }

    private Node createNode(NodeStatus status) {
        return new Node(nodeId, UUID.randomUUID(), "test-node", "hash", status, 16384, 8, 500, "192.168.1.10", Instant.now());
    }

    @Test
    @DisplayName("Create lease succeeds when capacity and limits are respected")
    void testCreateLeaseSuccess() {
        User borrower = createBorrower(false);
        Offer offer = createOffer(false);
        Node node = createNode(NodeStatus.ONLINE);

        when(userRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(leaseRepository.countByBorrowerIdAndStateIn(any(), any())).thenReturn(0L);
        when(offerRepository.findByIdForUpdate(offerId)).thenReturn(Optional.of(offer));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(leaseRepository.findByNodeIdAndStateIn(any(), any())).thenReturn(Collections.emptyList());
        when(aesGcmCrypto.encrypt(any())).thenReturn("encrypted-password");
        when(leaseRepository.save(any(Lease.class))).thenAnswer(i -> i.getArgument(0));

        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        LeaseResponse response = leaseService.createLease(borrowerId, request);

        assertNotNull(response);
        assertEquals(LeaseState.REQUESTED, response.state());
        assertEquals(2048, response.ramMb());
        assertEquals(2, response.cores());
    }

    @Test
    @DisplayName("Create lease rejected when user is banned")
    void testCreateLeaseBannedUser() {
        User borrower = createBorrower(true);
        when(userRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));

        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(IllegalArgumentException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @Test
    @DisplayName("Create lease rejected when max active leases exceeded")
    void testCreateLeaseMaxActiveExceeded() {
        User borrower = createBorrower(false);
        when(userRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(leaseRepository.countByBorrowerIdAndStateIn(any(), any())).thenReturn(1L);

        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(CapacityExceededException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @Test
    @DisplayName("Create lease rejected when node is offline")
    void testCreateLeaseNodeOffline() {
        User borrower = createBorrower(false);
        Offer offer = createOffer(false);
        Node node = createNode(NodeStatus.OFFLINE);

        when(userRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(leaseRepository.countByBorrowerIdAndStateIn(any(), any())).thenReturn(0L);
        when(offerRepository.findByIdForUpdate(offerId)).thenReturn(Optional.of(offer));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(IllegalArgumentException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @Test
    @DisplayName("Create lease rejected when node capacity exceeded")
    void testCreateLeaseCapacityExceeded() {
        User borrower = createBorrower(false);
        Offer offer = createOffer(false);
        Node node = createNode(NodeStatus.ONLINE);

        Lease existing = new Lease(
                UUID.randomUUID(), offerId, UUID.randomUUID(), nodeId,
                7000, 3, 80, 2, LeaseState.ACTIVE,
                "lease-1", 33001, "minidc", "enc", null, null, Instant.now(), Instant.now(), null
        );

        when(userRepository.findById(borrowerId)).thenReturn(Optional.of(borrower));
        when(leaseRepository.countByBorrowerIdAndStateIn(any(), any())).thenReturn(0L);
        when(offerRepository.findByIdForUpdate(offerId)).thenReturn(Optional.of(offer));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(leaseRepository.findByNodeIdAndStateIn(any(), any())).thenReturn(List.of(existing));

        // 7000 + 2048 = 9048 > offer max 8192
        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(CapacityExceededException.class, () -> leaseService.createLease(borrowerId, request));
    }
}

