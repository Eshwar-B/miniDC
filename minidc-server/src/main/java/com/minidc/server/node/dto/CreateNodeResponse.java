package com.minidc.server.node.dto;

import java.util.UUID;

/**
 * Response payload containing newly generated node identifier and one-time plain agent token.
 */
public record CreateNodeResponse(
        UUID nodeId,
        String agentToken
) {
}
