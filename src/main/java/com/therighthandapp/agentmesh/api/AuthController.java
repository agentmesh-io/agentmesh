package com.therighthandapp.agentmesh.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Auth verification endpoint for Traefik forwardAuth middleware.
 * Returns 200 if authenticated, 401 if not.
 * Passes user/tenant info as response headers for downstream services.
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Value("${agentmesh.security.api-key:}")
    private String validApiKey;

    /**
     * Verify authentication for Traefik forwardAuth.
     * Traefik calls this for every request; 200 = allow, 401 = deny.
     */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(HttpServletRequest request) {
        // If no API key configured, allow all (dev mode)
        if (validApiKey == null || validApiKey.isBlank()) {
            return ResponseEntity.ok()
                    .header("X-User-Id", "anonymous")
                    .header("X-Tenant-Id", "default")
                    .body(Map.of("status", "authenticated", "mode", "development"));
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                apiKey = auth.substring(7);
            }
        }

        if (validApiKey.equals(apiKey)) {
            // Extract tenant from header or default
            String tenantId = request.getHeader("X-Tenant-Id");
            if (tenantId == null || tenantId.isBlank()) tenantId = "default";

            return ResponseEntity.ok()
                    .header("X-User-Id", "api-user")
                    .header("X-Tenant-Id", tenantId)
                    .body(Map.of("status", "authenticated"));
        }

        return ResponseEntity.status(401)
                .body(Map.of("status", "unauthorized", "message", "Invalid API key"));
    }
}

