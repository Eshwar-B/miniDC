package com.minidc.server.offer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for creating or updating a compute capacity offer.
 */
public record OfferRequest(
        @NotNull(message = "Node ID is required")
        UUID nodeId,

        @Min(value = 512, message = "RAM must be at least 512 MB")
        int maxRamMb,

        @Min(value = 1, message = "Cores must be at least 1")
        int maxCores,

        @Min(value = 5, message = "Disk must be at least 5 GB")
        int maxDiskGb,

        @Min(value = 1, message = "Hours must be at least 1")
        int maxHours,

        LocalTime windowStart,
        LocalTime windowEnd,
        boolean autoApprove,
        List<UUID> allowedUserIds
) {
}
