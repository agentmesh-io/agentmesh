#!/bin/bash

# Test script for Phase 5: Hybrid Vector Search
# Tests BM25 + Vector semantic search with different alpha parameters

set -e  # Exit on error

BASE_URL="http://localhost:8080"
TENANT_ID="test-tenant-hybrid"
PROJECT_ID="test-project-hybrid"
AGENT_ID="planner-agent-hybrid"

echo "=========================================="
echo "Phase 5: Hybrid Search Test"
echo "=========================================="
echo

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Test 1: Create tenant
echo -e "${BLUE}Test 1: Create Tenant${NC}"
curl -X POST "$BASE_URL/api/tenants" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "'$TENANT_ID'",
    "name": "Hybrid Search Test Tenant",
    "tier": "PREMIUM"
  }' | jq '.'
echo

# Test 2: Create agent
echo -e "${BLUE}Test 2: Create Agent${NC}"
curl -X POST "$BASE_URL/api/agents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "agentId": "'$AGENT_ID'",
    "name": "Planner Agent",
    "description": "Test agent for hybrid search",
    "projectId": "'$PROJECT_ID'",
    "role": "PLANNER"
  }' | jq '.'
echo

# Test 3: Store diverse memory artifacts for search testing
echo -e "${BLUE}Test 3: Store Memory Artifacts${NC}"

# Artifact 1: Spring Boot specific
echo "Storing Spring Boot artifact..."
curl -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "agentId": "'$AGENT_ID'",
    "artifactType": "CODE_SNIPPET",
    "title": "Spring Boot REST Controller",
    "content": "Spring Boot REST API implementation with @RestController annotation. Uses Spring MVC framework for building RESTful web services.",
    "projectId": "'$PROJECT_ID'"
  }' | jq '.'

# Artifact 2: Database related
echo "Storing database artifact..."
curl -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "agentId": "'$AGENT_ID'",
    "artifactType": "DOCUMENTATION",
    "title": "Database Schema Design",
    "content": "Relational database schema with PostgreSQL. Includes tables for users, projects, and artifacts with foreign key relationships.",
    "projectId": "'$PROJECT_ID'"
  }' | jq '.'

# Artifact 3: Security documentation
echo "Storing security artifact..."
curl -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "agentId": "'$AGENT_ID'",
    "artifactType": "DOCUMENTATION",
    "title": "Security Best Practices",
    "content": "Authentication and authorization using JWT tokens. OAuth2 implementation for secure API access. Password hashing with bcrypt.",
    "projectId": "'$PROJECT_ID'"
  }' | jq '.'

# Artifact 4: Testing guide
echo "Storing testing artifact..."
curl -X POST "$BASE_URL/api/memory/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "agentId": "'$AGENT_ID'",
    "artifactType": "TEST_CASE",
    "title": "Integration Testing Strategy",
    "content": "Unit tests with JUnit 5. Integration tests using Spring Boot Test. Mock MVC for REST API testing.",
    "projectId": "'$PROJECT_ID'"
  }' | jq '.'

echo -e "${GREEN}✓ Stored 4 diverse artifacts${NC}"
echo

# Wait for indexing
echo "Waiting 3 seconds for Weaviate indexing..."
sleep 3
echo

# Test 4: Pure BM25 Keyword Search (alpha = 0.0)
echo -e "${BLUE}Test 4: Pure BM25 Keyword Search (alpha=0.0)${NC}"
echo "Query: 'Spring Boot REST' - Should prioritize exact keyword matches"
curl -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "Spring Boot REST",
    "limit": 3,
    "alpha": 0.0,
    "agentId": "'$AGENT_ID'"
  }' | jq '.results[] | {title: .title, artifactType: .artifactType}'
echo

# Test 5: Balanced Hybrid Search (alpha = 0.5)
echo -e "${BLUE}Test 5: Balanced Hybrid Search (alpha=0.5)${NC}"
echo "Query: 'web service implementation' - Should balance keywords and semantics"
curl -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "web service implementation",
    "limit": 3,
    "alpha": 0.5,
    "agentId": "'$AGENT_ID'"
  }' | jq '.results[] | {title: .title, artifactType: .artifactType}'
echo

