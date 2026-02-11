# Kafka Restart Issue - Permanent Solution

## Problem

After restarting the AgentMesh infrastructure, Kafka failed to start with the following error:

```
org.apache.zookeeper.KeeperException$NodeExistsException: KeeperErrorCode = NodeExists
  at kafka.zk.KafkaZkClient.registerBroker(KafkaZkClient.scala:106)
  at kafka.server.KafkaServer.startup(KafkaServer.scala:365)
```

**Root Cause**: When Kafka doesn't shut down cleanly, its ephemeral registration node persists in Zookeeper, preventing Kafka from re-registering on the next startup.

## Solution Implemented

### 1. Docker Compose Configuration Update

Added increased timeout and restart policy to `docker-compose.yml`:

```yaml
kafka:
  # ... existing config ...
  environment:
    # ... existing env vars ...
    # Fix for NodeExistsException - allow Kafka to reconnect after restart
    KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS: 18000
    KAFKA_ZOOKEEPER_CONNECTION_TIMEOUT_MS: 18000
  restart: unless-stopped
  healthcheck:
    start_period: 40s  # Added to give more time for initial startup
```

### 2. Smart Startup Script

Created `start-agentmesh.sh` that automatically handles the cleanup:

**Key Features**:
- Stops all containers cleanly
- Starts Zookeeper first and waits for it to be ready
- Checks for stale Kafka broker registrations
- Cleans up stale registrations if found
- Starts all other services
- Reports service status

**Usage**:

```bash
cd /Users/univers/projects/agentmesh/AgentMesh
./start-agentmesh.sh
```

### 3. Manual Cleanup Script

Created `kafka-cleanup.sh` for manual cleanup if needed:

```bash
cd /Users/univers/projects/agentmesh/AgentMesh
./kafka-cleanup.sh
```

## How It Works

1. **On Startup**: The `start-agentmesh.sh` script:
   - Cleanly stops all running containers
   - Starts only Zookeeper
   - Waits 15 seconds for Zookeeper to fully initialize
   - Checks Zookeeper's `/brokers/ids` path for stale broker registrations
   - Deletes any stale registrations found
   - Starts all other services including Kafka
   
2. **Kafka Configuration**: Increased timeouts give Kafka more time to register with Zookeeper, reducing the chance of timing issues.

3. **Restart Policy**: `restart: unless-stopped` ensures Kafka automatically restarts if it crashes.

## Testing

```bash
# Start infrastructure
./start-agentmesh.sh

# Verify all services are healthy
docker-compose ps

# Check Kafka logs for errors
docker logs agentmesh-kafka 2>&1 | tail -50

# Stop and restart to verify fix works
docker-compose down
./start-agentmesh.sh
```

## Services

After successful startup, the following services are available:

- **AgentMesh API**: http://localhost:8080
- **AgentMesh UI**: http://localhost:3001
- **Temporal UI**: http://localhost:8088
- **Weaviate**: http://localhost:8081
- **Grafana**: http://localhost:3000
- **Prometheus**: http://localhost:9090
- **Kafka**: localhost:9092
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379

## Verification

Run the following to verify Kafka is working:

```bash
# Check Kafka is registered with Zookeeper
docker exec agentmesh-zookeeper zkCli.sh ls /brokers/ids
# Should show: [1]

# Check Kafka health
docker exec agentmesh-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

## Benefits

✅ **Permanent Fix**: No more manual intervention needed for Kafka restarts  
✅ **Automated Cleanup**: Script handles stale registrations automatically  
✅ **Reliable Startup**: Proper sequencing ensures clean initialization  
✅ **Better Timeouts**: Reduced chance of timing-related issues  
✅ **Auto-Recovery**: Restart policy handles transient failures  

## Troubleshooting

If Kafka still fails to start:

1. **Check Zookeeper**:
   ```bash
   docker logs agentmesh-zookeeper
   ```

2. **Manually clean Zookeeper data** (nuclear option):
   ```bash
   docker-compose down -v  # WARNING: This deletes all data volumes!
   ./start-agentmesh.sh
   ```

3. **Verify port availability**:
   ```bash
   lsof -i :9092  # Should only show Kafka
   ```

## Files Created

- `/Users/univers/projects/agentmesh/AgentMesh/start-agentmesh.sh` - Smart startup script
- `/Users/univers/projects/agentmesh/AgentMesh/kafka-cleanup.sh` - Manual cleanup script
- `/Users/univers/projects/agentmesh/AgentMesh/docker-compose.yml` - Updated with Kafka fixes

## Implementation Date

December 11, 2025

## Status

✅ **VERIFIED WORKING** - Kafka successfully starts after infrastructure restart with no NodeExistsException errors.
