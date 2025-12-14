package com.ktb.chatapp.service.ratelimit;

import com.ktb.chatapp.model.RateLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based implementation of RateLimitStore using Redisson.
 * Provides distributed rate limiting with automatic TTL management.
 * Each rate limit entry expires based on its expiresAt timestamp.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "storage.ratelimit.type", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class RateLimitRedisStore implements RateLimitStore {

    private static final String KEY_PREFIX = "ratelimit:";

    private final RedissonClient redissonClient;

    private String getKey(String clientId) {
        return KEY_PREFIX + clientId;
    }

    @Override
    public Optional<RateLimit> findByClientId(String clientId) {
        try {
            RBucket<RateLimit> bucket = redissonClient.getBucket(getKey(clientId));
            RateLimit rateLimit = bucket.get();

            if (rateLimit == null) {
                log.debug("Rate limit not found for clientId: {}", clientId);
                return Optional.empty();
            }

            // Check if expired (double-check since Redis TTL might not be exact)
            if (rateLimit.getExpiresAt() != null && rateLimit.getExpiresAt().isBefore(Instant.now())) {
                log.debug("Rate limit expired for clientId: {}", clientId);
                bucket.delete(); // Clean up expired entry
                return Optional.empty();
            }

            log.debug("Rate limit found for clientId: {}, count: {}", clientId, rateLimit.getCount());
            return Optional.of(rateLimit);
        } catch (Exception e) {
            log.error("Error finding rate limit for clientId: {}", clientId, e);
            return Optional.empty();
        }
    }

    @Override
    public RateLimit save(RateLimit rateLimit) {
        try {
            String clientId = rateLimit.getClientId();
            RBucket<RateLimit> bucket = redissonClient.getBucket(getKey(clientId));

            // Calculate TTL from expiresAt
            if (rateLimit.getExpiresAt() != null) {
                Duration ttl = Duration.between(Instant.now(), rateLimit.getExpiresAt());

                if (ttl.isNegative() || ttl.isZero()) {
                    // If already expired, don't save it
                    log.debug("Rate limit already expired for clientId: {}, not saving", clientId);
                    bucket.delete(); // Ensure it's deleted
                    return rateLimit;
                }

                // Save with TTL
                bucket.set(rateLimit, ttl.toMillis(), TimeUnit.MILLISECONDS);
                log.debug("Rate limit saved for clientId: {} with TTL {} ms, count: {}",
                        clientId, ttl.toMillis(), rateLimit.getCount());
            } else {
                // No expiry - save indefinitely (not recommended for rate limiting)
                bucket.set(rateLimit);
                log.warn("Rate limit saved without TTL for clientId: {} (no expiresAt set)", clientId);
            }

            return rateLimit;
        } catch (Exception e) {
            log.error("Error saving rate limit for clientId: {}", rateLimit.getClientId(), e);
            throw new RuntimeException("Failed to save rate limit to Redis", e);
        }
    }

    /**
     * Increment rate limit count and update TTL.
     *
     * @param clientId the client identifier
     * @param expiresAt the expiry timestamp
     * @return the updated rate limit
     */
    public RateLimit increment(String clientId, Instant expiresAt) {
        try {
            Optional<RateLimit> existingOpt = findByClientId(clientId);

            RateLimit rateLimit;
            if (existingOpt.isPresent()) {
                rateLimit = existingOpt.get();
                rateLimit.setCount(rateLimit.getCount() + 1);
                rateLimit.setExpiresAt(expiresAt); // Update expiry
            } else {
                rateLimit = RateLimit.builder()
                        .clientId(clientId)
                        .count(1)
                        .expiresAt(expiresAt)
                        .build();
            }

            return save(rateLimit);
        } catch (Exception e) {
            log.error("Error incrementing rate limit for clientId: {}", clientId, e);
            throw new RuntimeException("Failed to increment rate limit", e);
        }
    }

    /**
     * Delete rate limit entry.
     *
     * @param clientId the client identifier
     */
    public void delete(String clientId) {
        try {
            RBucket<RateLimit> bucket = redissonClient.getBucket(getKey(clientId));
            bucket.delete();
            log.debug("Rate limit deleted for clientId: {}", clientId);
        } catch (Exception e) {
            log.error("Error deleting rate limit for clientId: {}", clientId, e);
            throw new RuntimeException("Failed to delete rate limit from Redis", e);
        }
    }
}
