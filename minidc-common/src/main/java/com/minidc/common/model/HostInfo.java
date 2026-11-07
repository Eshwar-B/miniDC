package com.minidc.common.model;

/**
 * Hardware capacity and virtualization capability details of a host node.
 */
public record HostInfo(
        int totalRamMb,
        int freeRamMb,
        int totalCores,
        int freeDiskGb,
        boolean virtualizationOk
) {
}
