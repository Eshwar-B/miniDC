package com.minidc.server.redis;

import com.minidc.server.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DistributedLockServiceTest {

    private DistributedLockService lockService;
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
        lockService = new DistributedLockService(redisStateService);
    }

    @Test
    void testWithLockExecutesActionAndReleases() {
        AtomicBoolean executed = new AtomicBoolean(false);

        String result = lockService.withLock("offer:123", Duration.ofSeconds(2), Duration.ofSeconds(5), () -> {
            executed.set(true);
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertTrue(executed.get());
        assertFalse(redisStateService.hasKey("minidc:lock:offer:123"), "Lock should be released on completion");
    }

    @Test
    void testWithLockTimeoutWhenAlreadyHeld() {
        // Artificially hold the lock
        redisStateService.setIfAbsent("minidc:lock:offer:busy", "other-holder", Duration.ofSeconds(10));

        assertThrows(IllegalStateException.class, () ->
                lockService.withLock("offer:busy", Duration.ofMillis(200), Duration.ofSeconds(5), () -> "FAIL")
        );
    }
}
