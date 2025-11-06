#!/bin/bash

# Phase 5: Simplified Hybrid Search Test
# Tests only the core hybrid search functionality without tenant/agent setup

BASE_URL="http://localhost:8080"
AGENT_ID="test-agent-001"

echo "=========================================="
echo "Phase 5: Simplified Hybrid Search Test"
echo "=========================================="
echo ""

# Test 1: Store Memory Artifacts
echo "Test 1: Store Memory Artifacts"
echo "Storing Spring Boot artifact..."
ARTIFACT1=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring Boot REST Controller",
    "content": "Spring Boot provides @RestController annotation for building REST APIs. Use @GetMapping, @PostMapping for HTTP methods.",
    "artifactType": "CODE_SNIPPET",
    "agentId": "'$AGENT_ID'",
    "metadata": {
      "language": "java",
      "framework": "spring-boot"
    }
  }')
echo "$ARTIFACT1"
ARTIFACT1_ID=$(echo "$ARTIFACT1" | jq -r '.id')
echo ""

echo "Storing database artifact..."
ARTIFACT2=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Database Schema Design",
    "content": "Database normalization reduces redundancy. Use foreign keys for relationships. Index frequently queried columns.",
    "artifactType": "DOCUMENTATION",
    "agentId": "'$AGENT_ID'",
    "metadata": {
      "topic": "database",
      "complexity": "intermediate"
    }
  }')
echo "$ARTIFACT2"
ARTIFACT2_ID=$(echo "$ARTIFACT2" | jq -r '.id')
echo ""

echo "Storing security artifact..."
ARTIFACT3=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Security Best Practices",
    "content": "Always validate input. Use parameterized queries to prevent SQL injection. Hash passwords with bcrypt.",
    "artifactType": "BEST_PRACTICE",
    "agentId": "'$AGENT_ID'",
    "metadata": {
      "priority": "high",
      "category": "security"
    }
  }')
echo "$ARTIFACT3"
ARTIFACT3_ID=$(echo "$ARTIFACT3" | jq -r '.id')
echo ""

echo "Storing testing artifact..."
ARTIFACT4=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Integration Testing Strategy",
    "content": "Use TestContainers for integration tests with real databases. Mock external services.",
    "artifactType": "DOCUMENTATION",
    "agentId": "'$AGENT_ID'",
    "metadata": {
      "testing_type": "integration"
    }
  }')
echo "$ARTIFACT4"
ARTIFACT4_ID=$(echo "$ARTIFACT4" | jq -r '.id')
echo ""

if [ ! -z "$ARTIFACT1_ID" ] && [ ! -z "$ARTIFACT2_ID" ] && [ ! -z "$ARTIFACT3_ID" ] && [ ! -z "$ARTIFACT4_ID" ]; then
  echo "✓ Stored 4 diverse artifacts"
else
  echo "✗ Failed to store some artifacts"
  exit 1
fi
echo ""

echo "Waiting 3 seconds for Weaviate indexing..."
sleep 3
echo ""

# Test 2: Pure BM25 Keyword Search (alpha=0.0)
echo "Test 2: Pure BM25 Keyword Search (alpha=0.0)"
echo "Query: 'Spring Boot REST' - Should prioritize exact keyword matches"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Spring Boot REST",
    "limit": 3,
    "alpha": 0.0,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
echo "Top result title: $(echo "$RESULT" | jq -r '.results[0].title // "NONE"')"
echo ""

# Test 3: Balanced Hybrid Search (alpha=0.5)
echo "Test 3: Balanced Hybrid Search (alpha=0.5)"
echo "Query: 'API development best practices'"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "API development best practices",
    "limit": 3,
    "alpha": 0.5,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
echo "Top result title: $(echo "$RESULT" | jq -r '.results[0].title // "NONE"')"
echo ""

# Test 4: Semantic-Focused Search (alpha=0.75, default)
echo "Test 4: Semantic-Focused Search (alpha=0.75, default)"
echo "Query: 'preventing SQL attacks' - Should use semantic understanding"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "preventing SQL attacks",
    "limit": 3,
    "alpha": 0.75,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
