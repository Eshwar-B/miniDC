package com.minidc.server.redis;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * High-speed token revocation and blacklisting service backed by Redis.
 * Enables O(1) checks during JWT authentication and instantaneous session revocation.
 */
@Service
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "minidc:jwt:blacklist:";
    private final RedisStateService redisStateService;

    public TokenBlacklistService(RedisStateService redisStateService) {
        this.redisStateService = redisStateService;
    }

    /**
     * Blacklists a JWT token until its natural expiration.
     */
    public void blacklistToken(String token, Duration remainingTtl) {
        if (token == null || token.isBlank()) {
            return;
        }
        String key = hashToken(token);
        redisStateService.setWithTtl(BLACKLIST_PREFIX + key, "revoked", remainingTtl);
    }

    /**
     * Checks whether a JWT token has been blacklisted.
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = hashToken(token);
        return redisStateService.hasKey(BLACKLIST_PREFIX + key);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(token.hashCode());
        }
    }
}