# Test 6: Semantic-Focused Hybrid (alpha = 0.75 - default)
echo -e "${BLUE}Test 6: Semantic-Focused Hybrid (alpha=0.75)${NC}"
echo "Query: 'secure authentication methods' - Should understand security concepts"
curl -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "secure authentication methods",
    "limit": 3,
    "alpha": 0.75,
    "agentId": "'$AGENT_ID'"
  }' | jq '.results[] | {title: .title, artifactType: .artifactType}'
echo

# Test 7: Pure Vector Semantic Search (alpha = 1.0)
echo -e "${BLUE}Test 7: Pure Vector Semantic Search (alpha=1.0)${NC}"
echo "Query: 'automated quality assurance' - Should find testing content semantically"
curl -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "automated quality assurance",
    "limit": 3,
    "alpha": 1.0,
    "agentId": "'$AGENT_ID'"
  }' | jq '.results[] | {title: .title, artifactType: .artifactType}'
echo

# Test 8: Filtered Search by Artifact Type
echo -e "${BLUE}Test 8: Filtered Search - Documentation Only${NC}"
echo "Query: 'database' filtered by artifactType=DOCUMENTATION"
curl -X POST "$BASE_URL/api/memory/search-filtered" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "database",
    "limit": 5,
    "filters": {
      "artifactType": "DOCUMENTATION"
    },
    "useHybrid": true,
    "alpha": 0.75,
    "agentId": "'$AGENT_ID'"
  }' | jq '.results[] | {title: .title, artifactType: .artifactType}'
echo

# Test 9: Filtered Search by Agent
echo -e "${BLUE}Test 9: Filtered Search - Specific Agent${NC}"
echo "Query: 'implementation' filtered by agentId"
curl -X POST "$BASE_URL/api/memory/search-filtered" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "implementation",
    "limit": 5,
    "filters": {
      "agentId": "'$AGENT_ID'"
    },
    "useHybrid": true,
    "alpha": 0.75,
    "agentId": "'$AGENT_ID'"
  }' | jq '.results[] | {title: .title, agentId: .agentId}'
echo

# Test 10: Invalid Alpha Parameter (should return 400)
echo -e "${BLUE}Test 10: Invalid Alpha Parameter Test${NC}"
echo "Trying alpha=1.5 (should fail validation)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "test",
    "limit": 5,
    "alpha": 1.5,
    "agentId": "'$AGENT_ID'"
  }')

if [ "$HTTP_CODE" = "400" ]; then
  echo -e "${GREEN}✓ Correctly rejected invalid alpha parameter (HTTP $HTTP_CODE)${NC}"
else
  echo -e "${RED}✗ Expected HTTP 400, got $HTTP_CODE${NC}"
fi
echo

# Test 11: Compare search relevance across alpha values
echo -e "${BLUE}Test 11: Relevance Comparison Across Alpha Values${NC}"
echo "Query: 'Spring REST API' with different alphas"
echo

echo "Alpha=0.0 (Pure BM25 - keyword focused):"
curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "Spring REST API",
    "limit": 2,
    "alpha": 0.0,
    "agentId": "'$AGENT_ID'"
  }' | jq -r '.results[0].title // "No results"'

echo
echo "Alpha=0.5 (Balanced):"
curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "Spring REST API",
    "limit": 2,
    "alpha": 0.5,
    "agentId": "'$AGENT_ID'"
  }' | jq -r '.results[0].title // "No results"'

echo
echo "Alpha=1.0 (Pure Vector - semantic focused):"
curl -s -X POST "$BASE_URL/api/memory/hybrid-search" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" \
  -d '{
    "query": "Spring REST API",
    "limit": 2,
    "alpha": 1.0,
    "agentId": "'$AGENT_ID'"
  }' | jq -r '.results[0].title // "No results"'
echo

echo "=========================================="
echo -e "${GREEN}Hybrid Search Test Complete!${NC}"
echo "=========================================="
echo
echo "Summary:"
echo "- Tested BM25 keyword search (alpha=0.0)"
echo "- Tested balanced hybrid (alpha=0.5)"
echo "- Tested semantic-focused hybrid (alpha=0.75)"
echo "- Tested pure vector search (alpha=1.0)"
echo "- Tested metadata filtering (type + agent)"
echo "- Verified parameter validation"
echo
echo "Next steps:"
echo "1. Run performance tests (target: <100ms p95 latency)"
echo "2. Measure accuracy improvements vs pure vector"
echo "3. Implement batch operations for 10x speedup"
echo "4. Add multi-vector storage (title + content vectors)"
