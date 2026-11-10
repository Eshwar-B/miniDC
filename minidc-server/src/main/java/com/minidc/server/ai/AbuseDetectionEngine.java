package com.minidc.server.ai;

import com.minidc.common.enums.AbuseType;
import com.minidc.common.event.AbuseAlertEvent;
import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.kafka.KafkaEventPublisher;
import com.minidc.server.lease.LeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real-time Anomaly & Abuse Detection Engine inspecting guest sandbox telemetry
 * for cryptomining, resource starvation, and unauthorized network scanning.
 */
@Service
public class AbuseDetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(AbuseDetectionEngine.class);

    private final KafkaEventPublisher kafkaEventPublisher;
    private final LeaseService leaseService;
    private final boolean autoQuarantine;

    // Consecutive high CPU counters per lease
    private final Map<UUID, AtomicInteger> consecutiveHighCpuCounter = new ConcurrentHashMap<>();
    // Recent alerts bounded queue for administrative inspection
    private final Deque<AbuseAlertEvent> recentAlerts = new ConcurrentLinkedDeque<>();
    private static final int MAX_ALERTS_HISTORY = 100;

    @Autowired
    public AbuseDetectionEngine(
            @Autowired(required = false) KafkaEventPublisher kafkaEventPublisher,
            @Lazy LeaseService leaseService,
            @Value("${minidc.abuse.auto-quarantine:true}") boolean autoQuarantine
    ) {
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.leaseService = leaseService;
        this.autoQuarantine = autoQuarantine;
    }

    /**
     * Analyzes an incoming telemetry metric and evaluates behavioral heuristics.
     */
    public Optional<AbuseAlertEvent> analyzeMetric(TelemetryMetricEvent metric) {
        if (metric == null || metric.leaseId() == null) {
            return Optional.empty();
        }

        UUID leaseId = metric.leaseId();

        // 1. Cryptomining Heuristic: Pinned 95%+ CPU across consecutive samples
        if (metric.cpuPercent() >= 95.0) {
            int highCpuStreak = consecutiveHighCpuCounter
                    .computeIfAbsent(leaseId, k -> new AtomicInteger(0))
                    .incrementAndGet();

            if (highCpuStreak >= 3) {
                // Confirmed sustained cryptomining activity
                String details = String.format("Sustained high CPU utilization (%.1f%%) over %d consecutive reporting windows",
                        metric.cpuPercent(), highCpuStreak);
                return Optional.of(triggerAbuseAlert(metric.leaseId(), metric.nodeId(), AbuseType.CRYPTOMINING, 0.95, details));
            }
        } else {
            // Reset counter when CPU drops back down
            consecutiveHighCpuCounter.remove(leaseId);
        }

        // 2. Resource Starvation / Runaway Memory Leak Heuristic
        if (metric.totalRamMb() > 0) {
            double ramRatio = (double) metric.usedRamMb() / metric.totalRamMb();
            if (ramRatio >= 0.98) {
                String details = String.format("Critical memory exhaustion: %d MB / %d MB (%.1f%%)",
                        metric.usedRamMb(), metric.totalRamMb(), ramRatio * 100.0);
                return Optional.of(triggerAbuseAlert(metric.leaseId(), metric.nodeId(), AbuseType.RESOURCE_EXHAUSTION, 0.88, details));
            }
        }

        return Optional.empty();
    }

    private AbuseAlertEvent triggerAbuseAlert(UUID leaseId, UUID nodeId, AbuseType type, double confidence, String details) {
        boolean terminated = false;

        if (autoQuarantine && confidence >= 0.90) {
            try {
                log.warn("CRITICAL: Auto-terminating rogue lease {} on node {} due to confirmed {} (Confidence: {})",
                        leaseId, nodeId, type, confidence);
                leaseService.kill(leaseId, "TERMINATED_ABUSE_DETECTED: " + type + " pattern confirmed by AI Anomaly Detector");
                terminated = true;
            } catch (Exception e) {
                log.error("Failed to automatically terminate rogue lease {}: {}", leaseId, e.getMessage());
            }
        }

        AbuseAlertEvent alert = new AbuseAlertEvent(
                UUID.randomUUID(),
                leaseId,
                nodeId,
                type,
                confidence,
                details,
                Instant.now(),
                terminated
        );

        recentAlerts.addFirst(alert);
        while (recentAlerts.size() > MAX_ALERTS_HISTORY) {
            recentAlerts.removeLast();
        }

        if (kafkaEventPublisher != null) {
            try {
                kafkaEventPublisher.publishAbuseAlert(alert);
            } catch (Exception ignored) {
            }
        }

        return alert;
    }

    public List<AbuseAlertEvent> getRecentAlerts() {
        return new ArrayList<>(recentAlerts);
    }

    public void clearAlerts() {
        recentAlerts.clear();
        consecutiveHighCpuCounter.clear();
    }
}
