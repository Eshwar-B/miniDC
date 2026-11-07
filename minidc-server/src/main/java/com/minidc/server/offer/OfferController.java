package com.minidc.server.offer;

import com.minidc.server.offer.dto.OfferRequest;
import com.minidc.server.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller managing capacity offers published by lenders.
 */
@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Offer createOffer(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody OfferRequest request
    ) {
        return offerService.createOffer(user.id(), request);
    }

    @PutMapping("/{id}")
    public Offer updateOffer(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID id,
            @Valid @RequestBody OfferRequest request
    ) {
        return offerService.updateOffer(id, user.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOffer(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID id
    ) {
        offerService.deleteOffer(id, user.id());
    }

    @GetMapping("/available")
    public List<Offer> getAvailableOffers(@AuthenticationPrincipal CurrentUser user) {
        return offerService.getAvailableOffers(user.id());
    }
}
