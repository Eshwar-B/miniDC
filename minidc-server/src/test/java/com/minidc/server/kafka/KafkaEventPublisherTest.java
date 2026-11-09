package com.minidc.server.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.enums.LeaseState;
import com.minidc.common.event.AuditEventMessage;
import com.minidc.common.event.KafkaTopics;
import com.minidc.common.event.LeaseLifecycleEvent;
import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEventPublisherTest {

    private KafkaEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        AppProperties properties = new AppProperties(
                "secret-12345678901234567890123456789012",
                12,
                "key",
                "http://localhost:8080/guacamole",
                "00112233445566778899aabbccddeeff",
                1,
                false,
                null,
                null,
                new AppProperties.KafkaProperties(false, "localhost:9092", "minidc.dlq")
        );
        publisher = new KafkaEventPublisher(null, properties, objectMapper);
    }

    @Test
    void shouldDispatchTelemetryToInMemoryListeners() {
        List<TelemetryMetricEvent> received = new ArrayList<>();
        publisher.registerInMemoryListener(KafkaTopics.TOPIC_TELEMETRY, obj -> {
            if (obj instanceof TelemetryMetricEvent e) {
                received.add(e);
            }
        });

        UUID nodeId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        TelemetryMetricEvent event = new TelemetryMetricEvent(nodeId, leaseId, 45.5, 2048, 8192, 20, Instant.now());

        publisher.publishTelemetry(event);

        assertEquals(1, received.size());
        assertEquals(45.5, received.get(0).cpuPercent());
        assertEquals(leaseId, received.get(0).leaseId());
    }

    @Test
    void shouldDispatchLeaseLifecycleToInMemoryListeners() {
        List<LeaseLifecycleEvent> received = new ArrayList<>();
        publisher.registerInMemoryListener(KafkaTopics.TOPIC_LEASE_LIFECYCLE, obj -> {
            if (obj instanceof LeaseLifecycleEvent e) {
                received.add(e);
            }
        });

        UUID leaseId = UUID.randomUUID();
        LeaseLifecycleEvent event = new LeaseLifecycleEvent(
                leaseId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LeaseState.ACTIVE, "VM ready", Instant.now()
        );

        publisher.publishLeaseLifecycle(event);

        assertEquals(1, received.size());
        assertEquals(LeaseState.ACTIVE, received.get(0).state());
        assertEquals(leaseId, received.get(0).leaseId());
    }

    @Test
    void shouldDispatchAuditEventsToInMemoryListeners() {
        List<AuditEventMessage> received = new ArrayList<>();
        publisher.registerInMemoryListener(KafkaTopics.TOPIC_AUDIT, obj -> {
            if (obj instanceof AuditEventMessage e) {
                received.add(e);
            }
        });

        UUID actorId = UUID.randomUUID();
        AuditEventMessage event = new AuditEventMessage(actorId, null, null, "TEST_EVENT", "Detail msg", Instant.now());

        publisher.publishAuditEvent(event);

        assertEquals(1, received.size());
        assertEquals("TEST_EVENT", received.get(0).event());
    }

    @Test
    void shouldRouteErrorsToDlqTopic() {
        List<Object> dlqMessages = new ArrayList<>();
        publisher.registerInMemoryListener(KafkaTopics.TOPIC_DLQ, dlqMessages::add);

        publisher.sendToDlq(KafkaTopics.TOPIC_TELEMETRY, "bad-key", "corrupt-payload", "Parse error");

        assertEquals(1, dlqMessages.size());
        assertTrue(dlqMessages.get(0) instanceof Map);
        Map<?, ?> map = (Map<?, ?>) dlqMessages.get(0);
        assertEquals("Parse error", map.get("errorReason"));
        assertEquals(KafkaTopics.TOPIC_TELEMETRY, map.get("originalTopic"));
    }
}

