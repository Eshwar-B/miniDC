package com.minidc.common.ws.payload;

/**
 * Payload sent by an agent during initial node registration.
 */
public record RegisterPayload(
        int ramMb,
        int cores,
        int diskGb,
        String address,
        String agentVersion,
        boolean kvmOk
) {
}
