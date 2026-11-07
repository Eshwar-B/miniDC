package com.minidc.server.node;

import com.minidc.common.enums.NodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a lender machine registered as a compute node.
 */
@Entity
@Table(name = "nodes")
public class Node {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NodeStatus status;

    @Column(name = "total_ram_mb")
    private Integer totalRamMb;

    @Column(name = "total_cores")
    private Integer totalCores;

    @Column(name = "total_disk_gb")
    private Integer totalDiskGb;

    @Column(length = 100)
    private String address;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    public Node() {
    }

    public Node(UUID id, UUID ownerId, String name, String tokenHash, NodeStatus status,
                Integer totalRamMb, Integer totalCores, Integer totalDiskGb, String address, Instant lastHeartbeat) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.status = status;
        this.totalRamMb = totalRamMb;
        this.totalCores = totalCores;
        this.totalDiskGb = totalDiskGb;
        this.address = address;
        this.lastHeartbeat = lastHeartbeat;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public Integer getTotalRamMb() {
        return totalRamMb;
    }

    public void setTotalRamMb(Integer totalRamMb) {
        this.totalRamMb = totalRamMb;
    }

    public Integer getTotalCores() {
        return totalCores;
    }

    public void setTotalCores(Integer totalCores) {
        this.totalCores = totalCores;
    }

    public Integer getTotalDiskGb() {
        return totalDiskGb;
    }

    public void setTotalDiskGb(Integer totalDiskGb) {
        this.totalDiskGb = totalDiskGb;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
}
