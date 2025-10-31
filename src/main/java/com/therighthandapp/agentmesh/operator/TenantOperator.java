package com.therighthandapp.agentmesh.operator;

import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.TenantService;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Kubernetes Operator for AgentMesh Tenant resources.
 *
 * Watches for Tenant CRD changes and automatically:
 * - Creates Kubernetes namespaces
 * - Applies resource quotas
 * - Deploys network policies
 * - Syncs with AgentMesh database
 * - Handles tenant lifecycle
 */
@Component
@ConditionalOnProperty(name = "agentmesh.operator.enabled", havingValue = "true")
public class TenantOperator {
    private static final Logger log = LoggerFactory.getLogger(TenantOperator.class);

    @Autowired(required = false)
    private ApiClient apiClient;

    @Autowired
    private TenantService tenantService;

    @Value("${agentmesh.operator.reconcile-interval-seconds:30}")
    private int reconcileIntervalSeconds;

    private Controller controller;

    @PostConstruct
    public void init() {
        if (apiClient == null) {
            log.warn("Kubernetes ApiClient not available - operator disabled");
            return;
        }

        try {
            startOperator();
            log.info("AgentMesh Tenant Operator started successfully");
        } catch (Exception e) {
            log.error("Failed to start Tenant Operator", e);
        }
    }

    /**
     * Start the operator controller
     */
    private void startOperator() {
        // Create informer factory
        SharedInformerFactory informerFactory = new SharedInformerFactory(apiClient);

        // Create generic API for Tenant CRD
        GenericKubernetesApi<TenantResource, TenantResourceList> tenantApi =
            new GenericKubernetesApi<>(
                TenantResource.class,
                TenantResourceList.class,
                "agentmesh.io",
                "v1",
                "tenants",
                apiClient
            );

        // Create informer for Tenant resources
        SharedIndexInformer<TenantResource> tenantInformer =
            informerFactory.sharedIndexInformerFor(
                (params) -> tenantApi.list().getObject(),
                TenantResource.class,
                Duration.ofSeconds(reconcileIntervalSeconds).toMillis()
            );

        // Create reconciler
        TenantReconciler reconciler = new TenantReconciler(tenantApi, tenantService, apiClient);

        // Build controller
        controller = ControllerBuilder
            .defaultBuilder(informerFactory)
            .watch((workQueue) ->
                ControllerBuilder.controllerWatchBuilder(TenantResource.class, workQueue)
                    .withResyncPeriod(Duration.ofSeconds(reconcileIntervalSeconds))
                    .build())
            .withReconciler(reconciler)
            .withName("TenantController")
            .withWorkerCount(2)
            .build();

        // Start informer factory
        informerFactory.startAllRegisteredInformers();

        // Start controller in background
        new Thread(() -> {
            try {
                controller.run();
            } catch (Exception e) {
                log.error("Controller execution failed", e);
            }
        }).start();
    }

    /**
     * Reconciler for Tenant resources
     */
    public static class TenantReconciler implements Reconciler {
        private static final Logger log = LoggerFactory.getLogger(TenantReconciler.class);

        private final GenericKubernetesApi<TenantResource, TenantResourceList> api;
        private final TenantService tenantService;
        private final ApiClient apiClient;

        public TenantReconciler(GenericKubernetesApi<TenantResource, TenantResourceList> api,
                              TenantService tenantService,
                              ApiClient apiClient) {
            this.api = api;
            this.tenantService = tenantService;
            this.apiClient = apiClient;
        }

