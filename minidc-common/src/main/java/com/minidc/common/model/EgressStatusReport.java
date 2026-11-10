package com.minidc.common.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Edge node status report confirming firewall isolation and WireGuard tunnel health.
 */
public record EgressStatusReport(
        UUID nodeId,
        boolean lanIsolated,
        boolean wireguardActive,
        String assignedIp,
        String exitGateway,
        String firewallEngine,
        Instant reportedAt
) {
}
