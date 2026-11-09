package com.minidc.server.ws.stomp;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.event.TelemetryMetricEvent;
import com.minidc.server.lease.Lease;
import com.minidc.server.offer.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class StompNotificationServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private StompNotificationService notificationService;

    @BeforeEach
    void setUp() {
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        notificationService = new StompNotificationService(messagingTemplate);
    }

    @Test
    void shouldBroadcastOfferUpdate() {
        UUID offerId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Offer offer = new Offer(offerId, nodeId, 8192, 4, 50, 10, null, null, true, null, true);

        notificationService.broadcastOffer(offer, "CREATED");

        verify(messagingTemplate).convertAndSend(eq("/topic/offers"), any(Map.class));
    }

    @Test
    void shouldBroadcastLeaseUpdateToTopicAndUserQueues() {
        UUID leaseId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        Lease lease = new Lease(
                leaseId, UUID.randomUUID(), borrowerId, nodeId,
                4096, 2, 20, 2, LeaseState.ACTIVE,
                "lease-test", 33001, "minidc", "enc",
                Instant.now(), Instant.now().plusSeconds(7200),
                Instant.now(), Instant.now(), null
        );

        notificationService.broadcastLease(lease, "STATE_CHANGED");

        verify(messagingTemplate).convertAndSend(eq("/topic/leases/" + leaseId), any(Map.class));
        verify(messagingTemplate).convertAndSendToUser(eq(borrowerId.toString()), eq("/queue/leases"), any(Map.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/leases/incoming"), any(Map.class));
    }

    @Test
    void shouldBroadcastTelemetryToLeaseAndNodeTopics() {
        UUID leaseId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        TelemetryMetricEvent metric = new TelemetryMetricEvent(nodeId, leaseId, 62.0, 3000, 8192, 15, Instant.now());

        notificationService.broadcastTelemetry(metric);

        verify(messagingTemplate).convertAndSend(eq("/topic/leases/" + leaseId + "/telemetry"), eq(metric));
        verify(messagingTemplate).convertAndSend(eq("/topic/nodes/" + nodeId + "/telemetry"), eq(metric));
    }

    @Test
    void shouldSendDirectUserNotification() {
        UUID userId = UUID.randomUUID();
        notificationService.sendUserNotification(userId, "Lease Approved", "Your lease is ready", "SUCCESS");

        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), any(Map.class));
    }
}

