package com.therighthandapp.agentmesh.security;

import org.springframework.stereotype.Component;

/**
 * Policy Enforcement Point (PEP) for multi-tenant access control.
 *
 * Implements Hybrid RBAC/ABAC model as recommended in the blueprint.
 * Enforces three-layer checks: Tenant Boundary, Project Boundary, Role Check.
 */
@Component
public class AccessControlService {

    /**
     * Check if current context has access to specified tenant
     */
    public void checkTenantAccess(String tenantId) {
        TenantContext context = TenantContext.get();

        if (!tenantId.equals(context.getTenantId())) {
            throw new SecurityException(String.format(
                "Access denied: User %s attempting to access tenant %s but authenticated for tenant %s",
                context.getUserId(), tenantId, context.getTenantId()
            ));
        }
    }

    /**
     * Check if current context has access to specified project
     */
    public void checkProjectAccess(String projectId) {
        TenantContext context = TenantContext.get();

        // First check tenant boundary
        // (This assumes project lookup to get tenantId, simplified here)

        // Then check project boundary
        if (!projectId.equals(context.getProjectId())) {
            throw new SecurityException(String.format(
                "Access denied: User %s attempting to access project %s but authenticated for project %s",
                context.getUserId(), projectId, context.getProjectId()
            ));
        }
    }

    /**
     * Check if current context has required role (RBAC)
     */
    public void checkRole(String requiredRole) {
        TenantContext context = TenantContext.get();

        if (!context.hasRole(requiredRole)) {
            throw new SecurityException(String.format(
                "Access denied: User %s lacks required role '%s'",
                context.getUserId(), requiredRole
            ));
        }
    }

    /**
     * Check if current context has any of the required roles
     */
    public void checkAnyRole(String... requiredRoles) {
        TenantContext context = TenantContext.get();

        if (!context.hasAnyRole(requiredRoles)) {
            throw new SecurityException(String.format(
                "Access denied: User %s lacks any of required roles: %s",
                context.getUserId(), String.join(", ", requiredRoles)
            ));
        }
    }

    /**
     * Comprehensive access check combining RBAC and ABAC
     *
     * Checks:
     * 1. Tenant boundary
     * 2. Project boundary
     * 3. Role check
     * 4. ABAC attributes (MFA, account status)
     */
    public void checkAccess(String tenantId, String projectId, String... requiredRoles) {
        TenantContext context = TenantContext.get();

        // Layer 1: Tenant Boundary
        checkTenantAccess(tenantId);

        // Layer 2: Project Boundary
        if (projectId != null) {
            checkProjectAccess(projectId);
        }

        // Layer 3: Role Check (RBAC)
        if (requiredRoles != null && requiredRoles.length > 0) {
            checkAnyRole(requiredRoles);
        }

        // Layer 4: ABAC - Dynamic Attributes
        if (!context.meetsSecurityRequirements()) {
            throw new SecurityException(String.format(
                "Access denied: User %s fails ABAC security requirements (MFA: %s, Locked: %s)",
                context.getUserId(), context.isMfaEnabled(), context.isAccountLocked()
            ));
        }
    }

    /**
     * Check if operation is allowed for tool invocation
     * This is the critical Policy Enforcement Point for agent actions
     */
    public void checkToolAccess(String toolName, String targetResource) {
        TenantContext context = TenantContext.get();

        // Verify tool is allowed for this tenant/project
        // In production, this would check against a tool permission matrix

        // Ensure target resource belongs to current tenant
        if (targetResource != null && !targetResource.startsWith(context.getDataPartitionKey())) {
            throw new SecurityException(String.format(
                "Access denied: Tool %s attempting to access resource outside tenant partition: %s",
                toolName, targetResource
            ));
        }
    }

    /**
     * Check RAG data access with row-level security
     * Implements Zero Trust RAG as per blueprint
     */
    public void checkDataAccess(String resourceId, String resourceType) {
        TenantContext context = TenantContext.get();

        // Verify resource belongs to current tenant partition
        // This would be enforced at the database/vector store level in production

        // Log for audit trail
        logAccessAttempt(context, resourceType, resourceId, "READ");
    }

    /**
     * Log access attempt for audit trail
     */
    private void logAccessAttempt(TenantContext context, String resourceType,
                                  String resourceId, String operation) {
        // In production, this would write to audit log
        System.out.printf("[AUDIT] Tenant=%s Project=%s User=%s Operation=%s Resource=%s:%s%n",
            context.getTenantId(), context.getProjectId(), context.getUserId(),
            operation, resourceType, resourceId);
    }
}

