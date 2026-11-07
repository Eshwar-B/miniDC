package com.minidc.server.lease;

import com.minidc.server.lease.dto.CreateLeaseRequest;
import com.minidc.server.lease.dto.LeaseResponse;
import com.minidc.server.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for borrower lease requests and lender approval workflows.
 */
@RestController
@RequestMapping("/api/leases")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaseResponse createLease(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateLeaseRequest request
    ) {
        return leaseService.createLease(user.id(), request);
    }

    @GetMapping("/mine")
    public List<LeaseResponse> getMyLeases(@AuthenticationPrincipal CurrentUser user) {
        return leaseService.getMyLeases(user.id()).stream().map(LeaseResponse::from).toList();
    }

    @GetMapping("/incoming")
    public List<LeaseResponse> getIncomingLeases(@AuthenticationPrincipal CurrentUser user) {
        return leaseService.getIncomingLeases(user.id()).stream().map(LeaseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public LeaseResponse getLease(@PathVariable UUID id) {
        return LeaseResponse.from(leaseService.getLease(id));
    }

    @PostMapping("/{id}/approve")
    public LeaseResponse approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return LeaseResponse.from(leaseService.approve(id, user.id()));
    }

    @PostMapping("/{id}/reject")
    public LeaseResponse reject(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String reason
    ) {
        return LeaseResponse.from(leaseService.reject(id, user.id(), reason));
    }

    @PostMapping("/{id}/kill")
    public LeaseResponse kill(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) String reason
    ) {
        return LeaseResponse.from(leaseService.kill(id, user.id(), reason));
    }

    @PostMapping("/{id}/end")
    public LeaseResponse end(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return LeaseResponse.from(leaseService.endLease(id, user.id()));
    }
}
