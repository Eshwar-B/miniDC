package com.minidc.server.node;

import com.minidc.common.enums.NodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data repository for managing compute Node entity persistence.
 */
@Repository
public interface NodeRepository extends JpaRepository<Node, UUID> {
    List<Node> findByOwnerId(UUID ownerId);
    List<Node> findByStatusAndLastHeartbeatBefore(NodeStatus status, Instant cutoff);
}
