package com.therighthandapp.agentmesh.agents.architect.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Application Service: Architecture Parser
 * 
 * Parses LLM response JSON into SystemArchitecture domain model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchitectureParser {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Parse LLM JSON response into domain model
     */
    public SystemArchitecture parse(String llmResponse, ExecutionPlan plan) throws ArchitectureParsingException {
        try {
            // Extract JSON if wrapped in markdown
            String cleanJson = extractJson(llmResponse);
            
            JsonNode root = objectMapper.readTree(cleanJson);
            
            SystemArchitecture.SystemArchitectureBuilder builder = SystemArchitecture.builder()
                    .planId(plan.getPlanId())
                    .srsId(plan.getSrsId())
                    .projectTitle(plan.getProjectTitle());
            
            // Parse architecture style
            if (root.has("architectureStyle")) {
                builder.architectureStyle(parseArchitectureStyle(root.get("architectureStyle")));
            }
            
            // Parse components
            if (root.has("components")) {
                builder.components(parseComponents(root.get("components")));
            }
            
            // Parse data flows
            if (root.has("dataFlows")) {
                builder.dataFlows(parseDataFlows(root.get("dataFlows")));
            }
            
            // Parse integrations
            if (root.has("integrations")) {
                builder.integrations(parseIntegrations(root.get("integrations")));
            }
            
            // Parse deployment
            if (root.has("deployment")) {
                builder.deployment(parseDeployment(root.get("deployment")));
            }
            
            // Parse security
            if (root.has("security")) {
                builder.security(parseSecurity(root.get("security")));
            }
            
            // Parse scalability
            if (root.has("scalability")) {
                builder.scalability(parseScalability(root.get("scalability")));
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Failed to parse architecture from LLM response", e);
            throw new ArchitectureParsingException("Failed to parse architecture", e);
        }
    }
    
    private String extractJson(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
    
    private SystemArchitecture.ArchitectureStyle parseArchitectureStyle(JsonNode node) {
        return SystemArchitecture.ArchitectureStyle.builder()
                .primaryStyle(node.path("primaryStyle").asText())
                .patterns(jsonArrayToList(node.path("patterns")))
                .justification(node.path("justification").asText())
                .build();
    }
    
    private List<SystemArchitecture.Component> parseComponents(JsonNode arrayNode) {
        List<SystemArchitecture.Component> components = new ArrayList<>();
        
        for (JsonNode componentNode : arrayNode) {
            SystemArchitecture.Component component = SystemArchitecture.Component.builder()
                    .name(componentNode.path("name").asText())
                    .type(componentNode.path("type").asText())
                    .description(componentNode.path("description").asText())
                    .responsibility(componentNode.path("responsibility").asText())
                    .exposedAPIs(jsonArrayToList(componentNode.path("exposedAPIs")))
                    .dependencies(jsonArrayToList(componentNode.path("dependencies")))
                    .technology(parseTechnology(componentNode.path("technology")))
                    .build();
            components.add(component);
        }
        
        return components;
    }
    
    private SystemArchitecture.TechnologyChoice parseTechnology(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.TechnologyChoice.builder()
                .framework(node.path("framework").asText())
                .language(node.path("language").asText())
                .database(node.path("database").asText(null))
                .libraries(jsonArrayToList(node.path("libraries")))
                .justification(node.path("justification").asText())
                .build();
    }
    
    private List<SystemArchitecture.DataFlow> parseDataFlows(JsonNode arrayNode) {
        List<SystemArchitecture.DataFlow> flows = new ArrayList<>();
        
        for (JsonNode flowNode : arrayNode) {
            SystemArchitecture.DataFlow flow = SystemArchitecture.DataFlow.builder()
                    .source(flowNode.path("source").asText())
                    .target(flowNode.path("target").asText())
                    .protocol(flowNode.path("protocol").asText())
                    .dataType(flowNode.path("dataType").asText())
                    .synchronous(flowNode.path("synchronous").asBoolean(true))
                    .description(flowNode.path("description").asText())
                    .build();
            flows.add(flow);
        }
        
        return flows;
    }
    
    private List<SystemArchitecture.Integration> parseIntegrations(JsonNode arrayNode) {
        List<SystemArchitecture.Integration> integrations = new ArrayList<>();
        
        for (JsonNode intNode : arrayNode) {
            SystemArchitecture.Integration integration = SystemArchitecture.Integration.builder()
                    .name(intNode.path("name").asText())
                    .type(intNode.path("type").asText())
                    .provider(intNode.path("provider").asText())
                    .isExternal(intNode.path("isExternal").asBoolean(true))
                    .purpose(intNode.path("purpose").asText())
                    .requiredCredentials(jsonArrayToList(intNode.path("requiredCredentials")))
                    .build();
            integrations.add(integration);
        }
        
        return integrations;
    }
    
    private SystemArchitecture.DeploymentArchitecture parseDeployment(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.DeploymentArchitecture.builder()
                .platform(node.path("platform").asText())
                .components(parseDeploymentComponents(node.path("components")))
                .loadBalancing(parseLoadBalancing(node.path("loadBalancing")))
                .environmentVariables(jsonArrayToList(node.path("environmentVariables")))
                .build();
    }
    
    private List<SystemArchitecture.DeploymentArchitecture.DeploymentComponent> parseDeploymentComponents(JsonNode arrayNode) {
        List<SystemArchitecture.DeploymentArchitecture.DeploymentComponent> components = new ArrayList<>();
        
        for (JsonNode compNode : arrayNode) {
            SystemArchitecture.DeploymentArchitecture.DeploymentComponent component = 
                    SystemArchitecture.DeploymentArchitecture.DeploymentComponent.builder()
                    .componentName(compNode.path("componentName").asText())
                    .replicas(compNode.path("replicas").asInt(1))
                    .resourceRequirements(compNode.path("resourceRequirements").asText())
                    .exposedPorts(jsonArrayToList(compNode.path("exposedPorts")))
                    .healthCheck(parseHealthCheck(compNode.path("healthCheck")))
                    .build();
            components.add(component);
        }
        
        return components;
    }
    
    private SystemArchitecture.DeploymentArchitecture.HealthCheck parseHealthCheck(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.DeploymentArchitecture.HealthCheck.builder()
                .endpoint(node.path("endpoint").asText())
                .intervalSeconds(node.path("intervalSeconds").asInt(30))
                .timeoutSeconds(node.path("timeoutSeconds").asInt(5))
                .build();
    }
    
    private SystemArchitecture.LoadBalancingStrategy parseLoadBalancing(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.LoadBalancingStrategy.builder()
                .type(node.path("type").asText())
                .stickySession(node.path("stickySession").asBoolean(false))
                .healthCheckPath(node.path("healthCheckPath").asText())
                .build();
    }
    
    private SystemArchitecture.SecurityArchitecture parseSecurity(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.SecurityArchitecture.builder()
                .authentication(parseAuthentication(node.path("authentication")))
                .authorization(parseAuthorization(node.path("authorization")))
                .encryptionPoints(jsonArrayToList(node.path("encryptionPoints")))
                .securityHeaders(jsonArrayToList(node.path("securityHeaders")))
                .rateLimiting(parseRateLimiting(node.path("rateLimiting")))
                .build();
    }
    
    private SystemArchitecture.SecurityArchitecture.AuthenticationStrategy parseAuthentication(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.SecurityArchitecture.AuthenticationStrategy.builder()
                .method(node.path("method").asText())
                .tokenExpiration(node.path("tokenExpiration").asText())
                .refreshTokenEnabled(node.path("refreshTokenEnabled").asBoolean(true))
                .build();
    }
    
    private SystemArchitecture.SecurityArchitecture.AuthorizationStrategy parseAuthorization(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.SecurityArchitecture.AuthorizationStrategy.builder()
                .model(node.path("model").asText())
                .roles(parseRoles(node.path("roles")))
                .build();
    }
    
    private List<SystemArchitecture.SecurityArchitecture.Role> parseRoles(JsonNode arrayNode) {
        List<SystemArchitecture.SecurityArchitecture.Role> roles = new ArrayList<>();
        
        for (JsonNode roleNode : arrayNode) {
            SystemArchitecture.SecurityArchitecture.Role role = 
                    SystemArchitecture.SecurityArchitecture.Role.builder()
                    .name(roleNode.path("name").asText())
                    .permissions(jsonArrayToList(roleNode.path("permissions")))
                    .build();
            roles.add(role);
        }
        
        return roles;
    }
    
    private SystemArchitecture.SecurityArchitecture.RateLimitingPolicy parseRateLimiting(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.SecurityArchitecture.RateLimitingPolicy.builder()
                .requestsPerMinute(node.path("requestsPerMinute").asInt(100))
                .strategy(node.path("strategy").asText())
                .exemptedEndpoints(jsonArrayToList(node.path("exemptedEndpoints")))
                .build();
    }
    
    private SystemArchitecture.ScalabilityStrategy parseScalability(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.ScalabilityStrategy.builder()
                .horizontal(parseHorizontalScaling(node.path("horizontal")))
                .vertical(parseVerticalScaling(node.path("vertical")))
                .caching(parseCaching(node.path("caching")))
                .database(parseDatabaseScaling(node.path("database")))
                .build();
    }
    
    private SystemArchitecture.ScalabilityStrategy.HorizontalScaling parseHorizontalScaling(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.ScalabilityStrategy.HorizontalScaling.builder()
                .enabled(node.path("enabled").asBoolean(true))
                .minInstances(node.path("minInstances").asInt(2))
                .maxInstances(node.path("maxInstances").asInt(10))
                .triggers(parseScalingMetrics(node.path("triggers")))
                .build();
    }
    
    private List<SystemArchitecture.ScalabilityStrategy.ScalingMetric> parseScalingMetrics(JsonNode arrayNode) {
        List<SystemArchitecture.ScalabilityStrategy.ScalingMetric> metrics = new ArrayList<>();
        
        for (JsonNode metricNode : arrayNode) {
            SystemArchitecture.ScalabilityStrategy.ScalingMetric metric = 
                    SystemArchitecture.ScalabilityStrategy.ScalingMetric.builder()
                    .metric(metricNode.path("metric").asText())
                    .threshold(metricNode.path("threshold").asInt())
                    .build();
            metrics.add(metric);
        }
        
        return metrics;
    }
    
    private SystemArchitecture.ScalabilityStrategy.VerticalScaling parseVerticalScaling(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.ScalabilityStrategy.VerticalScaling.builder()
                .cpuAllocation(node.path("cpuAllocation").asText())
                .memoryAllocation(node.path("memoryAllocation").asText())
                .autoScale(node.path("autoScale").asBoolean(false))
                .build();
    }
    
    private SystemArchitecture.ScalabilityStrategy.CachingStrategy parseCaching(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.ScalabilityStrategy.CachingStrategy.builder()
                .layers(jsonArrayToList(node.path("layers")))
                .cacheStore(node.path("cacheStore").asText())
                .ttlSeconds(node.path("ttlSeconds").asInt(3600))
                .build();
    }
    
    private SystemArchitecture.ScalabilityStrategy.DatabaseScaling parseDatabaseScaling(JsonNode node) {
        if (node.isMissingNode()) return null;
        
        return SystemArchitecture.ScalabilityStrategy.DatabaseScaling.builder()
                .readReplicas(node.path("readReplicas").asBoolean(false))
                .replicaCount(node.path("replicaCount").asInt(0))
                .sharding(node.path("sharding").asBoolean(false))
                .shardingKey(node.path("shardingKey").asText(null))
                .build();
    }
    
    private List<String> jsonArrayToList(JsonNode arrayNode) {
        if (arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return new ArrayList<>();
        }
        
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
    
    /**
     * Exception for parsing failures
     */
    public static class ArchitectureParsingException extends Exception {
        public ArchitectureParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
