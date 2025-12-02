package com.therighthandapp.agentmesh.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for LoRA (Low-Rank Adaptation) adapters.
 * Handles loading, unloading, and caching of tenant-specific model adaptations.
 *
 * LoRA allows efficient fine-tuning of large language models with minimal overhead.
 * Each tenant can have custom adapters that specialize the base model for their domain.
 */
@Service
public class LoRAAdapterManager {
    private static final Logger log = LoggerFactory.getLogger(LoRAAdapterManager.class);

    // Cache of loaded adapters: adapterId -> adapter metadata
    private final Map<String, LoadedAdapter> loadedAdapters = new ConcurrentHashMap<>();

    @Value("${agentmesh.llm.lora.enabled:false}")
    private boolean loraEnabled;

    @Value("${agentmesh.llm.lora.max-cached-adapters:10}")
    private int maxCachedAdapters;

    @Value("${agentmesh.llm.lora.adapter-base-path:/var/agentmesh/lora-adapters}")
    private String adapterBasePath;

    /**
     * Load a LoRA adapter for a tenant
     *
     * @param tenantId The tenant ID
     * @param adapterPath Path to the adapter weights (relative or absolute)
     * @return Adapter ID for use in inference
     */
    public String loadAdapter(String tenantId, String adapterPath) {
        if (!loraEnabled) {
            log.debug("LoRA disabled, skipping adapter load for tenant: {}", tenantId);
            return null;
        }

        String adapterId = generateAdapterId(tenantId, adapterPath);

        // Check if already loaded
        if (loadedAdapters.containsKey(adapterId)) {
            LoadedAdapter adapter = loadedAdapters.get(adapterId);
            adapter.incrementUsageCount();
            log.debug("Using cached LoRA adapter: {} (usage: {})", adapterId, adapter.getUsageCount());
            return adapterId;
        }

        // Check cache size limit
        if (loadedAdapters.size() >= maxCachedAdapters) {
            evictLeastUsedAdapter();
        }

        try {
            // In production, this would:
            // 1. Load adapter weights from storage (S3, local disk, etc.)
            // 2. Apply adapter to base model using LoRA serving framework
            // 3. Register adapter with inference engine

            LoadedAdapter adapter = new LoadedAdapter(adapterId, tenantId, adapterPath);
            loadedAdapters.put(adapterId, adapter);

            log.info("Loaded LoRA adapter: {} for tenant: {} from: {}",
                adapterId, tenantId, adapterPath);

            return adapterId;

        } catch (Exception e) {
            log.error("Failed to load LoRA adapter for tenant: " + tenantId, e);
            return null;
        }
    }

    /**
     * Unload a LoRA adapter
     */
    public void unloadAdapter(String adapterId) {
        if (!loraEnabled || adapterId == null) {
            return;
        }

        LoadedAdapter adapter = loadedAdapters.get(adapterId);
        if (adapter != null) {
            adapter.decrementUsageCount();
            log.debug("Decremented usage count for adapter: {} (now: {})",
                adapterId, adapter.getUsageCount());
        }
    }

    /**
     * Force unload an adapter (remove from cache)
     */
    public void forceUnloadAdapter(String adapterId) {
        if (!loraEnabled) {
            return;
        }

        LoadedAdapter adapter = loadedAdapters.remove(adapterId);
        if (adapter != null) {
            log.info("Force unloaded LoRA adapter: {}", adapterId);

            // In production, cleanup adapter from inference engine
        }
    }

    /**
     * Get loaded adapter info
     */
    public LoadedAdapter getAdapterInfo(String adapterId) {
        return loadedAdapters.get(adapterId);
    }

    /**
     * Get all loaded adapters
     */
    public Map<String, LoadedAdapter> getAllLoadedAdapters() {
        return Map.copyOf(loadedAdapters);
    }

    /**
     * Clear all loaded adapters (primarily for testing)
     */
    public void clearAllAdapters() {
        loadedAdapters.clear();
        log.info("Cleared all loaded adapters");
    }

    /**
     * Evict least recently used adapter
     */
    private void evictLeastUsedAdapter() {
        LoadedAdapter leastUsed = loadedAdapters.values().stream()
            .min((a, b) -> Long.compare(a.getLastUsed(), b.getLastUsed()))
            .orElse(null);

        if (leastUsed != null && leastUsed.getUsageCount() == 0) {
            forceUnloadAdapter(leastUsed.getAdapterId());
            log.info("Evicted least used adapter: {}", leastUsed.getAdapterId());
        }
    }

    /**
     * Generate unique adapter ID
     */
    private String generateAdapterId(String tenantId, String adapterPath) {
        return "lora-%s-%d".formatted(tenantId, adapterPath.hashCode());
    }

    /**
     * Metadata for a loaded LoRA adapter
     */
    public static class LoadedAdapter {
        private final String adapterId;
        private final String tenantId;
        private final String adapterPath;
        private final long loadedAt;
        private long lastUsed;
        private int usageCount;

        public LoadedAdapter(String adapterId, String tenantId, String adapterPath) {
            this.adapterId = adapterId;
            this.tenantId = tenantId;
            this.adapterPath = adapterPath;
            this.loadedAt = System.currentTimeMillis();
            this.lastUsed = System.currentTimeMillis();
            this.usageCount = 0;
        }

        public void incrementUsageCount() {
            this.usageCount++;
            this.lastUsed = System.currentTimeMillis();
        }

        public void decrementUsageCount() {
            if (this.usageCount > 0) {
                this.usageCount--;
            }
            this.lastUsed = System.currentTimeMillis();
        }

        // Getters
        public String getAdapterId() { return adapterId; }
        public String getTenantId() { return tenantId; }
        public String getAdapterPath() { return adapterPath; }
        public long getLoadedAt() { return loadedAt; }
        public long getLastUsed() { return lastUsed; }
        public int getUsageCount() { return usageCount; }
    }
}

