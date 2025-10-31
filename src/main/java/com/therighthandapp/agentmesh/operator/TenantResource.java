package com.therighthandapp.agentmesh.operator;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

/**
 * Kubernetes Custom Resource for AgentMesh Tenant
 */
public class TenantResource implements KubernetesObject {
    private String apiVersion = "agentmesh.io/v1";
    private String kind = "Tenant";
    private V1ObjectMeta metadata;
    private TenantSpec spec;
    private TenantStatus status;

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @Override
    public V1ObjectMeta getMetadata() {
        return metadata;
    }

    public void setMetadata(V1ObjectMeta metadata) {
        this.metadata = metadata;
    }

    public TenantSpec getSpec() {
        return spec;
    }

    public void setSpec(TenantSpec spec) {
        this.spec = spec;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }
}

