package com.minidc.server.egress;

import com.minidc.common.model.EgressPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZeroTrustPolicyServiceTest {

    private ZeroTrustPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new ZeroTrustPolicyService("vpn.minidc.local:51820");
    }

    @Test
    void shouldIdentifyRfc1918AndPrivateSubnets() {
        assertTrue(policyService.isPrivateOrLocalAddress("192.168.1.1"));
        assertTrue(policyService.isPrivateOrLocalAddress("192.168.0.254"));
        assertTrue(policyService.isPrivateOrLocalAddress("10.0.0.1"));
        assertTrue(policyService.isPrivateOrLocalAddress("10.200.5.12"));
        assertTrue(policyService.isPrivateOrLocalAddress("172.16.0.1"));
        assertTrue(policyService.isPrivateOrLocalAddress("172.31.255.255"));
        assertTrue(policyService.isPrivateOrLocalAddress("127.0.0.1"));
        assertTrue(policyService.isPrivateOrLocalAddress("localhost"));
        assertTrue(policyService.isPrivateOrLocalAddress("169.254.169.254")); // Cloud metadata IP
    }

    @Test
    void shouldIdentifyPublicInternetAddressesAsNonPrivate() {
        assertFalse(policyService.isPrivateOrLocalAddress("8.8.8.8"));
        assertFalse(policyService.isPrivateOrLocalAddress("1.1.1.1"));
        assertFalse(policyService.isPrivateOrLocalAddress("93.184.216.34"));
    }

    @Test
    void shouldBlockSpamAndAbusePorts() {
        assertTrue(policyService.isPortBlocked(25));  // SMTP
        assertTrue(policyService.isPortBlocked(465)); // SMTPS
        assertTrue(policyService.isPortBlocked(587)); // Submission
        assertTrue(policyService.isPortBlocked(22));  // Outbound SSH
        assertFalse(policyService.isPortBlocked(80)); // HTTP
        assertFalse(policyService.isPortBlocked(443)); // HTTPS
        assertFalse(policyService.isPortBlocked(8080));
    }

    @Test
    void shouldVerifyTrafficPolicyAccurately() {
        // Private LAN should be rejected
        var privateResult = policyService.verifyTraffic("192.168.1.50", 80);
        assertFalse(privateResult.allowed());
        assertTrue(privateResult.reason().contains("private/local subnet"));

        // SMTP port on public IP should be rejected
        var smtpResult = policyService.verifyTraffic("8.8.8.8", 25);
        assertFalse(smtpResult.allowed());
        assertTrue(smtpResult.reason().contains("blocked to prevent spam/abuse"));

        // Public HTTPS should be allowed
        var publicResult = policyService.verifyTraffic("8.8.8.8", 443);
        assertTrue(publicResult.allowed());
    }

    @Test
    void shouldProvideDefaultStrictPolicy() {
        EgressPolicy policy = policyService.getActivePolicy();
        assertTrue(policy.blockLan());
        assertTrue(policy.wireguardEnabled());
        assertEquals("vpn.minidc.local:51820", policy.exitGatewayEndpoint());
        assertTrue(policy.blockedPorts().contains(25));
        assertTrue(policy.privateSubnets().contains("192.168.0.0/16"));
    }
}

