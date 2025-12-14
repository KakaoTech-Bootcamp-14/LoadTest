package com.ktb.chatapp.service.session;

import com.ktb.chatapp.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based implementation of SessionStore using Redisson.
 * Provides distributed session storage with automatic TTL management.
 * Sessions expire after 30 minutes of inactivity.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "storage.session.type", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class SessionRedisStore implements SessionStore {

    private static final String KEY_PREFIX = "session:user:";
    private static final long SESSION_TTL_MINUTES = 30;

    private final RedissonClient redissonClient;

    private String getUserKey(String userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public Optional<Session> findByUserId(String userId) {
        try {
            RBucket<Session> bucket = redissonClient.getBucket(getUserKey(userId));
            Session session = bucket.get();

            if (session == null) {
                log.debug("Session not found for userId: {}", userId);
                return Optional.empty();
            }

            log.debug("Session found for userId: {}", userId);
            return Optional.of(session);
        } catch (Exception e) {
            log.error("Error finding session for userId: {}", userId, e);
            return Optional.empty();
        }
    }

    @Override
    public Session save(Session session) {
        try {
            String userId = session.getUserId();
            RBucket<Session> bucket = redissonClient.getBucket(getUserKey(userId));

            // Save session with TTL
            bucket.set(session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

            log.debug("Session saved for userId: {} with TTL {} minutes", userId, SESSION_TTL_MINUTES);
            return session;
        } catch (Exception e) {
            log.error("Error saving session for userId: {}", session.getUserId(), e);
            throw new RuntimeException("Failed to save session to Redis", e);
        }
    }

    @Override
    public void delete(String userId, String sessionId) {
        try {
            Optional<Session> sessionOpt = findByUserId(userId);
            if (sessionOpt.isPresent() && sessionId.equals(sessionOpt.get().getSessionId())) {
                RBucket<Session> bucket = redissonClient.getBucket(getUserKey(userId));
                bucket.delete();
                log.debug("Session deleted for userId: {}, sessionId: {}", userId, sessionId);
            } else {
                log.debug("Session not found or sessionId mismatch for userId: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error deleting session for userId: {}", userId, e);
            throw new RuntimeException("Failed to delete session from Redis", e);
        }
    }

    @Override
    public void deleteAll(String userId) {
        try {
            RBucket<Session> bucket = redissonClient.getBucket(getUserKey(userId));
            bucket.delete();
            log.debug("All sessions deleted for userId: {}", userId);
        } catch (Exception e) {
            log.error("Error deleting all sessions for userId: {}", userId, e);
            throw new RuntimeException("Failed to delete sessions from Redis", e);
        }
    }

    /**
     * Update session's last activity timestamp and refresh TTL.
     *
     * @param userId the user identifier
     * @param lastActivity the last activity timestamp
     */
    public void updateLastActivity(String userId, long lastActivity) {
        try {
            Optional<Session> sessionOpt = findByUserId(userId);
            if (sessionOpt.isPresent()) {
                Session session = sessionOpt.get();
                session.setLastActivity(lastActivity);
                save(session); // This also refreshes the TTL
                log.debug("Last activity updated for userId: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error updating last activity for userId: {}", userId, e);
        }
    }
}
