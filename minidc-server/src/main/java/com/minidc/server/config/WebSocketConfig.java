package com.minidc.server.config;

import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.ws.AgentSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Configures WebSocket endpoints and handshake authentication for agent-to-server connections.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final AgentSocketHandler agentSocketHandler;
    private final NodeRepository nodeRepository;
    private final PasswordEncoder passwordEncoder;

    public WebSocketConfig(AgentSocketHandler agentSocketHandler,
                           NodeRepository nodeRepository,
                           PasswordEncoder passwordEncoder) {
        this.agentSocketHandler = agentSocketHandler;
        this.nodeRepository = nodeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentSocketHandler, "/ws/agent")
                .setAllowedOrigins("*")
                .addInterceptors(new AgentHandshakeInterceptor());
    }

    private class AgentHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            String nodeIdHeader = request.getHeaders().getFirst("X-Node-Id");
            String agentTokenHeader = request.getHeaders().getFirst("X-Agent-Token");

            if (nodeIdHeader == null || agentTokenHeader == null) {
                log.warn("Rejected agent connection: missing X-Node-Id or X-Agent-Token header");
                return false;
            }

            try {
                UUID nodeId = UUID.fromString(nodeIdHeader);
                Node node = nodeRepository.findById(nodeId).orElse(null);
                if (node == null) {
                    log.warn("Rejected agent connection: node {} not found", nodeId);
                    return false;
                }

                if (!passwordEncoder.matches(agentTokenHeader, node.getTokenHash())) {
                    log.warn("Rejected agent connection: token mismatch for node {}", nodeId);
                    return false;
                }

                attributes.put("nodeId", nodeId);
                return true;
            } catch (IllegalArgumentException e) {
                log.warn("Rejected agent connection: invalid UUID format for node {}", nodeIdHeader);
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Exception exception) {
        }
    }
}
