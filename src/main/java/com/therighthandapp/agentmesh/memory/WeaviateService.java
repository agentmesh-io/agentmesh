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
    private static final String TITLE_CLASS = "MemoryArtifactTitle";

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
            ensureTitleSchema();
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
     * Ensure title-only schema exists for multi-vector storage
     * This enables separate title and content vectors for improved precision
     */
    private void ensureTitleSchema() {
        try {
            // Check if title schema already exists
            Result<Boolean> existsResult = client.schema().exists().withClassName(TITLE_CLASS).run();
            if (existsResult.getResult() != null && existsResult.getResult()) {
                log.info("Weaviate schema '{}' already exists", TITLE_CLASS);
                return;
            }

            // Create title-only schema
            io.weaviate.client.v1.schema.model.WeaviateClass titleClass = 
                io.weaviate.client.v1.schema.model.WeaviateClass.builder()
                    .className(TITLE_CLASS)
                    .description("Memory artifact titles for focused search")
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
                            .description("Type of artifact")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("title")
                            .dataType(List.of("text"))
                            .description("Title of the memory artifact (vectorized)")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("artifactId")
                            .dataType(List.of("text"))
                            .description("Reference to full artifact ID in main class")
                            .build(),
                        io.weaviate.client.v1.schema.model.Property.builder()
                            .name("timestamp")
                            .dataType(List.of("text"))
                            .description("ISO-8601 timestamp")
                            .build()
                    ))
                    .build();
            
            Result<Boolean> result = client.schema().classCreator()
                .withClass(titleClass)
                .run();
            
            if (result.hasErrors()) {
                log.error("Failed to create title schema: {}", result.getError());
            } else {
                log.info("Successfully created Weaviate schema '{}'", TITLE_CLASS);
            }
        } catch (Exception e) {
            log.error("Exception creating title schema", e);
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
     * Store multiple memory artifacts in batch for improved performance
     * Uses Weaviate's batch API which is ~10x faster than individual inserts
     * 
     * @param artifacts List of memory artifacts to store
     * @return Map of artifact titles to their IDs, or empty map on failure
     */
    public Map<String, String> storeBatch(List<MemoryArtifact> artifacts) {
        if (client == null) {
            log.debug("Mock batch store: {} artifacts", artifacts.size());
            Map<String, String> mockResults = new HashMap<>();
            for (MemoryArtifact artifact : artifacts) {
                mockResults.put(artifact.getTitle(), UUID.randomUUID().toString());
            }
            return mockResults;
        }

        if (artifacts == null || artifacts.isEmpty()) {
            log.warn("No artifacts provided for batch storage");
            return Collections.emptyMap();
        }

        Map<String, String> results = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // Build batch with all artifacts
            List<Map<String, Object>> objects = new ArrayList<>();
            
            for (MemoryArtifact artifact : artifacts) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("agentId", artifact.getAgentId());
                properties.put("artifactType", artifact.getArtifactType());
                properties.put("title", artifact.getTitle());
                properties.put("content", artifact.getContent());
                properties.put("timestamp", artifact.getTimestamp().toString());
                objects.add(properties);
            }

            // Execute batch operation using Weaviate batch creator
            var batchBuilder = client.batch().objectsBatcher();
            for (int i = 0; i < objects.size(); i++) {
                batchBuilder = batchBuilder.withObjects(
                    WeaviateObject.builder()
                        .className(SCHEMA_CLASS)
                        .properties(objects.get(i))
                        .build()
                );
            }
            
            Result<io.weaviate.client.v1.batch.model.ObjectGetResponse[]> result = batchBuilder.run();

            if (result.hasErrors()) {
                log.error("Batch storage failed: {}", result.getError());
                return Collections.emptyMap();
            }

            // Map results back to artifacts
            io.weaviate.client.v1.batch.model.ObjectGetResponse[] responses = result.getResult();
            if (responses != null && responses.length == artifacts.size()) {
                for (int i = 0; i < artifacts.size(); i++) {
                    String id = responses[i].getId();
                    String title = artifacts.get(i).getTitle();
                    results.put(title, id);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch stored {} artifacts in {}ms (avg: {}ms per artifact)", 
                    artifacts.size(), duration, duration / artifacts.size());

        } catch (Exception e) {
            log.error("Exception during batch storage", e);
            return Collections.emptyMap();
        }

        return results;
    }

    /**
     * Store multiple artifacts with automatic batching
     * Splits large lists into optimal batch sizes for Weaviate
     * 
     * @param artifacts List of memory artifacts to store
     * @param batchSize Number of artifacts per batch (recommended: 50-100)
     * @return Total count of successfully stored artifacts
     */
    public int storeBatchWithAutoSplit(List<MemoryArtifact> artifacts, int batchSize) {
        if (artifacts == null || artifacts.isEmpty()) {
            return 0;
        }

        // Validate batch size
        if (batchSize <= 0 || batchSize > 200) {
            log.warn("Invalid batch size {}, using default 50", batchSize);
            batchSize = 50;
        }

        int totalStored = 0;
        int totalBatches = (int) Math.ceil((double) artifacts.size() / batchSize);
        
        log.info("Storing {} artifacts in {} batches of size {}", 
                artifacts.size(), totalBatches, batchSize);

        // Process in batches
        for (int i = 0; i < artifacts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, artifacts.size());
            List<MemoryArtifact> batch = artifacts.subList(i, end);
            
            Map<String, String> batchResults = storeBatch(batch);
            totalStored += batchResults.size();
            
            log.debug("Batch {}/{}: stored {}/{} artifacts", 
                    (i / batchSize) + 1, totalBatches, batchResults.size(), batch.size());
        }

        log.info("Batch storage complete: {}/{} artifacts stored successfully", 
                totalStored, artifacts.size());
        
        return totalStored;
    }

    /**
     * Store only the title vector for multi-vector search
     * Enables focused title-based search with higher precision for short queries
     * 
     * @param artifact The memory artifact to store
     * @param fullArtifactId ID of the full artifact in main class
     * @return ID of the stored title artifact, or null on failure
     */
    public String storeTitleOnly(MemoryArtifact artifact, String fullArtifactId) {
        if (client == null) {
            log.debug("Mock title store: {}", artifact.getTitle());
            return UUID.randomUUID().toString();
        }

        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put("agentId", artifact.getAgentId());
            properties.put("artifactType", artifact.getArtifactType());
            properties.put("title", artifact.getTitle());
            properties.put("artifactId", fullArtifactId);
            properties.put("timestamp", artifact.getTimestamp().toString());

            Result<WeaviateObject> result = client.data().creator()
                    .withClassName(TITLE_CLASS)
                    .withProperties(properties)
                    .run();

            if (result.hasErrors()) {
                log.error("Failed to store title in Weaviate: {}", result.getError());
                return null;
            }

            String id = result.getResult().getId();
            log.info("Stored title in Weaviate: {} (id={})", artifact.getTitle(), id);
            return id;
        } catch (Exception e) {
            log.error("Exception storing title in Weaviate", e);
            return null;
        }
    }

    /**
     * Store artifact in both main class (full content) and title class (title only)
     * Enables multi-vector search strategy for improved precision
     * 
     * @param artifact The memory artifact to store
     * @return ID of the full artifact, or null on failure
     */
    public String storeWithMultiVector(MemoryArtifact artifact) {
        // Store full artifact first
        String fullId = store(artifact);
        if (fullId == null) {
            log.error("Failed to store full artifact: {}", artifact.getTitle());
            return null;
        }

        // Store title-only version with reference to full artifact
        String titleId = storeTitleOnly(artifact, fullId);
        if (titleId == null) {
            log.warn("Failed to store title for artifact: {} (full artifact stored as {})", 
                    artifact.getTitle(), fullId);
            // Still return fullId since main artifact was stored successfully
        }

        return fullId;
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
     * Search title-only class for focused title matching
     * Best for short queries (1-5 words) where title precision is important
     * 
     * @param query Search query string
     * @param limit Maximum number of results
     * @param agentId Agent ID for filtering (optional)
     * @return List of matching artifacts
     */
    public List<MemoryArtifact> searchTitleClass(String query, int limit, String agentId) {
        if (client == null) {
            log.debug("Mock title search for: {}", query);
            return Collections.emptyList();
        }

        try {
            Field title = Field.builder().name("title").build();
            Field agentIdField = Field.builder().name("agentId").build();
            Field artifactType = Field.builder().name("artifactType").build();
            Field artifactId = Field.builder().name("artifactId").build();

            NearTextArgument nearText = NearTextArgument.builder()
                    .concepts(new String[]{query})
                    .build();

            var queryBuilder = client.graphQL().get()
                    .withClassName(TITLE_CLASS)
                    .withNearText(nearText)
                    .withLimit(limit)
                    .withFields(title, agentIdField, artifactType, artifactId);

            // Add agent filter if provided
            if (agentId != null && !agentId.isEmpty()) {
                WhereFilter agentFilter = WhereFilter.builder()
                        .path(new String[]{"agentId"})
                        .operator(Operator.Equal)
                        .valueText(agentId)
                        .build();
                queryBuilder = queryBuilder.withWhere(agentFilter);
            }

            @SuppressWarnings("deprecation")
            Result<GraphQLResponse> result = queryBuilder.run();

            if (result.hasErrors()) {
                log.error("Title search failed: {}", result.getError());
                return Collections.emptyList();
            }

            return parseTitleSearchResults(result.getResult());

        } catch (Exception e) {
            log.error("Exception during title search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Search content class (main class) for full content matching
     * Best for longer queries (5+ words) where content semantic understanding is important
     * 
     * @param query Search query string
     * @param limit Maximum number of results
     * @param agentId Agent ID for filtering (optional)
     * @return List of matching artifacts
     */
    public List<MemoryArtifact> searchContentClass(String query, int limit, String agentId) {
        // This is the same as regular semantic search on main class
        return semanticSearch(query, limit);
    }

    /**
     * Multi-vector search with smart routing based on query length
     * Routes short queries to title search, long queries to content search
     * 
     * @param query Search query string
     * @param limit Maximum number of results
     * @param agentId Agent ID for filtering (optional)
     * @return List of matching artifacts
     */
    public List<MemoryArtifact> multiVectorSearch(String query, int limit, String agentId) {
        if (client == null) {
            log.debug("Mock multi-vector search for: {}", query);
            return Collections.emptyList();
        }

        // Smart routing: short queries (≤5 words) → title search, long queries → content search
        String[] words = query.trim().split("\\s+");
        boolean useTitle = words.length <= 5;

        log.info("Multi-vector search: query='{}' ({} words) -> using {} class", 
                query, words.length, useTitle ? "title" : "content");

        if (useTitle) {
            return searchTitleClass(query, limit, agentId);
        } else {
            return searchContentClass(query, limit, agentId);
        }
    }

    /**
     * Parse title search results from GraphQL response
     */
    private List<MemoryArtifact> parseTitleSearchResults(GraphQLResponse response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }

        List<MemoryArtifact> artifacts = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> get = (Map<String, Object>) data.get("Get");
            
            if (get != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) get.get(TITLE_CLASS);
                
                if (results != null) {
                    for (Map<String, Object> item : results) {
                        MemoryArtifact artifact = new MemoryArtifact();
                        artifact.setTitle((String) item.get("title"));
                        artifact.setAgentId((String) item.get("agentId"));
                        artifact.setArtifactType((String) item.get("artifactType"));
                        // Note: artifactId contains reference to full artifact
                        // Could be used to fetch full content if needed
                        artifacts.add(artifact);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing title search results", e);
        }

        return artifacts;
    }

    /**
     * Hybrid search combining BM25 keyword matching with vector semantic search
     * @param query Search query string
     * @param limit Maximum number of results
     * @param alpha Balance between BM25 (0.0) and vector (1.0). Default 0.75 for balanced hybrid.
     * @param agentId Agent ID for tracking (optional)
```
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

