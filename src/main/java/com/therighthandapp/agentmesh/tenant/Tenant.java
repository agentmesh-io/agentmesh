package com.therighthandapp.agentmesh.tenant;

import com.therighthandapp.agentmesh.security.AesEncryptAttributeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tenant entity representing an organization in the multi-tenant system.
 * Each tenant has isolated resources, data, and configuration.
 *
 * <p>PII fields (name, organizationId, dataRegion) are encrypted at rest
 * using AES-256-GCM when PII_ENCRYPTION_KEY env var is set.</p>
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Convert(converter = AesEncryptAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Convert(converter = AesEncryptAttributeConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantTier tier = TenantTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    // Kubernetes namespace for this tenant
    @Column(nullable = false, unique = true)
    private String k8sNamespace;

    // Resource limits
    @Column(nullable = false)
    private Integer maxProjects = 10;

    @Column(nullable = false)
    private Integer maxAgents = 50;

    @Column(nullable = false)
    private Long maxStorageMb = 10240L; // 10GB default

    // Data sovereignty
    @Convert(converter = AesEncryptAttributeConverter.class)
    @Column
    private String dataRegion; // e.g., "us-east-1", "eu-west-1"

    @Column
    private Boolean requiresDataLocality = false;

    // Model specialization (LoRA adapters)
    @ElementCollection
    @CollectionTable(name = "tenant_lora_adapters",
                     joinColumns = @JoinColumn(name = "tenant_id"))
    @MapKeyColumn(name = "adapter_name")
    @Column(name = "adapter_path")
    private Map<String, String> loraAdapters = new HashMap<>();

    // Billing and metering
    @Column
    private Boolean outcomeBasedBilling = true;

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (k8sNamespace == null) {
            k8sNamespace = "agentmesh-" + organizationId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public TenantTier getTier() {
        return tier;
    }

    public void setTier(TenantTier tier) {
        this.tier = tier;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public String getK8sNamespace() {
        return k8sNamespace;
    }

    public void setK8sNamespace(String k8sNamespace) {
        this.k8sNamespace = k8sNamespace;
    }

    public Integer getMaxProjects() {
        return maxProjects;
    }

    public void setMaxProjects(Integer maxProjects) {
        this.maxProjects = maxProjects;
    }

    public Integer getMaxAgents() {
        return maxAgents;
    }

    public void setMaxAgents(Integer maxAgents) {
        this.maxAgents = maxAgents;
    }

    public Long getMaxStorageMb() {
        return maxStorageMb;
    }

    public void setMaxStorageMb(Long maxStorageMb) {
        this.maxStorageMb = maxStorageMb;
    }

    public String getDataRegion() {
        return dataRegion;
    }

    public void setDataRegion(String dataRegion) {
        this.dataRegion = dataRegion;
    }

    public Boolean getRequiresDataLocality() {
        return requiresDataLocality;
    }

    public void setRequiresDataLocality(Boolean requiresDataLocality) {
        this.requiresDataLocality = requiresDataLocality;
    }

    public Map<String, String> getLoraAdapters() {
        return loraAdapters;
    }

    public void setLoraAdapters(Map<String, String> loraAdapters) {
        this.loraAdapters = loraAdapters;
    }

    public Boolean getOutcomeBasedBilling() {
        return outcomeBasedBilling;
    }

    public void setOutcomeBasedBilling(Boolean outcomeBasedBilling) {
        this.outcomeBasedBilling = outcomeBasedBilling;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum TenantTier {
        FREE,
        STANDARD,
        PREMIUM,
        ENTERPRISE
    }

    public enum TenantStatus {
        ACTIVE,
        SUSPENDED,
        ARCHIVED
    }
}

