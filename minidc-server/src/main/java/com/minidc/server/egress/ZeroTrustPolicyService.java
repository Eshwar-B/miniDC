package com.minidc.server.egress;

import com.minidc.common.model.EgressPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.List;

/**
 * Evaluates and manages zero-trust network isolation policies to protect lender home LANs
 * and prevent botnet/spam egress from sandbox VMs.
 */
@Service
public class ZeroTrustPolicyService {

    private final String exitGatewayEndpoint;

    public ZeroTrustPolicyService(
            @Value("${minidc.egress.gateway-endpoint:vpn.minidc.local:51820}") String exitGatewayEndpoint
    ) {
        this.exitGatewayEndpoint = exitGatewayEndpoint;
    }

    public EgressPolicy getActivePolicy() {
        return EgressPolicy.defaultStrictPolicy(exitGatewayEndpoint);
    }

    /**
     * Determines whether an IP address is considered a private / local network address
     * that should be isolated from sandbox guests.
     */
    public boolean isPrivateOrLocalAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String clean = ip.trim();

        // Direct common string checks
        if (clean.equals("localhost") || clean.startsWith("127.") || clean.equals("::1") || clean.equals("0.0.0.0")) {
            return true;
        }

        try {
            InetAddress addr = InetAddress.getByName(clean);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                return true;
            }
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) {
                int b0 = bytes[0] & 0xFF;
                int b1 = bytes[1] & 0xFF;

                // 10.0.0.0/8
                if (b0 == 10) return true;
                // 172.16.0.0/12
                if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;
                // 192.168.0.0/16
                if (b0 == 192 && b1 == 168) return true;
                // 169.254.0.0/16 (Link-local & Cloud Metadata)
                if (b0 == 169 && b1 == 254) return true;
                // 127.0.0.0/8
                if (b0 == 127) return true;
            } else if (bytes.length == 16) {
                // IPv6 Unique Local Address fc00::/7
                int b0 = bytes[0] & 0xFF;
                if ((b0 & 0xFE) == 0xFC) return true;
            }
        } catch (Exception ignored) {
            // Unresolvable host or invalid IP
            return false;
        }
        return false;
    }

    /**
     * Determines whether an outbound destination port is blocked under platform policy.
     */
    public boolean isPortBlocked(int port) {
        return EgressPolicy.DEFAULT_BLOCKED_PORTS.contains(port);
    }

    public record PolicyVerificationResult(
            boolean allowed,
            String targetIp,
            int targetPort,
            String reason
    ) {}

    /**
     * Verifies whether network traffic to the given destination IP and port is permitted.
     */
    public PolicyVerificationResult verifyTraffic(String targetIp, int targetPort) {
        if (isPortBlocked(targetPort)) {
            return new PolicyVerificationResult(
                    false,
                    targetIp,
                    targetPort,
                    "Destination port " + targetPort + " is blocked to prevent spam/abuse (SMTP/SSH outbound)"
            );
        }
        if (isPrivateOrLocalAddress(targetIp)) {
            return new PolicyVerificationResult(
                    false,
                    targetIp,
                    targetPort,
                    "Destination IP " + targetIp + " belongs to a private/local subnet and is blocked by Zero-Trust LAN isolation"
            );
        }
        return new PolicyVerificationResult(
                true,
                targetIp,
                targetPort,
                "Traffic permitted through egress gateway"
        );
    }
}
