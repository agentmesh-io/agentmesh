# AgentMesh Quick Reference Card

## 🚀 Quick Start

```bash
# Start all services
cd /path/to/AgentMesh
docker-compose up -d

# Wait for services (30 seconds)
sleep 30

# Check status
docker-compose ps

# Run tests
cd test-scripts && ./run-all-tests.sh
```

---

## 🌐 Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| AgentMesh API | http://localhost:8080 | Main REST API |
| Temporal UI | http://localhost:8082 | Workflow management |
| Weaviate | http://localhost:8081 | Vector database |
| PostgreSQL | localhost:5432 | Primary database |

---

## 📋 Essential API Calls

### Tenant Management

```bash
# Create tenant
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{"organizationId": "org-001", "organizationName": "My Org", "tier": "PROFESSIONAL"}'

# Get tenant
curl http://localhost:8080/api/tenants/{tenantId}

# Upgrade tier
curl -X PUT http://localhost:8080/api/tenants/{tenantId}/tier \
  -H "Content-Type: application/json" \
  -d '{"tier": "ENTERPRISE"}'
```

### Agent Management

```bash
# Create agent
curl -X POST "http://localhost:8080/api/agents?id=my-agent-001"

# List agents
curl http://localhost:8080/api/agents

# Start agent
curl -X POST http://localhost:8080/api/agents/my-agent-001/start

# Send message
curl -X POST http://localhost:8080/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{"senderId": "agent1", "recipientId": "agent2", "content": "Hello"}'
```

### Blackboard

```bash
# Post entry
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=agent1&entryType=CODE&title=MyCode" \
  -H "Content-Type: text/plain" \
  -d "Code content here"

# Get all entries
curl http://localhost:8080/api/blackboard/entries

# Get by type
curl http://localhost:8080/api/blackboard/entries/type/CODE

# Create snapshot
curl -X POST http://localhost:8080/api/blackboard/snapshot
```

### Vector Memory

```bash
# Store artifact
curl -X POST http://localhost:8080/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{"artifactType": "CODE_SNIPPET", "title": "Example", "content": "...", "metadata": {}}'

# Semantic search
curl "http://localhost:8080/api/memory/search?query=authentication&limit=5"

# Get by type
curl "http://localhost:8080/api/memory/artifacts/type/CODE_SNIPPET?limit=10"
```

### MAST Monitoring

```bash
# Recent violations
curl http://localhost:8080/api/mast/violations/recent

# Agent health
curl http://localhost:8080/api/mast/health/{agentId}

# Statistics
curl http://localhost:8080/api/mast/statistics/failure-modes
```

---

## 🐳 Docker Commands

```bash
# Start services
docker-compose up -d

# Stop services (keep data)
docker-compose down

# Stop and remove all data
docker-compose down -v

# View all logs
docker-compose logs -f

# View specific service logs
docker logs agentmesh-app -f
docker logs agentmesh-postgres -f
docker logs agentmesh-weaviate -f
docker logs agentmesh-temporal -f

# Check service health
docker-compose ps

# Restart service
docker-compose restart agentmesh

# Rebuild and restart
docker-compose up -d --build
```

---

## 🗄️ Database Access

```bash
# Connect to PostgreSQL
docker exec -it agentmesh-postgres psql -U agentmesh -d agentmesh

# Common SQL queries
\dt                                    # List tables
\d tablename                           # Describe table
SELECT * FROM tenants;                 # View tenants
SELECT * FROM blackboard_entries ORDER BY created_at DESC LIMIT 10;
SELECT * FROM mast_violations WHERE resolved = false;
\q                                     # Quit
```

---

## 🧪 Testing

```bash
# Run all tests
cd test-scripts
./run-all-tests.sh

# Run specific test
./01-tenant-management-test.sh
./02-agent-lifecycle-test.sh
./03-blackboard-test.sh
./04-memory-test.sh
./05-mast-test.sh
```

---

## 🔍 Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Weaviate ready
curl http://localhost:8081/v1/.well-known/ready

# Weaviate schema
curl http://localhost:8081/v1/schema

# Temporal namespaces
curl http://localhost:8082/api/v1/namespaces
```

---

## 🏗️ Build Commands

```bash
# Build with Maven
mvn clean package -DskipTests

# Build with tests
mvn clean package

# Run specific test
mvn test -Dtest=TestClassName

