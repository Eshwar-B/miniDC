package com.minidc.server.egress;

import com.minidc.common.model.WireGuardPeerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WireGuardServiceTest {

    private WireGuardService wireGuardService;

    @BeforeEach
    void setUp() {
        wireGuardService = new WireGuardService(
                "vpn.minidc.local:51820",
                "testGatewayPubKey1234567890abcdef=",
                true
        );
    }

    @Test
    void shouldAllocateUniqueIpsForDifferentLeases() {
        UUID lease1 = UUID.randomUUID();
        UUID lease2 = UUID.randomUUID();

        WireGuardPeerConfig config1 = wireGuardService.allocateForLease(lease1);
        WireGuardPeerConfig config2 = wireGuardService.allocateForLease(lease2);

        assertNotNull(config1);
        assertNotNull(config2);
        assertNotEquals(config1.interfaceAddress(), config2.interfaceAddress());
        assertTrue(config1.interfaceAddress().startsWith("10.44.0."));
        assertTrue(config2.interfaceAddress().startsWith("10.44.0."));
        assertEquals("vpn.minidc.local:51820", config1.endpoint());
        assertEquals("testGatewayPubKey1234567890abcdef=", config1.publicKey());
    }

    @Test
    void shouldReturnSameConfigForSameLeaseId() {
        UUID leaseId = UUID.randomUUID();
        WireGuardPeerConfig first = wireGuardService.allocateForLease(leaseId);
        WireGuardPeerConfig second = wireGuardService.allocateForLease(leaseId);

        assertSame(first, second);
        assertEquals(first.interfaceAddress(), second.interfaceAddress());
    }

    @Test
    void shouldReleaseIpOnLeaseTermination() {
        UUID leaseId = UUID.randomUUID();
        WireGuardPeerConfig config = wireGuardService.allocateForLease(leaseId);
        assertEquals(1, wireGuardService.getActivePeerCount());

        wireGuardService.releaseLease(leaseId);
        assertEquals(0, wireGuardService.getActivePeerCount());
        assertTrue(wireGuardService.getLeaseConfig(leaseId).isEmpty());
    }

    @Test
    void shouldAllocateAndReleaseNodeConfig() {
        UUID nodeId = UUID.randomUUID();
        WireGuardPeerConfig config = wireGuardService.allocateForNode(nodeId);
        assertNotNull(config);
        assertEquals(1, wireGuardService.getActivePeerCount());

        wireGuardService.releaseNode(nodeId);
        assertEquals(0, wireGuardService.getActivePeerCount());
    }
}
