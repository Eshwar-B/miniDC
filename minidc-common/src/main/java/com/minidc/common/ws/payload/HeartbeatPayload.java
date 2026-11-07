package com.minidc.common.ws.payload;

/**
 * Periodic telemetry payload dispatched by active node agents.
 */
public record HeartbeatPayload(
        int freeRamMb,
        double load1,
        int activeVms,
        int diskFreeGb
) {
}
