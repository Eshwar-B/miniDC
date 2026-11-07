package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests validating state machine transition rules and guardrails for VM leases.
 */
@ExtendWith(MockitoExtension.class)
class LeaseStateMachineTest {

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
    private final AppProperties properties = new AppProperties("secret12345678901234567890123456", 12, "Y2hhbmdlLW1lLWNoYW5nZS1tZS1jaGFuZ2UtbWUtMzI=", "http://localhost:8080/guacamole", "secret", 1, true);

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

    private Lease createLease(LeaseState state) {
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
                "encPassword",
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
        Lease lease = createLease(LeaseState.REQUESTED);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.transition(lease.getId(), LeaseState.APPROVED, "approved");
        assertEquals(LeaseState.APPROVED, result.getState());
        verify(auditService).log(any(), any(), any(), org.mockito.ArgumentMatchers.eq("LEASE_APPROVED"), org.mockito.ArgumentMatchers.eq("approved"));
    }

    @Test
    @DisplayName("Valid transition: APPROVED -> PROVISIONING triggers create VM command")
    void testApprovedToProvisioning() {
        Lease lease = createLease(LeaseState.APPROVED);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aesGcmCrypto.decrypt("encPassword")).thenReturn("plainPass");

        Lease result = leaseService.transition(lease.getId(), LeaseState.PROVISIONING, "start provisioning");
        assertEquals(LeaseState.PROVISIONING, result.getState());
        verify(agentCommandSender).sendCreateVm(any(), any());
    }

    @Test
    @DisplayName("Valid transition: PROVISIONING -> ACTIVE")
    void testProvisioningToActive() {
        Lease lease = createLease(LeaseState.PROVISIONING);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.transition(lease.getId(), LeaseState.ACTIVE, "vm ready");
        assertEquals(LeaseState.ACTIVE, result.getState());
    }

    @Test
    @DisplayName("Valid transition: ACTIVE -> ENDED triggers destroy VM command")
    void testActiveToEnded() {
        Lease lease = createLease(LeaseState.ACTIVE);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.transition(lease.getId(), LeaseState.ENDED, "finished");
        assertEquals(LeaseState.ENDED, result.getState());
        verify(agentCommandSender).sendDestroyVm(lease.getNodeId(), lease.getId());
    }

    @Test
    @DisplayName("Invalid transition: REQUESTED -> ACTIVE throws InvalidStateException")
    void testInvalidTransitionRequestedToActive() {
        Lease lease = createLease(LeaseState.REQUESTED);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));

        assertThrows(InvalidStateException.class, () ->
                leaseService.transition(lease.getId(), LeaseState.ACTIVE, "invalid")
        );
    }

    @Test
    @DisplayName("Invalid transition: ENDED -> ACTIVE throws InvalidStateException")
    void testInvalidTransitionEndedToActive() {
        Lease lease = createLease(LeaseState.ENDED);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));

        assertThrows(InvalidStateException.class, () ->
                leaseService.transition(lease.getId(), LeaseState.ACTIVE, "invalid")
        );
    }
}

