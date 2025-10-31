package com.therighthandapp.agentmesh.operator;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;

import java.util.List;

/**
 * List of Tenant resources
 */
public class TenantResourceList implements KubernetesListObject {
    private String apiVersion = "agentmesh.io/v1";
    private String kind = "TenantList";
    private V1ListMeta metadata;
    private List<TenantResource> items;

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
    public V1ListMeta getMetadata() {
        return metadata;
    }

    public void setMetadata(V1ListMeta metadata) {
        this.metadata = metadata;
    }

    public List<TenantResource> getItems() {
        return items;
    }

    public void setItems(List<TenantResource> items) {
        this.items = items;
    }
}

