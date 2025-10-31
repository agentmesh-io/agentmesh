package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.billing.BillingService;
import com.therighthandapp.agentmesh.billing.BillingStatement;
import com.therighthandapp.agentmesh.llm.TokenUsageSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST API for billing and usage analytics
 */
@RestController
@RequestMapping("/api/billing")
@Tag(name = "Billing", description = "Billing, usage tracking, and cost estimation APIs")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * Get billing statement for a tenant
     * GET /api/billing/tenants/{tenantId}/statement?start={start}&end={end}
     */
    @Operation(
        summary = "Generate billing statement",
        description = "Generate a detailed billing statement for a tenant covering token usage, outcome-based charges, and tier discounts for a specified period"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statement generated successfully",
            content = @Content(schema = @Schema(implementation = BillingStatement.class))),
        @ApiResponse(responseCode = "404", description = "Tenant not found", content = @Content)
    })
    @GetMapping("/tenants/{tenantId}/statement")
    public ResponseEntity<BillingStatement> getStatement(
            @Parameter(description = "Tenant ID", required = true) @PathVariable String tenantId,
            @Parameter(description = "Period start (ISO 8601)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @Parameter(description = "Period end (ISO 8601)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        BillingStatement statement = billingService.generateStatement(tenantId, start, end);
        return ResponseEntity.ok(statement);
    }

    /**
     * Get current month usage for a tenant
     * GET /api/billing/tenants/{tenantId}/current-month
     */
    @GetMapping("/tenants/{tenantId}/current-month")
    public ResponseEntity<TokenUsageSummary> getCurrentMonthUsage(@PathVariable String tenantId) {
        TokenUsageSummary usage = billingService.getCurrentMonthUsage(tenantId);
        return ResponseEntity.ok(usage);
    }

    /**
     * Get estimated cost for current month
     * GET /api/billing/tenants/{tenantId}/estimated-cost
     */
    @GetMapping("/tenants/{tenantId}/estimated-cost")
    public ResponseEntity<EstimatedCostResponse> getEstimatedCost(@PathVariable String tenantId) {
        double cost = billingService.getEstimatedMonthCost(tenantId);
        return ResponseEntity.ok(new EstimatedCostResponse(tenantId, cost));
    }

    /**
     * DTO for estimated cost response
     */
    public static class EstimatedCostResponse {
        private final String tenantId;
        private final double estimatedCost;

        public EstimatedCostResponse(String tenantId, double estimatedCost) {
            this.tenantId = tenantId;
            this.estimatedCost = estimatedCost;
        }

        public String getTenantId() { return tenantId; }
        public double getEstimatedCost() { return estimatedCost; }
    }
}

