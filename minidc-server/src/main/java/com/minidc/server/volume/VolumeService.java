package com.minidc.server.volume;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.VolumeStatus;
import com.minidc.common.model.VolumeInfo;
import com.minidc.common.model.VolumeSnapshotInfo;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business service for managing user persistent volumes, mounting to leases, and managing snapshots.
 */
@Service
public class VolumeService {

    private static final Logger log = LoggerFactory.getLogger(VolumeService.class);

    private final VolumeRepository volumeRepository;
    private final VolumeSnapshotRepository snapshotRepository;
    private final VolumeStorageService storageService;
    private final LeaseRepository leaseRepository;
    private final AuditService auditService;

    public VolumeService(
            VolumeRepository volumeRepository,
            VolumeSnapshotRepository snapshotRepository,
            VolumeStorageService storageService,
            LeaseRepository leaseRepository,
            AuditService auditService
    ) {
        this.volumeRepository = volumeRepository;
        this.snapshotRepository = snapshotRepository;
        this.storageService = storageService;
        this.leaseRepository = leaseRepository;
        this.auditService = auditService;
    }

    @Transactional
    public VolumeInfo createVolume(UUID userId, String name, int sizeGb, String mountPath) {
        if (sizeGb <= 0 || sizeGb > 500) {
            throw new IllegalArgumentException("Volume size must be between 1 and 500 GB");
        }
        if (mountPath == null || mountPath.isBlank()) {
            mountPath = "/home/borrower/workspace";
        }

        UUID volumeId = UUID.randomUUID();
        Instant now = Instant.now();
        Volume volume = new Volume(
                volumeId,
                userId,
                name != null && !name.isBlank() ? name : "Volume-" + volumeId.toString().substring(0, 6),
                sizeGb,
                VolumeStatus.AVAILABLE,
                mountPath,
                null,
                null,
                now,
                now
        );

        Volume saved = volumeRepository.save(volume);
        auditService.log(userId, null, null, "VOLUME_CREATED", "Created volume " + saved.getName() + " (" + sizeGb + "GB)");
        return toInfo(saved);
    }

    @Transactional(readOnly = true)
    public List<VolumeInfo> getUserVolumes(UUID userId) {
        return volumeRepository.findByUserId(userId).stream()
                .filter(v -> v.getStatus() != VolumeStatus.DELETED)
                .map(this::toInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public VolumeInfo getVolume(UUID volumeId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new IllegalArgumentException("Volume not found: " + volumeId));
        return toInfo(volume);
    }

    @Transactional
    public VolumeInfo attachToLease(UUID volumeId, UUID leaseId, UUID userId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new IllegalArgumentException("Volume not found: " + volumeId));

        if (!volume.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Forbidden: You do not own this volume");
        }
        if (volume.getStatus() == VolumeStatus.ATTACHED) {
            throw new IllegalStateException("Volume is already attached to lease: " + volume.getActiveLeaseId());
        }

        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

        if (!lease.getBorrowerId().equals(userId)) {
            throw new IllegalArgumentException("Forbidden: You do not own the target lease");
        }
        if (lease.getState() != LeaseState.ACTIVE && lease.getState() != LeaseState.PROVISIONING && lease.getState() != LeaseState.PAUSED) {
            throw new IllegalStateException("Cannot attach volume to lease in state: " + lease.getState());
        }

        volume.setStatus(VolumeStatus.ATTACHED);
        volume.setActiveLeaseId(leaseId);
        volume.setUpdatedAt(Instant.now());
        Volume saved = volumeRepository.save(volume);

        auditService.log(userId, leaseId, lease.getNodeId(), "VOLUME_ATTACHED", "Attached volume " + volume.getName() + " to lease " + leaseId);
        return toInfo(saved);
    }

    @Transactional
    public VolumeInfo detachFromLease(UUID volumeId, UUID userId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new IllegalArgumentException("Volume not found: " + volumeId));

        if (!volume.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Forbidden: You do not own this volume");
        }
        if (volume.getStatus() != VolumeStatus.ATTACHED) {
            throw new IllegalStateException("Volume is not attached");
        }

        UUID prevLeaseId = volume.getActiveLeaseId();
        volume.setStatus(VolumeStatus.AVAILABLE);
        volume.setActiveLeaseId(null);
        volume.setUpdatedAt(Instant.now());
        Volume saved = volumeRepository.save(volume);

        // Auto snapshot upon detach to preserve state
        createSnapshot(volumeId, userId);

        auditService.log(userId, prevLeaseId, null, "VOLUME_DETACHED", "Detached volume " + volume.getName());
        return toInfo(saved);
    }

    @Transactional
    public VolumeSnapshotInfo createSnapshot(UUID volumeId, UUID userId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new IllegalArgumentException("Volume not found: " + volumeId));

        if (!volume.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Forbidden: You do not own this volume");
        }

        VolumeStorageService.SnapshotResult backup = storageService.backupVolume(volume.getId(), volume.getName(), volume.getSizeGb());

        UUID snapshotId = UUID.randomUUID();
        Instant now = Instant.now();
        VolumeSnapshot snapshot = new VolumeSnapshot(
                snapshotId,
                volume.getId(),
                backup.snapshotRef(),
                backup.sizeBytes(),
                now
        );

        snapshotRepository.save(snapshot);

        volume.setLastSnapshotRef(backup.snapshotRef());
        volume.setUpdatedAt(now);
        volumeRepository.save(volume);

        auditService.log(userId, null, null, "VOLUME_SNAPSHOT_CREATED", "Snapshot created: " + backup.snapshotRef());
        return new VolumeSnapshotInfo(snapshot.getId(), snapshot.getVolumeId(), snapshot.getSnapshotRef(), snapshot.getSizeBytes(), snapshot.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<VolumeSnapshotInfo> getSnapshots(UUID volumeId) {
        return snapshotRepository.findByVolumeIdOrderByCreatedAtDesc(volumeId).stream()
                .map(s -> new VolumeSnapshotInfo(s.getId(), s.getVolumeId(), s.getSnapshotRef(), s.getSizeBytes(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteVolume(UUID volumeId, UUID userId) {
        Volume volume = volumeRepository.findById(volumeId)
                .orElseThrow(() -> new IllegalArgumentException("Volume not found: " + volumeId));

        if (!volume.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Forbidden: You do not own this volume");
        }
        if (volume.getStatus() == VolumeStatus.ATTACHED) {
            throw new IllegalStateException("Cannot delete an attached volume. Detach it first.");
        }

        volume.setStatus(VolumeStatus.DELETED);
        volume.setUpdatedAt(Instant.now());
        volumeRepository.save(volume);

        auditService.log(userId, null, null, "VOLUME_DELETED", "Deleted volume " + volume.getName());
    }

    private VolumeInfo toInfo(Volume v) {
        return new VolumeInfo(
                v.getId(),
                v.getUserId(),
                v.getName(),
                v.getSizeGb(),
                v.getStatus(),
                v.getMountPath(),
                v.getActiveLeaseId(),
                v.getLastSnapshotRef(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
