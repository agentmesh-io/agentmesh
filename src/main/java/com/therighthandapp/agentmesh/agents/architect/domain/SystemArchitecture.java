package com.therighthandapp.agentmesh.agents.architect.domain;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Domain Model: SystemArchitecture
 * 
 * Represents the comprehensive system architecture designed by the Architect Agent.
 * This is an immutable value object that encapsulates architectural decisions,
 * component designs, technology choices, and deployment strategies.
 * 
 * DDD: Rich domain model with business logic for validation and analysis.
 */
@Value
@Builder
@With
public class SystemArchitecture {
    
    String architectureId;
    String planId;
    String srsId;
    String projectTitle;
    LocalDateTime createdAt;
    
    ArchitectureStyle architectureStyle;
    List<Component> components;
    List<DataFlow> dataFlows;
    List<Integration> integrations;
    DeploymentArchitecture deployment;
    SecurityArchitecture security;
    ScalabilityStrategy scalability;
    
    Map<String, String> metadata;
    
    /**
     * Domain logic: Validate architecture completeness
     */
    public ArchitectureValidationResult validate() {
        List<String> errors = new java.util.ArrayList<>();
        
        if (architectureStyle == null) {
            errors.add("Architecture style not defined");
        }
        
        if (components == null || components.isEmpty()) {
            errors.add("No components defined");
        }
        
        if (deployment == null) {
            errors.add("Deployment architecture not defined");
        }
        
        if (security == null) {
            errors.add("Security architecture not defined");
        }
        
        // Validate each component
        if (components != null) {
            for (Component component : components) {
                ComponentValidationResult result = component.validate();
                if (!result.isValid()) {
                    errors.addAll(result.getErrors());
                }
            }
        }
        
        // Validate data flows reference valid components
        if (dataFlows != null && components != null) {
            List<String> componentNames = components.stream()
                    .map(Component::getName)
                    .toList();
            
            for (DataFlow flow : dataFlows) {
                if (!componentNames.contains(flow.getSource())) {
                    errors.add("Data flow references unknown source component: " + flow.getSource());
                }
                if (!componentNames.contains(flow.getTarget())) {
                    errors.add("Data flow references unknown target component: " + flow.getTarget());
                }
            }
        }
        
        return errors.isEmpty() ? 
                ArchitectureValidationResult.valid() : 
                ArchitectureValidationResult.invalid(errors);
    }
    
