package com.therighthandapp.agentmesh.security;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Local-IdP authentication service (M13.2). Verifies BCrypt passwords,
 * issues access + refresh tokens, and rotates refresh tokens on use.
 *
 * <p>This service is the single source of truth that the {@code AuthController}
 * (HTTP /api/auth/login + /refresh + /verify + /logout) and the WebSocket
 * handshake interceptor (commit 7/8) both call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserAccountRepository users;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenStore refreshTokens;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Login with username + password against the seeded users table. */
    @Transactional
    public Optional<TokenPair> login(String username, String password) {
        Optional<UserAccount> opt = users.findByUsername(username);
        if (opt.isEmpty()) {
            log.warn("[auth] login: unknown username {}", redact(username));
            return Optional.empty();
        }
        UserAccount user = opt.get();
        if (!user.isEnabled() || user.isLocked()) {
            log.warn("[auth] login: disabled/locked username={}", redact(username));
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("[auth] login: bad password for username={}", redact(username));
            return Optional.empty();
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);

        JwtIssuer.Issued access = jwtIssuer.mint(
                user.getId().toString(),
                user.getTenantId().toString(),
                null,
                user.rolesAsList());
        String refresh = refreshTokens.issue(user.getId().toString());
        log.info("[auth] login OK userId={} tenantId={} roles={}",
                user.getId(), user.getTenantId(), user.getRoles());
        return Optional.of(new TokenPair(access, refresh, user));
    }

    /** Rotate a refresh token: returns a new access+refresh pair on success. */
    @Transactional
    public Optional<TokenPair> refresh(String refreshToken) {
        String[] outNew = new String[1];
        Optional<String> userIdOpt = refreshTokens.rotate(refreshToken, outNew);
        if (userIdOpt.isEmpty()) {
            log.warn("[auth] refresh: invalid/expired token");
            return Optional.empty();
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdOpt.get());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Optional<UserAccount> userOpt = users.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().isEnabled() || userOpt.get().isLocked()) {
            return Optional.empty();
        }
        UserAccount user = userOpt.get();
        JwtIssuer.Issued access = jwtIssuer.mint(
                user.getId().toString(),
                user.getTenantId().toString(),
                null,
                user.rolesAsList());
        return Optional.of(new TokenPair(access, outNew[0], user));
    }

    /** Logout — revoke both refresh + access (jti blocklist until exp). */
    public void logout(String refreshToken, String accessToken) {
        if (refreshToken != null) refreshTokens.revoke(refreshToken);
        if (accessToken != null) {
            try {
                JWTClaimsSet claims = jwtIssuer.parse(accessToken);
                String jti = claims.getJWTID();
                if (jti != null && claims.getExpirationTime() != null) {
                    refreshTokens.blocklistJti(jti, claims.getExpirationTime().toInstant());
                }
            } catch (JwtIssuer.InvalidTokenException ignored) {
                // already invalid — nothing to revoke
            }
        }
    }

    /** Validate an access token and return the parsed claims. */
    public Optional<JWTClaimsSet> verify(String accessToken) {
        try {
            JWTClaimsSet claims = jwtIssuer.parse(accessToken);
            if (refreshTokens.isJtiRevoked(claims.getJWTID())) return Optional.empty();
            return Optional.of(claims);
        } catch (JwtIssuer.InvalidTokenException e) {
            return Optional.empty();
        }
    }

    private static String redact(String username) {
        if (username == null || username.length() < 3) return "***";
        return username.substring(0, 2) + "***";
    }

    /** Result of a successful login or refresh. */
    public record TokenPair(JwtIssuer.Issued access, String refreshToken, UserAccount user) {}
}

