package com.therighthandapp.agentmesh.llm;

import com.therighthandapp.agentmesh.security.AccessControlService;
import com.therighthandapp.agentmesh.security.TenantContext;
import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Multi-tenant aware LLM client with LoRA adapter support.
 *
 * Features:
 * - Automatic tenant context detection
 * - LoRA adapter loading per tenant
 * - Token usage tracking per tenant/project
 * - Cost attribution
 * - Outcome-based billing support
 */
@Service
public class MultiTenantLLMClient {
    private static final Logger log = LoggerFactory.getLogger(MultiTenantLLMClient.class);

    private final LLMClient baseLLMClient;
    private final TenantRepository tenantRepository;
    private final TokenUsageTracker tokenUsageTracker;

    @Value("${agentmesh.multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    @Value("${agentmesh.llm.lora.enabled:false}")
    private boolean loraEnabled;

    @Autowired(required = false)
    private AccessControlService accessControl;

    @Autowired(required = false)
    private LoRAAdapterManager loraManager;

    public MultiTenantLLMClient(LLMClient baseLLMClient,
                                TenantRepository tenantRepository,
                                TokenUsageTracker tokenUsageTracker) {
        this.baseLLMClient = baseLLMClient;
        this.tenantRepository = tenantRepository;
        this.tokenUsageTracker = tokenUsageTracker;
    }

    /**
     * Complete prompt with automatic tenant context and LoRA adapter loading
     */
    public LLMResponse complete(String prompt, Map<String, Object> params) {
        // Get tenant context
        TenantContext context = getTenantContext();

        // Enforce access control if enabled
        if (multitenancyEnabled && accessControl != null && context != null) {
            accessControl.checkAccess(context.getTenantId(), context.getProjectId());
        }

        // Load tenant-specific LoRA adapter if available
        String adapterId = null;
        if (loraEnabled && context != null && loraManager != null) {
            adapterId = loadTenantLoRAAdapter(context.getTenantId());
        }

        // Add tenant context to params for tracking
        Map<String, Object> enrichedParams = new HashMap<>(params != null ? params : new HashMap<>());
        if (context != null) {
            enrichedParams.put("_tenant_id", context.getTenantId());
            enrichedParams.put("_project_id", context.getProjectId());
            enrichedParams.put("_lora_adapter_id", adapterId);
        }

        // Execute LLM call
        long startTime = System.currentTimeMillis();
        LLMResponse response;

        try {
            response = baseLLMClient.complete(prompt, enrichedParams);
        } finally {
            // Unload LoRA adapter after use
            if (adapterId != null && loraManager != null) {
                loraManager.unloadAdapter(adapterId);
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Track token usage
        if (context != null) {
            trackTokenUsage(context, response, duration);
        }

        log.info("LLM completion: tenant={}, project={}, tokens={}, duration={}ms, adapter={}",
            context != null ? context.getTenantId() : "none",
            context != null ? context.getProjectId() : "none",
            response.getUsage() != null ? response.getUsage().getTotalTokens() : 0,
            duration,
            adapterId != null ? adapterId : "none");

        return response;
    }

    /**
     * Chat completion with tenant context
     */
    public LLMResponse chat(java.util.List<ChatMessage> messages, Map<String, Object> params) {
        TenantContext context = getTenantContext();

        // Enforce access control
        if (multitenancyEnabled && accessControl != null && context != null) {
            accessControl.checkAccess(context.getTenantId(), context.getProjectId());
        }

        // Load LoRA adapter
        String adapterId = null;
        if (loraEnabled && context != null && loraManager != null) {
            adapterId = loadTenantLoRAAdapter(context.getTenantId());
        }

        // Add tracking metadata
        Map<String, Object> enrichedParams = new HashMap<>(params != null ? params : new HashMap<>());
        if (context != null) {
            enrichedParams.put("_tenant_id", context.getTenantId());
            enrichedParams.put("_project_id", context.getProjectId());
            enrichedParams.put("_lora_adapter_id", adapterId);
        }

        long startTime = System.currentTimeMillis();
        LLMResponse response;

        try {
            response = baseLLMClient.chat(messages, enrichedParams);
        } finally {
            if (adapterId != null && loraManager != null) {
                loraManager.unloadAdapter(adapterId);
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Track usage
        if (context != null) {
            trackTokenUsage(context, response, duration);
        }

        return response;
    }

    /**
     * Generate embeddings with tenant context
     */
    public float[] embed(String text) {
        TenantContext context = getTenantContext();

        if (multitenancyEnabled && accessControl != null && context != null) {
            accessControl.checkAccess(context.getTenantId(), context.getProjectId());
        }

        return baseLLMClient.embed(text);
    }

    /**
     * Load tenant-specific LoRA adapter
     */
    private String loadTenantLoRAAdapter(String tenantId) {
        if (loraManager == null) {
            return null;
        }

        try {
            Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
            if (tenantOpt.isEmpty()) {
                log.warn("Tenant not found for LoRA loading: {}", tenantId);
                return null;
            }

            Tenant tenant = tenantOpt.get();
            Map<String, String> adapters = tenant.getLoraAdapters();

            if (adapters == null || adapters.isEmpty()) {
                log.debug("No LoRA adapters configured for tenant: {}", tenantId);
                return null;
            }

            // Load the primary adapter (or "default" if specified)
            String adapterPath = adapters.getOrDefault("default", adapters.values().iterator().next());
            String adapterId = loraManager.loadAdapter(tenantId, adapterPath);

            log.info("Loaded LoRA adapter for tenant {}: {}", tenantId, adapterId);
            return adapterId;

        } catch (Exception e) {
            log.error("Failed to load LoRA adapter for tenant: " + tenantId, e);
            return null;
        }
    }

    /**
     * Track token usage for billing and analytics
     */
    private void trackTokenUsage(TenantContext context, LLMResponse response, long durationMs) {
        if (response.getUsage() == null) {
            return;
        }

        LLMUsage usage = response.getUsage();

        TokenUsageRecord record = new TokenUsageRecord();
        record.setTenantId(context.getTenantId());
        record.setProjectId(context.getProjectId());
        record.setUserId(context.getUserId());
        record.setPromptTokens(usage.getPromptTokens());
        record.setCompletionTokens(usage.getCompletionTokens());
        record.setTotalTokens(usage.getTotalTokens());
        record.setEstimatedCost(usage.getEstimatedCost());
        record.setDurationMs(durationMs);
        record.setModel(response.getModelName());

        tokenUsageTracker.track(record);
    }

    /**
     * Get tenant context from thread-local or return null
     */
    private TenantContext getTenantContext() {
        if (!multitenancyEnabled) {
            return null;
        }
        return TenantContext.getOrNull();
    }

    /**
     * Get token usage for a tenant (for billing)
     */
    public TokenUsageSummary getUsageSummary(String tenantId, String projectId,
                                            java.time.Instant start, java.time.Instant end) {
        return tokenUsageTracker.getSummary(tenantId, projectId, start, end);
    }

    /**
     * Get estimated cost for a tenant/project
     */
    public double getEstimatedCost(String tenantId, String projectId,
                                  java.time.Instant start, java.time.Instant end) {
        TokenUsageSummary summary = getUsageSummary(tenantId, projectId, start, end);
        return summary != null ? summary.getTotalCost() : 0.0;
    }
}

