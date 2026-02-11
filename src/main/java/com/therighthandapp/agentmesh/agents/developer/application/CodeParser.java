package com.therighthandapp.agentmesh.agents.developer.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Application Service: Parse LLM JSON response into CodeArtifact domain model
 */
@Slf4j
public class CodeParser {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Parse LLM response into CodeArtifact
     */
    public CodeArtifact parse(String llmResponse, String planId, String architectureId, String projectTitle) {
        try {
            // Extract JSON from markdown code blocks if present
            String jsonContent = extractJson(llmResponse);
            
            JsonNode root = objectMapper.readTree(jsonContent);
            
            // Parse source files
            List<CodeArtifact.SourceFile> sourceFiles = parseSourceFiles(root.get("sourceFiles"));
            
            // Parse dependencies
            List<CodeArtifact.Dependency> dependencies = parseDependencies(root.get("dependencies"));
            
            // Parse build configuration
            CodeArtifact.BuildConfiguration buildConfig = parseBuildConfig(root.get("buildConfig"));
            
            // Calculate quality metrics
            CodeArtifact.QualityMetrics qualityMetrics = calculateQualityMetrics(sourceFiles);
            
            return CodeArtifact.builder()
                .planId(planId)
                .architectureId(architectureId)
                .projectTitle(projectTitle)
                .sourceFiles(sourceFiles)
                .dependencies(dependencies)
                .buildConfig(buildConfig)
                .qualityMetrics(qualityMetrics)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to parse code response", e);
            throw new CodeParsingException("Failed to parse LLM code response: " + e.getMessage(), e);
        }
    }
    
    private String extractJson(String content) {
        // Remove markdown code blocks if present
        String cleaned = content.trim();
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
    
    private List<CodeArtifact.SourceFile> parseSourceFiles(JsonNode filesNode) {
        List<CodeArtifact.SourceFile> files = new ArrayList<>();
        
        if (filesNode != null && filesNode.isArray()) {
            for (JsonNode fileNode : filesNode) {
                String content = fileNode.get("content").asText();
                List<String> imports = new ArrayList<>();
                
                JsonNode importsNode = fileNode.get("imports");
                if (importsNode != null && importsNode.isArray()) {
                    imports = StreamSupport.stream(importsNode.spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList());
                }
                
                CodeArtifact.SourceFile file = CodeArtifact.SourceFile.builder()
                    .filePath(fileNode.get("filePath").asText())
                    .fileName(fileNode.get("fileName").asText())
                    .language(fileNode.get("language").asText())
                    .content(content)
                    .imports(imports)
                    .lineCount(content.split("\n").length)
                    .build();
                    
                files.add(file);
            }
        }
        
        return files;
    }
    
    private List<CodeArtifact.Dependency> parseDependencies(JsonNode depsNode) {
        List<CodeArtifact.Dependency> dependencies = new ArrayList<>();
        
        if (depsNode != null && depsNode.isArray()) {
            for (JsonNode depNode : depsNode) {
                CodeArtifact.Dependency dep = CodeArtifact.Dependency.builder()
                    .groupId(depNode.get("groupId").asText())
                    .artifactId(depNode.get("artifactId").asText())
                    .version(depNode.get("version").asText())
                    .scope(depNode.has("scope") ? depNode.get("scope").asText() : "compile")
                    .build();
                    
                dependencies.add(dep);
            }
        }
        
        return dependencies;
    }
    
    private CodeArtifact.BuildConfiguration parseBuildConfig(JsonNode configNode) {
        if (configNode == null) {
            return CodeArtifact.BuildConfiguration.builder()
                .buildTool("maven")
                .javaVersion("21")
                .targetRuntime("jar")
                .buildPlugins(List.of())
                .properties(Map.of())
                .build();
        }
        
        List<String> plugins = new ArrayList<>();
        JsonNode pluginsNode = configNode.get("buildPlugins");
        if (pluginsNode != null && pluginsNode.isArray()) {
            plugins = StreamSupport.stream(pluginsNode.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
        }
        
        Map<String, String> properties = new HashMap<>();
        JsonNode propsNode = configNode.get("properties");
        if (propsNode != null && propsNode.isObject()) {
            propsNode.fields().forEachRemaining(entry -> 
                properties.put(entry.getKey(), entry.getValue().asText())
            );
        }
        
        return CodeArtifact.BuildConfiguration.builder()
            .buildTool(configNode.has("buildTool") ? configNode.get("buildTool").asText() : "maven")
            .javaVersion(configNode.has("javaVersion") ? configNode.get("javaVersion").asText() : "21")
            .targetRuntime(configNode.has("targetRuntime") ? configNode.get("targetRuntime").asText() : "jar")
            .buildPlugins(plugins)
            .properties(properties)
            .build();
    }
    
    private CodeArtifact.QualityMetrics calculateQualityMetrics(List<CodeArtifact.SourceFile> sourceFiles) {
        int totalLines = 0;
        int codeLines = 0;
        int commentLines = 0;
        int classCount = 0;
        int methodCount = 0;
        int totalMethodLength = 0;
        List<String> qualityIssues = new ArrayList<>();
        
        for (CodeArtifact.SourceFile file : sourceFiles) {
            String[] lines = file.getContent().split("\n");
            totalLines += lines.length;
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    commentLines++;
                } else if (!trimmed.isEmpty()) {
                    codeLines++;
                }
            }
            
            // Count classes and methods
            String content = file.getContent();
            classCount += countOccurrences(content, "class ");
            methodCount += countOccurrences(content, "public ") + countOccurrences(content, "private ");
        }
        
        double commentRatio = totalLines > 0 ? (double) commentLines / totalLines : 0;
        double avgMethodLength = methodCount > 0 ? (double) totalMethodLength / methodCount : 0;
        
        // Quality checks
        if (commentRatio < 0.1) {
            qualityIssues.add("Low comment ratio: " + String.format("%.2f", commentRatio));
        }
        
        return CodeArtifact.QualityMetrics.builder()
            .totalLines(totalLines)
            .codeLines(codeLines)
            .commentLines(commentLines)
            .commentRatio(commentRatio)
            .classCount(classCount)
            .methodCount(methodCount)
            .averageMethodLength(avgMethodLength)
            .qualityIssues(qualityIssues)
            .build();
    }
    
    private int countOccurrences(String content, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
    
    public static class CodeParsingException extends RuntimeException {
        public CodeParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
