package com.therighthandapp.agentmesh.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that adds correlation IDs to MDC for request tracing.
 * Enables tracking requests through the entire agent execution pipeline.
 */
@Component
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TENANT_ID_MDC_KEY = "tenantId";
    private static final String AGENT_TYPE_MDC_KEY = "agentType";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            // Get or generate correlation ID
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            // Add to MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            
            // Extract tenant ID if present in request
            String tenantId = extractTenantId(httpRequest);
            if (tenantId != null) {
                MDC.put(TENANT_ID_MDC_KEY, tenantId);
            }
            
            // Extract agent type from path
            String agentType = extractAgentType(httpRequest);
            if (agentType != null) {
                MDC.put(AGENT_TYPE_MDC_KEY, agentType);
            }
            
            chain.doFilter(request, response);
            
        } finally {
            // Clean up MDC
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(TENANT_ID_MDC_KEY);
            MDC.remove(AGENT_TYPE_MDC_KEY);
        }
    }
    
    private String extractTenantId(HttpServletRequest request) {
        // Try to get from header
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }
        
        // Try to get from request parameter
        tenantId = request.getParameter("tenantId");
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }
        
        return null;
    }
    
    private String extractAgentType(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Match patterns like /api/agents/execute/{agentType}
        if (path.contains("/agents/execute/")) {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("execute".equals(parts[i]) && i + 1 < parts.length) {
                    return parts[i + 1];
                }
            }
        }
        
        return null;
    }
}
