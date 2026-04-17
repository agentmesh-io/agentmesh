package com.therighthandapp.agentmesh.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Lightweight API authentication filter.
 * Validates X-API-Key header for external API access.
 * Enabled only when agentmesh.security.api-key-auth.enabled=true.
 *
 * For production, this should be replaced with JWT/OAuth2 (M11).
 * Works with Traefik gateway forwarding auth headers.
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "agentmesh.security.api-key-auth.enabled", havingValue = "true")
public class ApiKeyAuthFilter implements Filter {

    @Value("${agentmesh.security.api-key:}")
    private String validApiKey;

    // Public endpoints that don't require auth
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/api/diagnostics",
            "/api/auth/verify",
            "/ws"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Allow public endpoints
        if (isPublicPath(path) || "OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Skip auth if no API key configured (dev mode)
        if (validApiKey == null || validApiKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Check API key
        String apiKey = httpRequest.getHeader("X-API-Key");
        if (apiKey == null) {
            apiKey = httpRequest.getHeader("Authorization");
            if (apiKey != null && apiKey.startsWith("Bearer ")) {
                apiKey = apiKey.substring(7);
            }
        }

        if (validApiKey.equals(apiKey)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Unauthorized API access attempt: {} {}", httpRequest.getMethod(), path);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}

