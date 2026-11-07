package com.minidc.server.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an immutable audit log entry for system and user events.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "lease_id")
    private UUID leaseId;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(nullable = false, length = 60)
    private String event;

    @Column(columnDefinition = "TEXT")
    private String detail;

    public AuditLog() {
    }

    public AuditLog(Instant at, UUID actorId, UUID leaseId, UUID nodeId, String event, String detail) {
        this.at = at != null ? at : Instant.now();
        this.actorId = actorId;
        this.leaseId = leaseId;
        this.nodeId = nodeId;
        this.event = event;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(UUID leaseId) {
        this.leaseId = leaseId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
