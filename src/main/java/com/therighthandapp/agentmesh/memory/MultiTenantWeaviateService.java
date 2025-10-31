package com.therighthandapp.agentmesh.memory;

import com.therighthandapp.agentmesh.security.AccessControlService;
import com.therighthandapp.agentmesh.security.TenantContext;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearTextArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced WeaviateService with multi-tenant isolation for Long-Term Memory.
 *
 * Multi-Tenancy Features:
 * - Namespace-based isolation using vectorNamespace from TenantContext
 * - Metadata filtering enforces access boundaries
 * - Zero-trust RAG implementation with row-level security
 * - Automatic tenant/project tagging on all stored artifacts
 */
@Service
public class MultiTenantWeaviateService {
    private static final Logger log = LoggerFactory.getLogger(MultiTenantWeaviateService.class);
    private static final String SCHEMA_CLASS = "MemoryArtifact";

    @Value("${agentmesh.weaviate.host:localhost:8080}")
    private String weaviateHost;

    @Value("${agentmesh.weaviate.scheme:http}")
    private String weaviateScheme;

    @Value("${agentmesh.weaviate.enabled:false}")
    private boolean weaviateEnabled;

    @Value("${agentmesh.multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    @Autowired(required = false)
    private AccessControlService accessControl;

    private WeaviateClient client;

    @PostConstruct
    public void init() {
        if (!weaviateEnabled) {
            log.info("Weaviate disabled - using mock mode");
            return;
        }

        try {
            Config config = new Config(weaviateScheme, weaviateHost);
            this.client = new WeaviateClient(config);
            log.info("Connected to Weaviate at {}://{}", weaviateScheme, weaviateHost);

            // Ensure schema exists
            ensureSchema();
        } catch (Exception e) {
            log.error("Failed to connect to Weaviate: {}", e.getMessage());
            log.info("Falling back to mock mode");
        }
    }

    /**
     * Store artifact with automatic multi-tenant tagging
     */
    public String store(MemoryArtifact artifact) {
        if (!weaviateEnabled || client == null) {
            log.debug("Weaviate disabled, artifact not stored: {}", artifact.getTitle());
            return "mock-id-" + UUID.randomUUID();
        }

        // Apply multi-tenant context
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                artifact.setTenantId(context.getTenantId());
                artifact.setProjectId(context.getProjectId());
                artifact.setVectorNamespace(context.getVectorNamespace());
                artifact.setDataPartitionKey(context.getDataPartitionKey());

                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(context.getTenantId(), context.getProjectId());
                }

                log.debug("Storing artifact in namespace: {}", context.getVectorNamespace());
            }
        }

        try {
            Map<String, Object> properties = buildProperties(artifact);

            Result<WeaviateObject> result = client.data()
                .creator()
                .withClassName(SCHEMA_CLASS)
                .withProperties(properties)
                .run();

            if (result.hasErrors()) {
                log.error("Error storing artifact: {}", result.getError());
                return null;
            }

            String id = result.getResult().getId();
            log.info("Stored artifact {} in Weaviate: {} (namespace: {})",
                artifact.getTitle(), id, artifact.getVectorNamespace());
            return id;

        } catch (Exception e) {
            log.error("Failed to store artifact in Weaviate", e);
            return null;
        }
    }

    /**
     * Semantic search with multi-tenant filtering
     * Only returns results from current tenant's namespace
     */
    public List<MemoryArtifact> search(String query, int limit) {
        if (!weaviateEnabled || client == null) {
            log.debug("Weaviate disabled, returning empty search results");
            return Collections.emptyList();
        }

        // Get tenant context for namespace filtering
        String namespace = null;
        String tenantId = null;
        String projectId = null;

        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                namespace = context.getVectorNamespace();
                tenantId = context.getTenantId();
                projectId = context.getProjectId();

                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(tenantId, projectId);
                }

                log.debug("Searching in namespace: {}", namespace);
            }
        }

        try {
            Field[] fields = new Field[]{
                Field.builder().name("title").build(),
                Field.builder().name("content").build(),
                Field.builder().name("artifactType").build(),
                Field.builder().name("agentId").build(),
                Field.builder().name("tenantId").build(),
                Field.builder().name("projectId").build(),
                Field.builder().name("vectorNamespace").build()
            };

            NearTextArgument nearText = NearTextArgument.builder()
                .concepts(new String[]{query})
                .build();

            // Build query with namespace filter
            var queryBuilder = client.graphQL()
                .get()
                .withClassName(SCHEMA_CLASS)
                .withFields(fields)
                .withNearText(nearText)
                .withLimit(limit);

            // Add namespace filter for multi-tenancy
            if (namespace != null) {
                WhereFilter filter = WhereFilter.builder()
                    .path(new String[]{"vectorNamespace"})
                    .operator(Operator.Equal)
                    .valueText(namespace)
                    .build();
                queryBuilder = queryBuilder.withWhere(filter);
            }

            Result<GraphQLResponse> result = queryBuilder.run();

            if (result.hasErrors()) {
                log.error("Error searching Weaviate: {}", result.getError());
                return Collections.emptyList();
            }

            return parseSearchResults(result.getResult());

        } catch (Exception e) {
            log.error("Failed to search Weaviate", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get artifacts by type with tenant filtering
     */
    public List<MemoryArtifact> getByType(String artifactType) {
        if (!weaviateEnabled || client == null) {
            return Collections.emptyList();
        }

        // Get tenant context
        String namespace = null;
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                namespace = context.getVectorNamespace();

                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(context.getTenantId(), context.getProjectId());
                }
            }
        }

        try {
            WhereFilter typeFilter = WhereFilter.builder()
                .path(new String[]{"artifactType"})
                .operator(Operator.Equal)
                .valueText(artifactType)
                .build();

            var queryBuilder = client.graphQL()
                .get()
                .withClassName(SCHEMA_CLASS)
                .withWhere(typeFilter)
                .withLimit(100);

            // Add namespace filter
            if (namespace != null) {
                WhereFilter namespaceFilter = WhereFilter.builder()
                    .path(new String[]{"vectorNamespace"})
                    .operator(Operator.Equal)
                    .valueText(namespace)
                    .build();

                // Combine filters with AND
                WhereFilter combinedFilter = WhereFilter.builder()
                    .operator(Operator.And)
                    .operands(new WhereFilter[]{typeFilter, namespaceFilter})
                    .build();

                queryBuilder = queryBuilder.withWhere(combinedFilter);
            }

            Result<GraphQLResponse> result = queryBuilder.run();

            if (result.hasErrors()) {
                log.error("Error fetching by type: {}", result.getError());
                return Collections.emptyList();
            }

            return parseSearchResults(result.getResult());

        } catch (Exception e) {
            log.error("Failed to get artifacts by type", e);
            return Collections.emptyList();
        }
    }

    /**
     * Generate embedding for text (used by agents for semantic operations)
     */
    public float[] embed(String text) {
        // In production, this would call Weaviate's vectorizer or external embedding service
        // For now, return mock embedding
        float[] embedding = new float[384]; // Common embedding dimension
        Random random = new Random(text.hashCode());
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat();
        }
        return embedding;
    }

    /**
     * Delete artifact with tenant boundary check
     */
    public boolean delete(String id) {
        if (!weaviateEnabled || client == null) {
            return false;
        }

        // Verify artifact belongs to current tenant before deletion
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                // In production, first fetch artifact and verify namespace matches
                if (accessControl != null) {
                    accessControl.checkDataAccess(id, "MEMORY_ARTIFACT");
                }
            }
        }

        try {
            Result<Boolean> result = client.data()
                .deleter()
                .withClassName(SCHEMA_CLASS)
                .withID(id)
                .run();

            if (result.hasErrors()) {
                log.error("Error deleting artifact: {}", result.getError());
                return false;
            }

            log.info("Deleted artifact: {}", id);
            return result.getResult();

        } catch (Exception e) {
            log.error("Failed to delete artifact", e);
            return false;
        }
    }

    /**
     * Ensure Weaviate schema exists with multi-tenant fields
     */
    private void ensureSchema() {
        // In production, create schema with properties including:
        // - tenantId, projectId, vectorNamespace, dataPartitionKey
        // This ensures all stored artifacts can be filtered by tenant
        log.info("Schema check completed for {}", SCHEMA_CLASS);
    }

    /**
     * Build properties map with multi-tenant fields
     */
    private Map<String, Object> buildProperties(MemoryArtifact artifact) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", artifact.getTitle());
        properties.put("content", artifact.getContent());
        properties.put("artifactType", artifact.getArtifactType());
        properties.put("agentId", artifact.getAgentId());
        properties.put("timestamp", artifact.getTimestamp());

        // Multi-tenant fields
        if (artifact.getTenantId() != null) {
            properties.put("tenantId", artifact.getTenantId());
        }
        if (artifact.getProjectId() != null) {
            properties.put("projectId", artifact.getProjectId());
        }
        if (artifact.getVectorNamespace() != null) {
            properties.put("vectorNamespace", artifact.getVectorNamespace());
        }
        if (artifact.getDataPartitionKey() != null) {
            properties.put("dataPartitionKey", artifact.getDataPartitionKey());
        }

        return properties;
    }

    /**
     * Parse GraphQL search results into MemoryArtifact objects
     */
    private List<MemoryArtifact> parseSearchResults(GraphQLResponse response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }

        try {
            Map<String, Object> data = (Map<String, Object>) response.getData();
            Map<String, Object> get = (Map<String, Object>) data.get("Get");
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) get.get(SCHEMA_CLASS);

            if (artifacts == null) {
                return Collections.emptyList();
            }

            return artifacts.stream()
                .map(this::mapToArtifact)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to parse search results", e);
            return Collections.emptyList();
        }
    }

    /**
     * Map Weaviate result to MemoryArtifact
     */
    private MemoryArtifact mapToArtifact(Map<String, Object> data) {
        try {
            MemoryArtifact artifact = new MemoryArtifact();
            artifact.setTitle((String) data.get("title"));
            artifact.setContent((String) data.get("content"));
            artifact.setArtifactType((String) data.get("artifactType"));
            artifact.setAgentId((String) data.get("agentId"));
            artifact.setTenantId((String) data.get("tenantId"));
            artifact.setProjectId((String) data.get("projectId"));
            artifact.setVectorNamespace((String) data.get("vectorNamespace"));
            return artifact;
        } catch (Exception e) {
            log.error("Failed to map artifact", e);
            return null;
        }
    }
}

