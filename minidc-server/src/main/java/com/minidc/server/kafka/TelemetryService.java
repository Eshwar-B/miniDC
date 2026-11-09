package com.minidc.server.kafka;

import com.minidc.common.event.TelemetryMetricEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing time-series telemetry metrics for edge nodes and active sandbox leases.
 * Stores sliding windows of recent utilization metrics in high-speed memory buffers.
 */
@Service
public class TelemetryService {

    private static final int MAX_HISTORY_PER_ENTITY = 120; // 10 minutes at 5s intervals

    private final Map<UUID, Deque<TelemetryMetricEvent>> leaseMetrics = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<TelemetryMetricEvent>> nodeMetrics = new ConcurrentHashMap<>();

    public void recordMetric(TelemetryMetricEvent metric) {
        if (metric == null) {
            return;
        }

        if (metric.leaseId() != null) {
            leaseMetrics.compute(metric.leaseId(), (k, deque) -> {
                if (deque == null) {
                    deque = new ArrayDeque<>();
                }
                synchronized (deque) {
                    if (deque.size() >= MAX_HISTORY_PER_ENTITY) {
                        deque.pollFirst();
                    }
                    deque.addLast(metric);
                }
                return deque;
            });
        }

        if (metric.nodeId() != null) {
            nodeMetrics.compute(metric.nodeId(), (k, deque) -> {
                if (deque == null) {
                    deque = new ArrayDeque<>();
                }
                synchronized (deque) {
                    if (deque.size() >= MAX_HISTORY_PER_ENTITY) {
                        deque.pollFirst();
                    }
                    deque.addLast(metric);
                }
                return deque;
            });
        }
    }

    public List<TelemetryMetricEvent> getRecentMetricsForLease(UUID leaseId) {
        Deque<TelemetryMetricEvent> deque = leaseMetrics.get(leaseId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    public List<TelemetryMetricEvent> getRecentMetricsForNode(UUID nodeId) {
        Deque<TelemetryMetricEvent> deque = nodeMetrics.get(nodeId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    public TelemetryMetricEvent getLatestMetricForLease(UUID leaseId) {
        Deque<TelemetryMetricEvent> deque = leaseMetrics.get(leaseId);
        if (deque == null) {
            return null;
        }
        synchronized (deque) {
            return deque.peekLast();
        }
    }

    public void clearHistory(UUID leaseId) {
        if (leaseId != null) {
            leaseMetrics.remove(leaseId);
        }
    }
}

