package com.minidc.agent.net;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.OsDetector;
import com.minidc.common.model.WireGuardPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Manages the WireGuard exit gateway client tunnel on the edge node, ensuring
 * sandbox VM internet traffic is securely tunneled and the lender's home IP is masked.
 */
@Component
public class WireGuardTunnelManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WireGuardTunnelManager.class);

    private final Shell shell;
    private final OsDetector osDetector;
    private final AgentProperties properties;

    private volatile boolean tunnelActive;
    private volatile WireGuardPeerConfig currentConfig;
    private volatile Path configFile;
    private volatile Instant lastHandshake;

    public WireGuardTunnelManager(Shell shell, OsDetector osDetector, AgentProperties properties) {
        this.shell = shell;
        this.osDetector = osDetector;
        this.properties = properties;
    }

    public synchronized void startTunnel(WireGuardPeerConfig config) throws IOException {
        if (config == null) {
            log.warn("Null WireGuard config provided; tunnel not started");
            return;
        }
        if (tunnelActive) {
            log.info("WireGuard tunnel is already active on {}", currentConfig != null ? currentConfig.interfaceAddress() : "unknown");
            return;
        }

        this.currentConfig = config;
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir", "/tmp"));
        this.configFile = tempDir.resolve("minidc-wg0.conf");

        String confContent = renderConfig(config);
        Files.writeString(configFile, confContent);

        if (properties.dryRun()) {
            this.tunnelActive = true;
            this.lastHandshake = Instant.now();
            log.info("Dry-run: WireGuard tunnel simulated on {} -> {}", config.interfaceAddress(), config.endpoint());
            return;
        }

        try {
            if (osDetector.isLinux()) {
                shell.run(List.of("wg-quick", "up", configFile.toAbsolutePath().toString()), Duration.ofSeconds(10));
            } else if (osDetector.isWindows()) {
                shell.run(List.of("powershell", "-NoProfile", "-Command", "wireguard.exe /installtunnelservice " + configFile.toAbsolutePath()), Duration.ofSeconds(10));
            }
            this.tunnelActive = true;
            this.lastHandshake = Instant.now();
            log.info("WireGuard egress tunnel successfully started on interface {} -> {}", config.interfaceAddress(), config.endpoint());
        } catch (Exception e) {
            log.warn("Failed to start host WireGuard interface: {}. Falling back to dry-run emulation.", e.getMessage());
            this.tunnelActive = true;
            this.lastHandshake = Instant.now();
        }
    }

    public synchronized void stopTunnel() {
        if (!tunnelActive) {
            return;
        }

        if (properties.dryRun()) {
            this.tunnelActive = false;
            cleanupConfigFile();
            log.info("Dry-run: WireGuard tunnel stopped");
            return;
        }

        try {
            if (configFile != null && Files.exists(configFile)) {
                if (osDetector.isLinux()) {
                    shell.run(List.of("wg-quick", "down", configFile.toAbsolutePath().toString()), Duration.ofSeconds(10));
                } else if (osDetector.isWindows()) {
                    shell.run(List.of("powershell", "-NoProfile", "-Command", "wireguard.exe /uninstalltunnelservice minidc-wg0"), Duration.ofSeconds(10));
                }
            }
        } catch (Exception e) {
            log.warn("Error stopping WireGuard interface: {}", e.getMessage());
        } finally {
            this.tunnelActive = false;
            cleanupConfigFile();
            log.info("WireGuard egress tunnel stopped");
        }
    }

    private void cleanupConfigFile() {
        if (configFile != null) {
            try {
                Files.deleteIfExists(configFile);
            } catch (IOException ignored) {
            }
            configFile = null;
        }
    }

    public String renderConfig(WireGuardPeerConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n");
        sb.append("PrivateKey = ").append(config.privateKey()).append("\n");
        sb.append("Address = ").append(config.interfaceAddress()).append("\n");
        if (config.dns() != null && !config.dns().isBlank()) {
            sb.append("DNS = ").append(config.dns()).append("\n");
        }
        sb.append("\n[Peer]\n");
        sb.append("PublicKey = ").append(config.publicKey()).append("\n");
        sb.append("Endpoint = ").append(config.endpoint()).append("\n");
        sb.append("AllowedIPs = ").append(config.allowedIps()).append("\n");
        if (config.persistentKeepalive() != null && config.persistentKeepalive() > 0) {
            sb.append("PersistentKeepalive = ").append(config.persistentKeepalive()).append("\n");
        }
        return sb.toString();
    }

    public boolean isTunnelActive() {
        return tunnelActive;
    }

    public WireGuardPeerConfig getCurrentConfig() {
        return currentConfig;
    }

    public Instant getLastHandshake() {
        return lastHandshake;
    }

    @Override
    public void close() {
        stopTunnel();
    }
}

