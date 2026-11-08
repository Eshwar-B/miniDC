package com.minidc.server.redis;

import com.minidc.server.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resilient distributed state and caching service.
 * Interacts with Redis (via StringRedisTemplate) for cluster-wide state,
 * with automatic, thread-safe in-memory fallback when Redis is unavailable or disabled.
 */
@Service
public class RedisStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisStateService.class);
    private static final String NODE_PRESENCE_PREFIX = "minidc:node:presence:";

    private final Optional<StringRedisTemplate> redisTemplate;
    private final boolean redisEnabled;

    // In-memory fallback caches for offline/dev environments
    private final Map<String, FallbackEntry> inMemoryStore = new ConcurrentHashMap<>();
    private final Map<String, AtomicRateEntry> inMemoryRateLimits = new ConcurrentHashMap<>();

    private record FallbackEntry(String value, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    private record AtomicRateEntry(AtomicLong counter, Instant windowExpiresAt) {}

    @Autowired
    public RedisStateService(@Autowired(required = false) StringRedisTemplate redisTemplate,
                             AppProperties properties) {
        this.redisTemplate = Optional.ofNullable(redisTemplate);
        this.redisEnabled = properties.redis() == null || properties.redis().enabled();
    }

    /**
     * Set a key-value pair with an expiration TTL.
     */
    public void setWithTtl(String key, String value, Duration ttl) {
        if (useRedis()) {
            try {
                redisTemplate.get().opsForValue().set(key, value, ttl);
                return;
            } catch (Exception e) {
                log.debug("Redis operation failed, falling back to in-memory store: {}", e.getMessage());
            }
        }
        Instant expires = ttl != null ? Instant.now().plus(ttl) : null;
        inMemoryStore.put(key, new FallbackEntry(value, expires));
    }

    /**
     * Get a value by key.
     */
    public Optional<String> get(String key) {
        if (useRedis()) {
            try {
                String val = redisTemplate.get().opsForValue().get(key);
                return Optional.ofNullable(val);
            } catch (Exception e) {
                log.debug("Redis operation failed, falling back to in-memory store: {}", e.getMessage());
            }
        }
        FallbackEntry entry = inMemoryStore.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                inMemoryStore.remove(key);
                return Optional.empty();
            }
            return Optional.ofNullable(entry.value());
        }
        return Optional.empty();
    }

    /**
     * Check if key exists and has not expired.
     */
    public boolean hasKey(String key) {
        if (useRedis()) {
            try {
                Boolean has = redisTemplate.get().hasKey(key);
                return Boolean.TRUE.equals(has);
            } catch (Exception e) {
                log.debug("Redis operation failed, falling back to in-memory store: {}", e.getMessage());
            }
        }
        FallbackEntry entry = inMemoryStore.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                inMemoryStore.remove(key);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Atomically set key if absent with a TTL (distributed lock primitive).
     * Returns true if lock was acquired, false otherwise.
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        if (useRedis()) {
            try {
                Boolean acquired = redisTemplate.get().opsForValue().setIfAbsent(key, value, ttl);
                return Boolean.TRUE.equals(acquired);
            } catch (Exception e) {
                log.debug("Redis setIfAbsent failed, falling back to in-memory store: {}", e.getMessage());
            }
        }
        synchronized (inMemoryStore) {
            FallbackEntry existing = inMemoryStore.get(key);
            if (existing != null && !existing.isExpired()) {
                return false;
            }
            Instant expires = ttl != null ? Instant.now().plus(ttl) : null;
            inMemoryStore.put(key, new FallbackEntry(value, expires));
            return true;
        }
    }

    /**
     * Delete a key.
     */
    public void delete(String key) {
        if (useRedis()) {
            try {
                redisTemplate.get().delete(key);
            } catch (Exception e) {
                log.debug("Redis delete failed: {}", e.getMessage());
            }
        }
        inMemoryStore.remove(key);
    }

    /**
     * Atomically increment a counter within a sliding window.
     * Used for rate limiting.
     */
    public long incrementAndGet(String key, Duration window) {
        if (useRedis()) {
            try {
                Long count = redisTemplate.get().opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redisTemplate.get().expire(key, window);
                }
                return count != null ? count : 1L;
            } catch (Exception e) {
                log.debug("Redis increment failed, falling back to in-memory store: {}", e.getMessage());
            }
        }

        Instant now = Instant.now();
        AtomicRateEntry entry = inMemoryRateLimits.compute(key, (k, old) -> {
            if (old == null || now.isAfter(old.windowExpiresAt())) {
                return new AtomicRateEntry(new AtomicLong(1), now.plus(window));
            }
            old.counter().incrementAndGet();
            return old;
        });
        return entry.counter().get();
    }

    /**
     * Record a node heartbeat with TTL.
     */
    public void recordNodeHeartbeat(UUID nodeId, Duration ttl) {
        String key = NODE_PRESENCE_PREFIX + nodeId;
        setWithTtl(key, Instant.now().toString(), ttl);
    }

    /**
     * Query whether a node is currently online based on heartbeat TTL.
     */
    public boolean isNodeOnline(UUID nodeId) {
        String key = NODE_PRESENCE_PREFIX + nodeId;
        return hasKey(key);
    }

    /**
     * Helper to determine whether Redis operations should be attempted.
     */
    private boolean useRedis() {
        return redisEnabled && redisTemplate.isPresent();
    }

    /**
     * Clear fallback stores (for testing purposes).
     */
    public void clearFallback() {
        inMemoryStore.clear();
        inMemoryRateLimits.clear();
    }
}
