package com.therighthandapp.agentmesh.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * RS256 JWT issuer + parser (M13.2 — Sprint AuthN/Z).
 *
 * <p>Access token lifetime: {@code agentmesh.security.jwt.access-ttl} (default 15 min,
 * per ROADMAP_M13 §13.2 acceptance criterion). Refresh tokens are opaque
 * (handled separately by {@link RefreshTokenStore}). Claims layout:
 * <ul>
 *   <li>{@code sub} — userId (UUID string)</li>
 *   <li>{@code tenant_id} — tenant UUID</li>
 *   <li>{@code project_id} — optional project UUID</li>
 *   <li>{@code roles} — string array (admin/developer/viewer)</li>
 *   <li>{@code jti} — random UUID, used for blocklist on logout</li>
 *   <li>{@code iat / exp} — standard</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtIssuer {

    static final String ISSUER = "agentmesh";

    private final RsaKeyProvider keys;
    private final Duration accessTtl;

    public JwtIssuer(
            RsaKeyProvider keys,
            @Value("${agentmesh.security.jwt.access-ttl:PT15M}") Duration accessTtl
    ) {
        this.keys = keys;
        this.accessTtl = accessTtl;
    }

    /** Mint a fresh access token. */
    public Issued mint(String userId, String tenantId, String projectId, List<String> roles) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtl);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(ISSUER)
                .audience(ISSUER)
                .claim("tenant_id", tenantId)
                .claim("roles", roles == null ? List.<String>of() : roles)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp));
        if (projectId != null && !projectId.isBlank()) {
            claims.claim("project_id", projectId);
        }

        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keys.keyId())
                    .type(JOSEObjectType.JWT)
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(new RSASSASigner(keys.privateKey()));
            return new Issued(jwt.serialize(), jti, exp);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    /** Validate signature + expiry; returns parsed claims or throws. */
    public JWTClaimsSet parse(String token) throws InvalidTokenException {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(keys.publicKey()))) {
                throw new InvalidTokenException("Bad signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || Instant.now().isAfter(exp.toInstant())) {
                throw new InvalidTokenException("Token expired");
            }
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new InvalidTokenException("Bad issuer");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw new InvalidTokenException("Malformed token: " + e.getMessage());
        }
    }

    public Duration accessTtl() { return accessTtl; }

    public record Issued(String token, String jti, Instant expiresAt) {}

    public static class InvalidTokenException extends Exception {
        public InvalidTokenException(String msg) { super(msg); }
    }
}

