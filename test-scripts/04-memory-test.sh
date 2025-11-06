#!/bin/bash

# Test Script 4: Vector Database (Weaviate) Memory Tests
# This script tests long-term memory storage and semantic search

set -e  # Exit on error

BASE_URL="http://localhost:8080"

echo "=================================="
echo "Test 4: Vector Database (Weaviate) Memory"
echo "=================================="
echo ""

# Test 4.1: Store SRS document
echo "→ Test 4.1: Storing SRS document..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "SRS",
    "title": "E-Commerce Platform Requirements Specification",
    "content": "The system shall provide comprehensive e-commerce functionality including user authentication with JWT tokens, product catalog management with search and filtering, shopping cart with session persistence, secure payment processing with PCI compliance, order tracking and history, user profile management, admin dashboard for inventory management, and RESTful API for mobile integration. Security requirements include HTTPS encryption, SQL injection prevention, XSS protection, CSRF tokens, and rate limiting.",
    "metadata": {
      "project": "proj-ecommerce-001",
      "version": "1.0",
      "author": "planner-test-001",
      "date": "2025-10-31"
    }
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "id"; then
  echo "✓ PASSED: SRS document stored"
else
  echo "✗ FAILED: Could not store SRS document"
  exit 1
fi
echo ""

# Test 4.2: Store code snippet
echo "→ Test 4.2: Storing JWT token generation code snippet..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "CODE_SNIPPET",
    "title": "JWT Token Generation with Spring Security",
    "content": "public String generateToken(UserDetails userDetails) { Map<String, Object> claims = new HashMap<>(); claims.put(\"roles\", userDetails.getAuthorities()); return Jwts.builder().setClaims(claims).setSubject(userDetails.getUsername()).setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS)).signWith(getSigningKey(), SignatureAlgorithm.HS256).compact(); }",
    "metadata": {
      "language": "java",
      "framework": "spring-security",
      "pattern": "jwt-authentication",
      "author": "coder-test-001"
    }
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "id"; then
  echo "✓ PASSED: Code snippet stored"
else
  echo "✗ FAILED: Could not store code snippet"
  exit 1
fi
echo ""

# Test 4.3: Store REST controller pattern
echo "→ Test 4.3: Storing REST controller pattern..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "CODE_SNIPPET",
    "title": "Spring Boot REST Controller Pattern with Validation",
    "content": "@RestController @RequestMapping(\"/api/products\") @Validated public class ProductController { @Autowired private ProductService productService; @GetMapping public ResponseEntity<List<Product>> getAllProducts() { return ResponseEntity.ok(productService.findAll()); } @PostMapping public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) { Product product = productService.create(request); return ResponseEntity.status(HttpStatus.CREATED).body(product); } @GetMapping(\"/{id}\") public ResponseEntity<Product> getProduct(@PathVariable Long id) { return productService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build()); }}",
    "metadata": {
      "language": "java",
      "framework": "spring-boot",
      "pattern": "rest-controller",
      "features": "validation,error-handling"
    }
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "id"; then
  echo "✓ PASSED: REST controller pattern stored"
else
  echo "✗ FAILED: Could not store REST controller pattern"
  exit 1
fi
echo ""

# Test 4.4: Store failure lesson (MAST)
echo "→ Test 4.4: Storing failure lesson..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "FAILURE_LESSON",
    "title": "Context Loss During Multi-Step Authentication Implementation",
    "content": "During implementation of JWT authentication system, the coder agent lost context of the security requirements specification midway through the task. The agent implemented basic token generation but forgot to include role-based claims and token refresh functionality mentioned in the SRS. Root cause: The agent did not retrieve the SRS from memory before starting each sub-task. Solution: Implement mandatory RAG retrieval of relevant SRS sections before each code generation step. Additionally, the planner should break down complex features into smaller, self-contained tasks with explicit context references.",
    "metadata": {
      "mastCode": "FM-1.4",
      "mastCategory": "INTER_AGENT_MISALIGNMENT",
      "severity": "HIGH",
      "resolved": true,
      "resolution": "Added automatic context retrieval before each task",
      "agentInvolved": "coder-test-001",
      "date": "2025-10-31"
    }
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "id"; then
  echo "✓ PASSED: Failure lesson stored"
else
  echo "✗ FAILED: Could not store failure lesson"
  exit 1
