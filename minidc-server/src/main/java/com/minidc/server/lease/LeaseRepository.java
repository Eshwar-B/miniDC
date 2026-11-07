package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Data repository for querying and persisting Lease entities.
 */
@Repository
public interface LeaseRepository extends JpaRepository<Lease, UUID> {

    List<Lease> findByBorrowerId(UUID borrowerId);

    List<Lease> findByNodeId(UUID nodeId);

    List<Lease> findByNodeIdIn(Collection<UUID> nodeIds);

    List<Lease> findByNodeIdAndStateIn(UUID nodeId, Collection<LeaseState> states);

    long countByBorrowerIdAndStateIn(UUID borrowerId, Collection<LeaseState> states);

    List<Lease> findByStateAndEndsAtBefore(LeaseState state, Instant endsAt);

    List<Lease> findByStateInAndEndsAtBefore(Collection<LeaseState> states, Instant endsAt);

    List<Lease> findByStateAndCreatedAtBefore(LeaseState state, Instant createdAt);

    List<Lease> findByStateAndUpdatedAtBefore(LeaseState state, Instant updatedAt);

    @Query("SELECT l FROM Lease l JOIN Node n ON l.nodeId = n.id WHERE l.state = 'ACTIVE' AND n.status = 'OFFLINE' AND n.lastHeartbeat < :cutoff")
    List<Lease> findActiveOnOfflineNodes(@Param("cutoff") Instant cutoff);
}
