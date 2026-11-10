package com.minidc.server.ai;

import com.minidc.common.enums.AbuseType;
import com.minidc.common.event.AbuseAlertEvent;
import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.kafka.KafkaEventPublisher;
import com.minidc.server.lease.LeaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AbuseDetectionEngineTest {

    private KafkaEventPublisher kafkaEventPublisher;
    private LeaseService leaseService;
    private AbuseDetectionEngine engine;

    @BeforeEach
    void setUp() {
        kafkaEventPublisher = Mockito.mock(KafkaEventPublisher.class);
        leaseService = Mockito.mock(LeaseService.class);
        engine = new AbuseDetectionEngine(kafkaEventPublisher, leaseService, true);
    }

    @Test
    void shouldDetectCryptominingAfterThreeConsecutiveHighCpuSamples() {
        UUID leaseId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        // Sample 1: 98% CPU
        var alert1 = engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 98.0, 1024, 4096, 5, Instant.now()));
        assertTrue(alert1.isEmpty(), "Single high sample should not trigger alert");

        // Sample 2: 99% CPU
        var alert2 = engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 99.0, 1024, 4096, 5, Instant.now()));
        assertTrue(alert2.isEmpty(), "Two high samples should not trigger alert");

        // Sample 3: 97% CPU -> Should trigger CRYPTOMINING alert and kill lease
        var alert3 = engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 97.0, 1024, 4096, 5, Instant.now()));
        assertTrue(alert3.isPresent(), "Third high sample should trigger alert");
        assertEquals(AbuseType.CRYPTOMINING, alert3.get().type());
        assertTrue(alert3.get().autoTerminated());

        verify(leaseService).kill(eq(leaseId), contains("CRYPTOMINING"));
        verify(kafkaEventPublisher).publishAbuseAlert(any(AbuseAlertEvent.class));
    }

    @Test
    void shouldResetHighCpuStreakWhenLoadNormalizes() {
        UUID leaseId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        // 2 samples at 100%
        engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 100.0, 1024, 4096, 5, Instant.now()));
        engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 100.0, 1024, 4096, 5, Instant.now()));

        // Normalized to 40%
        engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 40.0, 1024, 4096, 5, Instant.now()));

        // Next high sample starts from 1 again, so no alert
        var alert = engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 100.0, 1024, 4096, 5, Instant.now()));
        assertTrue(alert.isEmpty());
        verify(leaseService, never()).kill(any(), any());
    }

    @Test
    void shouldDetectMemoryExhaustionAbuse() {
        UUID leaseId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        // 99.5% RAM utilization (4080 / 4096)
        var alert = engine.analyzeMetric(new TelemetryMetricEvent(nodeId, leaseId, 30.0, 4080, 4096, 5, Instant.now()));
        assertTrue(alert.isPresent());
        assertEquals(AbuseType.RESOURCE_EXHAUSTION, alert.get().type());
    }
}
