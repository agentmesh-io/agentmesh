package com.therighthandapp.agentmesh.billing;

/**
 * Type of billing event
 */
public enum BillingType {
    TOKEN,      // Token-based billing
    OUTCOME,    // Outcome-based billing (task success/failure)
    HYBRID,     // Combination of both
    SUBSCRIPTION, // Fixed monthly/yearly fee
    CUSTOM      // Custom pricing
}

