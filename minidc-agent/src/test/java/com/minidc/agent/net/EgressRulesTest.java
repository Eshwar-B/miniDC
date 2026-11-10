package com.minidc.agent.net;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.OsDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgressRulesTest {

    private Shell shell;
    private OsDetector osDetector;
    private AgentProperties properties;
    private EgressRules egressRules;

    @BeforeEach
    void setUp() {
        shell = Mockito.mock(Shell.class);
        osDetector = Mockito.mock(OsDetector.class);
        properties = AgentProperties.testDefault(true);
        egressRules = new EgressRules(shell, osDetector, properties);
    }

    @Test
    void shouldGenerateStrictLinuxNftablesCommands() {
        List<String> commands = egressRules.getLinuxSetupCommands();
        assertFalse(commands.isEmpty());

        String full = String.join("\n", commands);
        assertTrue(full.contains("nft add table inet minidc"));
        assertTrue(full.contains("10.0.0.0/8"));
        assertTrue(full.contains("172.16.0.0/12"));
        assertTrue(full.contains("192.168.0.0/16"));
        assertTrue(full.contains("169.254.0.0/16"));
        assertTrue(full.contains("25, 465, 587, 22"));
        assertTrue(full.contains("drop"));
    }

    @Test
    void shouldGenerateLinuxIptablesFallbackCommands() {
        List<String> commands = egressRules.getLinuxIptablesCommands();
        assertFalse(commands.isEmpty());

        String full = String.join("\n", commands);
        assertTrue(full.contains("iptables -I FORWARD"));
        assertTrue(full.contains("192.168.0.0/16"));
        assertTrue(full.contains("25,465,587,22"));
    }

    @Test
    void shouldGenerateWindowsFirewallCommands() {
        String blockLan = egressRules.getWindowsBlockLanCommand();
        assertTrue(blockLan.contains("New-NetFirewallRule"));
        assertTrue(blockLan.contains("minidc-block-lan"));
        assertTrue(blockLan.contains("192.168.0.0/16"));
        assertTrue(blockLan.contains("VBoxHeadless.exe"));

        String blockPorts = egressRules.getWindowsBlockAbusePortsCommand();
        assertTrue(blockPorts.contains("minidc-block-smtp-ssh"));
        assertTrue(blockPorts.contains("25,465,587,22"));

        UUID leaseId = UUID.randomUUID();
        String allowIn = egressRules.getWindowsAllowInboundCommand(leaseId, 3389, "192.168.1.100");
        assertTrue(allowIn.contains("minidc-in-" + leaseId));
        assertTrue(allowIn.contains("LocalPort 3389"));

        String removeIn = egressRules.getWindowsRemoveInboundCommand(leaseId);
        assertTrue(removeIn.contains("Remove-NetFirewallRule"));
        assertTrue(removeIn.contains(leaseId.toString()));
    }

    @Test
    void shouldSafelyExecuteInDryRun() {
        // Must not throw in dry-run mode
        egressRules.applyGlobalEgressRules();
        egressRules.applyLeaseInboundRule(UUID.randomUUID(), 3389);
        egressRules.removeLeaseInboundRule(UUID.randomUUID());
        egressRules.removeGlobalEgressRules();
    }
}
