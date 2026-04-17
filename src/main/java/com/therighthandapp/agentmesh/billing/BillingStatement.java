package com.therighthandapp.agentmesh.billing;

import com.therighthandapp.agentmesh.tenant.Tenant;

import java.time.Instant;

/**
 * Billing statement for a tenant for a specific period
 */
public class BillingStatement {
    private String tenantId;
    private String tenantName;
    private Instant periodStart;
    private Instant periodEnd;
    private long totalTokens;
    private double tokenCost;
    private double outcomeCost;
    private double discount;
    private double totalCost;
    private Tenant.TenantTier tier;

    // Getters and setters
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public double getTokenCost() {
        return tokenCost;
    }

    public void setTokenCost(double tokenCost) {
        this.tokenCost = tokenCost;
    }

    public double getOutcomeCost() {
        return outcomeCost;
    }

    public void setOutcomeCost(double outcomeCost) {
        this.outcomeCost = outcomeCost;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public Tenant.TenantTier getTier() {
        return tier;
    }

    public void setTier(Tenant.TenantTier tier) {
        this.tier = tier;
    }

    @Override
    public String toString() {
        return ("BillingStatement{tenant=%s (%s), period=%s to %s, tokens=%d, " +
            "tokenCost=$%.2f, outcomeCost=$%.2f, discount=$%.2f, total=$%.2f}").formatted(
            tenantName, tier, periodStart, periodEnd, totalTokens,
            tokenCost, outcomeCost, discount, totalCost);
    }
}

