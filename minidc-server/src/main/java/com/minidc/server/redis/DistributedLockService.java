package com.minidc.server.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Service for acquiring and releasing cluster-wide distributed locks
 * backed by Redis (or resilient local mutex when offline).
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private static final String LOCK_PREFIX = "minidc:lock:";

    private final RedisStateService redisStateService;

    public DistributedLockService(RedisStateService redisStateService) {
        this.redisStateService = redisStateService;
    }

    public interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Executes the given action within the scope of a distributed lock.
     *
     * @param lockKey    Unique lock identifier (e.g. "offer:" + offerId)
     * @param timeout    Maximum time to wait to acquire the lock
     * @param leaseTime  Duration after which the lock automatically expires if not released
     * @param action     The critical section logic to execute
     * @return Result of the action
     */
    public <T> T withLock(String lockKey, Duration timeout, Duration leaseTime, Supplier<T> action) {
        String fullKey = LOCK_PREFIX + lockKey;
        String lockValue = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        boolean acquired = false;

        try {
            while (System.currentTimeMillis() < deadline) {
                if (redisStateService.setIfAbsent(fullKey, lockValue, leaseTime)) {
                    acquired = true;
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for lock " + lockKey, ie);
                }
            }

            if (!acquired) {
                throw new IllegalStateException("Failed to acquire distributed lock for " + lockKey + " within " + timeout.toMillis() + "ms");
            }

            return action.get();
        } finally {
            if (acquired) {
                try {
                    // Check ownership before releasing (in case lock expired and was reacquired)
                    redisStateService.get(fullKey).ifPresent(owner -> {
                        if (lockValue.equals(owner)) {
                            redisStateService.delete(fullKey);
                        }
                    });
                } catch (Exception e) {
                    log.warn("Error releasing distributed lock for {}: {}", lockKey, e.getMessage());
                }
            }
        }
    }
}
