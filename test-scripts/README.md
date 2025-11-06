# AgentMesh Test Scripts

This directory contains automated test scripts for verifying the AgentMesh system functionality.

## Overview

The test scripts validate all major features of the AgentMesh platform:

1. **Tenant Management** - Multi-tenancy, tier upgrades, project management
2. **Agent Lifecycle** - Agent creation, state management, inter-agent communication
3. **Blackboard Architecture** - Shared memory, artifact storage, concurrent access
4. **Vector Database (Weaviate)** - Long-term memory, semantic search, knowledge retrieval
5. **MAST** - Failure detection and agent health monitoring

## Prerequisites

- Docker and Docker Compose installed
- AgentMesh services running (`docker-compose up -d`)
- curl command-line tool
- bash shell

## Quick Start

### Run All Tests

```bash
# Make scripts executable
chmod +x test-scripts/*.sh

# Run all test suites
cd test-scripts
./run-all-tests.sh
```

### Run Individual Test Suites

```bash
# Tenant management tests
./01-tenant-management-test.sh

# Agent lifecycle tests
./02-agent-lifecycle-test.sh

# Blackboard architecture tests
./03-blackboard-test.sh

# Vector database memory tests
./04-memory-test.sh

# MAST monitoring tests
./05-mast-test.sh
```

## Test Scripts Description

### 1. Tenant Management Test (`01-tenant-management-test.sh`)

Tests the multi-tenancy system:
- Create tenant with FREE tier
- Retrieve tenant by ID and organization ID
- Upgrade tenant to PROFESSIONAL tier
- Create multiple projects
- Verify tier limits enforcement

**Expected Duration**: ~5 seconds

### 2. Agent Lifecycle Test (`02-agent-lifecycle-test.sh`)

Tests agent creation and management:
- Create specialized agents (Planner, Coder, Reviewer, Debugger)
- List all agents
- Start and stop agents
- Inter-agent message passing
- View message log
- Error handling for non-existent agents

**Expected Duration**: ~10 seconds

### 3. Blackboard Architecture Test (`03-blackboard-test.sh`)

Tests the shared blackboard system:
- Post different artifact types (tasks, code, reviews, test results)
- Query entries by type and agent
- Update existing entries
- Create snapshots for rollback
- Concurrent entry posting (stress test)

**Expected Duration**: ~15 seconds

### 4. Vector Database Memory Test (`04-memory-test.sh`)

Tests long-term memory and semantic search:
- Store various artifact types (SRS, code snippets, failure lessons, architectural decisions)
- Semantic search with different queries
- Query artifacts by type
- Complex semantic queries

**Expected Duration**: ~20 seconds (includes indexing wait time)

### 5. MAST Test (`05-mast-test.sh`)

Tests failure detection and monitoring:
- Retrieve recent violations
- Get unresolved violations
- Query violations by agent
- Get failure mode statistics
- Check agent health scores

**Expected Duration**: ~5 seconds

## Output Format

Each test script provides detailed output:

```
==================================
Test X: Feature Name
==================================

→ Test X.1: Specific test case...
Response: {...}
✓ PASSED: Description of what passed

→ Test X.2: Another test case...
Response: {...}
✗ FAILED: Description of failure

==================================
All Tests PASSED/FAILED
==================================
```

## Success Criteria

All tests should pass with the following indicators:
- ✓ PASSED markers for each test case
- HTTP status codes as expected (200, 201, 404, etc.)
- Response data matches expected format
- Data is persisted correctly

## Troubleshooting

### Services Not Running

```bash
# Start services
cd /path/to/AgentMesh
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f
```

### API Not Responding

```bash
# Check if API is healthy
curl http://localhost:8080/actuator/health

# Check logs
docker logs agentmesh-app -f
```

### Test Failures

1. Check service health: `docker-compose ps`
2. Review logs: `docker-compose logs -f agentmesh`
3. Verify database connectivity: `docker logs agentmesh-postgres`
4. Check Weaviate status: `curl http://localhost:8081/v1/.well-known/ready`
5. Verify Temporal: `curl http://localhost:8082/api/v1/namespaces`

### Database Issues

```bash
# Reset database
docker-compose down -v
docker-compose up -d

# Wait for services to be ready
sleep 10
```

## Test Data Cleanup

The tests create data that persists in the database. To clean up:

```bash
# Stop and remove containers with volumes
docker-compose down -v

# Start fresh
docker-compose up -d
```

## Extending Tests

To add new tests:

1. Create a new test script: `06-new-feature-test.sh`
2. Follow the existing pattern:
   ```bash
   #!/bin/bash
   set -e
   BASE_URL="http://localhost:8080"
   
   echo "Testing new feature..."
   # Add test cases
   ```
3. Add to `run-all-tests.sh` TEST_SCRIPTS array
4. Make executable: `chmod +x 06-new-feature-test.sh`

## Integration with CI/CD

These scripts can be integrated into CI/CD pipelines:

```yaml
# Example GitHub Actions
- name: Run Tests
  run: |
    docker-compose up -d
    sleep 30  # Wait for services
    cd test-scripts
    ./run-all-tests.sh
```

## Performance Testing

For load testing, use tools like:
- **Apache JMeter**: GUI-based load testing
- **k6**: Modern load testing tool
- **wrk**: HTTP benchmarking tool

Example with k6:

```javascript
import http from 'k6/http';
import { check } from 'k6';

export default function() {
  let res = http.get('http://localhost:8080/api/agents');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}
```

## Documentation

For detailed API documentation, see:
- [TEST-SCENARIOS.md](../TEST-SCENARIOS.md) - Comprehensive test scenarios
- [API Documentation](../docs/API.md) - API reference
- [Architecture](../docs/ARCHITECTURE.md) - System architecture

## Support

If you encounter issues:
1. Check the logs: `docker-compose logs -f`
2. Verify service health: `docker-compose ps`
3. Review test output for specific error messages
4. Check database connectivity
5. Ensure all prerequisites are met

## Contributing

When contributing new tests:
1. Follow existing naming conventions
2. Add comprehensive test cases
3. Include error handling tests
4. Document expected behavior
5. Update this README

