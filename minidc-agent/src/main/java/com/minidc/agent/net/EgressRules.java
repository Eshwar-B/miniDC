package com.minidc.agent.net;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.OsDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Manages host network egress isolation rules via nftables (Linux) and Windows Defender Firewall (Windows).
 */
@Component
public class EgressRules {

    private static final Logger log = LoggerFactory.getLogger(EgressRules.class);

    private final Shell shell;
    private final OsDetector osDetector;
    private final AgentProperties properties;

    public EgressRules(Shell shell, OsDetector osDetector, AgentProperties properties) {
        this.shell = shell;
        this.osDetector = osDetector;
        this.properties = properties;
    }

    public List<String> getLinuxSetupCommands() {
        return List.of(
                "nft add table inet minidc",
                "nft add chain inet minidc fwd '{ type filter hook forward priority 0; policy accept; }'",
                "nft add rule inet minidc fwd iifname \"virbr-minidc\" ip daddr { 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 } drop",
                "nft add rule inet minidc fwd iifname \"virbr-minidc\" tcp dport { 25, 465, 587, 22 } drop",
                "nft add rule inet minidc fwd iifname \"virbr-minidc\" oifname \"virbr-minidc\" drop"
        );
    }

    public String getWindowsBlockLanCommand() {
        return "New-NetFirewallRule -DisplayName \"minidc-block-lan\" -Direction Outbound -Action Block -Program \"C:\\Program Files\\Oracle\\VirtualBox\\VBoxHeadless.exe\" -RemoteAddress 10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";
    }

    public String getWindowsAllowInboundCommand(UUID leaseId, int hostPort, String serverIp) {
        String remote = (serverIp != null && !serverIp.isBlank()) ? serverIp : "Any";
        return String.format(
                "New-NetFirewallRule -DisplayName \"minidc-in-%s\" -Direction Inbound -Action Allow -Protocol TCP -LocalPort %d -RemoteAddress %s",
                leaseId, hostPort, remote
        );
    }

    public String getWindowsRemoveInboundCommand(UUID leaseId) {
        return String.format("Remove-NetFirewallRule -DisplayName \"minidc-in-%s\" -ErrorAction SilentlyContinue", leaseId);
    }

    public void applyGlobalEgressRules() {
        if (properties.dryRun()) {
            log.info("Dry-run: skipping global egress firewall installation");
            return;
        }

        try {
            if (osDetector.isLinux()) {
                for (String cmd : getLinuxSetupCommands()) {
                    shell.run(List.of("sh", "-c", cmd), Duration.ofSeconds(5));
                }
                log.info("Applied Linux nftables egress isolation rules");
            } else if (osDetector.isWindows()) {
                shell.run(List.of("powershell", "-NoProfile", "-Command", getWindowsBlockLanCommand()), Duration.ofSeconds(5));
                log.info("Applied Windows Defender Firewall outbound LAN block rule");
            }
        } catch (Exception e) {
            log.warn("Could not apply global egress rules: {}", e.getMessage());
        }
    }

    public void applyLeaseInboundRule(UUID leaseId, int hostPort) {
        if (properties.dryRun()) {
            return;
        }
        if (osDetector.isWindows()) {
            try {
                String cmd = getWindowsAllowInboundCommand(leaseId, hostPort, null);
                shell.run(List.of("powershell", "-NoProfile", "-Command", cmd), Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Could not apply Windows inbound firewall rule for lease {}: {}", leaseId, e.getMessage());
            }
        }
    }

    public void removeLeaseInboundRule(UUID leaseId) {
        if (properties.dryRun()) {
            return;
        }
        if (osDetector.isWindows()) {
            try {
                String cmd = getWindowsRemoveInboundCommand(leaseId);
                shell.run(List.of("powershell", "-NoProfile", "-Command", cmd), Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Could not remove Windows inbound firewall rule for lease {}: {}", leaseId, e.getMessage());
            }
        }
    }
}

