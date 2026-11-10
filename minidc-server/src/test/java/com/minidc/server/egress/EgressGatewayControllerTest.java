package com.minidc.server.egress;

import com.minidc.common.model.EgressPolicy;
import com.minidc.common.model.WireGuardPeerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class EgressGatewayControllerTest {

    private ZeroTrustPolicyService policyService;
    private WireGuardService wireGuardService;
    private EgressGatewayController controller;

    @BeforeEach
    void setUp() {
        policyService = Mockito.mock(ZeroTrustPolicyService.class);
        wireGuardService = Mockito.mock(WireGuardService.class);
        controller = new EgressGatewayController(policyService, wireGuardService);
    }

    @Test
    void shouldReturnActivePolicy() {
        EgressPolicy mockPolicy = EgressPolicy.defaultStrictPolicy("vpn.test:51820");
        when(policyService.getActivePolicy()).thenReturn(mockPolicy);

        ResponseEntity<EgressPolicy> response = controller.getPolicy();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().blockLan());
    }

    @Test
    void shouldReturnGatewayStatus() {
        when(wireGuardService.isEgressEnabled()).thenReturn(true);
        when(wireGuardService.getGatewayEndpoint()).thenReturn("vpn.minidc.local:51820");
        when(wireGuardService.getGatewayPublicKey()).thenReturn("pubkey123=");
        when(wireGuardService.getActivePeerCount()).thenReturn(4);

        ResponseEntity<Map<String, Object>> response = controller.getStatus();
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("enabled"));
        assertEquals("vpn.minidc.local:51820", body.get("gatewayEndpoint"));
        assertEquals(4, body.get("activePeers"));
    }

    @Test
    void shouldVerifyTrafficThroughPolicyService() {
        var verification = new ZeroTrustPolicyService.PolicyVerificationResult(
                false, "192.168.1.1", 80, "Blocked LAN"
        );
        when(policyService.verifyTraffic("192.168.1.1", 80)).thenReturn(verification);

        var response = controller.verifyTraffic(new EgressGatewayController.VerifyRequest("192.168.1.1", 80));
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().allowed());
    }

    @Test
    void shouldAllocateForNode() {
        UUID nodeId = UUID.randomUUID();
        WireGuardPeerConfig config = new WireGuardPeerConfig(
                "10.44.0.2/24", "privKey=", "pubKey=", "vpn:51820", "0.0.0.0/0", "1.1.1.1", 25
        );
        when(wireGuardService.allocateForNode(nodeId)).thenReturn(config);

        ResponseEntity<WireGuardPeerConfig> response = controller.allocateForNode(nodeId);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("10.44.0.2/24", response.getBody().interfaceAddress());
    }
}

