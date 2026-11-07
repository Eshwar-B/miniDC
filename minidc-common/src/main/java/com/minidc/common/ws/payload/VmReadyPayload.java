package com.minidc.common.ws.payload;

import java.util.UUID;

/**
 * Notification payload emitted by an agent when a virtual machine is fully reachable.
 */
public record VmReadyPayload(
        UUID leaseId,
        String vmName,
        int port
) {
}
