package com.therighthandapp.agentmesh.agents.architect.application;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Service: Prompt Builder for Architecture Generation
 * 
 * Constructs prompts for LLM to generate system architectures.
 */
@Component
public class ArchitecturePromptBuilder {
    
    /**
     * Build comprehensive prompt for architecture generation
     */
    public String buildArchitecturePrompt(
            ExecutionPlan plan,
            List<String> similarArchitectures) {
        
        StringBuilder prompt = new StringBuilder();
        
        // System instruction
        prompt.append("You are an expert software architect tasked with designing a comprehensive system architecture.\n\n");
        
        // Context from execution plan
        prompt.append("PROJECT OVERVIEW:\n");
        prompt.append("Title: ").append(plan.getProjectTitle()).append("\n");
        prompt.append("SRS ID: ").append(plan.getSrsId()).append("\n\n");
        
        // Modules from execution plan
        prompt.append("MODULES TO ARCHITECT:\n");
        for (ExecutionPlan.Module module : plan.getModules()) {
            prompt.append("- ").append(module.getName()).append(": ").append(module.getDescription()).append("\n");
            if (module.getDependencies() != null && !module.getDependencies().isEmpty()) {
                prompt.append("  Dependencies: ").append(String.join(", ", module.getDependencies())).append("\n");
            }
        }
        prompt.append("\n");
        
        // Technology stack
        if (plan.getTechStack() != null) {
            prompt.append("TECHNOLOGY STACK:\n");
            prompt.append("Languages: ").append(String.join(", ", plan.getTechStack().getPrimaryLanguages())).append("\n");
            prompt.append("Frameworks: ").append(String.join(", ", plan.getTechStack().getFrameworks())).append("\n");
            if (plan.getTechStack().getDatabases() != null && !plan.getTechStack().getDatabases().isEmpty()) {
                prompt.append("Databases: ").append(String.join(", ", plan.getTechStack().getDatabases())).append("\n");
            }
            prompt.append("\n");
        }
        
        // Similar architectures for context
        if (similarArchitectures != null && !similarArchitectures.isEmpty()) {
            prompt.append("REFERENCE ARCHITECTURES (for inspiration):\n");
            for (int i = 0; i < similarArchitectures.size(); i++) {
                prompt.append((i + 1)).append(". ").append(similarArchitectures.get(i)).append("\n");
            }
            prompt.append("\n");
        }
        
        // Architecture requirements
        prompt.append("ARCHITECTURE REQUIREMENTS:\n");
        prompt.append("Generate a comprehensive system architecture that includes:\n\n");
        
        prompt.append("1. ARCHITECTURE STYLE:\n");
        prompt.append("   - Primary architectural style (Microservices, Monolith, Serverless, Event-Driven, etc.)\n");
        prompt.append("   - Design patterns to apply (CQRS, Event Sourcing, Saga, etc.)\n");
        prompt.append("   - Justification for chosen style\n\n");
        
        prompt.append("2. COMPONENTS:\n");
        prompt.append("   For each major component, specify:\n");
        prompt.append("   - Component name and type (API, Service, Database, Cache, Queue)\n");
        prompt.append("   - Responsibility and purpose\n");
        prompt.append("   - Technology choice (framework, language, database)\n");
        prompt.append("   - Exposed APIs and dependencies\n\n");
        
        prompt.append("3. DATA FLOWS:\n");
        prompt.append("   - Communication between components\n");
        prompt.append("   - Protocol (REST, gRPC, Message Queue, Database)\n");
        prompt.append("   - Synchronous vs asynchronous\n\n");
        
        prompt.append("4. INTEGRATIONS:\n");
        prompt.append("   - External system integrations\n");
        prompt.append("   - Authentication methods (OAuth, API keys, etc.)\n");
        prompt.append("   - Required credentials\n\n");
        
        prompt.append("5. DEPLOYMENT ARCHITECTURE:\n");
        prompt.append("   - Platform (Kubernetes, AWS ECS, Docker Compose)\n");
        prompt.append("   - Component deployment configuration\n");
        prompt.append("   - Replica counts and resource requirements\n");
        prompt.append("   - Load balancing strategy\n");
        prompt.append("   - Health checks\n\n");
        
        prompt.append("6. SECURITY ARCHITECTURE:\n");
        prompt.append("   - Authentication strategy (JWT, OAuth2, Session)\n");
        prompt.append("   - Authorization model (RBAC, ABAC)\n");
        prompt.append("   - Encryption points (data at rest, in transit)\n");
        prompt.append("   - Rate limiting policy\n");
        prompt.append("   - Security headers\n\n");
        
        prompt.append("7. SCALABILITY STRATEGY:\n");
        prompt.append("   - Horizontal scaling (min/max instances, triggers)\n");
        prompt.append("   - Vertical scaling (CPU/memory allocation)\n");
        prompt.append("   - Caching strategy (CDN, Application, Database)\n");
        prompt.append("   - Database scaling (read replicas, sharding)\n\n");
        
        // Output format
        prompt.append("OUTPUT FORMAT:\n");
        prompt.append("Provide the architecture in valid JSON format with this structure:\n");
        prompt.append("{\n");
        prompt.append("  \"architectureStyle\": {\n");
        prompt.append("    \"primaryStyle\": \"...\",\n");
        prompt.append("    \"patterns\": [...],\n");
        prompt.append("    \"justification\": \"...\"\n");
        prompt.append("  },\n");
        prompt.append("  \"components\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"...\",\n");
        prompt.append("      \"type\": \"...\",\n");
        prompt.append("      \"description\": \"...\",\n");
        prompt.append("      \"responsibility\": \"...\",\n");
        prompt.append("      \"exposedAPIs\": [...],\n");
        prompt.append("      \"dependencies\": [...],\n");
        prompt.append("      \"technology\": {\n");
        prompt.append("        \"framework\": \"...\",\n");
        prompt.append("        \"language\": \"...\",\n");
        prompt.append("        \"database\": \"...\",\n");
        prompt.append("        \"libraries\": [...],\n");
        prompt.append("        \"justification\": \"...\"\n");
        prompt.append("      }\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"dataFlows\": [...],\n");
        prompt.append("  \"integrations\": [...],\n");
        prompt.append("  \"deployment\": {...},\n");
        prompt.append("  \"security\": {...},\n");
        prompt.append("  \"scalability\": {...}\n");
        prompt.append("}\n\n");
        
        prompt.append("Ensure all JSON is valid and complete. Focus on practical, production-ready architecture.\n");
        
        return prompt.toString();
    }
}
