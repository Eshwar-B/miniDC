package com.minidc.agent.net;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.OsDetector;
import com.minidc.common.model.WireGuardPeerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WireGuardTunnelManagerTest {

    private Shell shell;
    private OsDetector osDetector;
    private AgentProperties properties;
    private WireGuardTunnelManager tunnelManager;

    @BeforeEach
    void setUp() {
        shell = Mockito.mock(Shell.class);
        osDetector = Mockito.mock(OsDetector.class);
        properties = AgentProperties.testDefault(true);
        tunnelManager = new WireGuardTunnelManager(shell, osDetector, properties);
    }

    @Test
    void shouldRenderWireGuardConfigCorrectly() {
        WireGuardPeerConfig config = new WireGuardPeerConfig(
                "10.44.0.3/24",
                "privKeyBase64==",
                "pubKeyBase64==",
                "vpn.minidc.local:51820",
                "0.0.0.0/0",
                "1.1.1.1",
                25
        );

        String rendered = tunnelManager.renderConfig(config);
        assertTrue(rendered.contains("[Interface]"));
        assertTrue(rendered.contains("PrivateKey = privKeyBase64=="));
        assertTrue(rendered.contains("Address = 10.44.0.3/24"));
        assertTrue(rendered.contains("DNS = 1.1.1.1"));
        assertTrue(rendered.contains("[Peer]"));
        assertTrue(rendered.contains("PublicKey = pubKeyBase64=="));
        assertTrue(rendered.contains("Endpoint = vpn.minidc.local:51820"));
        assertTrue(rendered.contains("AllowedIPs = 0.0.0.0/0"));
        assertTrue(rendered.contains("PersistentKeepalive = 25"));
    }

    @Test
    void shouldStartAndStopTunnelInDryRun() throws IOException {
        WireGuardPeerConfig config = new WireGuardPeerConfig(
                "10.44.0.5/24",
                "privKey==",
                "pubKey==",
                "vpn.minidc.local:51820",
                "0.0.0.0/0",
                "1.1.1.1",
                25
        );

        assertFalse(tunnelManager.isTunnelActive());
        tunnelManager.startTunnel(config);

        assertTrue(tunnelManager.isTunnelActive());
        assertNotNull(tunnelManager.getLastHandshake());
        assertEquals("10.44.0.5/24", tunnelManager.getCurrentConfig().interfaceAddress());

        tunnelManager.stopTunnel();
        assertFalse(tunnelManager.isTunnelActive());
    }
}
