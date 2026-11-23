package com.minidc.common.model;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a point-in-time snapshot of an encrypted volume.
 */
public record VolumeSnapshotInfo(
        UUID id,
        UUID volumeId,
        String snapshotRef,
        long sizeBytes,
        Instant createdAt
) {
}
