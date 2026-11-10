package com.minidc.server.egress;

import com.minidc.common.model.WireGuardPeerConfig;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WireGuardCryptoTest {

    @Test
    void shouldGenerateValidX25519KeyPair() {
        WireGuardCrypto.KeyPairHolder pair = WireGuardCrypto.generateKeyPair();
        assertNotNull(pair.privateKey());
        assertNotNull(pair.publicKey());

        byte[] privBytes = Base64.getDecoder().decode(pair.privateKey());
        byte[] pubBytes = Base64.getDecoder().decode(pair.publicKey());

        assertEquals(32, privBytes.length, "WireGuard private key must be 32 bytes");
        assertEquals(32, pubBytes.length, "WireGuard public key must be 32 bytes");
        assertNotEquals(pair.privateKey(), pair.publicKey());
    }

    @Test
    void shouldRenderValidClientConfig() {
        WireGuardPeerConfig config = new WireGuardPeerConfig(
                "10.44.0.2/24",
                "c2VjcmV0UHJpdmF0ZUtleTEyMzQ1Njc4OTAxMjM0NTY=",
                "c2VydmVyUHVibGljS2V5MTIzNDU2Nzg5MDEyMzQ1Njc=",
                "vpn.minidc.local:51820",
                "0.0.0.0/0",
                "1.1.1.1",
                25
        );

        String ini = WireGuardCrypto.renderClientConfig(config, "c2VydmVyUHVibGljS2V5MTIzNDU2Nzg5MDEyMzQ1Njc=");

        assertTrue(ini.contains("[Interface]"));
        assertTrue(ini.contains("PrivateKey = c2VjcmV0UHJpdmF0ZUtleTEyMzQ1Njc4OTAxMjM0NTY="));
        assertTrue(ini.contains("Address = 10.44.0.2/24"));
        assertTrue(ini.contains("DNS = 1.1.1.1"));
        assertTrue(ini.contains("[Peer]"));
        assertTrue(ini.contains("Endpoint = vpn.minidc.local:51820"));
        assertTrue(ini.contains("AllowedIPs = 0.0.0.0/0"));
        assertTrue(ini.contains("PersistentKeepalive = 25"));
    }

    @Test
    void shouldRenderServerPeerBlock() {
        String peerBlock = WireGuardCrypto.renderServerPeerBlock("clientPub123=", "10.44.0.5/32");
        assertTrue(peerBlock.contains("[Peer]"));
        assertTrue(peerBlock.contains("PublicKey = clientPub123="));
        assertTrue(peerBlock.contains("AllowedIPs = 10.44.0.5/32"));
    }
}
