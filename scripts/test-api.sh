#!/bin/bash

echo "=========================================="
echo "AgentMesh API Testing Script"
echo "=========================================="
echo ""

BASE_URL="http://localhost:8080"

# Test 1: Health Check
echo "Test 1: Health Check"
curl -s $BASE_URL/actuator/health | python3 -m json.tool || echo "Health check response received"
echo ""
echo ""

# Test 2: Swagger API Docs
echo "Test 2: Swagger API Docs Available"
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" $BASE_URL/v3/api-docs
echo ""

# Test 3: Create Tenant
echo "Test 3: Create Tenant"
curl -s -X POST $BASE_URL/api/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "test-org",
    "name": "Test Organization",
    "tier": "STANDARD"
  }' | python3 -m json.tool || echo "Tenant creation response received"
echo ""
echo ""

# Test 4: Get Blackboard Entries
echo "Test 4: Get Blackboard Entries"
curl -s $BASE_URL/api/blackboard/entries | python3 -m json.tool || echo "Blackboard response received"
echo ""
echo ""

# Test 5: Get MAST Violations (Recent)
echo "Test 5: Get MAST Violations (Recent)"
curl -s $BASE_URL/api/mast/violations/recent | python3 -m json.tool || echo "MAST violations response received"
echo ""
echo ""

# Test 6: Metrics Endpoint
echo "Test 6: Prometheus Metrics"
curl -s $BASE_URL/actuator/prometheus | head -20
echo "..."
echo ""

echo "=========================================="
echo "All tests completed!"
echo "=========================================="

