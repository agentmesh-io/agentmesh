package com.therighthandapp.agentmesh.agents.planner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain Service: Plan Parser
 * 
 * Responsible for parsing LLM JSON responses into ExecutionPlan domain objects.
 * This is a domain service because it contains complex business logic for
 * interpreting and validating LLM outputs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanParser {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Parse LLM JSON response into ExecutionPlan
     * 
     * @param llmResponse The raw JSON response from LLM
     * @param srsData The original SRS for context
     * @return Parsed and validated execution plan
     * @throws PlanParsingException if parsing fails
     */
    public ExecutionPlan parse(String llmResponse, SrsHandoffDto srsData) {
        try {
            log.debug("Parsing LLM response of length: {}", llmResponse.length());
            
            // Extract JSON from markdown if wrapped in code blocks
            String cleanedJson = extractJson(llmResponse);
            
            // Parse JSON
            JsonNode root = objectMapper.readTree(cleanedJson);
            
            // Build execution plan
            ExecutionPlan.ExecutionPlanBuilder planBuilder = ExecutionPlan.builder()
                .srsId(srsData.getIdeaId().toString())
                .projectTitle(srsData.getIdeaTitle())
                .generatedAt(java.time.LocalDateTime.now());
            
            // Parse modules
            if (root.has("modules")) {
                List<ExecutionPlan.Module> modules = parseModules(root.get("modules"));
                planBuilder.modules(modules);
            }
            
            // Parse file structure
            if (root.has("fileStructure")) {
                ExecutionPlan.FileStructure fileStructure = parseFileStructure(root.get("fileStructure"));
                planBuilder.fileStructure(fileStructure);
            }
            
            // Parse testing strategy
            if (root.has("testingStrategy")) {
                ExecutionPlan.TestingStrategy testingStrategy = parseTestingStrategy(root.get("testingStrategy"));
                planBuilder.testingStrategy(testingStrategy);
            }
            
            // Parse tech stack
            if (root.has("techStack")) {
                ExecutionPlan.TechStack techStack = parseTechStack(root.get("techStack"));
                planBuilder.techStack(techStack);
            }
            
            // Parse effort estimate
            if (root.has("effortEstimate")) {
                ExecutionPlan.EffortEstimate effortEstimate = parseEffortEstimate(root.get("effortEstimate"));
                planBuilder.effortEstimate(effortEstimate);
            }
            
            ExecutionPlan plan = planBuilder.build();
            
            // Validate
            ExecutionPlan.PlanValidationResult validation = plan.validate();
            if (!validation.isValid()) {
                log.warn("Plan validation failed: {}", validation.getErrors());
                throw new PlanParsingException("Plan validation failed: " + validation.getErrors());
            }
            
            log.info("Successfully parsed execution plan with {} modules", plan.getModules().size());
            return plan;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response as JSON", e);
            throw new PlanParsingException("Invalid JSON in LLM response", e);
        }
    }
    
    /**
     * Extract JSON from markdown code blocks
     */
    private String extractJson(String llmResponse) {
        // Check if wrapped in markdown code block
        if (llmResponse.contains("```json")) {
            int start = llmResponse.indexOf("```json") + 7;
            int end = llmResponse.indexOf("```", start);
            if (end > start) {
                return llmResponse.substring(start, end).trim();
            }
        } else if (llmResponse.contains("```")) {
            int start = llmResponse.indexOf("```") + 3;
            int end = llmResponse.indexOf("```", start);
            if (end > start) {
                return llmResponse.substring(start, end).trim();
            }
        }
        return llmResponse.trim();
    }
    
    /**
     * Parse modules array
     */
    private List<ExecutionPlan.Module> parseModules(JsonNode modulesNode) {
        List<ExecutionPlan.Module> modules = new ArrayList<>();
        
        for (JsonNode moduleNode : modulesNode) {
            ExecutionPlan.Module module = ExecutionPlan.Module.builder()
                .name(moduleNode.get("name").asText())
                .description(moduleNode.has("description") ? moduleNode.get("description").asText() : "")
                .priority(parsePriority(moduleNode.has("priority") ? moduleNode.get("priority").asText() : "MEDIUM"))
                .techStack(parseStringArray(moduleNode.get("techStack")))
                .files(parseFiles(moduleNode.get("files")))
                .dependencies(moduleNode.has("dependencies") ? parseStringArray(moduleNode.get("dependencies")) : List.of())
                .configuration(moduleNode.has("configuration") ? parseStringMap(moduleNode.get("configuration")) : Map.of())
                .build();
            
            modules.add(module);
        }
        
        return modules;
    }
    
    /**
     * Parse file definitions
     */
    private List<ExecutionPlan.FileDefinition> parseFiles(JsonNode filesNode) {
        List<ExecutionPlan.FileDefinition> files = new ArrayList<>();
        
        for (JsonNode fileNode : filesNode) {
            ExecutionPlan.FileDefinition file = ExecutionPlan.FileDefinition.builder()
                .path(fileNode.get("path").asText())
                .purpose(fileNode.has("purpose") ? fileNode.get("purpose").asText() : "")
                .type(parseFileType(fileNode.has("type") ? fileNode.get("type").asText() : "SOURCE_CODE"))
                .dependencies(fileNode.has("dependencies") ? parseStringArray(fileNode.get("dependencies")) : List.of())
                .requirements(fileNode.has("requirements") ? parseStringArray(fileNode.get("requirements")) : List.of())
                .build();
            
            files.add(file);
        }
        
        return files;
    }
    
    /**
     * Parse file structure
     */
    private ExecutionPlan.FileStructure parseFileStructure(JsonNode node) {
        return ExecutionPlan.FileStructure.builder()
            .rootDirectory(node.has("rootDirectory") ? node.get("rootDirectory").asText() : "project-root")
            .directories(node.has("directories") ? parseDirectories(node.get("directories")) : Map.of())
            .build();
    }
    
    /**
     * Parse directories recursively
     */
    private Map<String, ExecutionPlan.FileStructure.DirectoryNode> parseDirectories(JsonNode node) {
        Map<String, ExecutionPlan.FileStructure.DirectoryNode> directories = new HashMap<>();
        
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode dirNode = entry.getValue();
            
            ExecutionPlan.FileStructure.DirectoryNode dirNodeObj = ExecutionPlan.FileStructure.DirectoryNode.builder()
                .name(entry.getKey())
                .purpose(dirNode.has("purpose") ? dirNode.get("purpose").asText() : "")
                .files(dirNode.has("files") ? parseStringArray(dirNode.get("files")) : List.of())
                .subdirectories(dirNode.has("subdirectories") ? parseDirectories(dirNode.get("subdirectories")) : Map.of())
                .build();
            
            directories.put(entry.getKey(), dirNodeObj);
        }
        
        return directories;
    }
    
    /**
     * Parse testing strategy
     */
    private ExecutionPlan.TestingStrategy parseTestingStrategy(JsonNode node) {
        List<ExecutionPlan.TestingStrategy.TestCategory> categories = new ArrayList<>();
        
        if (node.has("testCategories")) {
            for (JsonNode catNode : node.get("testCategories")) {
                ExecutionPlan.TestingStrategy.TestCategory category = ExecutionPlan.TestingStrategy.TestCategory.builder()
                    .name(catNode.get("name").asText())
                    .description(catNode.has("description") ? catNode.get("description").asText() : "")
                    .estimatedTestCount(catNode.has("estimatedTestCount") ? catNode.get("estimatedTestCount").asInt() : 0)
                    .build();
                categories.add(category);
            }
        }
        
        return ExecutionPlan.TestingStrategy.builder()
            .targetCoveragePercent(node.has("targetCoveragePercent") ? node.get("targetCoveragePercent").asInt() : 80)
            .testingFrameworks(node.has("testingFrameworks") ? parseStringArray(node.get("testingFrameworks")) : List.of())
            .testCategories(categories)
            .criticalPaths(node.has("criticalPaths") ? parseStringArray(node.get("criticalPaths")) : List.of())
            .build();
    }
    
    /**
     * Parse tech stack
     */
    private ExecutionPlan.TechStack parseTechStack(JsonNode node) {
        return ExecutionPlan.TechStack.builder()
            .primaryLanguages(node.has("primaryLanguages") ? parseStringArray(node.get("primaryLanguages")) : List.of())
            .frameworks(node.has("frameworks") ? parseStringArray(node.get("frameworks")) : List.of())
            .libraries(node.has("libraries") ? parseStringArray(node.get("libraries")) : List.of())
            .databases(node.has("databases") ? parseStringArray(node.get("databases")) : List.of())
            .infrastructure(node.has("infrastructure") ? parseStringArray(node.get("infrastructure")) : List.of())
            .architecturePattern(node.has("architecturePattern") ? node.get("architecturePattern").asText() : "Layered")
            .build();
    }
    
    /**
     * Parse effort estimate
     */
    private ExecutionPlan.EffortEstimate parseEffortEstimate(JsonNode node) {
        Map<String, Integer> hoursByModule = new HashMap<>();
        if (node.has("hoursByModule")) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.get("hoursByModule").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                hoursByModule.put(entry.getKey(), entry.getValue().asInt());
            }
        }
        
        List<ExecutionPlan.EffortEstimate.DevelopmentPhase> phases = new ArrayList<>();
        if (node.has("phases")) {
            for (JsonNode phaseNode : node.get("phases")) {
                ExecutionPlan.EffortEstimate.DevelopmentPhase phase = ExecutionPlan.EffortEstimate.DevelopmentPhase.builder()
                    .name(phaseNode.get("name").asText())
                    .durationDays(phaseNode.has("durationDays") ? phaseNode.get("durationDays").asInt() : 0)
                    .tasks(phaseNode.has("tasks") ? parseStringArray(phaseNode.get("tasks")) : List.of())
                    .build();
                phases.add(phase);
            }
        }
        
        return ExecutionPlan.EffortEstimate.builder()
            .totalHours(node.has("totalHours") ? node.get("totalHours").asInt() : 0)
            .hoursByModule(hoursByModule)
            .estimatedLinesOfCode(node.has("estimatedLinesOfCode") ? node.get("estimatedLinesOfCode").asInt() : 0)
            .phases(phases.toArray(new ExecutionPlan.EffortEstimate.DevelopmentPhase[0]))
            .build();
    }
    
    // Utility methods
    
    private List<String> parseStringArray(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return result;
    }
    
    private Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> result = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }
    
    private ExecutionPlan.Priority parsePriority(String priority) {
        try {
            return ExecutionPlan.Priority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutionPlan.Priority.MEDIUM;
        }
    }
    
    private ExecutionPlan.FileDefinition.FileType parseFileType(String type) {
        try {
            return ExecutionPlan.FileDefinition.FileType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutionPlan.FileDefinition.FileType.SOURCE_CODE;
        }
    }
    
    /**
     * Exception for plan parsing errors
     */
    public static class PlanParsingException extends RuntimeException {
        public PlanParsingException(String message) {
            super(message);
        }
        
        public PlanParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
