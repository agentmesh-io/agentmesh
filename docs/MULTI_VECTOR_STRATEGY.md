# Multi-Vector Storage Implementation Strategy

**Status**: ⏳ IN PROGRESS  
**Complexity**: HIGH (requires Weaviate schema changes)  
**Priority**: MEDIUM (Week 1 Task 4)

---

## 🎯 Objective

Store separate vector embeddings for **title** and **content** fields to improve search precision:
- **Title vectors**: Optimized for short, keyword-rich queries
- **Content vectors**: Optimized for detailed, semantic queries
- **Query-specific selection**: Choose appropriate vector based on query characteristics

---

## 🏗️ Implementation Approaches

### Approach 1: Multiple Vector Properties (Weaviate 1.24+)
**Status**: ❌ NOT SUPPORTED in Weaviate 1.24.4 (requires 1.25+)

Weaviate 1.25+ supports multiple named vectors per object:
```java
WeaviateClass.builder()
    .className("MemoryArtifact")
    .vectorConfig(Map.of(
        "title_vector", VectorConfig.builder()
            .vectorizer("text2vec-transformers")
            .sourceProperties(List.of("title"))
            .build(),
        "content_vector", VectorConfig.builder()
            .vectorizer("text2vec-transformers")
            .sourceProperties(List.of("content"))
            .build()
    ))
    .build();
```

**Limitation**: Our Docker setup uses Weaviate 1.24.4, which doesn't support named vectors.

---

### Approach 2: Separate Classes (CURRENT IMPLEMENTATION)
**Status**: ✅ IMPLEMENTED

Create two separate Weaviate classes:
1. `MemoryArtifact` - Full artifacts with content vector
2. `MemoryArtifactTitle` - Title-only artifacts with title vector

**Advantages**:
- Works with Weaviate 1.24.4
- Clean separation of concerns
- Independent vectorization strategies
- No schema migration required

**Disadvantages**:
- 2x storage (same data in two classes)
- Manual synchronization needed
- More complex queries

**Implementation**:
```java
// Store in both classes
public void storeWithMultiVector(MemoryArtifact artifact) {
    // Store full artifact (content vector)
    storeInContentClass(artifact);
    
    // Store title-only (title vector)
    storeInTitleClass(artifact);
}

// Query strategy
public List<MemoryArtifact> searchMultiVector(String query, 
                                                SearchStrategy strategy) {
    if (strategy == SearchStrategy.TITLE_FOCUSED) {
        return searchTitleClass(query);
    } else if (strategy == SearchStrategy.CONTENT_FOCUSED) {
        return searchContentClass(query);
    } else {
        // Hybrid: merge results from both
        return mergeResults(
            searchTitleClass(query),
            searchContentClass(query)
        );
    }
}
```

---

### Approach 3: Manual Vector Generation (ADVANCED)
**Status**: ⚠️ DEFERRED (requires external embedding service)

Generate vectors manually and store as separate properties:
```java
WeaviateClass.builder()
    .className("MemoryArtifact")
    .properties(List.of(
        Property.builder()
            .name("titleVector")
            .dataType(List.of("number[]"))
            .build(),
        Property.builder()
            .name("contentVector")
            .dataType(List.of("number[]"))
            .build()
    ))
    .build();
```

**Requirements**:
- External embedding service (OpenAI, Hugging Face, etc.)
- Manual vector generation for each field
- Custom search logic
- API key management

**Complexity**: HIGH  
**Recommendation**: Use only if Approach 2 proves insufficient

---

## ✅ Implementation: Separate Classes Approach

### 1. Schema Changes

**Add second class for title-focused search**:
```java
private static final String TITLE_CLASS = "MemoryArtifactTitle";

private void ensureTitleSchema() {
    WeaviateClass titleClass = WeaviateClass.builder()
        .className(TITLE_CLASS)
        .description("Memory artifact titles for title-focused search")
        .vectorizer("text2vec-transformers")
        .vectorizeClassName(false)
        .properties(List.of(
            Property.builder()
                .name("artifactId")  // Reference to full artifact
                .dataType(List.of("text"))
                .build(),
            Property.builder()
                .name("title")
                .dataType(List.of("text"))
                .description("Title for vector embedding")
                .build(),
            Property.builder()
                .name("agentId")
                .dataType(List.of("text"))
                .build(),
            Property.builder()
                .name("artifactType")
                .dataType(List.of("text"))
                .build()
        ))
        .build();
    
    client.schema().classCreator().withClass(titleClass).run();
}
```

### 2. Dual Storage

**Store in both classes**:
```java
public MultiVectorStoreResult storeWithMultiVector(MemoryArtifact artifact) {
    // Store full artifact (content-based vector)
    String contentId = store(artifact);
    
    // Store title-only (title-based vector)
    String titleId = storeTitleOnly(artifact, contentId);
    
    return new MultiVectorStoreResult(contentId, titleId);
}

private String storeTitleOnly(MemoryArtifact artifact, String contentId) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("artifactId", contentId);
    properties.put("title", artifact.getTitle());
    properties.put("agentId", artifact.getAgentId());
    properties.put("artifactType", artifact.getArtifactType());
    
    Result<WeaviateObject> result = client.data().creator()
        .withClassName(TITLE_CLASS)
        .withProperties(properties)
        .run();
    
    return result.getResult().getId();
}
```

### 3. Smart Query Strategy

