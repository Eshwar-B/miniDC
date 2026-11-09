package com.minidc.server.ws.stomp;

import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.dto.LeaseResponse;
import com.minidc.server.offer.Offer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service for broadcasting real-time platform events to connected browser clients over STOMP.
 */
@Service
public class StompNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StompNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public StompNotificationService(@Autowired(required = false) SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastOffer(Offer offer, String action) {
        if (messagingTemplate == null) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "action", action != null ? action : "UPDATED",
                    "offer", offer,
                    "timestamp", System.currentTimeMillis()
            );
            messagingTemplate.convertAndSend("/topic/offers", payload);
            log.debug("Broadcasted offer update to /topic/offers: id={}", offer.getId());
        } catch (Exception e) {
            log.warn("Failed broadcasting offer update: {}", e.getMessage());
        }
    }

    public void broadcastLease(Lease lease, String action) {
        if (messagingTemplate == null || lease == null) {
            return;
        }
        try {
            LeaseResponse resp = LeaseResponse.from(lease);
            Map<String, Object> payload = Map.of(
                    "action", action != null ? action : "UPDATED",
                    "lease", resp,
                    "timestamp", System.currentTimeMillis()
            );

            // Broadcast to lease-specific topic
            messagingTemplate.convertAndSend("/topic/leases/" + lease.getId(), payload);

            // Push to borrower private queue
            if (lease.getBorrowerId() != null) {
                messagingTemplate.convertAndSendToUser(
                        lease.getBorrowerId().toString(),
                        "/queue/leases",
                        payload
                );
            }

            // Broadcast to incoming approvals topic
            messagingTemplate.convertAndSend("/topic/leases/incoming", payload);
            log.debug("Broadcasted lease update to /topic/leases/{}: state={}", lease.getId(), lease.getState());
        } catch (Exception e) {
            log.warn("Failed broadcasting lease update: {}", e.getMessage());
        }
    }

    public void broadcastTelemetry(TelemetryMetricEvent metric) {
        if (messagingTemplate == null || metric == null) {
            return;
        }
        try {
            if (metric.leaseId() != null) {
                messagingTemplate.convertAndSend("/topic/leases/" + metric.leaseId() + "/telemetry", metric);
            }
            if (metric.nodeId() != null) {
                messagingTemplate.convertAndSend("/topic/nodes/" + metric.nodeId() + "/telemetry", metric);
            }
        } catch (Exception e) {
            log.warn("Failed broadcasting telemetry: {}", e.getMessage());
        }
    }

    public void sendUserNotification(UUID userId, String title, String message, String type) {
        if (messagingTemplate == null || userId == null) {
            return;
        }
        try {
            Map<String, Object> notification = Map.of(
                    "title", title,
                    "message", message,
                    "type", type != null ? type : "INFO",
                    "timestamp", System.currentTimeMillis()
            );
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    notification
            );
            log.debug("Sent user notification to {}: {}", userId, title);
        } catch (Exception e) {
            log.warn("Failed sending user notification: {}", e.getMessage());
        }
    }
}

