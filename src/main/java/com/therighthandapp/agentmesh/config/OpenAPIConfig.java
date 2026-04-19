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
    OpenAPI agentMeshOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AgentMesh API")
                .description("""
                    Multi-Tenant Agentic AI Platform with Blackboard Architecture
                    
                    ## Features
                    - **Multi-Tenant Architecture**: Complete tenant isolation with RBAC/ABAC
                    - **Blackboard Pattern**: Shared knowledge space for agent collaboration
                    - **MAST Framework**: Multi-Agent Self-Correction with quality assurance
                    - **LoRA Support**: Tenant-specific model customization
                    - **Billing System**: Outcome-based and token-based pricing
                    - **Vector RAG**: Semantic search with Weaviate integration
                    - **Orchestration**: Temporal workflows for complex tasks
                    
                    ## Multi-Tenancy
                    All API operations support multi-tenancy via TenantContext. \
                    Set tenant/project context via authentication headers.
                    
                    ## Tiers
                    - **FREE**: Limited usage, $0/month
                    - **STANDARD**: 10 projects, 50 agents
                    - **PREMIUM**: 50 projects, 200 agents, 20% discount
                    - **ENTERPRISE**: Unlimited, 30% discount, custom pricing""")
                .version("1.0.0-RC1")
                .contact(new Contact()
                    .name("AgentMesh Team")
                    .email("support@agentmesh.io")
                    .url("https://agentmesh.io"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8081")
                    .description("Local Development (dev profile)"),
                new Server()
                    .url("http://api.localhost")
                    .description("Local via Traefik Gateway"),
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

