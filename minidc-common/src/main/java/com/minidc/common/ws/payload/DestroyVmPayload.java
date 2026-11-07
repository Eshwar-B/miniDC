package com.minidc.common.ws.payload;

import java.util.UUID;

/**
 * Command payload directing an agent to terminate and purge a guest virtual machine.
 */
public record DestroyVmPayload(
        UUID leaseId
) {
}
