package com.minidc.server.volume;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.enums.VolumeStatus;
import com.minidc.common.model.VolumeInfo;
import com.minidc.common.model.VolumeSnapshotInfo;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VolumeServiceTest {

    private VolumeRepository volumeRepository;
    private VolumeSnapshotRepository snapshotRepository;
    private VolumeStorageService storageService;
    private LeaseRepository leaseRepository;
    private AuditService auditService;
    private VolumeService volumeService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        volumeRepository = Mockito.mock(VolumeRepository.class);
        snapshotRepository = Mockito.mock(VolumeSnapshotRepository.class);
        storageService = Mockito.mock(VolumeStorageService.class);
        leaseRepository = Mockito.mock(LeaseRepository.class);
        auditService = Mockito.mock(AuditService.class);

        volumeService = new VolumeService(volumeRepository, snapshotRepository, storageService, leaseRepository, auditService);
    }

    @Test
    void shouldCreateVolumeWithValidParameters() {
        when(volumeRepository.save(any(Volume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VolumeInfo volume = volumeService.createVolume(userId, "Project-Data", 50, "/home/borrower/workspace");

        assertNotNull(volume);
        assertEquals("Project-Data", volume.name());
        assertEquals(50, volume.sizeGb());
        assertEquals(VolumeStatus.AVAILABLE, volume.status());
        assertEquals("/home/borrower/workspace", volume.mountPath());
        verify(volumeRepository).save(any(Volume.class));
    }

    @Test
    void shouldRejectInvalidVolumeSize() {
        assertThrows(IllegalArgumentException.class, () -> volumeService.createVolume(userId, "BadSize", 0, null));
        assertThrows(IllegalArgumentException.class, () -> volumeService.createVolume(userId, "BadSize", 600, null));
    }

    @Test
    void shouldAttachVolumeToActiveLease() {
        UUID volumeId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        Volume volume = new Volume(volumeId, userId, "Data", 20, VolumeStatus.AVAILABLE, "/mnt", null, null, Instant.now(), Instant.now());
        when(volumeRepository.findById(volumeId)).thenReturn(Optional.of(volume));
        when(volumeRepository.save(any(Volume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease lease = new Lease();
        lease.setId(leaseId);
        lease.setBorrowerId(userId);
        lease.setState(LeaseState.ACTIVE);
        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        VolumeInfo attached = volumeService.attachToLease(volumeId, leaseId, userId);

        assertEquals(VolumeStatus.ATTACHED, attached.status());
        assertEquals(leaseId, attached.activeLeaseId());
    }

    @Test
    void shouldCreateAndRetrieveSnapshots() {
        UUID volumeId = UUID.randomUUID();
        Volume volume = new Volume(volumeId, userId, "Data", 20, VolumeStatus.AVAILABLE, "/mnt", null, null, Instant.now(), Instant.now());
        when(volumeRepository.findById(volumeId)).thenReturn(Optional.of(volume));
        when(volumeRepository.save(any(Volume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(storageService.backupVolume(any(), any(), any(Integer.class))).thenReturn(
                new VolumeStorageService.SnapshotResult("s3://snap-123", 20480L, "hash123")
        );

        VolumeSnapshotInfo snapshot = volumeService.createSnapshot(volumeId, userId);

        assertNotNull(snapshot);
        assertEquals("s3://snap-123", snapshot.snapshotRef());
        verify(snapshotRepository).save(any(VolumeSnapshot.class));
    }
}
