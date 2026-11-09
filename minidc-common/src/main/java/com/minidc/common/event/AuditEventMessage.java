package com.minidc.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to minidc.audit.events for asynchronous security audit processing.
 */
public record AuditEventMessage(
        UUID actorId,
        UUID leaseId,
        UUID nodeId,
        String event,
        String detail,
        Instant timestamp
) {
    public AuditEventMessage {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}

