package com.minidc.server.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a lender's capacity offer detailing maximum resource boundaries and schedules.
 */
@Entity
@Table(name = "offers")
public class Offer {

    @Id
    private UUID id;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "max_ram_mb", nullable = false)
    private int maxRamMb;

    @Column(name = "max_cores", nullable = false)
    private int maxCores;

    @Column(name = "max_disk_gb", nullable = false)
    private int maxDiskGb;

    @Column(name = "max_hours", nullable = false)
    private int maxHours;

    @Column(name = "window_start")
    private LocalTime windowStart;

    @Column(name = "window_end")
    private LocalTime windowEnd;

    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_user_ids", columnDefinition = "uuid[]")
    private List<UUID> allowedUserIds;

    @Column(nullable = false)
    private boolean active;

    public Offer() {
    }

    public Offer(UUID id, UUID nodeId, int maxRamMb, int maxCores, int maxDiskGb, int maxHours,
                 LocalTime windowStart, LocalTime windowEnd, boolean autoApprove, List<UUID> allowedUserIds, boolean active) {
        this.id = id;
        this.nodeId = nodeId;
        this.maxRamMb = maxRamMb;
        this.maxCores = maxCores;
        this.maxDiskGb = maxDiskGb;
        this.maxHours = maxHours;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.autoApprove = autoApprove;
        this.allowedUserIds = allowedUserIds;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public int getMaxRamMb() {
        return maxRamMb;
    }

    public void setMaxRamMb(int maxRamMb) {
        this.maxRamMb = maxRamMb;
    }

    public int getMaxCores() {
        return maxCores;
    }

    public void setMaxCores(int maxCores) {
        this.maxCores = maxCores;
    }

    public int getMaxDiskGb() {
        return maxDiskGb;
    }

    public void setMaxDiskGb(int maxDiskGb) {
        this.maxDiskGb = maxDiskGb;
    }

    public int getMaxHours() {
        return maxHours;
    }

    public void setMaxHours(int maxHours) {
        this.maxHours = maxHours;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public boolean isAutoApprove() {
        return autoApprove;
    }

    public void setAutoApprove(boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    public List<UUID> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(List<UUID> allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