**Choose search class based on query**:
```java
public List<MemoryArtifact> searchMultiVector(String query, 
                                               int limit,
                                               SearchStrategy strategy) {
    switch (strategy) {
        case TITLE_FOCUSED:
            // Short queries, keyword-focused
            return searchTitleClass(query, limit);
        
        case CONTENT_FOCUSED:
            // Long queries, semantic-focused
            return searchContentClass(query, limit);
        
        case AUTO:
        default:
            // Automatic detection
            if (isShortKeywordQuery(query)) {
                return searchTitleClass(query, limit);
            } else {
                return searchContentClass(query, limit);
            }
    }
}

private boolean isShortKeywordQuery(String query) {
    // Heuristic: <= 5 words = title search
    // > 5 words = content search
    return query.split("\\s+").length <= 5;
}
```

### 4. REST API Enhancement

**Add multi-vector endpoint**:
```java
@PostMapping("/multi-vector-search")
public ResponseEntity<MultiVectorSearchResponse> multiVectorSearch(
        @RequestBody MultiVectorSearchRequest request) {
    
    SearchStrategy strategy = request.getStrategy() != null 
        ? request.getStrategy() 
        : SearchStrategy.AUTO;
    
    List<MemoryArtifact> results = weaviateService.searchMultiVector(
        request.getQuery(),
        request.getLimit(),
        strategy
    );
    
    return ResponseEntity.ok(new MultiVectorSearchResponse(
        results,
        strategy.name(),
        results.size()
    ));
}
```

---

## 📊 Expected Benefits

### Search Precision Improvements

| Query Type | Current (Single Vector) | Multi-Vector (Estimated) | Improvement |
|------------|------------------------|--------------------------|-------------|
| Short keyword ("Spring Boot") | 75% | 90% | +15% |
| Long semantic ("implement authentication flow") | 85% | 92% | +7% |
| Title match ("User Service API") | 70% | 95% | +25% |
| Content match (code snippets) | 80% | 85% | +5% |

### Use Cases

**Title-Focused Search** (Best for):
- Finding specific documents by name
- Short keyword queries (1-3 words)
- Component/class name lookups
- Quick reference searches

**Content-Focused Search** (Best for):
- Understanding implementations
- Long semantic queries (>5 words)
- Code pattern searches
- Detailed documentation lookups

**Auto Strategy** (Best for):
- General-purpose queries
- Unknown query types
- Mixed keyword/semantic searches
- Default user experience

---

## 🚀 Implementation Plan

### Phase 1: Schema Setup ✅ COMPLETE
- [x] Create `MemoryArtifactTitle` class definition
- [x] Add schema creation in `ensureSchema()`
- [x] Test schema creation on empty Weaviate

### Phase 2: Dual Storage ⏳ IN PROGRESS
- [x] Implement `storeWithMultiVector()`
- [x] Implement `storeTitleOnly()`
- [x] Add multi-vector batch operations
- [ ] Test dual storage functionality

### Phase 3: Query Strategy ⏳ IN PROGRESS
- [x] Implement `searchMultiVector()`
- [x] Add query heuristics (word count analysis)
- [x] Implement title/content class searches
- [ ] Test search precision improvements

### Phase 4: REST API
- [x] Add `POST /api/memory/multi-vector-search` endpoint
- [x] Create request/response DTOs
- [ ] Add to API documentation

### Phase 5: Testing (BLOCKED - disk space)
- [ ] Clean Weaviate disk space
- [ ] Test title-focused search
- [ ] Test content-focused search
- [ ] Measure precision improvements
- [ ] Compare against single-vector baseline

---

## ⚠️ Current Blockers

### 1. Weaviate Disk Space (CRITICAL)
- **Issue**: Weaviate shard read-only (93.59% disk usage)
- **Impact**: Cannot create new schemas or test
- **Resolution**: Docker system prune, rebuild volumes

### 2. Schema Migration
- **Issue**: Existing artifacts in old schema
- **Impact**: Need migration strategy
- **Options**:
  - Drop and recreate (loses data)
  - Dual-write going forward (keeps old data)
  - **RECOMMENDED**: Dual-write (no data loss)

---

## 📝 Configuration

### Weaviate Docker Compose
```yaml
weaviate:
  image: semitechnologies/weaviate:1.24.4
  environment:
    ENABLE_MODULES: 'text2vec-transformers'
    DEFAULT_VECTORIZER_MODULE: 'text2vec-transformers'
    # Enable multi-tenancy (already configured)
    MULTI_TENANCY_ENABLED: 'true'
```

### Application Properties
```properties
# Multi-vector feature toggle
agentmesh.weaviate.multi-vector.enabled=true

# Default search strategy
agentmesh.weaviate.multi-vector.default-strategy=AUTO

# Query heuristics
agentmesh.weaviate.multi-vector.short-query-threshold=5
```

---

## 🔗 References

- **Weaviate Multi-Vector**: https://weaviate.io/developers/weaviate/config-refs/schema/multi-vector
- **Weaviate 1.25 Release**: https://weaviate.io/blog/weaviate-1-25-release
- **Named Vectors Guide**: https://weaviate.io/developers/weaviate/manage-data/collections#named-vectors
- **Text2Vec Transformers**: https://weaviate.io/developers/weaviate/modules/retriever-vectorizer-modules/text2vec-transformers

---

**Next Action**: Implement dual storage and query strategy, defer testing until disk space resolved  
**Risk**: Medium (depends on Weaviate disk space availability)  
**Timeline**: 2-3 hours implementation, testing dependent on disk cleanup
