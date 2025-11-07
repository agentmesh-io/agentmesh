package com.therighthandapp.agentmesh.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Utility for propagating MDC context across threads.
 * Ensures correlation IDs are maintained in async operations.
 */
public class MDCContext {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String TENANT_ID_KEY = "tenantId";
    private static final String AGENT_TYPE_KEY = "agentType";

    /**
     * Get current correlation ID from MDC
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Set correlation ID in MDC
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
    }

    /**
     * Get current tenant ID from MDC
     */
    public static String getTenantId() {
        return MDC.get(TENANT_ID_KEY);
    }

    /**
     * Set tenant ID in MDC
     */
    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            MDC.put(TENANT_ID_KEY, tenantId);
        }
    }

    /**
     * Get current agent type from MDC
     */
    public static String getAgentType() {
        return MDC.get(AGENT_TYPE_KEY);
    }

    /**
     * Set agent type in MDC
     */
    public static void setAgentType(String agentType) {
        if (agentType != null && !agentType.isEmpty()) {
            MDC.put(AGENT_TYPE_KEY, agentType);
        }
    }

    /**
     * Capture current MDC context for propagation
     */
    public static Map<String, String> captureContext() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * Restore MDC context from captured map
     */
    public static void restoreContext(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }

    /**
     * Clear all MDC context
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Wrap a Runnable with MDC context propagation
     */
    public static Runnable wrap(Runnable runnable) {
        Map<String, String> context = captureContext();
        return () -> {
            Map<String, String> previousContext = captureContext();
            try {
                restoreContext(context);
                runnable.run();
            } finally {
                restoreContext(previousContext);
            }
        };
    }

    /**
     * Wrap a Callable with MDC context propagation
     */
    public static <V> Callable<V> wrap(Callable<V> callable) {
        Map<String, String> context = captureContext();
        return () -> {
            Map<String, String> previousContext = captureContext();
            try {
                restoreContext(context);
                return callable.call();
            } finally {
                restoreContext(previousContext);
            }
        };
    }
}
