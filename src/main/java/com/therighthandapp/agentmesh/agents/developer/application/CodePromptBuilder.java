package com.therighthandapp.agentmesh.agents.developer.application;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;

/**
 * Application Service: Build prompts for code generation
 * 
 * Constructs comprehensive prompts that include plan, architecture,
 * and similar code examples for context-aware code generation.
 */
public class CodePromptBuilder {
    
    /**
     * Build complete code generation prompt
     */
    public String buildPrompt(ExecutionPlan plan, SystemArchitecture architecture, String similarCodeContext) {
        StringBuilder prompt = new StringBuilder();
        
        // Project overview
        prompt.append("# Code Generation Task\n\n");
        prompt.append("Generate production-ready source code for the following project:\n\n");
        prompt.append("## Project Information\n");
        prompt.append("Title: ").append(plan.getProjectTitle()).append("\n");
        prompt.append("Language: ").append(plan.getTechStack().getPrimaryLanguages().get(0)).append("\n\n");
        
        // Architecture context
        if (architecture != null) {
            prompt.append("## Architecture\n");
            prompt.append("Style: ").append(architecture.getArchitectureStyle().getPrimaryStyle()).append("\n");
            prompt.append("Components:\n");
            architecture.getComponents().forEach(c -> 
                prompt.append("- ").append(c.getName()).append(" (").append(c.getType()).append("): ")
                      .append(c.getResponsibility()).append("\n")
            );
            prompt.append("\n");
        }
        
        // Modules to implement
        prompt.append("## Modules to Implement\n");
        plan.getModules().forEach(module -> {
            prompt.append("### ").append(module.getName()).append("\n");
            prompt.append("Description: ").append(module.getDescription()).append("\n");
            if (!module.getTechStack().isEmpty()) {
                prompt.append("Tech Stack: ").append(String.join(", ", module.getTechStack())).append("\n");
            }
            if (!module.getFiles().isEmpty()) {
                prompt.append("Files to generate:\n");
                module.getFiles().forEach(f -> 
                    prompt.append("- ").append(f.getPath()).append(" (").append(f.getPurpose()).append(")\n")
                );
            }
            prompt.append("\n");
        });
        
        // Tech stack requirements
        prompt.append("## Technology Stack\n");
        prompt.append("Languages: ").append(String.join(", ", plan.getTechStack().getPrimaryLanguages())).append("\n");
        prompt.append("Frameworks: ").append(String.join(", ", plan.getTechStack().getFrameworks())).append("\n");
        prompt.append("Databases: ").append(String.join(", ", plan.getTechStack().getDatabases())).append("\n\n");
        
        // File structure
        prompt.append("## File Structure\n");
        prompt.append("Root: ").append(plan.getFileStructure().getRootDirectory()).append("\n");
        plan.getFileStructure().getDirectories().forEach((path, node) -> 
            prompt.append("- ").append(path).append(": ").append(node.getPurpose()).append("\n")
        );
        prompt.append("\n");
        
        // Similar code context
        if (similarCodeContext != null && !similarCodeContext.isEmpty()) {
            prompt.append("## Similar Code Examples (for reference)\n");
            prompt.append(similarCodeContext).append("\n\n");
        }
        
        // Output requirements
        prompt.append("## Output Requirements\n");
        prompt.append("Generate a JSON response with the following structure:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"sourceFiles\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"filePath\": \"relative/path/to/File.java\",\n");
        prompt.append("      \"fileName\": \"File.java\",\n");
        prompt.append("      \"language\": \"java\",\n");
        prompt.append("      \"content\": \"complete source code\",\n");
        prompt.append("      \"imports\": [\"java.util.List\", \"...\"]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"dependencies\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"groupId\": \"org.springframework.boot\",\n");
        prompt.append("      \"artifactId\": \"spring-boot-starter-web\",\n");
        prompt.append("      \"version\": \"3.2.0\",\n");
        prompt.append("      \"scope\": \"compile\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"buildConfig\": {\n");
        prompt.append("    \"buildTool\": \"maven\",\n");
        prompt.append("    \"javaVersion\": \"21\",\n");
        prompt.append("    \"targetRuntime\": \"jar\"\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        
        prompt.append("Important:\n");
        prompt.append("- Generate COMPLETE, runnable code (no placeholders or TODOs)\n");
        prompt.append("- Include proper error handling and logging\n");
        prompt.append("- Follow best practices and design patterns\n");
        prompt.append("- Add meaningful comments for complex logic\n");
        prompt.append("- Ensure all imports are included\n");
        prompt.append("- Generate at least one main class and supporting classes\n");
        
        return prompt.toString();
    }
}
