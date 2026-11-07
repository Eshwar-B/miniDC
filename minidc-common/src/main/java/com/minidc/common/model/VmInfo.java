package com.minidc.common.model;

/**
 * Information describing a running virtual machine instance.
 */
public record VmInfo(
        String vmName,
        String guestIp,
        int hostPort
) {
}
