package com.minidc.server.guac;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cryptographic utility for Apache Guacamole auth-json extension.
 *
 * Implements HMAC-SHA256 signature calculation and AES-128-CBC encryption with zero IV,
 * matching org.apache.guacamole.auth.json.CryptoService specifications.
 */
@Component
public class GuacCrypto {

    private static final String CIPHER_ALGO = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int SIGNATURE_LEN = 32; // SHA-256 produces 32 bytes
    private static final int AES_128_KEY_LEN = 16; // 128-bit key = 16 bytes
    private static final byte[] ZERO_IV = new byte[16];

    /**
     * Parse or derive a 16-byte (128-bit) secret key from the configured key string.
     * Supports 32-character hex strings, 64-character hex strings, or arbitrary raw strings.
     */
    public byte[] parseKey(String keyString) {
        if (keyString == null || keyString.isBlank()) {
            throw new IllegalArgumentException("Guacamole secret key cannot be null or blank");
        }

        String cleaned = keyString.trim();
        // Check if string is a valid hex representation of 16 or 32 bytes
        if ((cleaned.length() == 32 || cleaned.length() == 64) && cleaned.matches("^[0-9a-fA-F]+$")) {
            byte[] hexBytes = HexFormat.of().parseHex(cleaned);
            if (hexBytes.length == AES_128_KEY_LEN) {
                return hexBytes;
            } else if (hexBytes.length == 32) {
                return Arrays.copyOf(hexBytes, AES_128_KEY_LEN);
            }
        }

        byte[] rawBytes = cleaned.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = new byte[AES_128_KEY_LEN];
        if (rawBytes.length >= AES_128_KEY_LEN) {
            System.arraycopy(rawBytes, 0, keyBytes, 0, AES_128_KEY_LEN);
        } else {
            System.arraycopy(rawBytes, 0, keyBytes, 0, rawBytes.length);
        }
        return keyBytes;
    }

    /**
     * Signs the JSON with HMAC-SHA256, prepends the 32-byte signature,
     * encrypts with AES-128-CBC (zero IV), and returns a Base64-encoded string.
     */
    public String encrypt(String json, String keyString) {
        try {
            byte[] keyBytes = parseKey(keyString);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            // 1. Calculate HMAC-SHA256 signature
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            byte[] signature = mac.doFinal(jsonBytes);

            // 2. Prepend signature to plaintext JSON bytes
            byte[] combined = new byte[SIGNATURE_LEN + jsonBytes.length];
            System.arraycopy(signature, 0, combined, 0, SIGNATURE_LEN);
            System.arraycopy(jsonBytes, 0, combined, SIGNATURE_LEN, jsonBytes.length);

            // 3. Encrypt combined payload with AES-128-CBC and zero IV
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ZERO_IV));
            byte[] encrypted = cipher.doFinal(combined);

            // 4. Base64 encode
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new SecurityException("Failed to encrypt Guacamole auth-json payload: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext, verifies the HMAC-SHA256 signature,
     * and returns the original JSON string.
     */
    public String decrypt(String base64Ciphertext, String keyString) {
        try {
            byte[] keyBytes = parseKey(keyString);
            byte[] encrypted = Base64.getDecoder().decode(base64Ciphertext);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ZERO_IV));
            byte[] combined = cipher.doFinal(encrypted);

            if (combined.length < SIGNATURE_LEN) {
                throw new SecurityException("Decrypted payload too short to contain signature");
            }

            byte[] signature = Arrays.copyOfRange(combined, 0, SIGNATURE_LEN);
            byte[] jsonBytes = Arrays.copyOfRange(combined, SIGNATURE_LEN, combined.length);

            // Verify HMAC-SHA256 signature
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            byte[] expectedSignature = mac.doFinal(jsonBytes);

            if (!MessageDigest.isEqual(signature, expectedSignature)) {
                throw new SecurityException("Invalid HMAC signature: payload has been tampered with or key is incorrect");
            }

            return new String(jsonBytes, StandardCharsets.UTF_8);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt Guacamole auth-json payload: " + e.getMessage(), e);
        }
    }
}
