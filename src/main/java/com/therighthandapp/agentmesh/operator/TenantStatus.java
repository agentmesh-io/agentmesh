package com.therighthandapp.agentmesh.operator;

import java.util.List;

/**
 * Status for Tenant CRD
 */
public class TenantStatus {
    private String phase;
    private String namespace;
    private String message;
    private List<Condition> conditions;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public static class Condition {
        private String type;
        private String status;
        private String reason;
        private String message;
        private String lastTransitionTime;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getLastTransitionTime() { return lastTransitionTime; }
        public void setLastTransitionTime(String lastTransitionTime) { this.lastTransitionTime = lastTransitionTime; }
    }
}

