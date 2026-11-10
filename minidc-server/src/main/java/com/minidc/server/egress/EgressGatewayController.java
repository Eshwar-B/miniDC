package com.minidc.server.egress;

import com.minidc.common.model.EgressPolicy;
import com.minidc.common.model.WireGuardPeerConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API exposing zero-trust egress policies, WireGuard exit gateway health, and traffic verification.
 */
@RestController
@RequestMapping("/api/egress")
public class EgressGatewayController {

    private final ZeroTrustPolicyService zeroTrustPolicyService;
    private final WireGuardService wireGuardService;

    public EgressGatewayController(
            ZeroTrustPolicyService zeroTrustPolicyService,
            WireGuardService wireGuardService
    ) {
        this.zeroTrustPolicyService = zeroTrustPolicyService;
        this.wireGuardService = wireGuardService;
    }

    @GetMapping("/policy")
    public ResponseEntity<EgressPolicy> getPolicy() {
        return ResponseEntity.ok(zeroTrustPolicyService.getActivePolicy());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", wireGuardService.isEgressEnabled(),
                "gatewayEndpoint", wireGuardService.getGatewayEndpoint(),
                "gatewayPublicKey", wireGuardService.getGatewayPublicKey(),
                "activePeers", wireGuardService.getActivePeerCount(),
                "zeroTrustLanIsolation", true,
                "blockedPorts", EgressPolicy.DEFAULT_BLOCKED_PORTS,
                "isolatedSubnets", EgressPolicy.DEFAULT_PRIVATE_SUBNETS
        ));
    }

    public record VerifyRequest(String targetIp, int targetPort) {}

    @PostMapping("/verify")
    public ResponseEntity<ZeroTrustPolicyService.PolicyVerificationResult> verifyTraffic(
            @RequestBody VerifyRequest request
    ) {
        return ResponseEntity.ok(zeroTrustPolicyService.verifyTraffic(request.targetIp(), request.targetPort()));
    }

    @PostMapping("/nodes/{nodeId}/allocate")
    public ResponseEntity<WireGuardPeerConfig> allocateForNode(@PathVariable UUID nodeId) {
        WireGuardPeerConfig config = wireGuardService.allocateForNode(nodeId);
        if (config == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(config);
    }
}

