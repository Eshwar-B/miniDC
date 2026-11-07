package com.minidc.server.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.ws.MessageType;
import com.minidc.common.ws.WsMessage;
import com.minidc.common.ws.payload.CreateVmPayload;
import com.minidc.common.ws.payload.DestroyVmPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Dispatches management instructions (such as CREATE_VM and DESTROY_VM) to connected agents.
 */
@Component
public class AgentCommandSender {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandSender.class);

    private final AgentSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public AgentCommandSender(AgentSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    public boolean sendCreateVm(UUID nodeId, CreateVmPayload payload) {
        return send(nodeId, MessageType.CREATE_VM, payload);
    }

    public boolean sendDestroyVm(UUID nodeId, UUID leaseId) {
        return send(nodeId, MessageType.DESTROY_VM, new DestroyVmPayload(leaseId));
    }

    public boolean sendPauseNode(UUID nodeId) {
        return send(nodeId, MessageType.PAUSE_NODE, null);
    }

    public boolean sendResumeNode(UUID nodeId) {
        return send(nodeId, MessageType.RESUME_NODE, null);
    }

    private boolean send(UUID nodeId, MessageType type, Object payloadObj) {
        Optional<WebSocketSession> sessionOpt = sessionRegistry.getSession(nodeId);
        if (sessionOpt.isEmpty()) {
            log.warn("Cannot send {} to node {}: session not connected", type, nodeId);
            return false;
        }

        try {
            JsonNode payloadNode = payloadObj != null ? objectMapper.valueToTree(payloadObj) : null;
            WsMessage msg = new WsMessage(type, UUID.randomUUID().toString(), payloadNode);
            String json = objectMapper.writeValueAsString(msg);
            sessionOpt.get().sendMessage(new TextMessage(json));
            return true;
        } catch (IOException e) {
            log.error("Failed to send message {} to node {}", type, nodeId, e);
            return false;
        }
    }
}
