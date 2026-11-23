package com.minidc.common.enums;

/**
 * Represents the lifecycle states of a VM lease.
 */
public enum LeaseState {
    REQUESTED,
    APPROVED,
    PROVISIONING,
    ACTIVE,
    PAUSED,
    EXPIRING,
    ENDED,
    REJECTED,
    CANCELLED,
    FAILED
}
