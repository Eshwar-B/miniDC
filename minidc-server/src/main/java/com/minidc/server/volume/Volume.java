package com.minidc.server.volume;

import com.minidc.common.enums.VolumeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a persistent encrypted storage volume for borrower work retention.
 */
@Entity
@Table(name = "volumes")
public class Volume {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "size_gb", nullable = false)
    private int sizeGb;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VolumeStatus status;

    @Column(name = "mount_path", nullable = false)
    private String mountPath;

    @Column(name = "active_lease_id")
    private UUID activeLeaseId;

    @Column(name = "last_snapshot_ref")
    private String lastSnapshotRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Volume() {
    }

    public Volume(UUID id, UUID userId, String name, int sizeGb, VolumeStatus status,
                  String mountPath, UUID activeLeaseId, String lastSnapshotRef,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.sizeGb = sizeGb;
        this.status = status;
        this.mountPath = mountPath;
        this.activeLeaseId = activeLeaseId;
        this.lastSnapshotRef = lastSnapshotRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSizeGb() {
        return sizeGb;
    }

    public void setSizeGb(int sizeGb) {
        this.sizeGb = sizeGb;
    }

    public VolumeStatus getStatus() {
        return status;
    }

    public void setStatus(VolumeStatus status) {
        this.status = status;
    }

    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    public UUID getActiveLeaseId() {
        return activeLeaseId;
    }

    public void setActiveLeaseId(UUID activeLeaseId) {
        this.activeLeaseId = activeLeaseId;
    }

    public String getLastSnapshotRef() {
        return lastSnapshotRef;
    }

    public void setLastSnapshotRef(String lastSnapshotRef) {
        this.lastSnapshotRef = lastSnapshotRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
