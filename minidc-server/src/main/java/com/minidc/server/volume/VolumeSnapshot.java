package com.minidc.server.volume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a point-in-time snapshot backup of a user volume.
 */
@Entity
@Table(name = "volume_snapshots")
public class VolumeSnapshot {

    @Id
    private UUID id;

    @Column(name = "volume_id", nullable = false)
    private UUID volumeId;

    @Column(name = "snapshot_ref", nullable = false)
    private String snapshotRef;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VolumeSnapshot() {
    }

    public VolumeSnapshot(UUID id, UUID volumeId, String snapshotRef, long sizeBytes, Instant createdAt) {
        this.id = id;
        this.volumeId = volumeId;
        this.snapshotRef = snapshotRef;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(UUID volumeId) {
        this.volumeId = volumeId;
    }

    public String getSnapshotRef() {
        return snapshotRef;
    }

    public void setSnapshotRef(String snapshotRef) {
        this.snapshotRef = snapshotRef;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
