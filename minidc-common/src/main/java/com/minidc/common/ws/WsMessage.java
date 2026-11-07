package com.minidc.common.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope for JSON messages transferred over the agent-server WebSocket channel.
 */
public record WsMessage(
        MessageType type,
        String id,
        JsonNode payload
) {
}
