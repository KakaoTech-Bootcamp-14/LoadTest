package com.ktb.chatapp.websocket.socketio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based implementation of ChatDataStore using Redisson.
 * Provides distributed storage for chat-related data across multiple servers.
 * Thread-safe and supports multi-EC2 deployments with Redis Cluster.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisChatDataStore implements ChatDataStore {

    private static final String KEY_PREFIX = "chat:data:";
    private final RedissonClient redissonClient;

    private String getKey(String key) {
        return KEY_PREFIX + key;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(getKey(key));
            Object value = bucket.get();

            if (value == null) {
                return Optional.empty();
            }

            // Type-safe casting with validation
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            } else {
                log.warn("Type mismatch for key {}: expected {}, got {}",
                        key, type.getName(), value.getClass().getName());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error retrieving value from Redis for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(getKey(key));
            bucket.set(value);
            log.debug("Stored data in Redis with key: {}", key);
        } catch (Exception e) {
            log.error("Error storing value in Redis for key: {}", key, e);
            throw new RuntimeException("Failed to store data in Redis", e);
        }
    }

    /**
     * Store a value with TTL (time-to-live).
     *
     * @param key the storage key
     * @param value the value to store
     * @param ttl time-to-live duration
     * @param timeUnit time unit for TTL
     */
    public void set(String key, Object value, long ttl, TimeUnit timeUnit) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(getKey(key));
            bucket.set(value, ttl, timeUnit);
            log.debug("Stored data in Redis with key: {} (TTL: {} {})", key, ttl, timeUnit);
        } catch (Exception e) {
            log.error("Error storing value with TTL in Redis for key: {}", key, e);
            throw new RuntimeException("Failed to store data in Redis", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(getKey(key));
            bucket.delete();
            log.debug("Deleted data from Redis with key: {}", key);
        } catch (Exception e) {
            log.error("Error deleting value from Redis for key: {}", key, e);
            throw new RuntimeException("Failed to delete data from Redis", e);
        }
    }

    @Override
    public int size() {
        // Note: Size operation is not efficient with RBucket-based storage
        // This is a limitation of the bucket-based approach
        // Consider using RMap if you need efficient size operations
        log.warn("size() operation is not supported with RBucket-based storage");
        return 0;
    }

    /**
     * Clear all data in the store.
     * Note: This operation is not efficient with RBucket-based storage.
     */
    public void clear() {
        log.warn("clear() operation is not efficiently supported with RBucket-based storage");
        log.warn("Consider using Redis KEYS or SCAN command if bulk deletion is needed");
    }
}
