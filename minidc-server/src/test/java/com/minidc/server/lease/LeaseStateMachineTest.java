package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.ws.payload.CreateVmPayload;
import com.minidc.server.audit.AuditService;
import com.minidc.server.config.AppProperties;
import com.minidc.server.crypto.AesGcmCrypto;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.OfferRepository;
import com.minidc.server.user.UserRepository;
import com.minidc.server.ws.AgentCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests validating state machine transition rules and guardrails for VM leases.
 */
class LeaseStateMachineTest {

    private LeaseRepository leaseRepository;
    private OfferRepository offerRepository;
    private NodeRepository nodeRepository;
    private UserRepository userRepository;
    private TestAuditService auditService;
    private AesGcmCrypto aesGcmCrypto;
    private TestAgentCommandSender agentCommandSender;

    private LeaseService leaseService;
    private Lease currentLease;

    private final AppProperties properties = new AppProperties(
            "secret12345678901234567890123456",
            12,
            "Y2hhbmdlLW1lLWNoYW5nZS1tZS1jaGFuZ2UtbWUtMzI=",
            "http://localhost:8080/guacamole",
            "secret",
            1,
            true,
            null,
            null
    );

    @BeforeEach
    void setUp() {
        aesGcmCrypto = new AesGcmCrypto(properties);
        auditService = new TestAuditService();
        agentCommandSender = new TestAgentCommandSender();

        leaseRepository = proxy(LeaseRepository.class, (p, method, args) -> {
            if ("findById".equals(method.getName())) {
                return Optional.ofNullable(currentLease);
            }
            if ("save".equals(method.getName()) && args != null && args.length > 0) {
                currentLease = (Lease) args[0];
                return currentLease;
            }
            return null;
        });

        offerRepository = proxy(OfferRepository.class, (p, m, a) -> null);
        nodeRepository = proxy(NodeRepository.class, (p, m, a) -> null);
        userRepository = proxy(UserRepository.class, (p, m, a) -> null);

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

    private Lease createLease(LeaseState state) {
        String encPassword = aesGcmCrypto.encrypt("plainPassword123");
        return new Lease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2048,
                2,
                20,
                2,
                state,
                "lease-test",
                null,
                "minidc",
                encPassword,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null
        );
    }

    @Test
    @DisplayName("Valid transition: REQUESTED -> APPROVED")
    void testRequestedToApproved() {
        currentLease = createLease(LeaseState.REQUESTED);
        Lease result = leaseService.transition(currentLease.getId(), LeaseState.APPROVED, "approved");
        assertEquals(LeaseState.APPROVED, result.getState());
        assertEquals("LEASE_APPROVED", auditService.lastEvent);
    }

    @Test
    @DisplayName("Valid transition: APPROVED -> PROVISIONING triggers create VM command")
    void testApprovedToProvisioning() {
        currentLease = createLease(LeaseState.APPROVED);
        Lease result = leaseService.transition(currentLease.getId(), LeaseState.PROVISIONING, "start provisioning");
        assertEquals(LeaseState.PROVISIONING, result.getState());
        assertNotNull(agentCommandSender.lastCreateVmPayload);
        assertEquals(currentLease.getId(), agentCommandSender.lastCreateVmPayload.leaseId());
    }

    @Test
    @DisplayName("Valid transition: PROVISIONING -> ACTIVE")
    void testProvisioningToActive() {
        currentLease = createLease(LeaseState.PROVISIONING);
        Lease result = leaseService.transition(currentLease.getId(), LeaseState.ACTIVE, "vm ready");
        assertEquals(LeaseState.ACTIVE, result.getState());
    }

    @Test
    @DisplayName("Valid transition: ACTIVE -> ENDED triggers destroy VM command")
    void testActiveToEnded() {
        currentLease = createLease(LeaseState.ACTIVE);
        Lease result = leaseService.transition(currentLease.getId(), LeaseState.ENDED, "finished");
        assertEquals(LeaseState.ENDED, result.getState());
        assertEquals(currentLease.getId(), agentCommandSender.lastDestroyVmLeaseId);
    }

    @Test
    @DisplayName("Invalid transition: REQUESTED -> ACTIVE throws InvalidStateException")
    void testInvalidTransitionRequestedToActive() {
        currentLease = createLease(LeaseState.REQUESTED);
        assertThrows(InvalidStateException.class, () ->
                leaseService.transition(currentLease.getId(), LeaseState.ACTIVE, "invalid")
        );
    }

    @Test
    @DisplayName("Invalid transition: ENDED -> ACTIVE throws InvalidStateException")
    void testInvalidTransitionEndedToActive() {
        currentLease = createLease(LeaseState.ENDED);
        assertThrows(InvalidStateException.class, () ->
                leaseService.transition(currentLease.getId(), LeaseState.ACTIVE, "invalid")
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }

    private static class TestAuditService extends AuditService {
        String lastEvent;
        String lastDetail;

        TestAuditService() {
            super(null);
        }

        @Override
        public void log(UUID actorId, UUID leaseId, UUID nodeId, String event, String detail) {
            this.lastEvent = event;
            this.lastDetail = detail;
        }
    }

    private static class TestAgentCommandSender extends AgentCommandSender {
        UUID lastCreateVmNodeId;
        CreateVmPayload lastCreateVmPayload;
        UUID lastDestroyVmNodeId;
        UUID lastDestroyVmLeaseId;

        TestAgentCommandSender() {
            super(null, null);
        }

        @Override
        public boolean sendCreateVm(UUID nodeId, CreateVmPayload payload) {
            this.lastCreateVmNodeId = nodeId;
            this.lastCreateVmPayload = payload;
            return true;
        }

        @Override
        public boolean sendDestroyVm(UUID nodeId, UUID leaseId) {
            this.lastDestroyVmNodeId = nodeId;
            this.lastDestroyVmLeaseId = leaseId;
            return true;
        }
    }
}
