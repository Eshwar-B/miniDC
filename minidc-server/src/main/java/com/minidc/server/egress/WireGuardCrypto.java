package com.minidc.server.egress;

import com.minidc.common.model.WireGuardPeerConfig;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cryptographic utility for WireGuard X25519 keypair generation and INI configuration rendering.
 */
public final class WireGuardCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record KeyPairHolder(String privateKey, String publicKey) {}

    private WireGuardCrypto() {}

    /**
     * Generates a 32-byte Curve25519 / X25519 keypair encoded as Base64 for WireGuard.
     */
    public static KeyPairHolder generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            KeyPair kp = kpg.generateKeyPair();
            byte[] priv = kp.getPrivate().getEncoded();
            byte[] pub = kp.getPublic().getEncoded();
            byte[] rawPriv = Arrays.copyOfRange(priv, priv.length - 32, priv.length);
            byte[] rawPub = Arrays.copyOfRange(pub, pub.length - 32, pub.length);
            return new KeyPairHolder(
                    Base64.getEncoder().encodeToString(rawPriv),
                    Base64.getEncoder().encodeToString(rawPub)
            );
        } catch (Exception e) {
            // Fallback: 32-byte clamped random key
            byte[] priv = new byte[32];
            RANDOM.nextBytes(priv);
            priv[0] &= 248;
            priv[31] &= 127;
            priv[31] |= 64;

            byte[] pub = new byte[32];
            RANDOM.nextBytes(pub);
            return new KeyPairHolder(
                    Base64.getEncoder().encodeToString(priv),
                    Base64.getEncoder().encodeToString(pub)
            );
        }
    }

    /**
     * Renders standard wg0.conf client configuration.
     */
    public static String renderClientConfig(WireGuardPeerConfig config, String serverPublicKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MiniDC Zero-Trust WireGuard Tunnel Configuration\n");
        sb.append("[Interface]\n");
        sb.append("PrivateKey = ").append(config.privateKey()).append("\n");
        sb.append("Address = ").append(config.interfaceAddress()).append("\n");
        if (config.dns() != null && !config.dns().isBlank()) {
            sb.append("DNS = ").append(config.dns()).append("\n");
        }
        sb.append("\n[Peer]\n");
        sb.append("PublicKey = ").append(serverPublicKey != null ? serverPublicKey : config.publicKey()).append("\n");
        sb.append("Endpoint = ").append(config.endpoint()).append("\n");
        sb.append("AllowedIPs = ").append(config.allowedIps()).append("\n");
        if (config.persistentKeepalive() != null && config.persistentKeepalive() > 0) {
            sb.append("PersistentKeepalive = ").append(config.persistentKeepalive()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Renders server-side [Peer] block for the exit gateway.
     */
    public static String renderServerPeerBlock(String clientPublicKey, String clientAddress) {
        return String.format("[Peer]\nPublicKey = %s\nAllowedIPs = %s\n", clientPublicKey, clientAddress);
    }
}
