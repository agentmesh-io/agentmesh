package com.therighthandapp.agentmesh.billing;

import com.therighthandapp.agentmesh.llm.TokenUsageSummary;
import com.therighthandapp.agentmesh.llm.TokenUsageTracker;
import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Billing service implementing outcome-based pricing model.
 *
 * Pricing Models:
 * 1. Token-based: Traditional pay-per-token (fallback)
 * 2. Outcome-based: Pay for successful task completion
 * 3. Hybrid: Base fee + success bonus
 *
 * Tier-based Discounts:
 * - FREE: $0 (limited usage)
 * - STANDARD: Standard rates
 * - PREMIUM: 20% discount
 * - ENTERPRISE: Custom pricing
 */
@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final TenantRepository tenantRepository;
    private final TokenUsageTracker tokenUsageTracker;
    private final BillingRecordRepository billingRecordRepository;

    // Base pricing (per 1K tokens)
    private static final double BASE_INPUT_TOKEN_COST = 0.01;   // $0.01 per 1K input tokens
    private static final double BASE_OUTPUT_TOKEN_COST = 0.03;  // $0.03 per 1K output tokens

    // Outcome-based pricing
    private static final double TASK_SUCCESS_FEE = 2.00;      // $2.00 per successful task
    private static final double TASK_FAILURE_CREDIT = -0.50;  // $0.50 credit for failed tasks

    public BillingService(TenantRepository tenantRepository,
                         TokenUsageTracker tokenUsageTracker,
                         BillingRecordRepository billingRecordRepository) {
        this.tenantRepository = tenantRepository;
        this.tokenUsageTracker = tokenUsageTracker;
        this.billingRecordRepository = billingRecordRepository;
    }

    /**
     * Calculate bill for a tenant for a period
     */
    public BillingStatement generateStatement(String tenantId, Instant start, Instant end) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        Tenant tenant = tenantOpt.get();

        // Get token usage
        TokenUsageSummary usage = tokenUsageTracker.getSummary(tenantId, null, start, end);

        // Calculate costs based on billing model
        double tokenCost = usage.getTotalCost(); // Pre-calculated estimated cost
        double outcomeCost = 0.0;

        if (tenant.getOutcomeBasedBilling()) {
            // Calculate outcome-based charges
            outcomeCost = calculateOutcomeCost(tenantId, start, end);
        }

        // Apply tier discounts
        double discount = applyTierDiscount(tenant.getTier(), tokenCost + outcomeCost);
        double totalCost = tokenCost + outcomeCost - discount;

        // FREE tier gets credits
        if (tenant.getTier() == Tenant.TenantTier.FREE) {
            totalCost = 0.0; // No charges for free tier
        }

        BillingStatement statement = new BillingStatement();
        statement.setTenantId(tenantId);
        statement.setTenantName(tenant.getName());
        statement.setPeriodStart(start);
        statement.setPeriodEnd(end);
        statement.setTotalTokens(usage.getTotalTokens());
        statement.setTokenCost(tokenCost);
        statement.setOutcomeCost(outcomeCost);
        statement.setDiscount(discount);
        statement.setTotalCost(totalCost);
        statement.setTier(tenant.getTier());

        log.info("Generated billing statement for tenant {}: ${} (tokens: {}, outcomes: ${}, discount: ${})",
            tenantId, String.format("%.2f", totalCost), usage.getTotalTokens(),
            String.format("%.2f", outcomeCost), String.format("%.2f", discount));

        return statement;
    }

    /**
     * Record a task completion for outcome-based billing
     */
    public void recordTaskOutcome(String tenantId, String projectId, String taskId,
                                  boolean success, int iterations) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty() || !tenantOpt.get().getOutcomeBasedBilling()) {
            return; // Skip if not using outcome-based billing
        }

        double cost = success ? TASK_SUCCESS_FEE : TASK_FAILURE_CREDIT;

        // Apply iteration penalty/bonus
        if (success && iterations > 1) {
            // Slight discount for needing multiple iterations
            cost = cost * (1.0 - (iterations - 1) * 0.1);
        }

        BillingRecord record = new BillingRecord();
        record.setTenantId(tenantId);
        record.setProjectId(projectId);
        record.setTaskId(taskId);
        record.setBillingType(BillingType.OUTCOME);
        record.setSuccess(success);
        record.setIterations(iterations);
        record.setAmount(cost);

        billingRecordRepository.save(record);

        log.info("Recorded task outcome: tenant={}, task={}, success={}, cost=${}",
            tenantId, taskId, success, String.format("%.2f", cost));
    }

    /**
     * Calculate outcome-based costs
     */
    private double calculateOutcomeCost(String tenantId, Instant start, Instant end) {
        Double totalCost = billingRecordRepository.sumAmountByTenantAndPeriod(tenantId, start, end);
        return totalCost != null ? totalCost : 0.0;
    }

    /**
     * Apply tier-based discount
     */
    private double applyTierDiscount(Tenant.TenantTier tier, double amount) {
        return switch (tier) {
            case FREE -> amount; // No discount (but will be zeroed out)
            case STANDARD -> 0.0; // No discount
            case PREMIUM -> amount * 0.20; // 20% discount
            case ENTERPRISE -> amount * 0.30; // 30% discount
        };
    }

    /**
     * Get current month usage for a tenant
     */
    public TokenUsageSummary getCurrentMonthUsage(String tenantId) {
        Instant now = Instant.now();
        Instant startOfMonth = now.truncatedTo(ChronoUnit.DAYS)
            .minus(now.atZone(java.time.ZoneId.systemDefault()).getDayOfMonth() - 1, ChronoUnit.DAYS);

        return tokenUsageTracker.getSummary(tenantId, null, startOfMonth, now);
    }

    /**
     * Get estimated cost for current month
     */
    public double getEstimatedMonthCost(String tenantId) {
        Instant now = Instant.now();
        Instant startOfMonth = now.truncatedTo(ChronoUnit.DAYS)
            .minus(now.atZone(java.time.ZoneId.systemDefault()).getDayOfMonth() - 1, ChronoUnit.DAYS);

        BillingStatement statement = generateStatement(tenantId, startOfMonth, now);
        return statement.getTotalCost();
    }
}

