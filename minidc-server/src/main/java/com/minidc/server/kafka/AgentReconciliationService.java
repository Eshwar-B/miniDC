package com.minidc.server.kafka;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.event.KafkaTopics;
import com.minidc.common.event.ReconciliationReport;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service that handles hypervisor reconciliation reports from edge agents.
 * Detects and coordinates cleanup of zombie and orphan VMs after agent power outages or crashes.
 */
@Service
public class AgentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AgentReconciliationService.class);

    private final LeaseRepository leaseRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final AuditService auditService;

    public AgentReconciliationService(LeaseRepository leaseRepository,
                                      KafkaEventPublisher kafkaEventPublisher,
                                      AuditService auditService) {
        this.leaseRepository = leaseRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.auditService = auditService;
    }

    @PostConstruct
    public void init() {
        kafkaEventPublisher.registerInMemoryListener(KafkaTopics.TOPIC_RECONCILIATION, this::handleReconciliationObj);
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_RECONCILIATION, groupId = "minidc-reconcile-group", autoStartup = "${minidc.kafka.enabled:false}")
    public void onKafkaMessage(ReconciliationReport report) {
        processReport(report);
    }

    private void handleReconciliationObj(Object obj) {
        if (obj instanceof ReconciliationReport report) {
            processReport(report);
        }
    }

    @Transactional
    public ReconciliationReport processReport(ReconciliationReport report) {
        UUID nodeId = report.nodeId();
        List<Lease> activeLeases = leaseRepository.findByNodeIdAndState(nodeId, LeaseState.ACTIVE);
        Set<String> expectedVmNames = activeLeases.stream()
                .map(Lease::getVmName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        List<String> actualRunning = report.runningVmNames() != null ? report.runningVmNames() : List.of();
        List<String> identifiedOrphans = new ArrayList<>();

        for (String vmName : actualRunning) {
            if (!expectedVmNames.contains(vmName)) {
                identifiedOrphans.add(vmName);
            }
        }

        log.info("Reconciliation for node {}: Expected {} active VMs, found {} running, identified {} orphans, agent cleaned {}",
                nodeId, expectedVmNames.size(), actualRunning.size(), identifiedOrphans.size(), report.cleanedVmNames().size());

        auditService.log(null, null, nodeId, "RECONCILIATION_COMPLETED",
                String.format("Expected: %d, Running: %d, Orphans: %d, Cleaned: %d",
                        expectedVmNames.size(), actualRunning.size(), identifiedOrphans.size(), report.cleanedVmNames().size()));

        ReconciliationReport updatedReport = new ReconciliationReport(
                nodeId,
                actualRunning,
                identifiedOrphans,
                report.cleanedVmNames(),
                Instant.now()
        );

        return updatedReport;
    }
}

