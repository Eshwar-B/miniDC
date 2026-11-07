package com.minidc.server.node;

import com.minidc.common.enums.NodeStatus;
import com.minidc.server.audit.AuditService;
import com.minidc.server.node.dto.CreateNodeRequest;
import com.minidc.server.node.dto.CreateNodeResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service managing compute node registration, token generation, and state manipulation.
 */
@Service
public class NodeService {

    private final NodeRepository nodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public NodeService(NodeRepository nodeRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.nodeRepository = nodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public CreateNodeResponse createNode(UUID ownerId, CreateNodeRequest request) {
        UUID nodeId = UUID.randomUUID();

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String agentToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = passwordEncoder.encode(agentToken);

        Node node = new Node(
                nodeId,
                ownerId,
                request.name(),
                tokenHash,
                NodeStatus.OFFLINE,
                null,
                null,
                null,
                null,
                null
        );
        nodeRepository.save(node);
        auditService.log(ownerId, null, nodeId, "NODE_CREATED", "Node registered: " + request.name());

        return new CreateNodeResponse(nodeId, agentToken);
    }

    @Transactional(readOnly = true)
    public List<Node> getMyNodes(UUID ownerId) {
        return nodeRepository.findByOwnerId(ownerId);
    }

    @Transactional
    public Node pauseNode(UUID nodeId, UUID ownerId) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        if (!node.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Unauthorized to modify node: " + nodeId);
        }
        node.setStatus(NodeStatus.PAUSED);
        auditService.log(ownerId, null, nodeId, "NODE_PAUSED", "Node status changed to PAUSED");
        return nodeRepository.save(node);
    }

    @Transactional
    public Node resumeNode(UUID nodeId, UUID ownerId) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        if (!node.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Unauthorized to modify node: " + nodeId);
        }
        node.setStatus(NodeStatus.ONLINE);
        auditService.log(ownerId, null, nodeId, "NODE_RESUMED", "Node status changed to ONLINE");
        return nodeRepository.save(node);
    }
}