# Build Docker image
docker build -t agentmesh:latest .

# Build with Docker Compose
docker-compose build
```

---

## 🐛 Troubleshooting

### Services not starting
```bash
# Check ports are free
lsof -i :8080,8081,8082,5432,7233

# Check Docker resources
docker stats

# Full cleanup and restart
docker-compose down -v
docker system prune -f
docker-compose up -d
```

### Application errors
```bash
# View recent logs
docker logs agentmesh-app --tail 100

# Follow logs in real-time
docker logs agentmesh-app -f

# Search for errors
docker logs agentmesh-app 2>&1 | grep -i "error\|exception"
```

### Database issues
```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# View PostgreSQL logs
docker logs agentmesh-postgres

# Reset database
docker-compose down -v
docker-compose up -d postgres
sleep 10
docker-compose up -d
```

### Weaviate issues
```bash
# Check Weaviate health
curl http://localhost:8081/v1/.well-known/live
curl http://localhost:8081/v1/.well-known/ready

# View schema
curl http://localhost:8081/v1/schema

# View logs
docker logs agentmesh-weaviate -f
```

---

## 📊 Monitoring

```bash
# Service status
docker-compose ps

# Resource usage
docker stats

# Disk usage
docker system df

# Container logs size
docker ps -a --format "table {{.Names}}\t{{.Size}}"

# Network inspect
docker network inspect agentmesh_default
```

---

## 🔑 Environment Variables

```bash
# View current config
docker-compose config

# Override variables
export SPRING_PROFILES_ACTIVE=prod
docker-compose up -d
```

---

## 📈 Performance Testing

```bash
# Simple load test with curl
for i in {1..100}; do
  curl http://localhost:8080/api/agents &
done
wait

# Using Apache Bench
ab -n 1000 -c 10 http://localhost:8080/api/agents

# Using wrk
wrk -t4 -c100 -d30s http://localhost:8080/api/agents
```

---

## 📝 Useful Aliases

Add to your `~/.zshrc` or `~/.bashrc`:

```bash
# AgentMesh aliases
alias am-start='cd /path/to/AgentMesh && docker-compose up -d'
alias am-stop='cd /path/to/AgentMesh && docker-compose down'
alias am-logs='cd /path/to/AgentMesh && docker-compose logs -f'
alias am-test='cd /path/to/AgentMesh/test-scripts && ./run-all-tests.sh'
alias am-restart='cd /path/to/AgentMesh && docker-compose restart agentmesh'
alias am-build='cd /path/to/AgentMesh && docker-compose up -d --build'
alias am-clean='cd /path/to/AgentMesh && docker-compose down -v && docker system prune -f'
alias am-db='docker exec -it agentmesh-postgres psql -U agentmesh -d agentmesh'
```

---

## 🎯 Common Workflows

### Create Complete Feature

```bash
# 1. Create tenant
TENANT_ID=$(curl -s -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{"organizationId": "org1", "organizationName": "Org", "tier": "PRO"}' \
  | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

# 2. Create agents
curl -X POST "http://localhost:8080/api/agents?id=planner-001"
curl -X POST "http://localhost:8080/api/agents?id=coder-001"
curl -X POST "http://localhost:8080/api/agents?id=reviewer-001"

# 3. Start workflow...
```

### Debug Failed Agent

```bash
# Check violations
curl http://localhost:8080/api/mast/violations/agent/coder-001

# Check health
curl http://localhost:8080/api/mast/health/coder-001

# Review messages
curl http://localhost:8080/api/agents/messages | jq '.[] | select(.senderId=="coder-001" or .recipientId=="coder-001")'

# Check recent blackboard entries
curl http://localhost:8080/api/blackboard/entries/agent/coder-001
```

---

## 📖 Documentation Links

- **PROJECT-SUMMARY.md**: Comprehensive project overview
- **TEST-SCENARIOS.md**: Detailed test scenarios
- **test-scripts/README.md**: Test automation guide
- **Temporal UI**: http://localhost:8082
- **Docker Compose**: docker-compose.yml

---

## 🆘 Getting Help

1. Check logs: `docker-compose logs -f`
2. Check service status: `docker-compose ps`
3. Review PROJECT-SUMMARY.md for architecture details
4. Check test-scripts/README.md for troubleshooting
5. Ensure Docker has sufficient resources (4GB+ RAM)

---

**Last Updated**: October 31, 2025

