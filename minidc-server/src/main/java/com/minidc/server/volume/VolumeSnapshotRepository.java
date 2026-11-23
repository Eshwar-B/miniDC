package com.minidc.server.volume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VolumeSnapshotRepository extends JpaRepository<VolumeSnapshot, UUID> {
    List<VolumeSnapshot> findByVolumeIdOrderByCreatedAtDesc(UUID volumeId);
}
