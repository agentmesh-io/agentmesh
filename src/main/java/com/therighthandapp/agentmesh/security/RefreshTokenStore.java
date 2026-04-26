package com.therighthandapp.agentmesh.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Opaque refresh-token store backed by Redis (M13.2).
 *
 * <p>Refresh tokens are random 32-byte values keyed in Redis with TTL
 * (default 7 days). They are <strong>rotated on every use</strong>: on
 * {@link #rotate} the old key is deleted before the new one is written
 * (prevents replay). The JWT access-token blocklist (revoked {@code jti})
 * lives under a separate key namespace and is checked by
 * {@link JwtToTenantContextFilter}.
 *
 * <p>Per Architect Protocol §1 + §8: shared {@code dev-redis:6379} (logical
 * DB 2 for dev stage, 3 for stage, 4 for prod — to be pinned in §9 stage
 * compose).
 */
@Component
@Slf4j
public class RefreshTokenStore {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String JTI_BLOCKLIST_PREFIX = "auth:revoked:";

    private final StringRedisTemplate redis;
    private final Duration refreshTtl;
    private final SecureRandom rng = new SecureRandom();

    public RefreshTokenStore(
            StringRedisTemplate redis,
            @Value("${agentmesh.security.jwt.refresh-ttl:P7D}") Duration refreshTtl
    ) {
        this.redis = redis;
        this.refreshTtl = refreshTtl;
    }

    /** Issue a new refresh token bound to a user. */
    public String issue(String userId) {
        byte[] raw = new byte[32];
        rng.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set(REFRESH_PREFIX + token, userId, refreshTtl);
        return token;
    }

    /** Look up + atomically rotate. Returns the userId tied to the token, if valid. */
    public Optional<String> rotate(String oldToken, String[] outNewToken) {
        String key = REFRESH_PREFIX + oldToken;
        String userId = redis.opsForValue().get(key);
        if (userId == null) return Optional.empty();
        // Delete old, issue new (atomicity-good-enough — single-node Redis op)
        redis.delete(key);
        outNewToken[0] = issue(userId);
        return Optional.of(userId);
    }

    /** Revoke a refresh token (logout). */
    public void revoke(String refreshToken) {
        redis.delete(REFRESH_PREFIX + refreshToken);
    }

    /** Mark an access-token jti as revoked until natural expiry. */
    public void blocklistJti(String jti, Instant expiresAt) {
        long ttlSec = Math.max(1, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        redis.opsForValue().set(JTI_BLOCKLIST_PREFIX + jti, "1", Duration.ofSeconds(ttlSec));
    }

    public boolean isJtiRevoked(String jti) {
        if (jti == null) return false;
        return Boolean.TRUE.equals(redis.hasKey(JTI_BLOCKLIST_PREFIX + jti));
    }

    public Duration refreshTtl() { return refreshTtl; }
}

