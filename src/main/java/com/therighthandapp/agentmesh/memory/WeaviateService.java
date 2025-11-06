package com.therighthandapp.agentmesh.memory;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * WeaviateService provides vector storage and semantic search for Long-Term Memory.
 * This enables RAG (Retrieval-Augmented Generation) for agents.
 */
@Service
public class WeaviateService {
    private static final Logger log = LoggerFactory.getLogger(WeaviateService.class);
    private static final String SCHEMA_CLASS = "MemoryArtifact";

    @Value("${agentmesh.weaviate.host:localhost:8080}")
    private String weaviateHost;

    @Value("${agentmesh.weaviate.scheme:http}")
    private String weaviateScheme;

    @Value("${agentmesh.weaviate.enabled:false}")
    private boolean weaviateEnabled;

    private WeaviateClient client;

    @PostConstruct
    public void init() {
        if (!weaviateEnabled) {
            log.info("Weaviate is disabled. Using mock mode for LTM.");
            return;
        }

        try {
            Config config = new Config(weaviateScheme, weaviateHost);
            client = new WeaviateClient(config);
            log.info("Weaviate client initialized: {}://{}", weaviateScheme, weaviateHost);
            ensureSchema();
        } catch (Exception e) {
            log.warn("Failed to initialize Weaviate client. Running in mock mode: {}", e.getMessage());
            client = null;
        }
    }

    private void ensureSchema() {
        try {
            // Check if schema already exists
            Result<Boolean> existsResult = client.schema().exists().withClassName(SCHEMA_CLASS).run();
            if (existsResult.getResult() != null && existsResult.getResult()) {
                log.info("Weaviate schema '{}' already exists", SCHEMA_CLASS);
                return;
            }

            // Create schema using WeaviateClass builder
            // Using text2vec-transformers vectorizer for semantic search
            io.weaviate.client.v1.schema.model.WeaviateClass weaviateClass = 
                io.weaviate.client.v1.schema.model.WeaviateClass.builder()
                    .className(SCHEMA_CLASS)
                    .description("Memory artifacts for agent long-term memory")
                    .vectorizer("text2vec-transformers")
                    .properties(List.of(
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("agentId")
                            .dataType(List.of("text"))
                            .description("ID of the agent that created this artifact")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("artifactType")
                            .dataType(List.of("text"))
                            .description("Type of artifact: SRS, CODE_SNIPPET, FAILURE_LESSON, etc.")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("title")
                            .dataType(List.of("text"))
                            .description("Title of the memory artifact")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("content")
                            .dataType(List.of("text"))
                            .description("Full content of the memory artifact")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("timestamp")
                            .dataType(List.of("text"))
                            .description("ISO-8601 timestamp when artifact was created")
                            .build()
                    ))
                    .build();
            
            Result<Boolean> result = client.schema().classCreator()
                .withClass(weaviateClass)
                .run();
            
            if (result.hasErrors()) {
                log.error("Failed to create Weaviate schema: {}", result.getError());
            } else {
                log.info("Successfully created Weaviate schema '{}'", SCHEMA_CLASS);
            }
        } catch (Exception e) {
            log.error("Exception creating Weaviate schema", e);
        }
    }

