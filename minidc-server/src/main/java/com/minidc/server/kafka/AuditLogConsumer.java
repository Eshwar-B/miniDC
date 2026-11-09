package com.minidc.server.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.common.event.AuditEventMessage;
import com.minidc.common.event.KafkaTopics;
import com.minidc.server.audit.AuditLog;
import com.minidc.server.audit.AuditLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer for asynchronous security and administrative audit events.
 * Decouples audit log persistence from the primary transaction path.
 */
@Component
public class AuditLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditLogConsumer.class);

    private final AuditLogRepository auditLogRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final ObjectMapper objectMapper;

    public AuditLogConsumer(AuditLogRepository auditLogRepository,
                            KafkaEventPublisher kafkaEventPublisher,
                            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        kafkaEventPublisher.registerInMemoryListener(KafkaTopics.TOPIC_AUDIT, this::handleEvent);
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_AUDIT, groupId = "minidc-audit-group", autoStartup = "${minidc.kafka.enabled:false}")
    public void onKafkaMessage(String message) {
        try {
            AuditEventMessage event = objectMapper.readValue(message, AuditEventMessage.class);
            handleEvent(event);
        } catch (Exception e) {
            log.warn("Failed to parse Kafka audit event: {}", e.getMessage());
            kafkaEventPublisher.sendToDlq(KafkaTopics.TOPIC_AUDIT, null, message, e.getMessage());
        }
    }

    public void handleEvent(Object eventObj) {
        try {
            AuditEventMessage event;
            if (eventObj instanceof AuditEventMessage e) {
                event = e;
            } else if (eventObj instanceof String str) {
                event = objectMapper.readValue(str, AuditEventMessage.class);
            } else {
                event = objectMapper.convertValue(eventObj, AuditEventMessage.class);
            }

            AuditLog logEntry = new AuditLog(
                    event.timestamp(),
                    event.actorId(),
                    event.leaseId(),
                    event.nodeId(),
                    event.event(),
                    event.detail()
            );
            auditLogRepository.save(logEntry);
            log.debug("Persisted audit event: {}", event.event());
        } catch (Exception e) {
            log.error("Failed to persist audit event: {}", e.getMessage(), e);
        }
    }
}

