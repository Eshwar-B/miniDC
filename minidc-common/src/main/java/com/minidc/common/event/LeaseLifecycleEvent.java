package com.minidc.common.event;

import com.minidc.common.enums.LeaseState;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to minidc.lease.lifecycle whenever a lease transitions state.
 */
public record LeaseLifecycleEvent(
        UUID leaseId,
        UUID offerId,
        UUID borrowerId,
        UUID nodeId,
        LeaseState state,
        String reason,
        Instant timestamp
) {
    public LeaseLifecycleEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
