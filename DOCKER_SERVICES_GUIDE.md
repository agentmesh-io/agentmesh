# AgentMesh Docker Services Guide

## Quick Start

```bash
cd /Users/univers/projects/agentmesh/AgentMesh

# Build the application
mvn clean package -DskipTests

# Start all services
docker compose up -d --build

# Check status
docker compose ps

# View logs
docker compose logs -f agentmesh-api-server
```

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| **API Server** | 18085 | http://localhost:18085 |
| **Swagger UI** | 18085 | http://localhost:18085/swagger-ui.html |
| **UI (Frontend)** | 13001 | http://localhost:13001 |
| **PostgreSQL** | 15432 | localhost:15432 |
| **Redis** | 16379 | localhost:16379 |
| **Kafka** | 19092 | localhost:19092 |
| **Zookeeper** | 12181 | localhost:12181 |
| **Weaviate** | 8081 | http://localhost:8081 |
| **Temporal** | 17233 | localhost:17233 |
| **Temporal Web** | 18082 | http://localhost:18082 |
| **Prometheus** | 19090 | http://localhost:19090 |
| **Grafana** | 13000 | http://localhost:13000 |

## Credentials

| Service | Username | Password |
|---------|----------|----------|
| PostgreSQL | agentmesh | agentmesh123 |
| Grafana | admin | agentmesh123 |

## Useful Commands

### Start/Stop Services
```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down --volumes

# Restart a specific service
docker compose restart agentmesh-api-server
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f agentmesh-api-server
docker compose logs -f agentmesh-kafka
docker compose logs -f agentmesh-temporal
```

### Health Checks
```bash
# API Health
curl http://localhost:18085/actuator/health

# API Info
curl http://localhost:18085/actuator/info

# Prometheus Metrics
curl http://localhost:18085/actuator/prometheus
```

### Database Access
```bash
# Connect to PostgreSQL
docker exec -it agentmesh-postgres psql -U agentmesh -d agentmesh

# Connect to Redis
docker exec -it agentmesh-redis redis-cli
```

## Troubleshooting

### Port Conflicts
If you see "port already allocated" errors, check what's using the port:
```bash
lsof -i :<port_number>
```

### Service Not Starting
Check logs for the specific service:
```bash
docker compose logs <service-name>
```

### Clean Restart
```bash
docker compose down --volumes --remove-orphans
docker compose up -d --build
```

### Rebuild API Server
```bash
mvn clean package -DskipTests
docker compose up -d --build agentmesh-api
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     AgentMesh Stack                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ���─────────────┐         │
│  │   UI        │  │  Grafana    │  │ Prometheus  │         │
│  │  :13001     │  │  :13000     │  │  :19090     │         │
│  └──────┬──────┘  └─────────────┘  └─────────────┘         │
│         │                                                    │
│  ┌──────▼──────────────────────────────────────────┐       │
│  │              API Server (:18085)                 │       │
│  │  - REST API                                      │       │
│  │  - WebSocket                                     │       │
│  │  - Swagger UI                                    │       │
│  └──────┬───────────────────────────────────────────┘       │
│         │                                                    │
│  ┌──────┼──────────────────────────────────────────┐       │
│  │      │         Infrastructure                    │       │
│  │  ┌───▼───┐  ┌─���──────┐  ┌────────┐  ┌────────┐ │       │
│  │  │Postgres│  │ Redis  │  │ Kafka  │  │Weaviate│ │       │
│  │  │:15432  │  │:16379  │  │:19092  │  │ :8081  │ │       │
│  │  └────────┘  └────────┘  └────────┘  └────────┘ │       │
│  │                                                   │       │
│  │  ┌────────┐  ┌────────┐                         │       │
│  │  │Temporal│  │Zookeeper│                         │       │
│  │  │:17233  │  │:12181  │                         │       │
│  │  └────────┘  └────────┘                         │       │
│  └──────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

