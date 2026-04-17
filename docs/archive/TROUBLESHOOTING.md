# AgentMesh Troubleshooting Guide

## Common Issues and Solutions

### 🔴 Port Conflicts

**Problem:** Services fail to start with "port is already allocated" errors

**Common ports:**
- `5432` - PostgreSQL
- `6379` - Redis  
- `9092` - Kafka
- `8080` - AgentMesh API
- `3001` - AgentMesh UI

**Solution 1: Use the restart script**
```bash
./restart-agentmesh.sh
```

**Solution 2: Manual cleanup**
```bash
# Stop all AgentMesh services
docker-compose down

# Check what's using the ports
lsof -i :6379 -i :5432 -i :9092

# Stop conflicting containers (e.g., righthand containers)
docker stop righthand-redis righthand-postgres

# Restart services
docker-compose up -d
```

**Solution 3: Find and kill process**
```bash
# Find process using port
lsof -ti :6379

# Kill process (if not Docker)
kill -9 <PID>
```

---

### 🔴 Kafka Restart Issues

**Problem:** Kafka fails with `NodeExistsException` or won't start after restart

**Symptoms:**
- `KeeperErrorCode = NodeExists`
- Kafka container exits immediately
- Zookeeper shows broker registration errors

**Solution 1: Clean Kafka state with script**
```bash
./restart-agentmesh.sh --clean-kafka
```

**Solution 2: Manual Kafka cleanup**
```bash
# Stop services
docker-compose down

# Remove Kafka containers
docker rm -f agentmesh-kafka agentmesh-zookeeper

# Remove Kafka volumes (this clears state)
docker volume rm -f agentmesh_kafka_data
docker volume rm -f agentmesh_zookeeper_data
docker volume rm -f agentmesh_zookeeper_logs

# Restart just Kafka
docker-compose up -d zookeeper
sleep 5
docker-compose up -d kafka
```

**Why this happens:**
- Kafka stores broker registration in Zookeeper
- Stale registrations from previous sessions cause conflicts
- Docker volume persistence keeps old state

---

### 🔴 Auto-BADS Container Issues

**Problem:** Auto-BADS container is unhealthy or won't start

**Solution:** Auto-BADS has been removed from docker-compose due to ND4J native library issues. Run it standalone:

```bash
cd /Users/univers/projects/agentmesh/Auto-BADS
mvn spring-boot:run
```

The UI expects Auto-BADS on `http://localhost:8083`

---

### 🔴 Services Won't Start (Dependency Chain)

**Problem:** Services fail because dependencies are unhealthy

**Solution 1: Start in order**
```bash
# Infrastructure first
docker-compose up -d postgres redis zookeeper kafka

# Wait for health
sleep 10

# Platform services
docker-compose up -d weaviate temporal

# Wait for health
sleep 10

# Application
docker-compose up -d agentmesh-api prometheus grafana

# UI last
docker-compose up -d agentmesh-ui
```

**Solution 2: Skip dependencies**
```bash
docker-compose up -d --no-deps agentmesh-ui
```

---

### 🔴 Database Connection Issues

**Problem:** Services can't connect to PostgreSQL

**Check database is running:**
```bash
docker exec agentmesh-postgres pg_isready -U agentmesh
```

**Check connection from API:**
```bash
docker logs agentmesh-api-server | grep -i "database\|postgres"
```

**Recreate database:**
```bash
docker-compose down
docker volume rm agentmesh_postgres_data
docker-compose up -d postgres
```

---

### 🔴 Full System Reset

**Problem:** Everything is broken, need fresh start

**Solution:**
```bash
# Nuclear option - removes ALL data
./restart-agentmesh.sh --full-clean
```

**Or manually:**
```bash
# Stop everything
docker-compose down

# Remove all containers
docker rm -f $(docker ps -aq --filter "name=agentmesh")

# Remove all volumes (⚠️ DELETES ALL DATA)
docker volume rm -f agentmesh_postgres_data
docker volume rm -f agentmesh_redis_data
docker volume rm -f agentmesh_kafka_data
docker volume rm -f agentmesh_zookeeper_data
docker volume rm -f agentmesh_zookeeper_logs
docker volume rm -f agentmesh_weaviate_data
docker volume rm -f agentmesh_prometheus_data
docker volume rm -f agentmesh_grafana_data

# Start fresh
docker-compose up -d
```

---

## Quick Commands

### Check Service Status
```bash
docker-compose ps
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f agentmesh-api

# Last 50 lines
docker logs agentmesh-api-server --tail 50
```

### Health Check
```bash
# Check all container health
docker ps --format "table {{.Names}}\t{{.Status}}"

# Test API health
curl http://localhost:8080/actuator/health

# Test UI
curl -I http://localhost:3001
```

### Restart Single Service
```bash
docker-compose restart agentmesh-api
```

### Rebuild Service
```bash
docker-compose up -d --build agentmesh-api
```

---

## Prevention Tips

1. **Always stop services cleanly:**
   ```bash
   docker-compose down
   ```

2. **Don't force kill Docker processes** - let services shut down gracefully

3. **Stop conflicting containers before starting:**
   ```bash
   docker stop righthand-redis righthand-postgres
   ```

4. **Monitor disk space** - Docker volumes can fill up:
   ```bash
   docker system df
   ```

5. **Clean up old containers periodically:**
   ```bash
   docker system prune -a
   ```

---

## Service Dependencies

```
PostgreSQL (base)
  └─> Temporal
  └─> AgentMesh API
      └─> Prometheus
          └─> Grafana

Zookeeper (base)
  └─> Kafka

Redis (base)

Transformers (base)
  └─> Weaviate

AgentMesh API
  └─> AgentMesh UI
```

Start services in dependency order to avoid issues.
