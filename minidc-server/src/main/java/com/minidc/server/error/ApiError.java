package com.minidc.server.error;

import java.time.Instant;

/**
 * Standard API error payload returned for failed REST operations.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message
) {
}
