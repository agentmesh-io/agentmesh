package com.therighthandapp.agentmesh.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for AgentMesh API documentation
 */
@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI agentMeshOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AgentMesh API")
                .description("Multi-Tenant Agentic AI Platform with Blackboard Architecture\n\n" +
                    "## Features\n" +
                    "- **Multi-Tenant Architecture**: Complete tenant isolation with RBAC/ABAC\n" +
                    "- **Blackboard Pattern**: Shared knowledge space for agent collaboration\n" +
                    "- **MAST Framework**: Multi-Agent Self-Correction with quality assurance\n" +
                    "- **LoRA Support**: Tenant-specific model customization\n" +
                    "- **Billing System**: Outcome-based and token-based pricing\n" +
                    "- **Vector RAG**: Semantic search with Weaviate integration\n" +
                    "- **Orchestration**: Temporal workflows for complex tasks\n\n" +
                    "## Multi-Tenancy\n" +
                    "All API operations support multi-tenancy via TenantContext. " +
                    "Set tenant/project context via authentication headers.\n\n" +
                    "## Tiers\n" +
                    "- **FREE**: Limited usage, $0/month\n" +
                    "- **STANDARD**: 10 projects, 50 agents\n" +
                    "- **PREMIUM**: 50 projects, 200 agents, 20% discount\n" +
                    "- **ENTERPRISE**: Unlimited, 30% discount, custom pricing")
                .version("1.0.0-Phase3")
                .contact(new Contact()
                    .name("AgentMesh Team")
                    .email("support@agentmesh.io")
                    .url("https://agentmesh.io"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development"),
                new Server()
                    .url("https://api.agentmesh.io")
                    .description("Production Server")))
            .tags(List.of(
                new Tag()
                    .name("Tenants")
                    .description("Tenant and project management operations"),
                new Tag()
                    .name("Billing")
                    .description("Billing, usage tracking, and cost estimation"),
                new Tag()
                    .name("Blackboard")
                    .description("Shared knowledge space for agent communication"),
                new Tag()
                    .name("MAST")
                    .description("Multi-Agent Self-Correction framework"),
                new Tag()
                    .name("Agents")
                    .description("Agent registration and lifecycle management"),
                new Tag()
                    .name("GitHub")
                    .description("GitHub integration and webhooks")
            ));
    }
}

