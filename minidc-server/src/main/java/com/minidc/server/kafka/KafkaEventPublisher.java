package com.minidc.server.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.event.AuditEventMessage;
import com.minidc.common.event.KafkaTopics;
import com.minidc.common.event.LeaseLifecycleEvent;
import com.minidc.common.event.ReconciliationReport;
import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * High-throughput event publisher for MiniDC's Kafka event streaming backbone.
 * Provides transparent, thread-safe in-memory dispatch fallback when Kafka broker is offline or disabled.
 */
@Service
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    // In-memory subscriber registry for offline/dev fallback
    private final Map<String, List<Consumer<Object>>> inMemoryListeners = new ConcurrentHashMap<>();

    @Autowired
    public KafkaEventPublisher(@Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
                               AppProperties properties,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void registerInMemoryListener(String topic, Consumer<Object> listener) {
        inMemoryListeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void publishTelemetry(TelemetryMetricEvent event) {
        String key = event.leaseId() != null ? event.leaseId().toString() : event.nodeId().toString();
        send(KafkaTopics.TOPIC_TELEMETRY, key, event);
    }

    public void publishLeaseLifecycle(LeaseLifecycleEvent event) {
        send(KafkaTopics.TOPIC_LEASE_LIFECYCLE, event.leaseId().toString(), event);
    }

    public void publishAuditEvent(AuditEventMessage event) {
        String key = event.actorId() != null ? event.actorId().toString() : "system";
        send(KafkaTopics.TOPIC_AUDIT, key, event);
    }

    public void publishReconciliation(ReconciliationReport event) {
        send(KafkaTopics.TOPIC_RECONCILIATION, event.nodeId().toString(), event);
    }

    public void sendToDlq(String originalTopic, String key, Object payload, String errorReason) {
        Map<String, Object> dlqEnvelope = Map.of(
                "originalTopic", originalTopic,
                "key", key != null ? key : "unknown",
                "payload", payload,
                "errorReason", errorReason,
                "timestamp", System.currentTimeMillis()
        );
        send(KafkaTopics.TOPIC_DLQ, key, dlqEnvelope);
    }

    public void send(String topic, String key, Object payload) {
        boolean sentToKafka = false;

        if (properties.kafka() != null && properties.kafka().enabled() && kafkaTemplate != null) {
            try {
                String json = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
                kafkaTemplate.send(topic, key, json);
                sentToKafka = true;
                log.debug("Published event to Kafka topic [{}], key [{}]", topic, key);
            } catch (Exception e) {
                log.warn("Failed to publish event to Kafka topic [{}]: {}. Routing to in-memory fallback.",
                        topic, e.getMessage());
            }
        }

        // Always dispatch to in-memory listeners when not sent to Kafka or when running in local fallback mode
        dispatchInMemory(topic, payload);
    }

    private void dispatchInMemory(String topic, Object payload) {
        List<Consumer<Object>> listeners = inMemoryListeners.get(topic);
        if (listeners != null && !listeners.isEmpty()) {
            for (Consumer<Object> listener : listeners) {
                try {
                    listener.accept(payload);
                } catch (Exception e) {
                    log.error("Error executing in-memory listener for topic [{}]: {}", topic, e.getMessage(), e);
                }
            }
        }
    }
}
