package com.minidc.common.ws.payload;

import java.util.UUID;

/**
 * Instruction payload directing an agent to spin up a guest virtual machine.
 */
public record CreateVmPayload(
        UUID leaseId,
        int ramMb,
        int cores,
        int diskGb,
        int hours,
        String vmUser,
        String vmPassword
) {
}
