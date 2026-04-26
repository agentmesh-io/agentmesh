package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.tenant.Project;
import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.TenantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST API for tenant and project management.
 * Provides endpoints for creating tenants, managing projects, and tier upgrades.
 *
 * <p>RBAC (M13.2): admin-only — tenant management is a privileged operation.
 */
@RestController
@RequestMapping("/api/tenants")
@PreAuthorize("@rbac.admin()")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Create a new tenant
     * POST /api/tenants
     */
    @PostMapping
    public ResponseEntity<Tenant> createTenant(@RequestBody TenantService.CreateTenantRequest request) {
        try {
            Tenant tenant = tenantService.createTenant(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get tenant by ID
     * GET /api/tenants/{tenantId}
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<Tenant> getTenant(@PathVariable String tenantId) {
        Optional<Tenant> tenant = tenantService.getTenant(tenantId);
        return tenant.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get tenant by organization ID
     * GET /api/tenants/org/{orgId}
     */
    @GetMapping("/org/{orgId}")
    public ResponseEntity<Tenant> getTenantByOrganization(@PathVariable String orgId) {
        Optional<Tenant> tenant = tenantService.getTenantByOrganizationId(orgId);
        return tenant.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update tenant tier
     * PUT /api/tenants/{tenantId}/tier
     */
    @PutMapping("/{tenantId}/tier")
    public ResponseEntity<Tenant> updateTier(
            @PathVariable String tenantId,
            @RequestBody TierUpdateRequest request) {
        try {
            Tenant tenant = tenantService.updateTenantTier(tenantId, request.getTier());
            return ResponseEntity.ok(tenant);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create project within tenant
     * POST /api/tenants/{tenantId}/projects
     */
    @PostMapping("/{tenantId}/projects")
    public ResponseEntity<Project> createProject(
            @PathVariable String tenantId,
            @RequestBody TenantService.CreateProjectRequest request) {
        try {
            Project project = tenantService.createProject(tenantId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(project);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Trigger Kubernetes provisioning for tenant
     * POST /api/tenants/{tenantId}/provision
     */
    @PostMapping("/{tenantId}/provision")
    public ResponseEntity<String> provisionKubernetes(@PathVariable String tenantId) {
        try {
            Optional<Tenant> tenantOpt = tenantService.getTenant(tenantId);
            if (tenantOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Tenant tenant = tenantOpt.get();
            tenantService.provisionKubernetesNamespace(tenant);
            tenantService.applyResourceQuotas(tenant);
            tenantService.deployNetworkPolicies(tenant);

            return ResponseEntity.ok("Kubernetes resources provisioned for namespace: " + tenant.getK8sNamespace());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                               .body("Provisioning failed: " + e.getMessage());
        }
    }

    /**
     * Cleanup test data (for testing purposes)
     * DELETE /api/tenants/cleanup?orgIdPattern=pattern1,pattern2
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<CleanupResponse> cleanupTestData(@RequestParam String orgIdPattern) {
        try {
            String[] patterns = orgIdPattern.split(",");
            int deleted = tenantService.cleanupTestTenants(patterns);
            return ResponseEntity.ok(new CleanupResponse(deleted));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DTO for tier update
     */
    public static class TierUpdateRequest {
        private Tenant.TenantTier tier;

        public Tenant.TenantTier getTier() {
            return tier;
        }

        public void setTier(Tenant.TenantTier tier) {
            this.tier = tier;
        }
    }

    /**
     * DTO for cleanup response
     */
    public static class CleanupResponse {
        private int deleted;

        public CleanupResponse(int deleted) {
            this.deleted = deleted;
        }

        public int getDeleted() {
            return deleted;
        }

        public void setDeleted(int deleted) {
            this.deleted = deleted;
        }
    }
}

