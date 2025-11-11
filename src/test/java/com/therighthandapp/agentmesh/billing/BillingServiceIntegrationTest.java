package com.therighthandapp.agentmesh.billing;

import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.TenantRepository;
import com.therighthandapp.agentmesh.llm.TokenUsageRecord;
import com.therighthandapp.agentmesh.llm.TokenUsageRepository;
import com.therighthandapp.agentmesh.llm.TokenUsageSummary;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for billing system
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BillingServiceIntegrationTest {

    @Autowired
    private BillingService billingService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @Autowired
    private BillingRecordRepository billingRecordRepository;
    
    @Autowired
    private EntityManager entityManager;

    private Tenant testTenant;
    private String projectId = "test-project-id";

    @BeforeEach
    public void setup() {
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setName("Test Corp");
        testTenant.setOrganizationId("test-org");
        testTenant.setTier(Tenant.TenantTier.PREMIUM);
        testTenant.setOutcomeBasedBilling(true);
        testTenant = tenantRepository.save(testTenant);
    }

    @Test
    public void testTokenBasedBilling() {
        // Create token usage records
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);

        TokenUsageRecord record1 = new TokenUsageRecord();
        record1.setTenantId(testTenant.getId());
        record1.setProjectId(projectId);
        record1.setModel("gpt-4");
        record1.setPromptTokens(1000);
        record1.setCompletionTokens(500);
        record1.setTotalTokens(1500);
        record1.setEstimatedCost(0.045); // (1000 * 0.01 + 500 * 0.03) / 1000
        tokenUsageRepository.save(record1);

        TokenUsageRecord record2 = new TokenUsageRecord();
        record2.setTenantId(testTenant.getId());
        record2.setProjectId(projectId);
        record2.setModel("gpt-4");
        record2.setPromptTokens(2000);
        record2.setCompletionTokens(1000);
        record2.setTotalTokens(3000);
        record2.setEstimatedCost(0.090);
        tokenUsageRepository.save(record2);
        
        // Flush to database so service can query them
        entityManager.flush();
        
        // Set end time AFTER creating records
        Instant end = Instant.now();

        // Generate billing statement
        BillingStatement statement = billingService.generateStatement(testTenant.getId(), start, end);

        // Assertions
        assertNotNull(statement);
        assertEquals(testTenant.getId(), statement.getTenantId());
        assertEquals(4500L, statement.getTotalTokens());
        assertEquals(0.135, statement.getTokenCost(), 0.001);
        assertTrue(statement.getTotalCost() > 0);

        // Premium tier gets 20% discount
        double expectedDiscount = 0.135 * 0.20;
        assertEquals(expectedDiscount, statement.getDiscount(), 0.01);
    }

    @Test
    public void testOutcomeBasedBilling() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);

        // Record successful tasks
        billingService.recordTaskOutcome(testTenant.getId(), projectId, "task-1", true, 1);
        billingService.recordTaskOutcome(testTenant.getId(), projectId, "task-2", true, 2);
        billingService.recordTaskOutcome(testTenant.getId(), projectId, "task-3", false, 3);
        
        // Flush to database
        entityManager.flush();
        
        // Set end time AFTER creating records
        Instant end = Instant.now();

        // Generate statement
        BillingStatement statement = billingService.generateStatement(testTenant.getId(), start, end);

        // Assertions
        assertNotNull(statement);
        assertTrue(statement.getOutcomeCost() > 0);

        // Should have: $2.00 + ($2.00 * 0.9) + (-$0.50) = $3.30
        assertEquals(3.30, statement.getOutcomeCost(), 0.1);
    }

    @Test
    public void testHybridBilling() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);

        // Add token usage
        TokenUsageRecord record = new TokenUsageRecord();
        record.setTenantId(testTenant.getId());
        record.setProjectId(projectId);
        record.setModel("gpt-4");
        record.setPromptTokens(10000);
        record.setCompletionTokens(5000);
        record.setTotalTokens(15000);
        record.setEstimatedCost(0.25);
        tokenUsageRepository.save(record);

        // Add outcome events
        billingService.recordTaskOutcome(testTenant.getId(), projectId, "task-1", true, 1);
        billingService.recordTaskOutcome(testTenant.getId(), projectId, "task-2", true, 1);
        
        // Flush to database
        entityManager.flush();
        
        // Set end time AFTER creating records
        Instant end = Instant.now();

        // Generate statement
        BillingStatement statement = billingService.generateStatement(testTenant.getId(), start, end);

        // Assertions
        assertNotNull(statement);
        assertTrue(statement.getTokenCost() > 0);
        assertTrue(statement.getOutcomeCost() > 0);

        double subtotal = statement.getTokenCost() + statement.getOutcomeCost();
        double discount = statement.getDiscount();
        assertEquals(subtotal - discount, statement.getTotalCost(), 0.01);
    }

    @Test
    public void testFreeTierBilling() {
        // Change to FREE tier
        testTenant.setTier(Tenant.TenantTier.FREE);
        tenantRepository.save(testTenant);

        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = Instant.now();

        // Add usage
        TokenUsageRecord record = new TokenUsageRecord();
        record.setTenantId(testTenant.getId());
        record.setProjectId(projectId);
        record.setModel("gpt-4");
        record.setPromptTokens(1000);
        record.setCompletionTokens(500);
        record.setTotalTokens(1500);
        record.setEstimatedCost(0.045);
        tokenUsageRepository.save(record);

        // Generate statement
        BillingStatement statement = billingService.generateStatement(testTenant.getId(), start, end);

        // FREE tier should have $0 total cost
        assertEquals(0.0, statement.getTotalCost(), 0.001);
    }

    @Test
    public void testCurrentMonthUsage() {
        // Add current month usage
        TokenUsageRecord record = new TokenUsageRecord();
        record.setTenantId(testTenant.getId());
        record.setProjectId(projectId);
        record.setModel("gpt-4");
        record.setPromptTokens(5000);
        record.setCompletionTokens(2500);
        record.setTotalTokens(7500);
        record.setEstimatedCost(0.125);
        tokenUsageRepository.save(record);

        // Get current month usage
        TokenUsageSummary summary = billingService.getCurrentMonthUsage(testTenant.getId());

        assertNotNull(summary);
        assertEquals(testTenant.getId(), summary.getTenantId());
        assertTrue(summary.getTotalTokens() > 0);
    }

    @Test
    public void testEstimatedMonthCost() {
        // Add usage
        TokenUsageRecord record = new TokenUsageRecord();
        record.setTenantId(testTenant.getId());
        record.setProjectId(projectId);
        record.setModel("gpt-4");
        record.setPromptTokens(10000);
        record.setCompletionTokens(5000);
        record.setTotalTokens(15000);
        record.setEstimatedCost(0.25);
        tokenUsageRepository.save(record);

        billingService.recordTaskOutcome(testTenant.getId(), projectId, "task-1", true, 1);

        // Get estimated cost
        double cost = billingService.getEstimatedMonthCost(testTenant.getId());

        assertTrue(cost > 0);
        // Should include token cost + outcome cost - premium discount
    }
}

