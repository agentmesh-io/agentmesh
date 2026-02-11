package com.therighthandapp.agentmesh.agents.planner.application;

import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain Service: Prompt Builder
 * 
 * Constructs optimized prompts for the LLM to generate execution plans.
 * This encapsulates the knowledge of how to structure prompts for best results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBuilder {
    
    /**
     * Build comprehensive prompt for execution plan generation
     * 
     * @param srsData The SRS from Auto-BADS
     * @param similarProjects Context from vector memory
     * @return Structured prompt for LLM
     */
    public String buildExecutionPlanPrompt(
            SrsHandoffDto srsData, 
            List<String> similarProjects) {
        
        StringBuilder prompt = new StringBuilder();
        
        // System context
        prompt.append("You are an expert software architect and technical lead.\n\n");
        prompt.append("Your task is to create a comprehensive execution plan for developing a software project.\n");
        prompt.append("Analyze the Software Requirements Specification (SRS) below and generate a detailed, structured plan.\n\n");
        
        // Project overview
        prompt.append("# PROJECT OVERVIEW\n\n");
        prompt.append("**Title:** ").append(srsData.getIdeaTitle()).append("\n\n");
        
        if (srsData.getProblemStatement() != null) {
            prompt.append("**Problem Statement:**\n");
            prompt.append(srsData.getProblemStatement()).append("\n\n");
        }
        
        if (srsData.getBusinessCase() != null) {
            prompt.append("**Business Case:**\n");
            prompt.append(srsData.getBusinessCase()).append("\n\n");
        }
        
        // Requirements
        if (srsData.getSrs() != null) {
            prompt.append("# REQUIREMENTS\n\n");
            
            // Functional requirements
            if (srsData.getSrs().getFunctionalRequirements() != null && 
                !srsData.getSrs().getFunctionalRequirements().isEmpty()) {
                prompt.append("## Functional Requirements\n\n");
                for (SrsHandoffDto.FunctionalRequirement fr : srsData.getSrs().getFunctionalRequirements()) {
                    prompt.append("- **").append(fr.getId()).append(":** ")
                           .append(fr.getRequirement()).append("\n");
                    if (fr.getRationale() != null) {
                        prompt.append("  - Rationale: ").append(fr.getRationale()).append("\n");
                    }
                }
                prompt.append("\n");
            }
            
            // Non-functional requirements
            if (srsData.getSrs().getNonFunctionalRequirements() != null && 
                !srsData.getSrs().getNonFunctionalRequirements().isEmpty()) {
                prompt.append("## Non-Functional Requirements\n\n");
                for (SrsHandoffDto.NonFunctionalRequirement nfr : srsData.getSrs().getNonFunctionalRequirements()) {
                    prompt.append("- **").append(nfr.getCategory()).append(":** ")
                           .append(nfr.getRequirement());
                    if (nfr.getMetric() != null && nfr.getTargetValue() != null) {
                        prompt.append(" (").append(nfr.getMetric()).append(": ").append(nfr.getTargetValue()).append(")");
                    }
                    prompt.append("\n");
                }
                prompt.append("\n");
            }
            
            // Architecture
            if (srsData.getSrs().getArchitecture() != null) {
                prompt.append("## Recommended Architecture\n\n");
                SrsHandoffDto.SystemArchitecture arch = srsData.getSrs().getArchitecture();
                prompt.append("- **Style:** ").append(arch.getArchitectureStyle()).append("\n");
                if (arch.getComponents() != null && !arch.getComponents().isEmpty()) {
                    prompt.append("- **Components:** ").append(String.join(", ", arch.getComponents())).append("\n");
                }
                if (arch.getDatabaseStrategy() != null) {
                    prompt.append("- **Database:** ").append(arch.getDatabaseStrategy()).append("\n");
                }
                prompt.append("\n");
            }
        }
        
        // Constraints
        if (srsData.getTechnicalConstraints() != null && !srsData.getTechnicalConstraints().isEmpty()) {
            prompt.append("# TECHNICAL CONSTRAINTS\n\n");
            for (String constraint : srsData.getTechnicalConstraints()) {
                prompt.append("- ").append(constraint).append("\n");
            }
            prompt.append("\n");
        }
        
        // Quality attributes
        if (srsData.getQualityAttributes() != null && !srsData.getQualityAttributes().isEmpty()) {
            prompt.append("# QUALITY ATTRIBUTES\n\n");
            for (String qa : srsData.getQualityAttributes()) {
                prompt.append("- ").append(qa).append("\n");
            }
            prompt.append("\n");
        }
        
        // Context from similar projects
        if (similarProjects != null && !similarProjects.isEmpty()) {
            prompt.append("# CONTEXT FROM SIMILAR PROJECTS\n\n");
            prompt.append("Consider these patterns from similar successful projects:\n\n");
            for (String project : similarProjects) {
                prompt.append(project).append("\n\n");
            }
        }
        
        // Instructions for output format
        prompt.append("# OUTPUT REQUIREMENTS\n\n");
        prompt.append("Generate a comprehensive execution plan in the following JSON structure:\n\n");
        prompt.append(getJsonSchemaExample());
        
        prompt.append("\n\n# IMPORTANT GUIDELINES\n\n");
        prompt.append("1. **Module Design:** Break down the system into cohesive, loosely-coupled modules\n");
        prompt.append("2. **File Organization:** Follow best practices for the chosen tech stack\n");
        prompt.append("3. **Testing Strategy:** Aim for >80% code coverage with unit, integration, and E2E tests\n");
        prompt.append("4. **Technology Selection:** Choose mature, well-supported technologies\n");
        prompt.append("5. **Requirements Tracing:** Ensure every functional requirement is addressed\n");
        prompt.append("6. **Security:** Include security considerations in architecture and file design\n");
        prompt.append("7. **Scalability:** Plan for horizontal scaling and high availability\n");
        prompt.append("8. **Maintainability:** Emphasize clean code, documentation, and separation of concerns\n\n");
        
        prompt.append("Respond ONLY with the JSON structure. Do not include explanations outside the JSON.\n");
        
        return prompt.toString();
    }
    
    /**
     * Provide JSON schema example for LLM
     */
    private String getJsonSchemaExample() {
        return """
        ```json
        {
          "modules": [
            {
              "name": "module-name",
              "description": "Brief description of module purpose",
              "priority": "HIGH|MEDIUM|LOW|CRITICAL",
              "techStack": ["technology1", "technology2"],
              "files": [
                {
                  "path": "relative/path/to/file.ext",
                  "purpose": "What this file does",
                  "type": "SOURCE_CODE|CONFIGURATION|DOCUMENTATION|TEST|INFRASTRUCTURE",
                  "dependencies": ["package1", "package2"],
                  "requirements": ["FR-001", "FR-002"]
                }
              ],
              "dependencies": ["other-module-name"],
              "configuration": {
                "key": "value"
              }
            }
          ],
          "fileStructure": {
            "rootDirectory": "project-root",
            "directories": {
              "src": {
                "name": "src",
                "purpose": "Source code",
                "files": ["main.js"],
                "subdirectories": {
                  "components": {
                    "name": "components",
                    "purpose": "React components",
                    "files": ["Component1.jsx"],
                    "subdirectories": {}
                  }
                }
              }
            }
          },
          "testingStrategy": {
            "targetCoveragePercent": 85,
            "testingFrameworks": ["Jest", "Cypress"],
            "testCategories": [
              {
                "name": "Unit Tests",
                "description": "Test individual functions",
                "estimatedTestCount": 50
              }
            ],
            "criticalPaths": ["User authentication flow", "Payment processing"]
          },
          "techStack": {
            "primaryLanguages": ["JavaScript", "TypeScript"],
            "frameworks": ["React", "Express"],
            "libraries": ["axios", "lodash"],
            "databases": ["PostgreSQL", "Redis"],
            "infrastructure": ["Docker", "Kubernetes"],
            "architecturePattern": "Microservices"
          },
          "effortEstimate": {
            "totalHours": 480,
            "hoursByModule": {
              "module-name": 120
            },
            "estimatedLinesOfCode": 5000,
            "phases": [
              {
                "name": "Phase 1: Setup",
                "durationDays": 5,
                "tasks": ["Setup repository", "Configure CI/CD"]
              }
            ]
          }
        }
        ```
        """;
    }
}
