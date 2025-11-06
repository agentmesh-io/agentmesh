package com.therighthandapp.agentmesh.tenant;

import com.therighthandapp.agentmesh.security.TenantContext;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for tenant lifecycle management including Kubernetes provisioning.
 * Handles tenant creation, resource quota management, and namespace isolation.
 */
@Service
public class TenantService {
    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;

    @Value("${agentmesh.multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    @Value("${agentmesh.multitenancy.kubernetes.enabled:false}")
    private boolean kubernetesEnabled;

    @Value("${agentmesh.multitenancy.kubernetes.namespace-prefix:agentmesh}")
    private String namespacePrefix;

    @Value("${agentmesh.multitenancy.kubernetes.apply-network-policies:true}")
    private boolean applyNetworkPolicies;

    @Value("${agentmesh.multitenancy.kubernetes.apply-resource-quotas:true}")
    private boolean applyResourceQuotas;

    @Autowired(required = false)
    private ApiClient k8sClient;

    public TenantService(TenantRepository tenantRepository, ProjectRepository projectRepository) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Create a new tenant with full provisioning
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        log.info("Creating tenant: {}", request.getOrganizationId());

        // Validate unique organization ID
        if (tenantRepository.existsByOrganizationId(request.getOrganizationId())) {
            throw new IllegalArgumentException("Organization ID already exists: " + request.getOrganizationId());
        }

        // Create tenant entity
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setOrganizationId(request.getOrganizationId());
        tenant.setTier(request.getTier());
        tenant.setDataRegion(request.getDataRegion());
        tenant.setRequiresDataLocality(request.getRequiresDataLocality());

        // Set tier-based resource limits
        applyTierDefaults(tenant);

        // Save tenant (triggers @PrePersist to generate k8sNamespace)
        tenant = tenantRepository.save(tenant);

        // Provision Kubernetes resources if enabled
        if (kubernetesEnabled && k8sClient != null) {
            try {
                provisionKubernetesNamespace(tenant);
                if (applyResourceQuotas) {
                    applyResourceQuotas(tenant);
                }
                if (applyNetworkPolicies) {
                    deployNetworkPolicies(tenant);
                }
            } catch (Exception e) {
                log.error("Failed to provision Kubernetes resources for tenant: " + tenant.getId(), e);
                // Note: Tenant is still created, K8s provisioning can be retried
            }
        }

        log.info("Tenant created successfully: {} ({})", tenant.getName(), tenant.getId());
        return tenant;
    }

