package com.minidc.server.volume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VolumeRepository extends JpaRepository<Volume, UUID> {
    List<Volume> findByUserId(UUID userId);
    Optional<Volume> findByActiveLeaseId(UUID leaseId);
}
