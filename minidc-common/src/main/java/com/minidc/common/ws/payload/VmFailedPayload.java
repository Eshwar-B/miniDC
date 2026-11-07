package com.minidc.common.ws.payload;

import java.util.UUID;

/**
 * Failure notification payload detailing why VM provisioning or runtime failed.
 */
public record VmFailedPayload(
        UUID leaseId,
        String reason
) {
}
