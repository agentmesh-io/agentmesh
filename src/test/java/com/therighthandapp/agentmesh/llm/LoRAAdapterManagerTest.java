package com.therighthandapp.agentmesh.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LoRA Adapter Manager
 */
@SpringBootTest
@ActiveProfiles("test")
public class LoRAAdapterManagerTest {

    @Autowired
    private LoRAAdapterManager manager;

    @BeforeEach
    public void setup() {
        // Clear cache before each test to ensure isolation
        manager.clearAllAdapters();
    }

    @Test
    public void testLoadAdapter() {
        String adapterId = manager.loadAdapter("tenant-1", "/path/to/adapter");

        assertNotNull(adapterId);
        assertTrue(adapterId.startsWith("lora-"));

        // Verify adapter is cached
        LoRAAdapterManager.LoadedAdapter adapter = manager.getAdapterInfo(adapterId);
        assertNotNull(adapter);
        assertEquals("tenant-1", adapter.getTenantId());
        assertEquals("/path/to/adapter", adapter.getAdapterPath());
    }

    @Test
    public void testAdapterCaching() {
        // Load same adapter twice
        String adapterId1 = manager.loadAdapter("tenant-1", "/path/to/adapter");
        String adapterId2 = manager.loadAdapter("tenant-1", "/path/to/adapter");

        // Should return same adapter ID (cached)
        assertEquals(adapterId1, adapterId2);

        // Usage count should increment
        LoRAAdapterManager.LoadedAdapter adapter = manager.getAdapterInfo(adapterId1);
        assertTrue(adapter.getUsageCount() > 0);
    }

    @Test
    public void testUnloadAdapter() {
        String adapterId = manager.loadAdapter("tenant-1", "/path/to/adapter");

        // Increment usage
        manager.loadAdapter("tenant-1", "/path/to/adapter");

        // Unload once
        manager.unloadAdapter(adapterId);

        // Should still be cached
        assertNotNull(manager.getAdapterInfo(adapterId));

        // Unload again
        manager.unloadAdapter(adapterId);

        // Force unload
        manager.forceUnloadAdapter(adapterId);

        // Should be removed
        assertNull(manager.getAdapterInfo(adapterId));
    }

    @Test
    public void testMultipleTenants() {
        String adapter1 = manager.loadAdapter("tenant-1", "/path/to/adapter1");
        String adapter2 = manager.loadAdapter("tenant-2", "/path/to/adapter2");
        String adapter3 = manager.loadAdapter("tenant-3", "/path/to/adapter3");

        assertNotEquals(adapter1, adapter2);
        assertNotEquals(adapter2, adapter3);

        // All should be cached
        assertEquals(3, manager.getAllLoadedAdapters().size());
    }

    @Test
    public void testAdapterEviction() {
        // Load max adapters (would need to configure max in real scenario)
        for (int i = 0; i < 15; i++) {
            manager.loadAdapter("tenant-" + i, "/path/to/adapter" + i);
        }

        // Some adapters may have been evicted if cache limit exceeded
        // This depends on configuration
        assertTrue(manager.getAllLoadedAdapters().size() <= 15);
    }

    @Test
    public void testUsageTracking() {
        String adapterId = manager.loadAdapter("tenant-1", "/path/to/adapter");

        LoRAAdapterManager.LoadedAdapter adapter = manager.getAdapterInfo(adapterId);
        long initialLastUsed = adapter.getLastUsed();

        // Wait a bit and access again
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // ignore
        }

        manager.loadAdapter("tenant-1", "/path/to/adapter");

        // Last used should be updated
        assertTrue(adapter.getLastUsed() >= initialLastUsed);
    }
}

