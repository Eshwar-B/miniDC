package com.minidc.agent.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.HostStats;
import com.minidc.common.model.HostInfo;
import com.minidc.common.ws.MessageType;
import com.minidc.common.ws.WsMessage;
import com.minidc.common.ws.payload.RegisterPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages outbound persistent WebSocket connection to the MiniDC control server with automated exponential backoff reconnects.
 */
@Component
public class ServerConnection extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ServerConnection.class);

    private final AgentProperties properties;
    private final HostStats hostStats;
    private final CommandDispatcher commandDispatcher;
    private final ObjectMapper objectMapper;

    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger backoffSeconds = new AtomicInteger(1);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile WebSocketSession session;

    public ServerConnection(AgentProperties properties,
                            HostStats hostStats,
                            CommandDispatcher commandDispatcher,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.hostStats = hostStats;
        this.commandDispatcher = commandDispatcher;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        connect();
    }

    public synchronized void connect() {
        if (session != null && session.isOpen()) {
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            return;
        }

        try {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("X-Node-Id", properties.nodeId().toString());
            headers.add("X-Agent-Token", properties.agentToken());

            URI uri = URI.create(properties.serverUrl());
            log.info("Connecting outbound WebSocket to {}", uri);

            client.execute(this, headers, uri).whenComplete((s, err) -> {
                connecting.set(false);
                if (err != null) {
                    log.warn("WebSocket connection failed: {}. Retrying in {}s", err.getMessage(), backoffSeconds.get());
                    scheduleReconnect();
                } else {
                    this.session = s;
                    backoffSeconds.set(1);
                    log.info("WebSocket connected to {}", uri);
                }
            });
        } catch (Exception e) {
            connecting.set(false);
            log.warn("Failed initiating WebSocket connection: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        int delay = backoffSeconds.getAndUpdate(cur -> Math.min(cur * 2, 60));
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        log.info("WebSocket connection established with control server");

        HostInfo host = hostStats.getHostInfo();
        RegisterPayload registerPayload = new RegisterPayload(
                host.totalRamMb(),
                host.totalCores(),
                host.freeDiskGb(),
                properties.bindAddress(),
                "1.0.0",
                host.virtualizationOk()
        );
        send(MessageType.REGISTER, registerPayload);
        commandDispatcher.onReconnectReconcile();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.session = null;
        log.warn("WebSocket closed: {}. Scheduling reconnect", status);
        scheduleReconnect();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsMessage wsMessage = objectMapper.readValue(message.getPayload(), WsMessage.class);
        commandDispatcher.dispatch(wsMessage);
    }

    public boolean send(MessageType type, Object payload) {
        WebSocketSession s = this.session;
        if (s == null || !s.isOpen()) {
            log.warn("Cannot send {}: WebSocket is closed", type);
            return false;
        }

        try {
            JsonNode payloadNode = payload != null ? objectMapper.valueToTree(payload) : null;
            WsMessage message = new WsMessage(type, UUID.randomUUID().toString(), payloadNode);
            String json = objectMapper.writeValueAsString(message);
            s.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException e) {
            log.error("Failed sending message {}", type, e);
            return false;
        }
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }
}