    /**
     * Store a memory artifact in Weaviate
     */
    public String store(MemoryArtifact artifact) {
        if (client == null) {
            log.debug("Mock store: {}", artifact.getTitle());
            return UUID.randomUUID().toString();
        }

        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put("agentId", artifact.getAgentId());
            properties.put("artifactType", artifact.getArtifactType());
            properties.put("title", artifact.getTitle());
            properties.put("content", artifact.getContent());
            properties.put("timestamp", artifact.getTimestamp().toString());

            Result<WeaviateObject> result = client.data().creator()
                    .withClassName(SCHEMA_CLASS)
                    .withProperties(properties)
                    .run();

            if (result.hasErrors()) {
                log.error("Failed to store artifact in Weaviate: {}", result.getError());
                return null;
            }

            String id = result.getResult().getId();
            log.info("Stored artifact in Weaviate: {} (id={})", artifact.getTitle(), id);
            return id;
        } catch (Exception e) {
            log.error("Exception storing artifact in Weaviate", e);
            return null;
        }
    }

    /**
     * Retrieve artifacts by semantic similarity (RAG query)
     */
    public List<MemoryArtifact> semanticSearch(String query, int limit) {
        if (client == null) {
            log.debug("Mock semantic search for: {}", query);
            return Collections.emptyList();
        }

        try {
            Field title = Field.builder().name("title").build();
            Field content = Field.builder().name("content").build();
            Field agentId = Field.builder().name("agentId").build();
            Field artifactType = Field.builder().name("artifactType").build();

            NearTextArgument nearText = NearTextArgument.builder()
                    .concepts(new String[]{query})
                    .build();

            Result<GraphQLResponse> result = client.graphQL().get()
                    .withClassName(SCHEMA_CLASS)
                    .withNearText(nearText)
                    .withLimit(limit)
                    .withFields(title, content, agentId, artifactType)
                    .run();

            if (result.hasErrors()) {
                log.error("Semantic search failed: {}", result.getError());
                return Collections.emptyList();
            }

            // Parse results and convert to MemoryArtifact objects
            GraphQLResponse response = result.getResult();
            if (response == null || response.getData() == null) {
                log.warn("Semantic search returned no data for query: {}", query);
                return Collections.emptyList();
            }

            List<MemoryArtifact> artifacts = new ArrayList<>();
            try {
                Map<String, Object> data = (Map<String, Object>) response.getData();
                Map<String, Object> get = (Map<String, Object>) data.get("Get");
                if (get != null) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) get.get(SCHEMA_CLASS);
                    if (results != null) {
                        for (Map<String, Object> item : results) {
                            MemoryArtifact artifact = new MemoryArtifact();
                            artifact.setAgentId((String) item.get("agentId"));
                            artifact.setArtifactType((String) item.get("artifactType"));
                            artifact.setTitle((String) item.get("title"));
                            artifact.setContent((String) item.get("content"));
                            artifacts.add(artifact);
                        }
                    }
                }
            } catch (Exception parseEx) {
                log.error("Error parsing semantic search results", parseEx);
            }

            log.info("Semantic search returned {} results for query: {}", artifacts.size(), query);
            return artifacts;

        } catch (Exception e) {
            log.error("Exception during semantic search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieve artifacts by type
     */
    public List<MemoryArtifact> findByType(String artifactType, int limit) {
        if (client == null) {
            log.debug("Mock findByType: {}", artifactType);
            return Collections.emptyList();
        }

        try {
            WhereFilter filter = WhereFilter.builder()
                    .path(new String[]{"artifactType"})
                    .operator(Operator.Equal)
                    .valueText(artifactType)
                    .build();

            Field title = Field.builder().name("title").build();
            Field content = Field.builder().name("content").build();
            Field agentId = Field.builder().name("agentId").build();
            Field artifactTypeField = Field.builder().name("artifactType").build();

            @SuppressWarnings("deprecation")
            Result<GraphQLResponse> result = client.graphQL().get()
                    .withClassName(SCHEMA_CLASS)
                    .withWhere(filter)
                    .withLimit(limit)
                    .withFields(title, content, agentId, artifactTypeField)
                    .run();

            if (result.hasErrors()) {
                log.error("Query by type failed: {}", result.getError());
                return Collections.emptyList();
            }

            // Parse results and convert to MemoryArtifact objects
            GraphQLResponse response = result.getResult();
            if (response == null || response.getData() == null) {
                log.warn("Query by type {} returned no data", artifactType);
                return Collections.emptyList();
            }

            List<MemoryArtifact> artifacts = new ArrayList<>();
            try {
                Map<String, Object> data = (Map<String, Object>) response.getData();
                Map<String, Object> get = (Map<String, Object>) data.get("Get");
                if (get != null) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) get.get(SCHEMA_CLASS);
                    if (results != null) {
                        for (Map<String, Object> item : results) {
                            MemoryArtifact artifact = new MemoryArtifact();
                            artifact.setAgentId((String) item.get("agentId"));
                            artifact.setArtifactType((String) item.get("artifactType"));
                            artifact.setTitle((String) item.get("title"));
                            artifact.setContent((String) item.get("content"));
                            artifacts.add(artifact);
                        }
                    }
                }
            } catch (Exception parseEx) {
                log.error("Error parsing findByType results", parseEx);
            }

            log.info("Query by type {} returned {} results", artifactType, artifacts.size());
            return artifacts;

        } catch (Exception e) {
            log.error("Exception querying by type", e);
            return Collections.emptyList();
        }
    }

    /**
     * Hybrid search combining BM25 keyword matching with vector semantic search
     * @param query Search query string
     * @param limit Maximum number of results
     * @param alpha Balance between BM25 (0.0) and vector (1.0). Default 0.75 for balanced hybrid.
     * @param agentId Agent ID for tracking (optional)
     * @return List of memory artifacts ranked by hybrid relevance
     */
    public List<MemoryArtifact> hybridSearch(String query, int limit, double alpha, String agentId) {
        if (client == null) {
            log.debug("Mock hybrid search for: {}", query);
            return Collections.emptyList();
        }

        // Validate alpha parameter
        if (alpha < 0.0 || alpha > 1.0) {
            log.warn("Invalid alpha {}, defaulting to 0.75", alpha);
            alpha = 0.75;
        }

        try {
            Field title = Field.builder().name("title").build();
            Field content = Field.builder().name("content").build();
            Field agentIdField = Field.builder().name("agentId").build();
            Field artifactType = Field.builder().name("artifactType").build();

            io.weaviate.client.v1.graphql.query.argument.HybridArgument hybridArg =
                    io.weaviate.client.v1.graphql.query.argument.HybridArgument.builder()
                            .query(query)
                            .alpha((float) alpha)
                            .build();

            Result<GraphQLResponse> result = client.graphQL().get()
                    .withClassName(SCHEMA_CLASS)
                    .withHybrid(hybridArg)
                    .withLimit(limit)
                    .withFields(title, content, agentIdField, artifactType)
                    .run();

            if (result.hasErrors()) {
                log.error("Hybrid search failed: {}", result.getError());
                return Collections.emptyList();
            }

            GraphQLResponse response = result.getResult();
            if (response == null || response.getData() == null) {
                log.warn("Hybrid search returned no data for query: {}", query);
                return Collections.emptyList();
            }

            List<MemoryArtifact> artifacts = parseSearchResults(response);
            log.info("Hybrid search (alpha={}) returned {} results for query: {}", alpha, artifacts.size(), query);
            return artifacts;

        } catch (Exception e) {
            log.error("Exception during hybrid search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Advanced search with metadata filters and optional hybrid mode
     * @param query Search query string
     * @param limit Maximum number of results
     * @param filters Map of filter criteria (artifactType, agentId, etc.)
     * @param useHybrid Whether to use hybrid search (true) or pure vector (false)
     * @param alpha Hybrid balance parameter (0.0-1.0)
     * @param agentId Agent ID for tracking (optional)
     * @return List of memory artifacts matching filters
     */
    public List<MemoryArtifact> searchWithFilters(String query, int limit, 
                                                   Map<String, Object> filters,
                                                   boolean useHybrid, double alpha,
                                                   String agentId) {
        if (client == null) {
            log.debug("Mock filtered search for: {}", query);
            return Collections.emptyList();
        }

        if (useHybrid && (alpha < 0.0 || alpha > 1.0)) {
            log.warn("Invalid alpha {}, defaulting to 0.75", alpha);
            alpha = 0.75;
        }

        try {
            Field title = Field.builder().name("title").build();
            Field content = Field.builder().name("content").build();
            Field agentIdField = Field.builder().name("agentId").build();
            Field artifactType = Field.builder().name("artifactType").build();

            // Build query with hybrid or vector search
            var queryBuilder = client.graphQL().get()
                    .withClassName(SCHEMA_CLASS)
                    .withLimit(limit)
                    .withFields(title, content, agentIdField, artifactType);

            if (useHybrid) {
                io.weaviate.client.v1.graphql.query.argument.HybridArgument hybridArg =
                        io.weaviate.client.v1.graphql.query.argument.HybridArgument.builder()
                                .query(query)
                                .alpha((float) alpha)
                                .build();
                queryBuilder = queryBuilder.withHybrid(hybridArg);
            } else {
                NearTextArgument nearText = NearTextArgument.builder()
                        .concepts(new String[]{query})
                        .build();
                queryBuilder = queryBuilder.withNearText(nearText);
            }

            // Apply filters if provided
            if (filters != null && !filters.isEmpty()) {
                List<WhereFilter> whereFilters = new ArrayList<>();

                if (filters.containsKey("artifactType")) {
                    whereFilters.add(WhereFilter.builder()
                            .path(new String[]{"artifactType"})
                            .operator(Operator.Equal)
                            .valueText((String) filters.get("artifactType"))
                            .build());
                }

                if (filters.containsKey("agentId")) {
                    whereFilters.add(WhereFilter.builder()
                            .path(new String[]{"agentId"})
                            .operator(Operator.Equal)
                            .valueText((String) filters.get("agentId"))
                            .build());
                }

                // Combine filters with AND operator
                if (whereFilters.size() == 1) {
                    queryBuilder = queryBuilder.withWhere(whereFilters.get(0));
                } else if (whereFilters.size() > 1) {
                    WhereFilter combinedFilter = WhereFilter.builder()
                            .operator(Operator.And)
                            .operands(whereFilters.toArray(new WhereFilter[0]))
                            .build();
                    queryBuilder = queryBuilder.withWhere(combinedFilter);
                }
            }

            Result<GraphQLResponse> result = queryBuilder.run();

            if (result.hasErrors()) {
                log.error("Filtered search failed: {}", result.getError());
                return Collections.emptyList();
            }

            GraphQLResponse response = result.getResult();
            if (response == null || response.getData() == null) {
                log.warn("Filtered search returned no data");
                return Collections.emptyList();
            }

            List<MemoryArtifact> artifacts = parseSearchResults(response);
            log.info("Filtered search returned {} results (hybrid={}, filters={})", 
                    artifacts.size(), useHybrid, filters);
            return artifacts;

        } catch (Exception e) {
            log.error("Exception during filtered search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Helper method to parse Weaviate GraphQL response into MemoryArtifact list
     */
    @SuppressWarnings("unchecked")
    private List<MemoryArtifact> parseSearchResults(GraphQLResponse response) {
        List<MemoryArtifact> artifacts = new ArrayList<>();
        try {
            Map<String, Object> data = (Map<String, Object>) response.getData();
            Map<String, Object> get = (Map<String, Object>) data.get("Get");
            if (get != null) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) get.get(SCHEMA_CLASS);
                if (results != null) {
                    for (Map<String, Object> item : results) {
                        MemoryArtifact artifact = new MemoryArtifact();
                        artifact.setAgentId((String) item.get("agentId"));
                        artifact.setArtifactType((String) item.get("artifactType"));
                        artifact.setTitle((String) item.get("title"));
                        artifact.setContent((String) item.get("content"));
                        artifacts.add(artifact);
                    }
                }
            }
        } catch (Exception parseEx) {
            log.error("Error parsing search results", parseEx);
        }
        return artifacts;
    }
}

