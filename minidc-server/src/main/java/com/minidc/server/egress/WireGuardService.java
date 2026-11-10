package com.minidc.server.egress;

import com.minidc.common.model.WireGuardPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages WireGuard egress IP allocations and peer configurations for edge nodes and sandbox leases.
 */
@Service
public class WireGuardService {

    private static final Logger log = LoggerFactory.getLogger(WireGuardService.class);

    private final String gatewayEndpoint;
    private final String gatewayPublicKey;
    private final boolean egressEnabled;

    private final AtomicInteger nextIpOctet = new AtomicInteger(2); // 10.44.0.2 .. 10.44.0.254
    private final Map<UUID, WireGuardPeerConfig> peerConfigsByLease = new ConcurrentHashMap<>();
    private final Map<UUID, WireGuardPeerConfig> peerConfigsByNode = new ConcurrentHashMap<>();
    private final Map<String, UUID> allocatedIps = new ConcurrentHashMap<>();

    public WireGuardService(
            @Value("${minidc.egress.gateway-endpoint:vpn.minidc.local:51820}") String gatewayEndpoint,
            @Value("${minidc.egress.gateway-public-key:mInIdCeGrEsSpUbLiCkEy1234567890abcdef=}") String gatewayPublicKey,
            @Value("${minidc.egress.enabled:true}") boolean egressEnabled
    ) {
        this.gatewayEndpoint = gatewayEndpoint;
        this.gatewayPublicKey = gatewayPublicKey;
        this.egressEnabled = egressEnabled;
    }

    /**
     * Allocates a WireGuard IP and client config for a given sandbox lease.
     */
    public synchronized WireGuardPeerConfig allocateForLease(UUID leaseId) {
        if (!egressEnabled) {
            return null;
        }
        if (peerConfigsByLease.containsKey(leaseId)) {
            return peerConfigsByLease.get(leaseId);
        }

        String clientIp = nextAvailableIp();
        WireGuardCrypto.KeyPairHolder keys = WireGuardCrypto.generateKeyPair();

        WireGuardPeerConfig config = new WireGuardPeerConfig(
                clientIp + "/24",
                keys.privateKey(),
                gatewayPublicKey,
                gatewayEndpoint,
                "0.0.0.0/0, ::/0",
                "1.1.1.1, 8.8.8.8",
                25
        );

        peerConfigsByLease.put(leaseId, config);
        allocatedIps.put(clientIp, leaseId);
        log.info("Allocated WireGuard peer IP {} for lease {}", clientIp, leaseId);
        return config;
    }

    /**
     * Allocates a WireGuard IP and client config for an edge node.
     */
    public synchronized WireGuardPeerConfig allocateForNode(UUID nodeId) {
        if (!egressEnabled) {
            return null;
        }
        if (peerConfigsByNode.containsKey(nodeId)) {
            return peerConfigsByNode.get(nodeId);
        }

        String clientIp = nextAvailableIp();
        WireGuardCrypto.KeyPairHolder keys = WireGuardCrypto.generateKeyPair();

        WireGuardPeerConfig config = new WireGuardPeerConfig(
                clientIp + "/24",
                keys.privateKey(),
                gatewayPublicKey,
                gatewayEndpoint,
                "0.0.0.0/0, ::/0",
                "1.1.1.1, 8.8.8.8",
                25
        );

        peerConfigsByNode.put(nodeId, config);
        allocatedIps.put(clientIp, nodeId);
        log.info("Allocated WireGuard peer IP {} for node {}", clientIp, nodeId);
        return config;
    }

    /**
     * Releases allocated WireGuard IP and configuration when a lease finishes or terminates.
     */
    public synchronized void releaseLease(UUID leaseId) {
        WireGuardPeerConfig config = peerConfigsByLease.remove(leaseId);
        if (config != null) {
            String ip = config.interfaceAddress().replace("/24", "");
            allocatedIps.remove(ip);
            log.info("Released WireGuard peer IP {} for lease {}", ip, leaseId);
        }
    }

    public synchronized void releaseNode(UUID nodeId) {
        WireGuardPeerConfig config = peerConfigsByNode.remove(nodeId);
        if (config != null) {
            String ip = config.interfaceAddress().replace("/24", "");
            allocatedIps.remove(ip);
            log.info("Released WireGuard peer IP {} for node {}", ip, nodeId);
        }
    }

    public Optional<WireGuardPeerConfig> getLeaseConfig(UUID leaseId) {
        return Optional.ofNullable(peerConfigsByLease.get(leaseId));
    }

    public Optional<WireGuardPeerConfig> getNodeConfig(UUID nodeId) {
        return Optional.ofNullable(peerConfigsByNode.get(nodeId));
    }

    public int getActivePeerCount() {
        return peerConfigsByLease.size() + peerConfigsByNode.size();
    }

    public String getGatewayEndpoint() {
        return gatewayEndpoint;
    }

    public String getGatewayPublicKey() {
        return gatewayPublicKey;
    }

    public boolean isEgressEnabled() {
        return egressEnabled;
    }

    private String nextAvailableIp() {
        for (int i = 0; i < 250; i++) {
            int octet = nextIpOctet.getAndIncrement();
            if (octet > 254) {
                nextIpOctet.set(2);
                octet = 2;
            }
            String candidate = "10.44.0." + octet;
            if (!allocatedIps.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("WireGuard subnet 10.44.0.0/24 exhausted - no free IP available");
    }
}

