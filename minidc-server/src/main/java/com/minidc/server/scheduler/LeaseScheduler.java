package com.minidc.server.scheduler;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.NodeStatus;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.lease.LeaseService;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled daemon task monitoring node health, lease lifespans, and automated state expiries.
 */
@Component
public class LeaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaseScheduler.class);

    private final NodeRepository nodeRepository;
    private final LeaseRepository leaseRepository;
    private final LeaseService leaseService;
    private final AuditService auditService;

    public LeaseScheduler(NodeRepository nodeRepository,
                          LeaseRepository leaseRepository,
                          LeaseService leaseService,
                          AuditService auditService) {
        this.nodeRepository = nodeRepository;
        this.leaseRepository = leaseRepository;
        this.leaseService = leaseService;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelay = 15000)
    public void tick() {
        Instant now = Instant.now();

        // 1. Mark nodes OFFLINE after 45s without heartbeat
        Instant heartbeatCutoff = now.minus(45, ChronoUnit.SECONDS);
        List<Node> deadNodes = nodeRepository.findByStatusAndLastHeartbeatBefore(NodeStatus.ONLINE, heartbeatCutoff);
        for (Node n : deadNodes) {
            n.setStatus(NodeStatus.OFFLINE);
            nodeRepository.save(n);
            auditService.log(null, null, n.getId(), "NODE_OFFLINE", "Node missed heartbeats for >45s");
            log.info("Marked node {} OFFLINE due to missed heartbeats", n.getId());
        }

        // 2. ACTIVE to EXPIRING 5 minutes before end
        Instant expiringCutoff = now.plus(5, ChronoUnit.MINUTES);
        List<Lease> nearEnd = leaseRepository.findByStateAndEndsAtBefore(LeaseState.ACTIVE, expiringCutoff);
        for (Lease l : nearEnd) {
            try {
                leaseService.transition(l.getId(), LeaseState.EXPIRING, "Approaching scheduled lease end time");
            } catch (Exception e) {
                log.error("Failed transition of lease {} to EXPIRING", l.getId(), e);
            }
        }

        // 3. End leases past ends_at
        List<Lease> expired = leaseRepository.findByStateInAndEndsAtBefore(
                List.of(LeaseState.ACTIVE, LeaseState.EXPIRING), now
        );
        for (Lease l : expired) {
            try {
                leaseService.transition(l.getId(), LeaseState.ENDED, "Lease duration expired");
            } catch (Exception e) {
                log.error("Failed ending expired lease {}", l.getId(), e);
            }
        }

        // 4. Reject REQUESTED older than 10 minutes
        Instant requestedTimeout = now.minus(10, ChronoUnit.MINUTES);
        List<Lease> staleRequested = leaseRepository.findByStateAndCreatedAtBefore(LeaseState.REQUESTED, requestedTimeout);
        for (Lease l : staleRequested) {
            try {
                leaseService.transition(l.getId(), LeaseState.REJECTED, "Approval timed out after 10 minutes");
            } catch (Exception e) {
                log.error("Failed rejecting stale lease request {}", l.getId(), e);
            }
        }

        // 5. Fail PROVISIONING older than 5 minutes
        Instant provTimeout = now.minus(5, ChronoUnit.MINUTES);
        List<Lease> staleProv = leaseRepository.findByStateAndUpdatedAtBefore(LeaseState.PROVISIONING, provTimeout);
        for (Lease l : staleProv) {
            try {
                leaseService.transition(l.getId(), LeaseState.FAILED, "VM provisioning timed out after 5 minutes");
            } catch (Exception e) {
                log.error("Failed failing stale provisioning lease {}", l.getId(), e);
            }
        }

        // 6. End ACTIVE leases on nodes offline longer than 3 minutes
        Instant offline3MinCutoff = now.minus(3, ChronoUnit.MINUTES);
        List<Lease> offlineNodeLeases = leaseRepository.findActiveOnOfflineNodes(offline3MinCutoff);
        for (Lease l : offlineNodeLeases) {
            try {
                leaseService.transition(l.getId(), LeaseState.ENDED, "Host node offline for more than 3 minutes");
            } catch (Exception e) {
                log.error("Failed ending lease {} on offline node", l.getId(), e);
            }
        }
    }
}
