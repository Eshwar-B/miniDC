package com.minidc.server.ai;

import com.minidc.common.enums.NodeStatus;
import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.kafka.TelemetryService;
import com.minidc.server.node.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * AI & Heuristic Reliability Scorer assessing edge compute nodes based on
 * heartbeat latency, historical uptime, and hardware capacity headroom.
 */
@Component
public class NodeReliabilityScorer {

    private final TelemetryService telemetryService;

    @Autowired
    public NodeReliabilityScorer(@Autowired(required = false) TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    public record ReliabilityAssessment(
            double score,               // 0.0 to 1.0
            double reliabilityPercent,  // 0.0 to 100.0
            String grade,               // A+, A, B, C, D, F
            String summary
    ) {}

    public ReliabilityAssessment assessNode(Node node) {
        if (node == null) {
            return new ReliabilityAssessment(0.0, 0.0, "F", "Node not found");
        }

        if (node.getStatus() == NodeStatus.OFFLINE) {
            return new ReliabilityAssessment(0.0, 0.0, "F", "Node is currently OFFLINE");
        }

        double score = 0.80; // Baseline 80% for registered active nodes

        // 1. Heartbeat freshness
        if (node.getLastHeartbeat() != null) {
            Duration elapsed = Duration.between(node.getLastHeartbeat(), Instant.now());
            long seconds = Math.abs(elapsed.getSeconds());
            if (seconds <= 45) {
                score += 0.10; // Fresh heartbeat bonus
            } else if (seconds > 90) {
                score -= 0.25; // Stale heartbeat penalty
            }
        } else {
            score -= 0.15;
        }

        // 2. Hardware capacity headroom bonus
        if (node.getTotalRamMb() != null && node.getTotalRamMb() >= 16384) {
            score += 0.05; // High RAM workstation
        }
        if (node.getTotalCores() != null && node.getTotalCores() >= 8) {
            score += 0.05; // Multi-core CPU headroom
        }

        // 3. Telemetry CPU load check
        if (telemetryService != null) {
            List<TelemetryMetricEvent> metrics = telemetryService.getRecentMetricsForNode(node.getId());
            if (!metrics.isEmpty()) {
                double avgCpu = metrics.stream()
                        .mapToDouble(TelemetryMetricEvent::cpuPercent)
                        .average()
                        .orElse(50.0);
                if (avgCpu < 25.0) {
                    score += 0.05; // Node is cool and lightly loaded
                } else if (avgCpu > 80.0) {
                    score -= 0.15; // Node under heavy existing host load
                }
            }
        }

        // Clamp between 0.10 and 1.00
        score = Math.max(0.10, Math.min(1.00, score));
        double percent = Math.round(score * 1000.0) / 10.0;

        String grade;
        if (percent >= 95.0) grade = "A+";
        else if (percent >= 90.0) grade = "A";
        else if (percent >= 80.0) grade = "B";
        else if (percent >= 70.0) grade = "C";
        else if (percent >= 60.0) grade = "D";
        else grade = "F";

        String summary = String.format("Grade %s (%s%% reliability) - %s node with %d MB RAM, %s cores",
                grade,
                percent,
                node.getStatus(),
                node.getTotalRamMb() != null ? node.getTotalRamMb() : 0,
                node.getTotalCores() != null ? node.getTotalCores().toString() : "N/A"
        );

        return new ReliabilityAssessment(score, percent, grade, summary);
    }
}
