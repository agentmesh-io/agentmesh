package com.therighthandapp.agentmesh.operator;

/**
 * Spec for Tenant CRD
 */
public class TenantSpec {
    private String organizationId;
    private String name;
    private String tier;
    private String dataRegion;
    private Boolean requiresDataLocality;
    private Integer maxProjects;
    private Integer maxAgents;
    private Long maxStorageMb;
    private ResourceQuotaSpec resourceQuota;

    // Getters and setters
    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
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

    public ResourceQuotaSpec getResourceQuota() {
        return resourceQuota;
    }

    public void setResourceQuota(ResourceQuotaSpec resourceQuota) {
        this.resourceQuota = resourceQuota;
    }

    public static class ResourceQuotaSpec {
        private String cpuRequest;
        private String cpuLimit;
        private String memoryRequest;
        private String memoryLimit;
        private String gpuRequest;
        private String pods;

        // Getters and setters
        public String getCpuRequest() { return cpuRequest; }
        public void setCpuRequest(String cpuRequest) { this.cpuRequest = cpuRequest; }

        public String getCpuLimit() { return cpuLimit; }
        public void setCpuLimit(String cpuLimit) { this.cpuLimit = cpuLimit; }

        public String getMemoryRequest() { return memoryRequest; }
        public void setMemoryRequest(String memoryRequest) { this.memoryRequest = memoryRequest; }

        public String getMemoryLimit() { return memoryLimit; }
        public void setMemoryLimit(String memoryLimit) { this.memoryLimit = memoryLimit; }

        public String getGpuRequest() { return gpuRequest; }
        public void setGpuRequest(String gpuRequest) { this.gpuRequest = gpuRequest; }

        public String getPods() { return pods; }
        public void setPods(String pods) { this.pods = pods; }
    }
}

