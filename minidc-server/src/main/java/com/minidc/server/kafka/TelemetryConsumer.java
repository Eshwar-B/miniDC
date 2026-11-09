package com.minidc.server.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.event.KafkaTopics;
import com.minidc.common.event.TelemetryMetricEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka and in-memory consumer that processes incoming telemetry metric streams
 * and aggregates them into the time-series TelemetryService buffer.
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private final TelemetryService telemetryService;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.minidc.server.ws.stomp.StompNotificationService stompNotificationService;

    public TelemetryConsumer(TelemetryService telemetryService,
                             KafkaEventPublisher kafkaEventPublisher,
                             ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.objectMapper = objectMapper;
    }

    public void setStompNotificationService(com.minidc.server.ws.stomp.StompNotificationService stompNotificationService) {
        this.stompNotificationService = stompNotificationService;
    }

    @PostConstruct
    public void init() {
        // Register in-memory listener for offline / dev fallback
        kafkaEventPublisher.registerInMemoryListener(KafkaTopics.TOPIC_TELEMETRY, this::handleEvent);
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_TELEMETRY, groupId = "minidc-telemetry-group", autoStartup = "${minidc.kafka.enabled:false}")
    public void onKafkaMessage(String message) {
        try {
            TelemetryMetricEvent event = objectMapper.readValue(message, TelemetryMetricEvent.class);
            handleEvent(event);
        } catch (Exception e) {
            log.warn("Failed to parse Kafka telemetry payload: {}", e.getMessage());
            kafkaEventPublisher.sendToDlq(KafkaTopics.TOPIC_TELEMETRY, null, message, e.getMessage());
        }
    }

    public void handleEvent(Object eventObj) {
        try {
            TelemetryMetricEvent event;
            if (eventObj instanceof TelemetryMetricEvent e) {
                event = e;
            } else if (eventObj instanceof String str) {
                event = objectMapper.readValue(str, TelemetryMetricEvent.class);
            } else {
                event = objectMapper.convertValue(eventObj, TelemetryMetricEvent.class);
            }
            telemetryService.recordMetric(event);
            if (stompNotificationService != null) {
                stompNotificationService.broadcastTelemetry(event);
            }
            log.trace("Recorded telemetry for lease {} / node {}", event.leaseId(), event.nodeId());
        } catch (Exception e) {
            log.error("Failed to process telemetry event: {}", e.getMessage(), e);
        }
    }
}
