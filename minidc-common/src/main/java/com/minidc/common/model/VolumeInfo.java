package com.minidc.common.model;

import com.minidc.common.enums.VolumeStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an encrypted persistent home storage volume for a borrower.
 */
public record VolumeInfo(
        UUID id,
        UUID userId,
        String name,
        int sizeGb,
        VolumeStatus status,
        String mountPath,
        UUID activeLeaseId,
        String lastSnapshotRef,
        Instant createdAt,
        Instant updatedAt
) {
}
