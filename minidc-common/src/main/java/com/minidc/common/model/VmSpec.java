package com.minidc.common.model;

import java.util.UUID;

/**
 * Specification parameters required to configure and boot a guest virtual machine.
 */
public record VmSpec(
        UUID leaseId,
        String vmName,
        int ramMb,
        int cores,
        int diskGb,
        int hours,
        String vmUser,
        String vmPassword,
        int hostPort,
        String bindAddress
) {
}
