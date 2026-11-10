package com.minidc.common.event;

import com.minidc.common.enums.AbuseType;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when anomaly detection algorithms confirm sandbox abuse.
 */
public record AbuseAlertEvent(
        UUID alertId,
        UUID leaseId,
        UUID nodeId,
        AbuseType type,
        double confidenceScore,
        String details,
        Instant detectedAt,
        boolean autoTerminated
) {
    public AbuseAlertEvent {
        if (alertId == null) {
            alertId = UUID.randomUUID();
        }
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }
}

