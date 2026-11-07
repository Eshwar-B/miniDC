package com.minidc.common.ws.payload;

import java.util.UUID;

/**
 * Acknowledgment payload sent by an agent after destroying a lease VM.
 */
public record VmDestroyedPayload(
        UUID leaseId
) {
}
