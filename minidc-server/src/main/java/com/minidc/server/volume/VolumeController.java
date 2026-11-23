package com.minidc.server.volume;

import com.minidc.common.model.VolumeInfo;
import com.minidc.common.model.VolumeSnapshotInfo;
import com.minidc.server.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for borrower persistent home directory volumes, snapshot syncing, and attachment.
 */
@RestController
@RequestMapping("/api/volumes")
public class VolumeController {

    private final VolumeService volumeService;

    public VolumeController(VolumeService volumeService) {
        this.volumeService = volumeService;
    }

    public record CreateVolumeRequest(String name, int sizeGb, String mountPath) {}
    public record AttachVolumeRequest(UUID leaseId) {}

    @PostMapping
    public ResponseEntity<VolumeInfo> createVolume(
            @RequestBody CreateVolumeRequest request,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(volumeService.createVolume(user.id(), request.name(), request.sizeGb(), request.mountPath()));
    }

    @GetMapping
    public ResponseEntity<List<VolumeInfo>> getMyVolumes(
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(volumeService.getUserVolumes(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VolumeInfo> getVolume(@PathVariable UUID id) {
        return ResponseEntity.ok(volumeService.getVolume(id));
    }

    @PostMapping("/{id}/attach")
    public ResponseEntity<VolumeInfo> attachVolume(
            @PathVariable UUID id,
            @RequestBody AttachVolumeRequest request,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(volumeService.attachToLease(id, request.leaseId(), user.id()));
    }

    @PostMapping("/{id}/detach")
    public ResponseEntity<VolumeInfo> detachVolume(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(volumeService.detachFromLease(id, user.id()));
    }

    @PostMapping("/{id}/snapshots")
    public ResponseEntity<VolumeSnapshotInfo> createSnapshot(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(volumeService.createSnapshot(id, user.id()));
    }

    @GetMapping("/{id}/snapshots")
    public ResponseEntity<List<VolumeSnapshotInfo>> getSnapshots(@PathVariable UUID id) {
        return ResponseEntity.ok(volumeService.getSnapshots(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVolume(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        volumeService.deleteVolume(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
