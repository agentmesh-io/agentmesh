package com.therighthandapp.agentmesh.security;

/**
 * Execution context carrying tenant and project information
 * for multi-tenant access control and data isolation.
 *
 * This context is propagated through all layers of the application
 * to enforce isolation boundaries.
 */
public class TenantContext {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private String tenantId;
    private String projectId;
    private String userId;
    private String organizationId;
    private String[] roles;

    // Data isolation keys
    private String dataPartitionKey;
    private String vectorNamespace;
    private String k8sNamespace;

    // Security attributes for ABAC
    private boolean mfaEnabled;
    private boolean accountLocked;
    private String ipAddress;

    public TenantContext() {}

    public TenantContext(String tenantId, String projectId, String userId) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.userId = userId;
    }

    /**
     * Set the current tenant context for this thread
     */
    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    /**
     * Get the current tenant context
     */
    public static TenantContext get() {
        TenantContext context = CONTEXT.get();
        if (context == null) {
            throw new SecurityException("No tenant context available - authentication required");
        }
        return context;
    }

    /**
     * Get the current tenant context if available, otherwise null
     */
    public static TenantContext getOrNull() {
        return CONTEXT.get();
    }

    /**
     * Clear the tenant context for this thread
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        if (roles == null) return false;
        for (String r : roles) {
            if (r.equals(role)) return true;
        }
        return false;
    }

    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... checkRoles) {
        if (roles == null) return false;
        for (String role : checkRoles) {
            if (hasRole(role)) return true;
        }
        return false;
    }

    /**
     * Validate security attributes for ABAC policy
     */
    public boolean meetsSecurityRequirements() {
        return !accountLocked && (mfaEnabled || hasRole("ADMIN"));
    }

    // Getters and setters
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String getDataPartitionKey() {
        return dataPartitionKey;
    }

    public void setDataPartitionKey(String dataPartitionKey) {
        this.dataPartitionKey = dataPartitionKey;
    }

    public String getVectorNamespace() {
        return vectorNamespace;
    }

    public void setVectorNamespace(String vectorNamespace) {
        this.vectorNamespace = vectorNamespace;
    }

    public String getK8sNamespace() {
        return k8sNamespace;
    }

    public void setK8sNamespace(String k8sNamespace) {
        this.k8sNamespace = k8sNamespace;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Override
    public String toString() {
        return "TenantContext{tenantId='%s', projectId='%s', userId='%s', org='%s'}".formatted(
            tenantId, projectId, userId, organizationId);
    }
}

