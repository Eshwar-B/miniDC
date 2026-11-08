package com.minidc.server.redis;

import com.minidc.server.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TokenBlacklistServiceTest {

    private TokenBlacklistService blacklistService;
    private RedisStateService redisStateService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "test-secret-at-least-32-chars-long!",
                12,
                "data-key-test-32-chars-minimum!",
                "http://localhost:8080/guacamole",
                "00112233445566778899aabbccddeeff",
                1,
                false,
                new AppProperties.RedisProperties(false),
                null
        );
        redisStateService = new RedisStateService(null, properties);
        blacklistService = new TokenBlacklistService(redisStateService);
    }

    @Test
    void testBlacklistAndVerifyToken() {
        String token = "sample.jwt.token.here";
        assertFalse(blacklistService.isBlacklisted(token));

        blacklistService.blacklistToken(token, Duration.ofMinutes(10));
        assertTrue(blacklistService.isBlacklisted(token));
    }

    @Test
    void testBlankTokenHandling() {
        assertFalse(blacklistService.isBlacklisted(null));
        assertFalse(blacklistService.isBlacklisted(""));
        assertFalse(blacklistService.isBlacklisted("   "));

        assertDoesNotThrow(() -> blacklistService.blacklistToken(null, Duration.ofMinutes(5)));
        assertDoesNotThrow(() -> blacklistService.blacklistToken("", Duration.ofMinutes(5)));
    }
}