    /**
     * Get tenant by ID with access control
     */
    public Optional<Tenant> getTenant(String tenantId) {
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null && !tenantId.equals(context.getTenantId())) {
                throw new SecurityException("Access denied: Cannot access tenant " + tenantId);
            }
        }
        return tenantRepository.findById(tenantId);
    }

    /**
     * Get tenant by organization ID
     */
    public Optional<Tenant> getTenantByOrganizationId(String organizationId) {
        return tenantRepository.findByOrganizationId(organizationId);
    }

    /**
     * Update tenant tier and reapply resource quotas
     */
    @Transactional
    public Tenant updateTenantTier(String tenantId, Tenant.TenantTier newTier) {
        Tenant tenant = getTenant(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        Tenant.TenantTier oldTier = tenant.getTier();
        tenant.setTier(newTier);
        applyTierDefaults(tenant);

        tenant = tenantRepository.save(tenant);

        // Update K8s resource quotas if enabled
        if (kubernetesEnabled && k8sClient != null) {
            try {
                applyResourceQuotas(tenant);
                log.info("Updated tenant {} tier from {} to {}", tenantId, oldTier, newTier);
            } catch (Exception e) {
                log.error("Failed to update K8s resource quotas for tenant: " + tenantId, e);
            }
        }

        return tenant;
    }

    /**
     * Create project within tenant
     */
    @Transactional
    public Project createProject(String tenantId, CreateProjectRequest request) {
        Tenant tenant = getTenant(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Check tenant project limit
        long currentProjects = projectRepository.countByTenantId(tenantId);
        if (currentProjects >= tenant.getMaxProjects()) {
            throw new IllegalStateException(String.format(
                "Tenant %s has reached maximum project limit: %d",
                tenantId, tenant.getMaxProjects()
            ));
        }

        // Validate unique project key within tenant
        if (projectRepository.findByTenantIdAndProjectKey(tenantId, request.getProjectKey()).isPresent()) {
            throw new IllegalArgumentException("Project key already exists in tenant: " + request.getProjectKey());
        }

        Project project = new Project();
        project.setTenant(tenant);
        project.setName(request.getName());
        project.setProjectKey(request.getProjectKey());
        project.setDescription(request.getDescription());

        project = projectRepository.save(project);

        log.info("Project created: {} in tenant {}", project.getProjectKey(), tenantId);
        return project;
    }

    /**
     * Provision Kubernetes namespace for tenant
     */
    public void provisionKubernetesNamespace(Tenant tenant) {
        if (!kubernetesEnabled || k8sClient == null) {
            log.debug("Kubernetes provisioning disabled, skipping namespace creation");
            return;
        }

        try {
            CoreV1Api api = new CoreV1Api(k8sClient);

            // Create namespace
            V1Namespace namespace = new V1Namespace()
                .metadata(new V1ObjectMeta()
                    .name(tenant.getK8sNamespace())
                    .putLabelsItem("tenant", tenant.getOrganizationId())
                    .putLabelsItem("managed-by", "agentmesh")
                    .putLabelsItem("tier", tenant.getTier().name().toLowerCase())
                );

            api.createNamespace(namespace).execute();
            log.info("Created Kubernetes namespace: {}", tenant.getK8sNamespace());

        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.info("Namespace already exists: {}", tenant.getK8sNamespace());
            } else {
                throw new RuntimeException("Failed to create namespace: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Apply resource quotas to tenant namespace
     */
    public void applyResourceQuotas(Tenant tenant) {
        if (!kubernetesEnabled || k8sClient == null || !applyResourceQuotas) {
            return;
        }

        try {
            CoreV1Api api = new CoreV1Api(k8sClient);

            // Calculate resource limits based on tenant tier
            Map<String, io.kubernetes.client.custom.Quantity> hard = calculateResourceLimits(tenant);

            V1ResourceQuota quota = new V1ResourceQuota()
                .metadata(new V1ObjectMeta()
                    .name("compute-resources")
                    .namespace(tenant.getK8sNamespace())
                )
                .spec(new V1ResourceQuotaSpec()
                    .hard(hard)
                );

            try {
                // Try to create
                api.createNamespacedResourceQuota(tenant.getK8sNamespace(), quota).execute();
                log.info("Created resource quota for namespace: {}", tenant.getK8sNamespace());
            } catch (ApiException e) {
                if (e.getCode() == 409) {
                    // Already exists, update it
                    api.replaceNamespacedResourceQuota("compute-resources", tenant.getK8sNamespace(), quota).execute();
                    log.info("Updated resource quota for namespace: {}", tenant.getK8sNamespace());
                } else {
                    throw e;
                }
            }

        } catch (ApiException e) {
            throw new RuntimeException("Failed to apply resource quotas: " + e.getMessage(), e);
        }
    }

    /**
     * Deploy network policies for tenant isolation
     */
    public void deployNetworkPolicies(Tenant tenant) {
        if (!kubernetesEnabled || k8sClient == null || !applyNetworkPolicies) {
            return;
        }

        // Network policies require networking.k8s.io/v1 API
        // This is a simplified implementation - production would use full NetworkPolicy spec
        log.info("Network policies would be applied for namespace: {}", tenant.getK8sNamespace());

        // In production, create NetworkPolicy to:
        // 1. Deny all cross-namespace traffic by default
        // 2. Allow traffic within same namespace
        // 3. Allow egress to external services (LLM APIs, etc.)
        // 4. Allow ingress from ingress controller
    }

    /**
     * Apply tier-based defaults to tenant
     */
    private void applyTierDefaults(Tenant tenant) {
        switch (tenant.getTier()) {
            case FREE:
                tenant.setMaxProjects(1);
                tenant.setMaxAgents(5);
                tenant.setMaxStorageMb(1024L); // 1GB
                break;
            case STANDARD:
                tenant.setMaxProjects(10);
                tenant.setMaxAgents(50);
                tenant.setMaxStorageMb(10240L); // 10GB
                break;
            case PREMIUM:
                tenant.setMaxProjects(50);
                tenant.setMaxAgents(200);
                tenant.setMaxStorageMb(51200L); // 50GB
                break;
            case ENTERPRISE:
                tenant.setMaxProjects(999);
                tenant.setMaxAgents(999);
                tenant.setMaxStorageMb(1048576L); // 1TB
                break;
        }
    }

    /**
     * Calculate Kubernetes resource limits based on tenant tier
     */
    private Map<String, io.kubernetes.client.custom.Quantity> calculateResourceLimits(Tenant tenant) {
        Map<String, io.kubernetes.client.custom.Quantity> limits = new HashMap<>();

        switch (tenant.getTier()) {
            case FREE:
                limits.put("requests.cpu", io.kubernetes.client.custom.Quantity.fromString("2"));
                limits.put("requests.memory", io.kubernetes.client.custom.Quantity.fromString("4Gi"));
                limits.put("limits.cpu", io.kubernetes.client.custom.Quantity.fromString("4"));
                limits.put("limits.memory", io.kubernetes.client.custom.Quantity.fromString("8Gi"));
                limits.put("pods", io.kubernetes.client.custom.Quantity.fromString("20"));
                break;
            case STANDARD:
                limits.put("requests.cpu", io.kubernetes.client.custom.Quantity.fromString("10"));
                limits.put("requests.memory", io.kubernetes.client.custom.Quantity.fromString("20Gi"));
                limits.put("limits.cpu", io.kubernetes.client.custom.Quantity.fromString("20"));
                limits.put("limits.memory", io.kubernetes.client.custom.Quantity.fromString("40Gi"));
                limits.put("pods", io.kubernetes.client.custom.Quantity.fromString("50"));
                break;
            case PREMIUM:
                limits.put("requests.cpu", io.kubernetes.client.custom.Quantity.fromString("50"));
                limits.put("requests.memory", io.kubernetes.client.custom.Quantity.fromString("100Gi"));
                limits.put("requests.nvidia.com/gpu", io.kubernetes.client.custom.Quantity.fromString("2"));
                limits.put("limits.cpu", io.kubernetes.client.custom.Quantity.fromString("100"));
                limits.put("limits.memory", io.kubernetes.client.custom.Quantity.fromString("200Gi"));
                limits.put("pods", io.kubernetes.client.custom.Quantity.fromString("200"));
                break;
            case ENTERPRISE:
                limits.put("requests.cpu", io.kubernetes.client.custom.Quantity.fromString("200"));
                limits.put("requests.memory", io.kubernetes.client.custom.Quantity.fromString("400Gi"));
                limits.put("requests.nvidia.com/gpu", io.kubernetes.client.custom.Quantity.fromString("8"));
                limits.put("limits.cpu", io.kubernetes.client.custom.Quantity.fromString("400"));
                limits.put("limits.memory", io.kubernetes.client.custom.Quantity.fromString("800Gi"));
                limits.put("pods", io.kubernetes.client.custom.Quantity.fromString("999"));
                break;
        }

        return limits;
    }

    /**
     * Request DTOs
     */
    public static class CreateTenantRequest {
        private String name;
        private String organizationId;
        private Tenant.TenantTier tier = Tenant.TenantTier.STANDARD;
        private String dataRegion;
        private Boolean requiresDataLocality = false;

        // Default constructor for Jackson
        public CreateTenantRequest() {}

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOrganizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
        public Tenant.TenantTier getTier() { return tier; }
        public void setTier(Tenant.TenantTier tier) { this.tier = tier; }
        public String getDataRegion() { return dataRegion; }
        public void setDataRegion(String dataRegion) { this.dataRegion = dataRegion; }
        public Boolean getRequiresDataLocality() { return requiresDataLocality; }
        public void setRequiresDataLocality(Boolean requiresDataLocality) {
            this.requiresDataLocality = requiresDataLocality;
        }
    }

    public static class CreateProjectRequest {
        private String name;
        private String projectKey;
        private String description;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

