package com.therighthandapp.agentmesh.llm;

import com.therighthandapp.agentmesh.security.TenantContext;
import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Multi-Tenant LLM Client
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MultiTenantLLMClientIntegrationTest {

    @Autowired
    private MultiTenantLLMClient llmClient;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @Autowired(required = false)
    private LoRAAdapterManager loraManager;

    private Tenant testTenant;
    private String projectId = "test-project-id";

    @BeforeEach
    public void setup() {
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setName("Test Corp");
        testTenant.setOrganizationId("test-org");
        testTenant.setTier(Tenant.TenantTier.STANDARD);

        // Add LoRA adapter configuration
        Map<String, String> adapters = new HashMap<>();
        adapters.put("default", "/path/to/adapter/weights");
        testTenant.setLoraAdapters(adapters);

        testTenant = tenantRepository.save(testTenant);

        // Set up tenant context with security attributes
        TenantContext context = new TenantContext(testTenant.getId(), projectId, "test-user");
        context.setRoles(new String[]{"DEVELOPER"});
        context.setDataPartitionKey(testTenant.getId() + "#TEST");
        context.setVectorNamespace("test_project");
        context.setMfaEnabled(true);  // Enable MFA for security checks
        context.setAccountLocked(false);
        TenantContext.set(context);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    @Test
    public void testCompleteWithTenantContext() {
        // Execute LLM completion
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.7);

        LLMResponse response = llmClient.complete("Write a hello world function", params);

        // Assertions
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertTrue(response.getContent().contains("hello") || response.getContent().contains("Hello"));

        // Verify token usage was tracked
        List<TokenUsageRecord> records = tokenUsageRepository.findByTenantIdAndProjectIdAndTimestampBetween(
            testTenant.getId(),
            projectId,
            java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.MINUTES),
            java.time.Instant.now()
        );

        assertFalse(records.isEmpty());
        TokenUsageRecord record = records.get(0);
        assertEquals(testTenant.getId(), record.getTenantId());
        assertEquals(projectId, record.getProjectId());
        assertTrue(record.getTotalTokens() > 0);
    }

    @Test
    public void testChatWithTenantContext() {
        // Create chat messages
        List<ChatMessage> messages = List.of(
            new ChatMessage("user", "What is 2+2?")
        );

        Map<String, Object> params = new HashMap<>();

        LLMResponse response = llmClient.chat(messages, params);

        // Assertions
        assertNotNull(response);
        assertNotNull(response.getContent());

        // Verify tracking
        List<TokenUsageRecord> records = tokenUsageRepository.findByTenantIdAndProjectIdAndTimestampBetween(
            testTenant.getId(),
            projectId,
            java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.MINUTES),
            java.time.Instant.now()
        );

        assertFalse(records.isEmpty());
    }

    @Test
    public void testEmbedWithTenantContext() {
        float[] embedding = llmClient.embed("test text for embedding");

        assertNotNull(embedding);
        assertTrue(embedding.length > 0);
    }

    @Test
    public void testTokenUsageTracking() {
        // Make multiple calls
        llmClient.complete("First prompt", null);
        llmClient.complete("Second prompt", null);
        llmClient.complete("Third prompt", null);

        // Get usage summary
        java.time.Instant start = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
        java.time.Instant end = java.time.Instant.now();

        TokenUsageSummary summary = llmClient.getUsageSummary(testTenant.getId(), projectId, start, end);

        assertNotNull(summary);
        assertEquals(testTenant.getId(), summary.getTenantId());
        assertEquals(projectId, summary.getProjectId());
        assertTrue(summary.getTotalTokens() > 0);
        assertTrue(summary.getTotalCost() > 0);
    }

    @Test
    public void testLoRAAdapterLoading() {
        if (loraManager == null) {
            // Skip if LoRA not enabled
            return;
        }

        // Execute completion (should trigger LoRA loading)
        llmClient.complete("Test prompt for LoRA", null);

        // Verify adapter was loaded (would be unloaded after use)
        // In a real test, we'd mock the LoRA manager and verify interactions
    }

    @Test
    public void testWithoutTenantContext() {
        // Clear context
        TenantContext.clear();

        // Should still work but without tracking
        LLMResponse response = llmClient.complete("Test without context", null);

        assertNotNull(response);
        assertNotNull(response.getContent());
    }

    @Test
    public void testCostEstimation() {
        // Make some calls
        llmClient.complete("Prompt 1", null);
        llmClient.complete("Prompt 2", null);

        // Get estimated cost
        java.time.Instant start = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
        java.time.Instant end = java.time.Instant.now();

        double cost = llmClient.getEstimatedCost(testTenant.getId(), projectId, start, end);

        assertTrue(cost > 0);
    }
}

