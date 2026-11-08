package com.minidc.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.NodeStatus;
import com.minidc.common.ws.WsMessage;
import com.minidc.common.ws.payload.HeartbeatPayload;
import com.minidc.common.ws.payload.RegisterPayload;
import com.minidc.common.ws.payload.VmDestroyedPayload;
import com.minidc.common.ws.payload.VmFailedPayload;
import com.minidc.common.ws.payload.VmReadyPayload;
import com.minidc.common.ws.payload.VmStatsPayload;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.lease.LeaseService;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles incoming WebSocket frames from connected agents, maintaining node heartbeats and VM status transitions.
 */
@Component
public class AgentSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentSocketHandler.class);

    private final AgentSessionRegistry sessionRegistry;
    private final NodeRepository nodeRepository;
    private final LeaseRepository leaseRepository;
    private final LeaseService leaseService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.minidc.server.redis.RedisStateService redisStateService;

    public AgentSocketHandler(AgentSessionRegistry sessionRegistry,
                              NodeRepository nodeRepository,
                              LeaseRepository leaseRepository,
                              LeaseService leaseService,
                              AuditService auditService,
                              ObjectMapper objectMapper,
                              com.minidc.server.redis.RedisStateService redisStateService) {
        this.sessionRegistry = sessionRegistry;
        this.nodeRepository = nodeRepository;
        this.leaseRepository = leaseRepository;
        this.leaseService = leaseService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.redisStateService = redisStateService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID nodeId = (UUID) session.getAttributes().get("nodeId");
        if (nodeId != null) {
            sessionRegistry.register(nodeId, session);
            redisStateService.recordNodeHeartbeat(nodeId, java.time.Duration.ofSeconds(60));
            log.info("Agent connected for node: {}", nodeId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID nodeId = (UUID) session.getAttributes().get("nodeId");
        if (nodeId != null) {
            sessionRegistry.unregister(nodeId);
            redisStateService.delete("minidc:node:presence:" + nodeId);
            log.info("Agent disconnected for node: {}", nodeId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID nodeId = (UUID) session.getAttributes().get("nodeId");
        WsMessage msg = objectMapper.readValue(message.getPayload(), WsMessage.class);

        switch (msg.type()) {
            case REGISTER -> {
                RegisterPayload payload = objectMapper.treeToValue(msg.payload(), RegisterPayload.class);
                Node node = nodeRepository.findById(nodeId).orElse(null);
                if (node != null) {
                    node.setTotalRamMb(payload.ramMb());
                    node.setTotalCores(payload.cores());
                    node.setTotalDiskGb(payload.diskGb());
                    node.setAddress(payload.address());
                    node.setStatus(NodeStatus.ONLINE);
                    node.setLastHeartbeat(Instant.now());
                    nodeRepository.save(node);
                    auditService.log(node.getOwnerId(), null, nodeId, "NODE_REGISTERED", "Agent version: " + payload.agentVersion());
                    log.info("Node {} registered successfully (RAM: {}MB, Cores: {}, Disk: {}GB)", nodeId, payload.ramMb(), payload.cores(), payload.diskGb());
                }
            }
            case HEARTBEAT -> {
                HeartbeatPayload payload = objectMapper.treeToValue(msg.payload(), HeartbeatPayload.class);
                redisStateService.recordNodeHeartbeat(nodeId, java.time.Duration.ofSeconds(60));
                Node node = nodeRepository.findById(nodeId).orElse(null);
                if (node != null) {
                    node.setStatus(NodeStatus.ONLINE);
                    node.setLastHeartbeat(Instant.now());
                    nodeRepository.save(node);
                    log.debug("Heartbeat received from node {}: freeRam={}MB, activeVms={}", nodeId, payload.freeRamMb(), payload.activeVms());
                }
            }
            case VM_READY -> {
                VmReadyPayload payload = objectMapper.treeToValue(msg.payload(), VmReadyPayload.class);
                Lease lease = leaseRepository.findById(payload.leaseId()).orElse(null);
                if (lease != null) {
                    lease.setVmName(payload.vmName());
                    lease.setVmPort(payload.port());
                    leaseRepository.save(lease);
                    leaseService.transition(lease.getId(), LeaseState.ACTIVE, "VM reported ready on port " + payload.port());
                    log.info("Lease {} is now ACTIVE on port {}", payload.leaseId(), payload.port());
                }
            }
            case VM_FAILED -> {
                VmFailedPayload payload = objectMapper.treeToValue(msg.payload(), VmFailedPayload.class);
                leaseService.transition(payload.leaseId(), LeaseState.FAILED, payload.reason());
                log.warn("Lease {} failed provisioning: {}", payload.leaseId(), payload.reason());
            }
            case VM_DESTROYED -> {
                VmDestroyedPayload payload = objectMapper.treeToValue(msg.payload(), VmDestroyedPayload.class);
                Lease lease = leaseRepository.findById(payload.leaseId()).orElse(null);
                if (lease != null && lease.getState() != LeaseState.ENDED) {
                    leaseService.transition(payload.leaseId(), LeaseState.ENDED, "VM destroyed acknowledgment received");
                }
                log.info("Lease {} VM confirmed destroyed", payload.leaseId());
            }
            case VM_STATS -> {
                VmStatsPayload payload = objectMapper.treeToValue(msg.payload(), VmStatsPayload.class);
                log.debug("VM stats for lease {}: cpuPct={}%, memMb={}MB", payload.leaseId(), payload.cpuPct(), payload.memMb());
            }
            default -> log.warn("Unhandled message type from node {}: {}", nodeId, msg.type());
        }
    }
}