echo "Top result title: $(echo "$RESULT" | jq -r '.results[0].title // "NONE"')"
echo "Expected: Security Best Practices (mentions SQL injection prevention)"
echo ""

# Test 5: Pure Vector Search (alpha=1.0)
echo "Test 5: Pure Vector Search (alpha=1.0)"
echo "Query: 'data storage design patterns'"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "data storage design patterns",
    "limit": 3,
    "alpha": 1.0,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
echo "Top result title: $(echo "$RESULT" | jq -r '.results[0].title // "NONE"')"
echo "Expected: Database Schema Design"
echo ""

# Test 6: Metadata Filtering
echo "Test 6: Metadata Filtering by Artifact Type"
echo "Filter: Only CODE_SNIPPET artifacts"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/search-filtered" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "programming",
    "limit": 5,
    "filters": {
      "artifactType": "CODE_SNIPPET"
    },
    "useHybrid": true,
    "alpha": 0.75,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
echo "Result count: $(echo "$RESULT" | jq '.results | length')"
echo "Expected: 1 (only Spring Boot artifact)"
echo ""

# Test 7: Batch Operations
echo "Test 7: Batch Operations"
echo "Storing 5 artifacts in a batch..."
BATCH_RESULT=$(curl -s -X POST "$BASE_URL/api/memory/artifacts/batch" \
  -H "Content-Type: application/json" \
  -d '{
    "artifacts": [
      {
        "title": "Batch Test 1",
        "content": "First batch test artifact",
        "artifactType": "DOCUMENTATION",
        "agentId": "'$AGENT_ID'"
      },
      {
        "title": "Batch Test 2",
        "content": "Second batch test artifact",
        "artifactType": "DOCUMENTATION",
        "agentId": "'$AGENT_ID'"
      },
      {
        "title": "Batch Test 3",
        "content": "Third batch test artifact",
        "artifactType": "DOCUMENTATION",
        "agentId": "'$AGENT_ID'"
      },
      {
        "title": "Batch Test 4",
        "content": "Fourth batch test artifact",
        "artifactType": "DOCUMENTATION",
        "agentId": "'$AGENT_ID'"
      },
      {
        "title": "Batch Test 5",
        "content": "Fifth batch test artifact",
        "artifactType": "DOCUMENTATION",
        "agentId": "'$AGENT_ID'"
      }
    ],
    "batchSize": 50
  }')
echo "$BATCH_RESULT" | jq '.'
STORED_COUNT=$(echo "$BATCH_RESULT" | jq -r '.storedCount // 0')
echo "Stored count: $STORED_COUNT"
echo "Expected: 5"
echo ""

# Test 8: Invalid Alpha Parameter (should reject)
echo "Test 8: Invalid Alpha Parameter (alpha=1.5, should be 0.0-1.0)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "test",
    "limit": 3,
    "alpha": 1.5,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STATUS=$(echo "$RESULT" | jq -r '.status // "unknown"')
if [ "$STATUS" == "400" ] || [ "$STATUS" == "500" ]; then
  echo "✓ Correctly rejected invalid alpha parameter"
else
  echo "⚠ Warning: Invalid alpha should have been rejected"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "All 8 tests completed."
echo ""
echo "Key Validations:"
echo "1. ✓ Artifact storage working"
echo "2. Alpha parameter variations tested (0.0, 0.5, 0.75, 1.0)"
echo "3. Metadata filtering tested"
echo "4. Batch operations tested"
echo "5. Parameter validation tested"
echo ""
echo "Next Steps:"
echo "1. Review test results above"
echo "2. Verify different alpha values return different rankings"
echo "3. Check that semantic search (alpha=0.75/1.0) understands intent"
echo "4. Confirm batch operation stored 5 artifacts efficiently"
echo ""
echo "For detailed logs: docker logs agentmesh-app --tail 100"
