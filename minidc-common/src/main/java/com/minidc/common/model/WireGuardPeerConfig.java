package com.minidc.common.model;

/**
 * Configuration payload representing a WireGuard client interface and its exit gateway peer.
 */
public record WireGuardPeerConfig(
        String interfaceAddress,
        String privateKey,
        String publicKey,
        String endpoint,
        String allowedIps,
        String dns,
        Integer persistentKeepalive
) {
    public WireGuardPeerConfig {
        if (allowedIps == null || allowedIps.isBlank()) {
            allowedIps = "0.0.0.0/0, ::/0";
        }
        if (dns == null || dns.isBlank()) {
            dns = "1.1.1.1, 8.8.8.8";
        }
        if (persistentKeepalive == null) {
            persistentKeepalive = 25;
        }
    }
}

