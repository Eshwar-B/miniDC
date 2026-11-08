package com.minidc.server.guac;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GuacCryptoTest {

    private GuacCrypto guacCrypto;
    private final String secretKeyHex = "00112233445566778899aabbccddeeff"; // 16-byte hex
    private final String sampleJson = """
            {"username":"borrower@minidc.local","expires":1700000000000,"connections":{"vm":{"protocol":"rdp"}}}
            """;

    @BeforeEach
    void setUp() {
        guacCrypto = new GuacCrypto();
    }

    @Test
    void testEncryptAndDecryptRoundtrip() {
        String encrypted = guacCrypto.encrypt(sampleJson, secretKeyHex);
        assertNotNull(encrypted);
        assertFalse(encrypted.isBlank());

        String decrypted = guacCrypto.decrypt(encrypted, secretKeyHex);
        assertEquals(sampleJson, decrypted);
    }

    @Test
    void testKeyParsingHexAndRaw() {
        byte[] key1 = guacCrypto.parseKey("00112233445566778899aabbccddeeff");
        assertEquals(16, key1.length);
        assertEquals((byte) 0x00, key1[0]);
        assertEquals((byte) 0x11, key1[1]);
        assertEquals((byte) 0xff, key1[15]);

        // 64-char hex string (32 bytes truncated to 16)
        String hex64 = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
        byte[] key2 = guacCrypto.parseKey(hex64);
        assertEquals(16, key2.length);

        // Arbitrary ASCII string padded/truncated
        byte[] key3 = guacCrypto.parseKey("my-short-secret");
        assertEquals(16, key3.length);
    }

    @Test
    void testTamperedPayloadThrowsSecurityException() {
        String encrypted = guacCrypto.encrypt(sampleJson, secretKeyHex);
        byte[] rawBytes = Base64.getDecoder().decode(encrypted);

        // Flip a byte in the encrypted ciphertext
        rawBytes[rawBytes.length - 1] ^= 0x55;
        String tamperedCiphertext = Base64.getEncoder().encodeToString(rawBytes);

        assertThrows(SecurityException.class, () -> guacCrypto.decrypt(tamperedCiphertext, secretKeyHex));
    }

    @Test
    void testWrongKeyFailsDecryption() {
        String encrypted = guacCrypto.encrypt(sampleJson, secretKeyHex);
        String wrongKey = "ffeeddccbbaa99887766554433221100";

        assertThrows(SecurityException.class, () -> guacCrypto.decrypt(encrypted, wrongKey));
    }

    @Test
    void testInvalidKeyRejection() {
        assertThrows(IllegalArgumentException.class, () -> guacCrypto.parseKey(null));
        assertThrows(IllegalArgumentException.class, () -> guacCrypto.parseKey("   "));
    }
}

