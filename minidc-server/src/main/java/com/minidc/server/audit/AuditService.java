package com.minidc.server.audit;

import com.minidc.common.event.AuditEventMessage;
import com.minidc.server.kafka.KafkaEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for recording audit log events and retrieving audit trails for administrative review.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaEventPublisher;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void setKafkaEventPublisher(KafkaEventPublisher kafkaEventPublisher) {
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID actorId, UUID leaseId, UUID nodeId, String event, String detail) {
        Instant now = Instant.now();
        AuditLog entry = new AuditLog(now, actorId, leaseId, nodeId, event, detail);
        auditLogRepository.save(entry);

        if (kafkaEventPublisher != null) {
            try {
                kafkaEventPublisher.publishAuditEvent(new AuditEventMessage(actorId, leaseId, nodeId, event, detail, now));
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByAtDesc();
    }
}
