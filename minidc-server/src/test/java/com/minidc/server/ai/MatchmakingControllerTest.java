package com.minidc.server.ai;

import com.minidc.common.enums.AbuseType;
import com.minidc.common.event.AbuseAlertEvent;
import com.minidc.common.model.MatchmakingScore;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MatchmakingControllerTest {

    private SmartMatchmaker smartMatchmaker;
    private NodeReliabilityScorer reliabilityScorer;
    private AbuseDetectionEngine abuseDetectionEngine;
    private NodeRepository nodeRepository;
    private MatchmakingController controller;

    @BeforeEach
    void setUp() {
        smartMatchmaker = Mockito.mock(SmartMatchmaker.class);
        reliabilityScorer = Mockito.mock(NodeReliabilityScorer.class);
        abuseDetectionEngine = Mockito.mock(AbuseDetectionEngine.class);
        nodeRepository = Mockito.mock(NodeRepository.class);
        controller = new MatchmakingController(smartMatchmaker, reliabilityScorer, abuseDetectionEngine, nodeRepository);
    }

    @Test
    void shouldReturnRecommendations() {
        UUID offerId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Offer offer = new Offer(offerId, nodeId, 4096, 2, 20, 2, null, null, true, null, true);
        Node node = new Node();
        node.setId(nodeId);
        node.setName("Node1");
        MatchmakingScore score = new MatchmakingScore(offerId, nodeId, 95.0, 98.0, "Top tier match");

        when(smartMatchmaker.recommendOffers(any())).thenReturn(List.of(
                new SmartMatchmaker.ScoredOfferRecommendation(offer, node, score)
        ));

        ResponseEntity<List<SmartMatchmaker.ScoredOfferRecommendation>> response = controller.recommendOffers(
                new SmartMatchmaker.MatchRequest(2048, 2, 20, 2)
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(95.0, response.getBody().get(0).matchScore().score());
    }

    @Test
    void shouldReturnNodeReliability() {
        UUID nodeId = UUID.randomUUID();
        Node node = new Node();
        node.setId(nodeId);
        node.setName("Node1");
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(reliabilityScorer.assessNode(node)).thenReturn(
                new NodeReliabilityScorer.ReliabilityAssessment(0.92, 92.0, "A", "Solid node")
        );

        ResponseEntity<NodeReliabilityScorer.ReliabilityAssessment> response = controller.getNodeReliability(nodeId);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("A", response.getBody().grade());
    }

    @Test
    void shouldReturnRecentAbuseAlerts() {
        AbuseAlertEvent alert = new AbuseAlertEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AbuseType.CRYPTOMINING, 0.95, "Mining alert", Instant.now(), true
        );
        when(abuseDetectionEngine.getRecentAlerts()).thenReturn(List.of(alert));

        ResponseEntity<List<AbuseAlertEvent>> response = controller.getRecentAbuseAlerts();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(AbuseType.CRYPTOMINING, response.getBody().get(0).type());
    }
}
