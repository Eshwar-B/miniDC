package com.minidc.server.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an invite code required for new user registrations.
 */
@Entity
@Table(name = "invites")
public class Invite {

    @Id
    @Column(length = 40)
    private String code;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "used_by")
    private UUID usedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Invite() {
    }

    public Invite(String code, UUID createdBy, UUID usedBy, Instant expiresAt) {
        this.code = code;
        this.createdBy = createdBy;
        this.usedBy = usedBy;
        this.expiresAt = expiresAt;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUsedBy() {
        return usedBy;
    }

    public void setUsedBy(UUID usedBy) {
        this.usedBy = usedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedBy != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
