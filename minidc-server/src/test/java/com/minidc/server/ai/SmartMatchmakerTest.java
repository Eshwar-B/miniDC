package com.minidc.server.ai;

import com.minidc.common.enums.NodeStatus;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.Offer;
import com.minidc.server.offer.OfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SmartMatchmakerTest {

    private OfferRepository offerRepository;
    private NodeRepository nodeRepository;
    private NodeReliabilityScorer reliabilityScorer;
    private SmartMatchmaker matchmaker;

    @BeforeEach
    void setUp() {
        offerRepository = Mockito.mock(OfferRepository.class);
        nodeRepository = Mockito.mock(NodeRepository.class);
        reliabilityScorer = new NodeReliabilityScorer(null);
        matchmaker = new SmartMatchmaker(offerRepository, nodeRepository, reliabilityScorer);
    }

    @Test
    void shouldFilterUnderResourcedOffers() {
        UUID node1Id = UUID.randomUUID();
        Node node1 = new Node();
        node1.setId(node1Id);
        node1.setName("Node1");
        node1.setStatus(NodeStatus.ONLINE);
        node1.setLastHeartbeat(Instant.now());

        // Low RAM offer (2048 MB)
        Offer lowRamOffer = new Offer(UUID.randomUUID(), node1Id, 2048, 2, 20, 4, null, null, true, null, true);
        when(offerRepository.findByActiveTrue()).thenReturn(List.of(lowRamOffer));
        when(nodeRepository.findById(node1Id)).thenReturn(Optional.of(node1));

        // Request 4096 MB RAM
        var recommendations = matchmaker.recommendOffers(new SmartMatchmaker.MatchRequest(4096, 2, 20, 2));
        assertTrue(recommendations.isEmpty(), "Under-resourced offer should be filtered out");
    }

    @Test
    void shouldRankAutoApproveOffersHigher() {
        UUID node1Id = UUID.randomUUID();
        UUID node2Id = UUID.randomUUID();

        Node node1 = new Node();
        node1.setId(node1Id);
        node1.setName("Node-Manual");
        node1.setStatus(NodeStatus.ONLINE);
        node1.setLastHeartbeat(Instant.now());

        Node node2 = new Node();
        node2.setId(node2Id);
        node2.setName("Node-Auto");
        node2.setStatus(NodeStatus.ONLINE);
        node2.setLastHeartbeat(Instant.now());

        Offer manualOffer = new Offer(UUID.randomUUID(), node1Id, 8192, 4, 50, 4, null, null, false, null, true);
        Offer autoOffer = new Offer(UUID.randomUUID(), node2Id, 8192, 4, 50, 4, null, null, true, null, true);

        when(offerRepository.findByActiveTrue()).thenReturn(List.of(manualOffer, autoOffer));
        when(nodeRepository.findById(node1Id)).thenReturn(Optional.of(node1));
        when(nodeRepository.findById(node2Id)).thenReturn(Optional.of(node2));

        var recommendations = matchmaker.recommendOffers(new SmartMatchmaker.MatchRequest(4096, 2, 20, 2));

        assertEquals(2, recommendations.size());
        assertEquals(autoOffer.getId(), recommendations.get(0).offer().getId(), "Auto-approve offer should be ranked first");
        assertTrue(recommendations.get(0).matchScore().score() > recommendations.get(1).matchScore().score());
    }
}
