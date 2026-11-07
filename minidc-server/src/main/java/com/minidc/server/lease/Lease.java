package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a sandbox compute lease allocation.
 */
@Entity
@Table(name = "leases")
public class Lease {

    @Id
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "borrower_id", nullable = false)
    private UUID borrowerId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "ram_mb", nullable = false)
    private int ramMb;

    @Column(nullable = false)
    private int cores;

    @Column(name = "disk_gb", nullable = false)
    private int diskGb;

    @Column(name = "requested_hours", nullable = false)
    private int requestedHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaseState state;

    @Column(name = "vm_name", length = 60)
    private String vmName;

    @Column(name = "vm_port")
    private Integer vmPort;

    @Column(name = "vm_user", length = 40)
    private String vmUser;

    @Column(name = "vm_password_enc", columnDefinition = "TEXT")
    private String vmPasswordEnc;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    public Lease() {
    }

    public Lease(UUID id, UUID offerId, UUID borrowerId, UUID nodeId, int ramMb, int cores, int diskGb,
                 int requestedHours, LeaseState state, String vmName, Integer vmPort, String vmUser,
                 String vmPasswordEnc, Instant startsAt, Instant endsAt, Instant createdAt, Instant updatedAt, String failReason) {
        this.id = id;
        this.offerId = offerId;
        this.borrowerId = borrowerId;
        this.nodeId = nodeId;
        this.ramMb = ramMb;
        this.cores = cores;
        this.diskGb = diskGb;
        this.requestedHours = requestedHours;
        this.state = state;
        this.vmName = vmName;
        this.vmPort = vmPort;
        this.vmUser = vmUser;
        this.vmPasswordEnc = vmPasswordEnc;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.failReason = failReason;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public void setOfferId(UUID offerId) {
        this.offerId = offerId;
    }

    public UUID getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(UUID borrowerId) {
        this.borrowerId = borrowerId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public int getRamMb() {
        return ramMb;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = ramMb;
    }

    public int getCores() {
        return cores;
    }

    public void setCores(int cores) {
        this.cores = cores;
    }

    public int getDiskGb() {
        return diskGb;
    }

    public void setDiskGb(int diskGb) {
        this.diskGb = diskGb;
    }

    public int getRequestedHours() {
        return requestedHours;
    }

    public void setRequestedHours(int requestedHours) {
        this.requestedHours = requestedHours;
    }

    public LeaseState getState() {
        return state;
    }

    public void setState(LeaseState state) {
        this.state = state;
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    public Integer getVmPort() {
        return vmPort;
    }

    public void setVmPort(Integer vmPort) {
        this.vmPort = vmPort;
    }

    public String getVmUser() {
        return vmUser;
    }

    public void setVmUser(String vmUser) {
        this.vmUser = vmUser;
    }

    public String getVmPasswordEnc() {
        return vmPasswordEnc;
    }

    public void setVmPasswordEnc(String vmPasswordEnc) {
        this.vmPasswordEnc = vmPasswordEnc;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