    /**
     * Domain logic: Get component by name
     */
    public Component getComponent(String name) {
        if (components == null) return null;
        return components.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Domain logic: Get all external integrations
     */
    public List<Integration> getExternalIntegrations() {
        if (integrations == null) return List.of();
        return integrations.stream()
                .filter(Integration::isExternal)
                .toList();
    }
    
    /**
     * Value Object: Component
     * Represents a major architectural component/service
     */
    @Value
    @Builder
    public static class Component {
        String name;
        String type; // "API", "Service", "Database", "Cache", "Queue", etc.
        String description;
        String responsibility;
        List<String> exposedAPIs;
        List<String> dependencies;
        TechnologyChoice technology;
        
        public ComponentValidationResult validate() {
            List<String> errors = new java.util.ArrayList<>();
            
            if (name == null || name.isBlank()) {
                errors.add("Component name is required");
            }
            if (type == null || type.isBlank()) {
                errors.add("Component type is required");
            }
            if (responsibility == null || responsibility.isBlank()) {
                errors.add("Component responsibility is required");
            }
            
            return errors.isEmpty() ? 
                    ComponentValidationResult.valid() : 
                    ComponentValidationResult.invalid(errors);
        }
    }
    
    /**
     * Value Object: Technology Choice
     */
    @Value
    @Builder
    public static class TechnologyChoice {
        String framework; // e.g., "Spring Boot", "Express.js", "FastAPI"
        String language;  // e.g., "Java 17", "TypeScript", "Python 3.11"
        String database;  // e.g., "PostgreSQL", "MongoDB", "Redis"
        List<String> libraries;
        String justification;
    }
    
    /**
     * Value Object: Data Flow
     * Represents communication between components
     */
    @Value
    @Builder
    public static class DataFlow {
        String source;
        String target;
        String protocol; // "REST", "gRPC", "Message Queue", "Database"
        String dataType;
        boolean synchronous;
        String description;
    }
    
    /**
     * Value Object: Integration
     * Represents integration with external systems
     */
    @Value
    @Builder
    public static class Integration {
        String name;
        String type; // "OAuth", "API", "Webhook", "Database"
        String provider;
        boolean isExternal;
        String purpose;
        List<String> requiredCredentials;
    }
    
    /**
     * Value Object: Architecture Style
     */
    @Value
    @Builder
    public static class ArchitectureStyle {
        String primaryStyle; // "Microservices", "Monolith", "Serverless", "Event-Driven"
        List<String> patterns; // "CQRS", "Event Sourcing", "Saga", etc.
        String justification;
    }
    
    /**
     * Value Object: Deployment Architecture
     */
    @Value
    @Builder
    public static class DeploymentArchitecture {
        String platform; // "Kubernetes", "AWS ECS", "Docker Compose", "VM"
        List<DeploymentComponent> components;
        LoadBalancingStrategy loadBalancing;
        List<String> environmentVariables;
        
        @Value
        @Builder
        public static class DeploymentComponent {
            String componentName;
            int replicas;
            String resourceRequirements; // CPU/Memory
            List<String> exposedPorts;
            HealthCheck healthCheck;
        }
        
        @Value
        @Builder
        public static class HealthCheck {
            String endpoint;
            int intervalSeconds;
            int timeoutSeconds;
        }
    }
    
    /**
     * Value Object: Load Balancing Strategy
     */
    @Value
    @Builder
    public static class LoadBalancingStrategy {
        String type; // "Round Robin", "Least Connections", "IP Hash"
        boolean stickySession;
        String healthCheckPath;
    }
    
    /**
     * Value Object: Security Architecture
     */
    @Value
    @Builder
    public static class SecurityArchitecture {
        AuthenticationStrategy authentication;
        AuthorizationStrategy authorization;
        List<String> encryptionPoints;
        List<String> securityHeaders;
        RateLimitingPolicy rateLimiting;
        
        @Value
        @Builder
        public static class AuthenticationStrategy {
            String method; // "JWT", "OAuth2", "Session", "API Key"
            String tokenExpiration;
            boolean refreshTokenEnabled;
        }
        
        @Value
        @Builder
        public static class AuthorizationStrategy {
            String model; // "RBAC", "ABAC", "ACL"
            List<Role> roles;
        }
        
        @Value
        @Builder
        public static class Role {
            String name;
            List<String> permissions;
        }
        
        @Value
        @Builder
        public static class RateLimitingPolicy {
            int requestsPerMinute;
            String strategy; // "Token Bucket", "Leaky Bucket", "Fixed Window"
            List<String> exemptedEndpoints;
        }
    }
    
    /**
     * Value Object: Scalability Strategy
     */
    @Value
    @Builder
    public static class ScalabilityStrategy {
        HorizontalScaling horizontal;
        VerticalScaling vertical;
        CachingStrategy caching;
        DatabaseScaling database;
        
        @Value
        @Builder
        public static class HorizontalScaling {
            boolean enabled;
            int minInstances;
            int maxInstances;
            List<ScalingMetric> triggers;
        }
        
        @Value
        @Builder
        public static class ScalingMetric {
            String metric; // "CPU", "Memory", "RequestRate", "QueueDepth"
            int threshold;
        }
        
        @Value
        @Builder
        public static class VerticalScaling {
            String cpuAllocation;
            String memoryAllocation;
            boolean autoScale;
        }
        
        @Value
        @Builder
        public static class CachingStrategy {
            List<String> layers; // "CDN", "Application", "Database"
            String cacheStore; // "Redis", "Memcached", "In-Memory"
            int ttlSeconds;
        }
        
        @Value
        @Builder
        public static class DatabaseScaling {
            boolean readReplicas;
            int replicaCount;
            boolean sharding;
            String shardingKey;
        }
    }
    
    /**
     * Validation results
     */
    @Value
    @Builder
    public static class ArchitectureValidationResult {
        boolean valid;
        List<String> errors;
        
        public static ArchitectureValidationResult valid() {
            return ArchitectureValidationResult.builder()
                    .valid(true)
                    .errors(List.of())
                    .build();
        }
        
        public static ArchitectureValidationResult invalid(List<String> errors) {
            return ArchitectureValidationResult.builder()
                    .valid(false)
                    .errors(errors)
                    .build();
        }
    }
    
    @Value
    @Builder
    public static class ComponentValidationResult {
        boolean valid;
        List<String> errors;
        
        public static ComponentValidationResult valid() {
            return ComponentValidationResult.builder()
                    .valid(true)
                    .errors(List.of())
                    .build();
        }
        
        public static ComponentValidationResult invalid(List<String> errors) {
            return ComponentValidationResult.builder()
                    .valid(false)
                    .errors(errors)
                    .build();
        }
    }
}
