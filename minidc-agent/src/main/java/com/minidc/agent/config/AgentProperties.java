package com.minidc.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Configuration properties guiding agent hypervisor choice, networking, and server connection parameters.
 */
@ConfigurationProperties(prefix = "minidc")
public record AgentProperties(
        String serverUrl,
        UUID nodeId,
        String agentToken,
        String hypervisor,
        String bindAddress,
        String imagesDir,
        String leasesDir,
        int reserveRamPercent,
        String portRange,
        boolean dryRun,
        String dockerImage,
        String probeHost
) {
    public AgentProperties {
        if (bindAddress == null || bindAddress.isBlank()) {
            bindAddress = "127.0.0.1";
        }
        if (hypervisor == null || hypervisor.isBlank()) {
            hypervisor = "auto";
        }
        if (reserveRamPercent <= 0) {
            reserveRamPercent = 25;
        }
        if (portRange == null || portRange.isBlank()) {
            portRange = "33000-33999";
        }
        if (imagesDir == null || imagesDir.isBlank()) {
            imagesDir = "./data/images";
        }
        if (leasesDir == null || leasesDir.isBlank()) {
            leasesDir = "./data/leases";
        }
        if (dockerImage == null || dockerImage.isBlank()) {
            dockerImage = "minidc-desktop:latest";
        }
        if (probeHost == null || probeHost.isBlank()) {
            probeHost = "127.0.0.1";
        }
    }

    public static AgentProperties testDefault(boolean dryRun) {
        return new AgentProperties(
                "ws://localhost:8080/ws/agent",
                UUID.randomUUID(),
                "test-agent-token",
                "docker",
                "127.0.0.1",
                "./data/images",
                "./data/leases",
                25,
                "33000-33999",
                dryRun,
                "minidc-desktop:latest",
                "127.0.0.1"
        );
    }
}

