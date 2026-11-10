package com.minidc.server.ai;

import com.minidc.common.event.AbuseAlertEvent;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API for AI-powered matchmaking recommendations, node reliability scoring, and abuse monitoring.
 */
@RestController
@RequestMapping("/api")
public class MatchmakingController {

    private final SmartMatchmaker smartMatchmaker;
    private final NodeReliabilityScorer reliabilityScorer;
    private final AbuseDetectionEngine abuseDetectionEngine;
    private final NodeRepository nodeRepository;

    public MatchmakingController(
            SmartMatchmaker smartMatchmaker,
            NodeReliabilityScorer reliabilityScorer,
            AbuseDetectionEngine abuseDetectionEngine,
            NodeRepository nodeRepository
    ) {
        this.smartMatchmaker = smartMatchmaker;
        this.reliabilityScorer = reliabilityScorer;
        this.abuseDetectionEngine = abuseDetectionEngine;
        this.nodeRepository = nodeRepository;
    }

    @PostMapping("/offers/recommend")
    public ResponseEntity<List<SmartMatchmaker.ScoredOfferRecommendation>> recommendOffers(
            @RequestBody SmartMatchmaker.MatchRequest request
    ) {
        return ResponseEntity.ok(smartMatchmaker.recommendOffers(request));
    }

    @GetMapping("/nodes/{nodeId}/reliability")
    public ResponseEntity<NodeReliabilityScorer.ReliabilityAssessment> getNodeReliability(
            @PathVariable UUID nodeId
    ) {
        Optional<Node> nodeOpt = nodeRepository.findById(nodeId);
        if (nodeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(reliabilityScorer.assessNode(nodeOpt.get()));
    }

    @GetMapping("/admin/abuse-alerts")
    public ResponseEntity<List<AbuseAlertEvent>> getRecentAbuseAlerts() {
        return ResponseEntity.ok(abuseDetectionEngine.getRecentAlerts());
    }
}

