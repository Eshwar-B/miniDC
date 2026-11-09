package com.minidc.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to minidc.telemetry.metrics capturing fine-grained CPU, RAM, and storage utilization.
 */
public record TelemetryMetricEvent(
        UUID nodeId,
        UUID leaseId,
        double cpuPercent,
        long usedRamMb,
        long totalRamMb,
        long diskUsedGb,
        Instant timestamp
) {
    public TelemetryMetricEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
