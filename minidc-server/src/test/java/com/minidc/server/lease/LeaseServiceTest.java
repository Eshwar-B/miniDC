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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests validating lease creation business rules, capacity guards, and offer constraints.
 */
class LeaseServiceTest {

    private LeaseRepository leaseRepository;
    private OfferRepository offerRepository;
    private NodeRepository nodeRepository;
    private UserRepository userRepository;

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

    private User currentUser;
    private Offer currentOffer;
    private Node currentNode;
    private long activeLeaseCount = 0;
    private List<Lease> existingNodeLeases = Collections.emptyList();

    @BeforeEach
    void setUp() {
        AesGcmCrypto aesGcmCrypto = new AesGcmCrypto(properties);
        AuditService auditService = new AuditService(null) {
            @Override
            public void log(UUID a, UUID l, UUID n, String e, String d) {}
        };
        AgentCommandSender agentCommandSender = new AgentCommandSender(null, null) {
            @Override
            public boolean sendCreateVm(UUID n, com.minidc.common.ws.payload.CreateVmPayload p) { return true; }
            @Override
            public boolean sendDestroyVm(UUID n, UUID l) { return true; }
        };

        leaseRepository = proxy(LeaseRepository.class, (p, method, args) -> {
            if ("countByBorrowerIdAndStateIn".equals(method.getName())) {
                return activeLeaseCount;
            }
            if ("findByNodeIdAndStateIn".equals(method.getName())) {
                return existingNodeLeases;
            }
            if ("save".equals(method.getName()) && args != null && args.length > 0) {
                return args[0];
            }
            return null;
        });

        offerRepository = proxy(OfferRepository.class, (p, method, args) -> {
            if ("findByIdForUpdate".equals(method.getName())) {
                return Optional.ofNullable(currentOffer);
            }
            return null;
        });

        nodeRepository = proxy(NodeRepository.class, (p, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(currentNode);
            }
            return null;
        });

        userRepository = proxy(UserRepository.class, (p, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(currentUser);
            }
            return null;
        });

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
        currentUser = createBorrower(false);
        currentOffer = createOffer(false);
        currentNode = createNode(NodeStatus.ONLINE);
        activeLeaseCount = 0;
        existingNodeLeases = Collections.emptyList();

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
        currentUser = createBorrower(true);
        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(IllegalArgumentException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @Test
    @DisplayName("Create lease rejected when max active leases exceeded")
    void testCreateLeaseMaxActiveExceeded() {
        currentUser = createBorrower(false);
        activeLeaseCount = 1;

        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(CapacityExceededException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @Test
    @DisplayName("Create lease rejected when node is offline")
    void testCreateLeaseNodeOffline() {
        currentUser = createBorrower(false);
        currentOffer = createOffer(false);
        currentNode = createNode(NodeStatus.OFFLINE);
        activeLeaseCount = 0;

        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(IllegalArgumentException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @Test
    @DisplayName("Create lease rejected when node capacity exceeded")
    void testCreateLeaseCapacityExceeded() {
        currentUser = createBorrower(false);
        currentOffer = createOffer(false);
        currentNode = createNode(NodeStatus.ONLINE);
        activeLeaseCount = 0;

        Lease existing = new Lease(
                UUID.randomUUID(), offerId, UUID.randomUUID(), nodeId,
                7000, 3, 80, 2, LeaseState.ACTIVE,
                "lease-1", 33001, "minidc", "enc", null, null, Instant.now(), Instant.now(), null
        );
        existingNodeLeases = List.of(existing);

        // 7000 + 2048 = 9048 > offer max 8192
        CreateLeaseRequest request = new CreateLeaseRequest(offerId, 2048, 2, 20, 2);
        assertThrows(CapacityExceededException.class, () -> leaseService.createLease(borrowerId, request));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }
}