        @Override
        public Result reconcile(Request request) {
            log.info("Reconciling Tenant: {}", request.getName());

            try {
                // Get the Tenant resource
                var response = api.get(request.getName());
                if (response == null || response.getObject() == null) {
                    log.info("Tenant {} not found - may have been deleted", request.getName());
                    return new Result(false);
                }

                TenantResource tenantResource = response.getObject();
                TenantSpec spec = tenantResource.getSpec();

                // Check if tenant exists in database
                var existingTenant = tenantService.getTenantByOrganizationId(spec.getOrganizationId());

                Tenant tenant;
                if (existingTenant.isEmpty()) {
                    // Create new tenant
                    log.info("Creating new tenant: {}", spec.getOrganizationId());
                    tenant = createTenantFromCRD(spec);
                } else {
                    // Update existing tenant
                    log.info("Updating tenant: {}", spec.getOrganizationId());
                    tenant = existingTenant.get();
                    updateTenantFromCRD(tenant, spec);
                }

                // Ensure Kubernetes resources
                ensureNamespace(tenant);
                ensureResourceQuota(tenant);
                ensureNetworkPolicies(tenant);

                // Update status
                updateStatus(tenantResource, "Active", "Tenant provisioned successfully");

                log.info("Successfully reconciled tenant: {}", spec.getOrganizationId());
                return new Result(false);

            } catch (Exception e) {
                log.error("Failed to reconcile tenant: " + request.getName(), e);
                return new Result(true, Duration.ofSeconds(30));
            }
        }

        private Tenant createTenantFromCRD(TenantSpec spec) {
            TenantService.CreateTenantRequest request = new TenantService.CreateTenantRequest();
            request.setOrganizationId(spec.getOrganizationId());
            request.setName(spec.getName() != null ? spec.getName() : spec.getOrganizationId());
            request.setTier(parseTier(spec.getTier()));
            request.setDataRegion(spec.getDataRegion());
            request.setRequiresDataLocality(spec.getRequiresDataLocality() != null ?
                spec.getRequiresDataLocality() : false);

            return tenantService.createTenant(request);
        }

        private void updateTenantFromCRD(Tenant tenant, TenantSpec spec) {
            // Update tier if changed
            Tenant.TenantTier newTier = parseTier(spec.getTier());
            if (!tenant.getTier().equals(newTier)) {
                tenantService.updateTenantTier(tenant.getId(), newTier);
            }
        }

        private void ensureNamespace(Tenant tenant) {
            try {
                CoreV1Api coreApi = new CoreV1Api(apiClient);

                // Check if namespace exists
                try {
                    coreApi.readNamespace(tenant.getK8sNamespace()).execute();
                    log.debug("Namespace already exists: {}", tenant.getK8sNamespace());
                } catch (ApiException e) {
                    if (e.getCode() == 404) {
                        // Create namespace
                        tenantService.provisionKubernetesNamespace(tenant);
                    } else {
                        throw e;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to ensure namespace for tenant: " + tenant.getId(), e);
            }
        }

        private void ensureResourceQuota(Tenant tenant) {
            try {
                tenantService.applyResourceQuotas(tenant);
            } catch (Exception e) {
                log.error("Failed to ensure resource quota for tenant: " + tenant.getId(), e);
            }
        }

        private void ensureNetworkPolicies(Tenant tenant) {
            try {
                tenantService.deployNetworkPolicies(tenant);
            } catch (Exception e) {
                log.error("Failed to ensure network policies for tenant: " + tenant.getId(), e);
            }
        }

        private void updateStatus(TenantResource resource, String phase, String message) {
            try {
                TenantStatus status = new TenantStatus();
                status.setPhase(phase);
                status.setMessage(message);
                status.setNamespace(resource.getSpec().getOrganizationId());

                resource.setStatus(status);

                api.updateStatus(resource, (r) -> r);
                log.debug("Updated status for tenant: {}", resource.getMetadata().getName());
            } catch (Exception e) {
                log.error("Failed to update status", e);
            }
        }

        private Tenant.TenantTier parseTier(String tier) {
            if (tier == null) return Tenant.TenantTier.STANDARD;
            try {
                return Tenant.TenantTier.valueOf(tier.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tier: {}, defaulting to STANDARD", tier);
                return Tenant.TenantTier.STANDARD;
            }
        }
    }
}