fi
echo ""

# Test 4.5: Store architectural decision
echo "→ Test 4.5: Storing architectural decision..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "ARCHITECTURE_DECISION",
    "title": "Use PostgreSQL for Relational Data, Weaviate for Semantic Search",
    "content": "Decision: We will use PostgreSQL as the primary relational database for structured data (tenants, projects, blackboard entries, billing records) and Weaviate as the vector database for semantic search and long-term memory. Context: The system requires both transactional consistency for operational data and semantic search capabilities for knowledge retrieval. Consequences: This hybrid approach provides ACID guarantees for business logic while enabling powerful semantic search for agent context retrieval. Trade-off: Additional operational complexity managing two databases, but the benefits outweigh the costs.",
    "metadata": {
      "type": "database-architecture",
      "status": "accepted",
      "author": "architect-001"
    }
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "id"; then
  echo "✓ PASSED: Architectural decision stored"
else
  echo "✗ FAILED: Could not store architectural decision"
  exit 1
fi
echo ""

# Wait for indexing
echo "Waiting 10 seconds for vector indexing..."
sleep 10

# Test 4.6: Semantic search - authentication
echo "→ Test 4.6: Semantic search for authentication..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/search?query=user%20authentication%20JWT%20security&limit=5")
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -qi "authentication\|jwt\|token"; then
  echo "✓ PASSED: Semantic search returned relevant results"
else
  echo "✗ FAILED: Semantic search did not return expected results"
  exit 1
fi
echo ""

# Test 4.7: Semantic search - REST patterns
echo "→ Test 4.7: Semantic search for REST controller patterns..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/search?query=REST%20controller%20Spring%20Boot%20validation&limit=3")
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -qi "rest\|controller\|spring"; then
  echo "✓ PASSED: Found REST controller patterns"
else
  echo "✗ FAILED: Could not find REST controller patterns"
  exit 1
fi
echo ""

# Test 4.8: Semantic search - failure lessons
echo "→ Test 4.8: Semantic search for context loss failures..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/search?query=context%20loss%20agent%20forgot%20requirements&limit=5")
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -qi "context\|loss\|requirements"; then
  echo "✓ PASSED: Found failure lesson about context loss"
else
  echo "✗ FAILED: Could not find failure lessons"
  exit 1
fi
echo ""

# Test 4.9: Query by artifact type - SRS
echo "→ Test 4.9: Querying artifacts by type (SRS)..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/artifacts/type/SRS?limit=10")
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "SRS"; then
  echo "✓ PASSED: Retrieved SRS documents"
else
  echo "✗ FAILED: Could not retrieve SRS documents"
  exit 1
fi
echo ""

# Test 4.10: Query by artifact type - CODE_SNIPPET
echo "→ Test 4.10: Querying artifacts by type (CODE_SNIPPET)..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/artifacts/type/CODE_SNIPPET?limit=10")
echo "Response: $RESPONSE"

CODE_COUNT=$(echo "$RESPONSE" | grep -o "CODE_SNIPPET" | wc -l)
if [ "$CODE_COUNT" -ge 2 ]; then
  echo "✓ PASSED: Retrieved code snippets (found $CODE_COUNT)"
else
  echo "✗ FAILED: Expected at least 2 code snippets, found $CODE_COUNT"
  exit 1
fi
echo ""

# Test 4.11: Query by artifact type - FAILURE_LESSON
echo "→ Test 4.11: Querying artifacts by type (FAILURE_LESSON)..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/artifacts/type/FAILURE_LESSON?limit=10")
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "FAILURE_LESSON"; then
  echo "✓ PASSED: Retrieved failure lessons"
else
  echo "✗ FAILED: Could not retrieve failure lessons"
  exit 1
fi
echo ""

# Test 4.12: Complex semantic query
echo "→ Test 4.12: Complex semantic query (e-commerce requirements)..."
RESPONSE=$(curl -s "${BASE_URL}/api/memory/search?query=e-commerce%20shopping%20cart%20payment%20security&limit=5")
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -qi "commerce\|shopping\|payment"; then
  echo "✓ PASSED: Complex semantic search successful"
else
  echo "✗ FAILED: Complex semantic search failed"
  exit 1
fi
echo ""

echo "=================================="
echo "All Vector Database Memory Tests PASSED"
echo "=================================="
echo ""

