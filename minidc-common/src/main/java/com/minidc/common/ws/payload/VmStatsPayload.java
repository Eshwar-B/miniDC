package com.minidc.common.ws.payload;

import java.util.UUID;

/**
 * Metric sample reporting current CPU and memory utilization for a guest VM.
 */
public record VmStatsPayload(
        UUID leaseId,
        double cpuPct,
        int memMb
) {
}
