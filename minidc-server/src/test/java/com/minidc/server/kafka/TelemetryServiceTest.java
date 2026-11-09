package com.minidc.server.kafka;

import com.minidc.common.event.TelemetryMetricEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServiceTest {

    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new TelemetryService();
    }

    @Test
    void shouldRecordAndRetrieveMetricsForLeaseAndNode() {
        UUID nodeId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        TelemetryMetricEvent m1 = new TelemetryMetricEvent(nodeId, leaseId, 15.0, 1024, 8192, 10, Instant.now());
        TelemetryMetricEvent m2 = new TelemetryMetricEvent(nodeId, leaseId, 35.0, 2048, 8192, 12, Instant.now());

        telemetryService.recordMetric(m1);
        telemetryService.recordMetric(m2);

        List<TelemetryMetricEvent> leaseList = telemetryService.getRecentMetricsForLease(leaseId);
        assertEquals(2, leaseList.size());
        assertEquals(15.0, leaseList.get(0).cpuPercent());
        assertEquals(35.0, leaseList.get(1).cpuPercent());

        List<TelemetryMetricEvent> nodeList = telemetryService.getRecentMetricsForNode(nodeId);
        assertEquals(2, nodeList.size());

        TelemetryMetricEvent latest = telemetryService.getLatestMetricForLease(leaseId);
        assertNotNull(latest);
        assertEquals(35.0, latest.cpuPercent());
    }

    @Test
    void shouldCapHistoryAt120Items() {
        UUID leaseId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        for (int i = 0; i < 150; i++) {
            telemetryService.recordMetric(new TelemetryMetricEvent(nodeId, leaseId, i, 1000, 8000, 5, Instant.now()));
        }

        List<TelemetryMetricEvent> history = telemetryService.getRecentMetricsForLease(leaseId);
        assertEquals(120, history.size());
        // First element should be sample #30 (since 0-29 were evicted)
        assertEquals(30.0, history.get(0).cpuPercent());
        assertEquals(149.0, history.get(119).cpuPercent());
    }

    @Test
    void shouldReturnEmptyListForUnknownEntities() {
        assertTrue(telemetryService.getRecentMetricsForLease(UUID.randomUUID()).isEmpty());
        assertTrue(telemetryService.getRecentMetricsForNode(UUID.randomUUID()).isEmpty());
        assertNull(telemetryService.getLatestMetricForLease(UUID.randomUUID()));
    }
}
