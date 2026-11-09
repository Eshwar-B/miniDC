package com.minidc.server.offer;

import com.minidc.common.enums.NodeStatus;
import com.minidc.server.audit.AuditService;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import com.minidc.server.offer.dto.OfferRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing lender offers and calculating marketplace availability.
 */
@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final NodeRepository nodeRepository;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.minidc.server.ws.stomp.StompNotificationService stompNotificationService;

    public OfferService(OfferRepository offerRepository, NodeRepository nodeRepository, AuditService auditService) {
        this.offerRepository = offerRepository;
        this.nodeRepository = nodeRepository;
        this.auditService = auditService;
    }

    public void setStompNotificationService(com.minidc.server.ws.stomp.StompNotificationService stompNotificationService) {
        this.stompNotificationService = stompNotificationService;
    }

    @Transactional
    public Offer createOffer(UUID ownerId, OfferRequest request) {
        Node node = nodeRepository.findById(request.nodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + request.nodeId()));

        if (!node.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Unauthorized to create offer for node: " + request.nodeId());
        }

        UUID offerId = UUID.randomUUID();
        Offer offer = new Offer(
                offerId,
                request.nodeId(),
                request.maxRamMb(),
                request.maxCores(),
                request.maxDiskGb(),
                request.maxHours(),
                request.windowStart(),
                request.windowEnd(),
                request.autoApprove(),
                request.allowedUserIds(),
                true
        );

        offerRepository.save(offer);
        auditService.log(ownerId, null, request.nodeId(), "OFFER_CREATED", "Offer created: " + offerId);
        if (stompNotificationService != null) {
            stompNotificationService.broadcastOffer(offer, "CREATED");
        }
        return offer;
    }

    @Transactional
    public Offer updateOffer(UUID offerId, UUID ownerId, OfferRequest request) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

        Node node = nodeRepository.findById(offer.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + offer.getNodeId()));

        if (!node.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Unauthorized to modify offer: " + offerId);
        }

        offer.setMaxRamMb(request.maxRamMb());
        offer.setMaxCores(request.maxCores());
        offer.setMaxDiskGb(request.maxDiskGb());
        offer.setMaxHours(request.maxHours());
        offer.setWindowStart(request.windowStart());
        offer.setWindowEnd(request.windowEnd());
        offer.setAutoApprove(request.autoApprove());
        offer.setAllowedUserIds(request.allowedUserIds());

        auditService.log(ownerId, null, offer.getNodeId(), "OFFER_UPDATED", "Offer updated: " + offerId);
        Offer saved = offerRepository.save(offer);
        if (stompNotificationService != null) {
            stompNotificationService.broadcastOffer(saved, "UPDATED");
        }
        return saved;
    }

    @Transactional
    public void deleteOffer(UUID offerId, UUID ownerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

        Node node = nodeRepository.findById(offer.getNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + offer.getNodeId()));

        if (!node.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Unauthorized to delete offer: " + offerId);
        }

        offer.setActive(false);
        offerRepository.save(offer);
        auditService.log(ownerId, null, offer.getNodeId(), "OFFER_DELETED", "Offer deactivated: " + offerId);
        if (stompNotificationService != null) {
            stompNotificationService.broadcastOffer(offer, "DELETED");
        }
    }

    @Transactional(readOnly = true)
    public List<Offer> getAvailableOffers(UUID borrowerId) {
        List<Offer> activeOffers = offerRepository.findByActiveTrue();
        return activeOffers.stream()
                .filter(o -> {
                    Node node = nodeRepository.findById(o.getNodeId()).orElse(null);
                    if (node == null || node.getStatus() != NodeStatus.ONLINE) {
                        return false;
                    }
                    if (o.getAllowedUserIds() != null && !o.getAllowedUserIds().isEmpty()) {
                        return o.getAllowedUserIds().contains(borrowerId);
                    }
                    return true;
                })
                .toList();
    }
}
