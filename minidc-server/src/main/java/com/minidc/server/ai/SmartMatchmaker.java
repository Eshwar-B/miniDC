package com.minidc.server.ai;

import com.minidc.common.enums.NodeStatus;
import com.minidc.common.model.MatchmakingScore;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.Offer;
import com.minidc.server.offer.OfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

/**
 * Intelligent Matchmaking & Scheduling service matching borrower compute specs
 * to the most reliable, compatible, and optimal edge node offers.
 */
@Service
public class SmartMatchmaker {

    private static final Logger log = LoggerFactory.getLogger(SmartMatchmaker.class);

    private final OfferRepository offerRepository;
    private final NodeRepository nodeRepository;
    private final NodeReliabilityScorer reliabilityScorer;

    public SmartMatchmaker(
            OfferRepository offerRepository,
            NodeRepository nodeRepository,
            NodeReliabilityScorer reliabilityScorer
    ) {
        this.offerRepository = offerRepository;
        this.nodeRepository = nodeRepository;
        this.reliabilityScorer = reliabilityScorer;
    }

    public record MatchRequest(
            int ramMb,
            int cores,
            int diskGb,
            int hours
    ) {}

    public record ScoredOfferRecommendation(
            Offer offer,
            Node node,
            MatchmakingScore matchScore
    ) {}

    public List<ScoredOfferRecommendation> recommendOffers(MatchRequest req) {
        List<Offer> activeOffers = offerRepository.findByActiveTrue();
        List<ScoredOfferRecommendation> results = new ArrayList<>();

        LocalTime now = LocalTime.now();

        for (Offer offer : activeOffers) {
            // Hard capacity filters
            if (offer.getMaxRamMb() < req.ramMb() ||
                offer.getMaxCores() < req.cores() ||
                offer.getMaxDiskGb() < req.diskGb() ||
                offer.getMaxHours() < req.hours()) {
                continue;
            }

            Optional<Node> nodeOpt = nodeRepository.findById(offer.getNodeId());
            if (nodeOpt.isEmpty()) {
                continue;
            }
            Node node = nodeOpt.get();
            if (node.getStatus() != NodeStatus.ONLINE) {
                continue;
            }

            // Window availability check
            if (offer.getWindowStart() != null && offer.getWindowEnd() != null) {
                try {
                    LocalTime start = offer.getWindowStart();
                    LocalTime end = offer.getWindowEnd();
                    if (!isWithinWindow(now, start, end, req.hours())) {
                        continue; // Schedule window cannot accommodate the full lease
                    }
                } catch (Exception ignored) {
                }
            }

            // Compute composite matchmaking score
            NodeReliabilityScorer.ReliabilityAssessment assessment = reliabilityScorer.assessNode(node);

            // 1. Reliability (50%)
            double reliabilityPart = assessment.score() * 0.50;

            // 2. Resource Fit (30%): ratio of requested to maximum capacity
            double ramRatio = (double) req.ramMb() / offer.getMaxRamMb();
            double coresRatio = (double) req.cores() / offer.getMaxCores();
            double resourceFit = ((ramRatio + coresRatio) / 2.0) * 0.30;

            // 3. Auto-approve instant activation bonus (20%)
            double autoApprovePart = offer.isAutoApprove() ? 0.20 : 0.05;

            double compositeScore = (reliabilityPart + resourceFit + autoApprovePart) * 100.0;
            compositeScore = Math.round(compositeScore * 10.0) / 10.0;

            String reason = buildReason(assessment, offer.isAutoApprove(), req);

            MatchmakingScore score = new MatchmakingScore(
                    offer.getId(),
                    node.getId(),
                    compositeScore,
                    assessment.reliabilityPercent(),
                    reason
            );

            results.add(new ScoredOfferRecommendation(offer, node, score));
        }

        // Sort descending by score
        results.sort((a, b) -> Double.compare(b.matchScore().score(), a.matchScore().score()));
        return results;
    }

    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end, int hours) {
        LocalTime finishTime = now.plusHours(hours);
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !finishTime.isAfter(end);
        } else {
            // Window wraps past midnight
            return !now.isBefore(start) || !finishTime.isAfter(end);
        }
    }

    private String buildReason(NodeReliabilityScorer.ReliabilityAssessment assessment, boolean autoApprove, MatchRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Node Grade %s (%s%% reliability). ", assessment.grade(), assessment.reliabilityPercent()));
        if (autoApprove) {
            sb.append("Instant auto-approval enabled. ");
        } else {
            sb.append("Manual lender review required. ");
        }
        sb.append(String.format("Comfortably accommodates %d MB RAM, %d cores for %d hours.", req.ramMb(), req.cores(), req.hours()));
        return sb.toString();
    }
}
