package com.minidc.common.enums;

/**
 * Types of malicious or policy-violating activity detected within guest sandboxes.
 */
public enum AbuseType {
    CRYPTOMINING,
    PORT_SCANNING,
    RESOURCE_EXHAUSTION,
    ABNORMAL_EGRESS
}
