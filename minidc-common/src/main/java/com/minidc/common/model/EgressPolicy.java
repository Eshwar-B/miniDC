package com.minidc.common.model;

import java.util.List;

/**
 * Declares the zero-trust network boundaries enforced for sandbox guests on edge nodes.
 */
public record EgressPolicy(
        boolean blockLan,
        List<String> privateSubnets,
        List<Integer> blockedPorts,
        boolean wireguardEnabled,
        String exitGatewayEndpoint
) {
    public static final List<String> DEFAULT_PRIVATE_SUBNETS = List.of(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            "127.0.0.0/8"
    );

    public static final List<Integer> DEFAULT_BLOCKED_PORTS = List.of(
            25,   // SMTP
            465,  // SMTPS
            587,  // SMTP Submission
            22    // Outbound SSH brute-force defense
    );

    public static EgressPolicy defaultStrictPolicy(String exitGatewayEndpoint) {
        return new EgressPolicy(
                true,
                DEFAULT_PRIVATE_SUBNETS,
                DEFAULT_BLOCKED_PORTS,
                exitGatewayEndpoint != null && !exitGatewayEndpoint.isBlank(),
                exitGatewayEndpoint
        );
    }
}

