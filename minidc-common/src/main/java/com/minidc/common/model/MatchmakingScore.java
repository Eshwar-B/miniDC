package com.minidc.common.model;

import java.util.UUID;

/**
 * Matchmaking recommendation score computed for an offer and edge node based on workload compatibility.
 */
public record MatchmakingScore(
        UUID offerId,
        UUID nodeId,
        double score,
        double reliabilityPercent,
        String recommendationReason
) implements Comparable<MatchmakingScore> {

    @Override
    public int compareTo(MatchmakingScore other) {
        return Double.compare(other.score, this.score); // Descending order
    }
}

