package com.minidc.server.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry maintaining active WebSocket sessions connected by compute node agents.
 */
@Component
public class AgentSessionRegistry {

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(UUID nodeId, WebSocketSession session) {
        sessions.put(nodeId, session);
    }

    public void unregister(UUID nodeId) {
        sessions.remove(nodeId);
    }

    public Optional<WebSocketSession> getSession(UUID nodeId) {
        WebSocketSession session = sessions.get(nodeId);
        if (session != null && session.isOpen()) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    public boolean isConnected(UUID nodeId) {
        return getSession(nodeId).isPresent();
    }

    public Map<UUID, WebSocketSession> getAllSessions() {
        return Map.copyOf(sessions);
    }
}
