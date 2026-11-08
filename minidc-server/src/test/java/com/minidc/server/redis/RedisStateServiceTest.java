package com.minidc.server.redis;

import com.minidc.server.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RedisStateServiceTest {

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
                new AppProperties.RedisProperties(false), // test fallback mode
                null
        );
        redisStateService = new RedisStateService(null, properties);
    }

    @Test
    void testSetAndGetWithFallback() {
        redisStateService.setWithTtl("key1", "val1", Duration.ofMinutes(5));
        Optional<String> val = redisStateService.get("key1");
        assertTrue(val.isPresent());
        assertEquals("val1", val.get());
        assertTrue(redisStateService.hasKey("key1"));

        redisStateService.delete("key1");
        assertFalse(redisStateService.hasKey("key1"));
        assertTrue(redisStateService.get("key1").isEmpty());
    }

    @Test
    void testSetIfAbsentAtomicLockBehavior() {
        boolean first = redisStateService.setIfAbsent("lock:res1", "ownerA", Duration.ofMinutes(1));
        assertTrue(first, "First lock acquisition should succeed");

        boolean second = redisStateService.setIfAbsent("lock:res1", "ownerB", Duration.ofMinutes(1));
        assertFalse(second, "Second lock acquisition should fail while locked");

        redisStateService.delete("lock:res1");
        boolean third = redisStateService.setIfAbsent("lock:res1", "ownerB", Duration.ofMinutes(1));
        assertTrue(third, "Lock acquisition should succeed after release");
    }

    @Test
    void testIncrementAndGetSlidingWindowCounter() {
        String counterKey = "counter:ip1";
        long c1 = redisStateService.incrementAndGet(counterKey, Duration.ofMinutes(1));
        assertEquals(1, c1);

        long c2 = redisStateService.incrementAndGet(counterKey, Duration.ofMinutes(1));
        assertEquals(2, c2);

        long c3 = redisStateService.incrementAndGet(counterKey, Duration.ofMinutes(1));
        assertEquals(3, c3);
    }

    @Test
    void testNodeHeartbeatPresence() {
        UUID nodeId = UUID.randomUUID();
        assertFalse(redisStateService.isNodeOnline(nodeId));

        redisStateService.recordNodeHeartbeat(nodeId, Duration.ofMinutes(1));
        assertTrue(redisStateService.isNodeOnline(nodeId));

        redisStateService.delete("minidc:node:presence:" + nodeId);
        assertFalse(redisStateService.isNodeOnline(nodeId));
    }
}

