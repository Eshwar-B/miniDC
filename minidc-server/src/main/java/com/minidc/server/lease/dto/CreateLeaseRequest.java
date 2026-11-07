package com.minidc.server.lease.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload submitted by a borrower to reserve resources from an active offer.
 */
public record CreateLeaseRequest(
        @NotNull(message = "Offer ID is required")
        UUID offerId,

        @Min(value = 512, message = "RAM must be at least 512 MB")
        int ramMb,

        @Min(value = 1, message = "Cores must be at least 1")
        int cores,

        @Min(value = 5, message = "Disk must be at least 5 GB")
        int diskGb,

        @Min(value = 1, message = "Requested hours must be at least 1")
        int requestedHours
) {
}
