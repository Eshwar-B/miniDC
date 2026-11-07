package com.minidc.server.auth.dto;

/**
 * Response payload returning an issued JWT bearer token.
 */
public record TokenResponse(
        String token
) {
}
