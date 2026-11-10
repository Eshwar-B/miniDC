package com.minidc.server.ai;

import com.minidc.common.enums.NodeStatus;
import com.minidc.server.node.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NodeReliabilityScorerTest {

    private NodeReliabilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new NodeReliabilityScorer(null);
    }

    @Test
    void shouldGiveZeroScoreToOfflineOrNullNode() {
        NodeReliabilityScorer.ReliabilityAssessment nullResult = scorer.assessNode(null);
        assertEquals(0.0, nullResult.score());
        assertEquals("F", nullResult.grade());

        Node offlineNode = new Node();
        offlineNode.setId(UUID.randomUUID());
        offlineNode.setName("Test-Offline");
        offlineNode.setStatus(NodeStatus.OFFLINE);
        NodeReliabilityScorer.ReliabilityAssessment offlineResult = scorer.assessNode(offlineNode);
        assertEquals(0.0, offlineResult.score());
        assertEquals("F", offlineResult.grade());
    }

    @Test
    void shouldRewardFreshHeartbeatAndHighCapacity() {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setName("High-End-Node");
        node.setStatus(NodeStatus.ONLINE);
        node.setLastHeartbeat(Instant.now().minusSeconds(10));
        node.setTotalRamMb(32768);
        node.setTotalCores(16);

        NodeReliabilityScorer.ReliabilityAssessment result = scorer.assessNode(node);
        assertTrue(result.score() >= 0.95, "Expected >= 0.95 score for premium online node");
        assertTrue(result.reliabilityPercent() >= 95.0);
        assertEquals("A+", result.grade());
    }

    @Test
    void shouldPenalizeStaleHeartbeat() {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setName("Stale-Node");
        node.setStatus(NodeStatus.ONLINE);
        node.setLastHeartbeat(Instant.now().minusSeconds(150)); // Stale > 90s
        node.setTotalRamMb(4096);
        node.setTotalCores(2);

        NodeReliabilityScorer.ReliabilityAssessment result = scorer.assessNode(node);
        assertTrue(result.score() < 0.80, "Expected penalty for stale heartbeat");
    }
}
