package com.therighthandapp.agentmesh.api;

import com.nimbusds.jwt.JWTClaimsSet;
import com.therighthandapp.agentmesh.security.AuthenticationService;
import com.therighthandapp.agentmesh.security.AuthenticationService.TokenPair;
import com.therighthandapp.agentmesh.security.JwtIssuer;
import com.therighthandapp.agentmesh.security.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuthN/Z endpoints (M13.2).
 *
 * <ul>
 *   <li>{@code POST /api/auth/login}    — username + password → access + refresh</li>
 *   <li>{@code POST /api/auth/refresh}  — refresh-token rotation</li>
 *   <li>{@code POST /api/auth/logout}   — revoke refresh + blocklist access jti</li>
 *   <li>{@code GET  /api/auth/verify}   — Traefik forwardAuth probe; 200 + identity headers, 401 otherwise</li>
 * </ul>
 *
 * <p>Per ROADMAP_M13 §13.2 acceptance criterion: the smoke profile must
 * return 401 for missing / bad tokens.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationService auth;
    private final JwtIssuer jwtIssuer;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", "Missing 'username' or 'password' in request body"));
        }
        return auth.login(username, password)
                .map(this::tokenResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "UNAUTHORIZED", "message", "Invalid credentials")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body == null ? null : body.get("refreshToken");
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", "Missing 'refreshToken' in request body"));
        }
        return auth.refresh(refreshToken)
                .map(this::tokenResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "UNAUTHORIZED", "message", "Invalid refresh token")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest req
    ) {
        String access = bearer(req);
        String refresh = body == null ? null : body.get("refreshToken");
        auth.logout(refresh, access);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /**
     * Traefik forwardAuth probe. Returns 200 with X-User-Id / X-Tenant-Id /
     * X-Roles headers on success, 401 with WWW-Authenticate otherwise.
     */
    @RequestMapping(value = "/verify", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> verify(HttpServletRequest req) {
        String token = bearer(req);
        if (token == null) {
            return unauthorized("Missing Bearer token");
        }
        return auth.verify(token)
                .map(this::verifyOk)
                .orElseGet(() -> unauthorized("Invalid or expired token"));
    }

    private ResponseEntity<Map<String, Object>> tokenResponse(TokenPair pair) {
        UserAccount u = pair.user();
        Map<String, Object> body = new HashMap<>();
        body.put("accessToken", pair.access().token());
        body.put("expiresAt", pair.access().expiresAt().toString());
        body.put("expiresInSeconds", jwtIssuer.accessTtl().toSeconds());
        body.put("refreshToken", pair.refreshToken());
        body.put("tokenType", "Bearer");
        body.put("user", Map.of(
                "id", u.getId().toString(),
                "username", u.getUsername(),
                "email", u.getEmail() == null ? "" : u.getEmail(),
                "tenantId", u.getTenantId().toString(),
                "roles", u.rolesAsList()));
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> verifyOk(JWTClaimsSet claims) {
        try {
            String userId = claims.getSubject();
            String tenantId = (String) claims.getClaim("tenant_id");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getClaim("roles");
            String rolesHdr = roles == null ? "" : String.join(",", roles);
            return ResponseEntity.ok()
                    .header("X-User-Id", userId == null ? "" : userId)
                    .header("X-Tenant-Id", tenantId == null ? "default" : tenantId)
                    .header("X-Roles", rolesHdr)
                    .body(Map.of(
                            "status", "AUTHENTICATED",
                            "userId", userId == null ? "" : userId,
                            "tenantId", tenantId == null ? "default" : tenantId,
                            "roles", roles == null ? List.<String>of() : roles));
        } catch (ClassCastException e) {
            return unauthorized("Malformed claims");
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String reason) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .body(Map.of("status", "UNAUTHORIZED", "message", reason));
    }

    private static String bearer(HttpServletRequest req) {
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h == null) return null;
        if (h.regionMatches(true, 0, "Bearer ", 0, 7)) return h.substring(7).trim();
        return null;
    }
}

