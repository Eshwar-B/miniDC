package com.minidc.server.kafka;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.event.ReconciliationReport;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReconciliationServiceTest {

    private LeaseRepository leaseRepository;
    private KafkaEventPublisher kafkaEventPublisher;
    private AuditService auditService;
    private AgentReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        leaseRepository = Mockito.mock(LeaseRepository.class);
        kafkaEventPublisher = Mockito.mock(KafkaEventPublisher.class);
        auditService = Mockito.mock(AuditService.class);
        reconciliationService = new AgentReconciliationService(leaseRepository, kafkaEventPublisher, auditService);
    }

    @Test
    void shouldDetectOrphanVmsWhenNotExpectedInDatabase() {
        UUID nodeId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        Lease activeLease = new Lease();
        activeLease.setId(leaseId);
        activeLease.setNodeId(nodeId);
        activeLease.setState(LeaseState.ACTIVE);
        activeLease.setVmName("lease-12345678");

        when(leaseRepository.findByNodeIdAndState(nodeId, LeaseState.ACTIVE))
                .thenReturn(List.of(activeLease));

        ReconciliationReport report = new ReconciliationReport(
                nodeId,
                List.of("lease-12345678", "lease-99999999-orphan"),
                List.of(),
                List.of(),
                Instant.now()
        );

        ReconciliationReport result = reconciliationService.processReport(report);

        assertEquals(1, result.orphanVmNames().size());
        assertEquals("lease-99999999-orphan", result.orphanVmNames().get(0));
        verify(auditService).log(any(), any(), any(), Mockito.eq("RECONCILIATION_COMPLETED"), any());
    }

    @Test
    void shouldReportZeroOrphansWhenAllRunningVmsMatchActiveLeases() {
        UUID nodeId = UUID.randomUUID();
        Lease activeLease = new Lease();
        activeLease.setId(UUID.randomUUID());
        activeLease.setNodeId(nodeId);
        activeLease.setState(LeaseState.ACTIVE);
        activeLease.setVmName("lease-valid-vm");

        when(leaseRepository.findByNodeIdAndState(nodeId, LeaseState.ACTIVE))
                .thenReturn(List.of(activeLease));

        ReconciliationReport report = new ReconciliationReport(
                nodeId,
                List.of("lease-valid-vm"),
                List.of(),
                List.of(),
                Instant.now()
        );

        ReconciliationReport result = reconciliationService.processReport(report);

        assertTrue(result.orphanVmNames().isEmpty());
    }
}
