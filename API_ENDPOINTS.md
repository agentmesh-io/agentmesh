# AgentMesh API Endpoints - Quick Reference

**Base URL:** `http://localhost:8080`

---

## 📋 Tenant Management

### Create Tenant
```bash
POST /api/tenants
Content-Type: application/json

{
  "organizationId": "acme",
  "name": "Acme Corporation",
  "tier": "STANDARD",
  "dataRegion": "us-east-1"
}
```

### Get Tenant
```bash
GET /api/tenants/{tenantId}
```

### Update Tenant Tier
```bash
PUT /api/tenants/{tenantId}/tier?tier=PREMIUM
```

### Create Project
```bash
POST /api/tenants/{tenantId}/projects
Content-Type: application/json

{
  "name": "My Project",
  "projectKey": "PROJ"
}
```

### Provision Kubernetes Namespace
```bash
POST /api/tenants/{tenantId}/provision
```

---

## 📝 Blackboard (Shared Memory)

### Post to Blackboard
```bash
POST /api/blackboard/entries?agentId=agent-1&entryType=CODE&title=Feature
Content-Type: text/plain

<code content>
```

### Get All Entries
```bash
GET /api/blackboard/entries
```

### Get Entries by Type
```bash
GET /api/blackboard/entries/type/{entryType}

# Examples:
GET /api/blackboard/entries/type/CODE
GET /api/blackboard/entries/type/TEST_CASE
GET /api/blackboard/entries/type/DOCUMENTATION
```

### Get Entries by Agent
```bash
GET /api/blackboard/entries/agent/{agentId}
```

### Get Specific Entry
```bash
GET /api/blackboard/entries/{id}
```

### Update Entry
```bash
PUT /api/blackboard/entries/{id}?agentId=agent-2
Content-Type: text/plain

<updated content>
```

### Create Snapshot
```bash
POST /api/blackboard/snapshots?snapshotName=v1.0
```

### Get Snapshot
```bash
GET /api/blackboard/snapshots/{snapshotId}
```

---

## 🔍 MAST (Self-Correction Monitoring)

### Get Recent Violations (Last 24 hours)
```bash
GET /api/mast/violations/recent
```

### Get Unresolved Violations
```bash
GET /api/mast/violations/unresolved
```

### Get Violations by Agent
```bash
GET /api/mast/violations/agent/{agentId}
```

### Get Failure Mode Statistics
```bash
GET /api/mast/failure-modes
```

### Get Agent Health
```bash
GET /api/mast/health/agent/{agentId}
```

---

## 🤖 Agent Management

### Register Agent
```bash
POST /api/agents
Content-Type: application/json

{
  "agentId": "srs-writer-1",
  "type": "SRS_WRITER",
  "capabilities": ["WRITE_SRS", "ANALYZE_REQUIREMENTS"]
}
```

### Get Agent
```bash
GET /api/agents/{agentId}
```

### Get All Agents
```bash
GET /api/agents
```

### Update Agent Status
```bash
PUT /api/agents/{agentId}/status?status=ACTIVE
```

---

## 💰 Billing

### Get Billing Statement
```bash
GET /api/billing/tenants/{tenantId}/statement?start=2025-10-01T00:00:00Z&end=2025-10-31T23:59:59Z
```

### Get Current Month Usage
```bash
GET /api/billing/tenants/{tenantId}/current-month
```

### Get Estimated Cost
```bash
GET /api/billing/tenants/{tenantId}/estimated-cost
```

---

## 🧠 Memory (Long-term Storage)

### Store Memory Artifact
```bash
POST /api/memory/artifacts
Content-Type: application/json

{
  "type": "CODE_PATTERN",
  "content": "...",
  "metadata": {
    "language": "java",
    "framework": "spring-boot"
  }
}
```

### Search Memory
```bash
GET /api/memory/search?query=authentication&limit=10
```

### Get Artifact
```bash
GET /api/memory/artifacts/{artifactId}
```

---

## 📊 Monitoring & Observability

### Health Check
```bash
GET /actuator/health
```

### Prometheus Metrics
```bash
GET /actuator/prometheus
```

### Application Info
```bash
GET /actuator/info
```

---

## 📖 API Documentation

### Swagger UI (Interactive)
```
http://localhost:8080/swagger-ui.html
```

### OpenAPI Specification (JSON)
```
http://localhost:8080/v3/api-docs
```

---

## 🔧 Examples

### Complete Workflow: Create Tenant and Post to Blackboard
```bash
# 1. Create tenant
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "demo",
    "name": "Demo Corp",
    "tier": "STANDARD"
  }'

# 2. Register agent
curl -X POST http://localhost:8080/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "demo-agent-1",
    "type": "CODE_GENERATOR",
    "capabilities": ["GENERATE_CODE"]
  }'

# 3. Post to blackboard
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=demo-agent-1&entryType=CODE&title=HelloWorld" \
  -H "Content-Type: text/plain" \
  -d 'public class HelloWorld { public static void main(String[] args) { System.out.println("Hello!"); } }'

# 4. Get all blackboard entries
curl http://localhost:8080/api/blackboard/entries

# 5. Check MAST violations
curl http://localhost:8080/api/mast/violations/recent
```

---

## 🎯 Common Use Cases

### Monitor Agent Behavior
```bash
# Get recent violations
curl http://localhost:8080/api/mast/violations/recent

# Get specific agent health
curl http://localhost:8080/api/mast/health/agent/{agentId}

# Get failure mode statistics
curl http://localhost:8080/api/mast/failure-modes
```

### Track Agent Collaboration
```bash
# Get all blackboard entries
curl http://localhost:8080/api/blackboard/entries

# Get entries by type
curl http://localhost:8080/api/blackboard/entries/type/CODE

# Get entries by agent
curl http://localhost:8080/api/blackboard/entries/agent/agent-1
```

### Manage Billing
```bash
# Get current month usage
curl http://localhost:8080/api/billing/tenants/tenant-123/current-month

# Get estimated cost
curl http://localhost:8080/api/billing/tenants/tenant-123/estimated-cost

# Get full statement
curl "http://localhost:8080/api/billing/tenants/tenant-123/statement?start=2025-10-01T00:00:00Z&end=2025-10-31T23:59:59Z"
```

---

## ⚠️ Error Codes

| Code | Meaning | Common Causes |
|------|---------|---------------|
| 200 | Success | Request processed successfully |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid input data |
| 404 | Not Found | Resource doesn't exist |
| 500 | Server Error | Internal server error |

---

**Updated:** October 31, 2025  
**Version:** 1.0.0  
**Status:** ✅ All endpoints tested and working

