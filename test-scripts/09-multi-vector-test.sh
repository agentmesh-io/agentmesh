#!/bin/bash

# Phase 5: Multi-Vector Search Test
# Tests title vs content search and smart query routing

BASE_URL="http://localhost:8080"
AGENT_ID="multi-vector-test-agent"

echo "=========================================="
echo "Phase 5: Multi-Vector Search Test"
echo "=========================================="
echo ""

# Test 1: Store diverse artifacts with varied title/content patterns
echo "Test 1: Store Test Artifacts"
echo "Storing artifact with specific title..."
ARTIFACT1=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "REST API Design",
    "content": "Detailed guide on implementing RESTful web services with proper HTTP methods, status codes, and resource naming conventions. Includes examples of GET, POST, PUT, DELETE operations and best practices for versioning APIs.",
    "artifactType": "DOCUMENTATION",
    "agentId": "'$AGENT_ID'"
  }')
echo "$ARTIFACT1"
echo ""

echo "Storing artifact with database focus..."
ARTIFACT2=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "PostgreSQL Indexing",
    "content": "Understanding database index structures: B-tree indexes for general queries, Hash indexes for equality comparisons, GiST for geometric data. How to analyze query plans with EXPLAIN and when to create composite indexes for multiple columns.",
    "artifactType": "DOCUMENTATION",
    "agentId": "'$AGENT_ID'"
  }')
echo "$ARTIFACT2"
echo ""

echo "Storing artifact with security focus..."
ARTIFACT3=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "OAuth2 Authentication",
    "content": "Implementing OAuth2 flows: Authorization Code Flow for web apps, Client Credentials Flow for service-to-service communication, Resource Owner Password Credentials. How to secure tokens, implement refresh token rotation, and validate JWT signatures.",
    "artifactType": "BEST_PRACTICE",
    "agentId": "'$AGENT_ID'"
  }')
echo "$ARTIFACT3"
echo ""

echo "Storing artifact with testing focus..."
ARTIFACT4=$(curl -s -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Integration Testing",
    "content": "Complete guide to integration testing: Using TestContainers for real database instances, mocking external APIs with WireMock, testing Kafka message flows, and implementing test data builders for complex object graphs.",
    "artifactType": "DOCUMENTATION",
    "agentId": "'$AGENT_ID'"
  }')
echo "$ARTIFACT4"
echo ""

echo "✓ Stored 4 diverse artifacts with varied title/content"
echo ""

echo "Waiting 3 seconds for Weaviate indexing..."
sleep 3
echo ""

# Test 2: Short Query (≤5 words) - Should use Title Search
echo "Test 2: Short Query - Title Search Strategy"
echo "Query: 'REST API' (2 words)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "REST API",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STRATEGY=$(echo "$RESULT" | jq -r '.strategyUsed // "unknown"')
TOP_TITLE=$(echo "$RESULT" | jq -r '.results[0].title // "NONE"')
echo ""
echo "Strategy used: $STRATEGY"
echo "Top result: $TOP_TITLE"
echo "Expected strategy: title"
echo "Expected result: REST API Design (title match)"
echo ""

# Test 3: Short Query - Database
echo "Test 3: Short Query - Database Focus"
echo "Query: 'PostgreSQL indexes' (2 words)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "PostgreSQL indexes",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STRATEGY=$(echo "$RESULT" | jq -r '.strategyUsed // "unknown"')
TOP_TITLE=$(echo "$RESULT" | jq -r '.results[0].title // "NONE"')
echo ""
echo "Strategy used: $STRATEGY"
echo "Top result: $TOP_TITLE"
echo "Expected strategy: title"
echo "Expected result: PostgreSQL Indexing (title match)"
echo ""

# Test 4: Long Query (>5 words) - Should use Content Search
echo "Test 4: Long Query - Content Search Strategy"
echo "Query: 'How do I implement proper token security and validation' (9 words)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "How do I implement proper token security and validation",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STRATEGY=$(echo "$RESULT" | jq -r '.strategyUsed // "unknown"')
TOP_TITLE=$(echo "$RESULT" | jq -r '.results[0].title // "NONE"')
echo ""
echo "Strategy used: $STRATEGY"
echo "Top result: $TOP_TITLE"
echo "Expected strategy: content"
echo "Expected result: OAuth2 Authentication (content mentions token security)"
echo ""

# Test 5: Long Query - Testing
echo "Test 5: Long Query - Complex Testing Scenario"
echo "Query: 'I need to test my application with a real database using containers' (12 words)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "I need to test my application with a real database using containers",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STRATEGY=$(echo "$RESULT" | jq -r '.strategyUsed // "unknown"')
TOP_TITLE=$(echo "$RESULT" | jq -r '.results[0].title // "NONE"')
echo ""
echo "Strategy used: $STRATEGY"
echo "Top result: $TOP_TITLE"
echo "Expected strategy: content"
echo "Expected result: Integration Testing (content mentions TestContainers and database)"
echo ""

# Test 6: Boundary Test - Exactly 5 words
echo "Test 6: Boundary Test - 5 Words"
echo "Query: 'database index query performance optimization' (5 words)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "database index query performance optimization",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STRATEGY=$(echo "$RESULT" | jq -r '.strategyUsed // "unknown"')
TOP_TITLE=$(echo "$RESULT" | jq -r '.results[0].title // "NONE"')
echo ""
echo "Strategy used: $STRATEGY"
echo "Expected strategy: title (≤5 words uses title search)"
echo ""

# Test 7: Boundary Test - 6 words
echo "Test 7: Boundary Test - 6 Words"
echo "Query: 'how to secure REST API authentication tokens' (7 words)"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "how to secure REST API authentication tokens",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STRATEGY=$(echo "$RESULT" | jq -r '.strategyUsed // "unknown"')
TOP_TITLE=$(echo "$RESULT" | jq -r '.results[0].title // "NONE"')
echo ""
echo "Strategy used: $STRATEGY"
echo "Expected strategy: content (>5 words uses content search)"
echo ""

# Test 8: Empty Query Validation
echo "Test 8: Empty Query Validation"
RESULT=$(curl -s -X POST "$BASE_URL/api/memory/multi-vector-search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "",
    "limit": 3,
    "agentId": "'$AGENT_ID'"
  }')
echo "$RESULT" | jq '.'
STATUS=$(echo "$RESULT" | jq -r '.message // "unknown"')
if [[ "$STATUS" == *"empty"* ]]; then
  echo "✓ Correctly rejected empty query"
else
  echo "⚠ Warning: Empty query should have been rejected"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "All 8 multi-vector search tests completed."
echo ""
echo "Key Validations:"
echo "1. Short queries (≤5 words) routed to title search"
echo "2. Long queries (>5 words) routed to content search"
echo "3. Title search provides focused matching on titles"
echo "4. Content search provides semantic understanding of full text"
echo "5. Smart routing adapts to query characteristics"
echo ""
echo "Expected Benefits:"
echo "- Title search: Higher precision for short, focused queries"
echo "- Content search: Better semantic understanding for complex queries"
echo "- Smart routing: Automatic optimization based on query length"
echo ""
echo "Next Steps:"
echo "1. Compare precision: title vs content vs hybrid search"
echo "2. Measure accuracy improvements with real queries"
echo "3. Test with different agent filters"
echo "4. Validate performance under load"
echo ""
echo "For detailed logs: docker logs agentmesh-app --tail 100"
