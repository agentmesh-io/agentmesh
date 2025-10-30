# AgentMesh - Autonomous Software Engineering Mesh

A production-ready multi-agent system for automating the software development lifecycle (SDLC), implementing the ASEM architecture with **Blackboard pattern**, **Weaviate vector storage**, and **Temporal orchestration**.

## Architecture Overview

### Core Components

1. **Blackboard Architecture** - Shared memory space for agent collaboration
   - Atomic, transactional state updates with optimistic locking
   - Event-driven notifications for real-time coordination
   - Snapshot/rollback capability for resilience
   - PostgreSQL/H2 backend with JPA

2. **Long-Term Memory (LTM)** - Weaviate vector database
   - Semantic search for RAG (Retrieval-Augmented Generation)
   - Stores artifacts, SRS documents, code, and knowledge graphs
   - Enables context-aware agent operations

3. **Temporal Orchestration** - Durable workflow engine
   - Manages the SDLC workflow (Plan → Code → Test → Review → Debug)
   - Built-in retry, timeout, and failure handling
   - Supports complex multi-agent coordination patterns

4. **Agent Registry** - In-memory agent coordination (existing)
   - Agent lifecycle management (create, start, stop)
   - Message routing between agents

## Quick Start

### Prerequisites

- Java 22+
- Maven 3.8+
- Docker (optional, for Weaviate and Temporal)

### Run Locally (H2 + Mock Mode)

```bash
# Build and test
mvn clean test

# Run the application
mvn spring-boot:run

# Access endpoints
open http://localhost:8080/actuator/health
open http://localhost:8080/h2-console
```

The application runs in **mock mode** by default (no external dependencies):
- H2 in-memory database for Blackboard
- Weaviate disabled (mock storage)
- Temporal disabled (mock orchestration)

### Run with Weaviate and Temporal (Full Mode)

#### 1. Start Weaviate

```bash
docker run -d \
  --name weaviate \
  -p 8080:8080 \
  -e AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED=true \
  -e PERSISTENCE_DATA_PATH=/var/lib/weaviate \
  cr.weaviate.io/semitechnologies/weaviate:latest
```

#### 2. Start Temporal

```bash
docker run -d \
  --name temporal \
  -p 7233:7233 \
  temporalio/auto-setup:latest
```

#### 3. Configure AgentMesh

Edit `src/main/resources/application.yml`:

```yaml
agentmesh:
  weaviate:
    enabled: true
    host: localhost:8080
  
  temporal:
    enabled: true
    service-address: 127.0.0.1:7233
```

#### 4. Run

```bash
mvn spring-boot:run
```

## API Endpoints

### Agent Management

```bash
# Create an agent
curl -X POST 'http://localhost:8080/api/agents?id=coder-agent'

# Start an agent
curl -X POST http://localhost:8080/api/agents/coder-agent/start

# Send a message between agents
curl -X POST -H "Content-Type: application/json" \
  -d '{"fromAgentId":"coder","toAgentId":"reviewer","payload":"Code ready for review"}' \
  http://localhost:8080/api/agents/message

# List all agents
curl http://localhost:8080/api/agents

# View message log
curl http://localhost:8080/api/agents/messages
```

### Blackboard Operations

```bash
# Post an entry to the Blackboard
curl -X POST \
  'http://localhost:8080/api/blackboard/entries?agentId=planner&entryType=PLAN&title=Feature%20Plan' \
  -H 'Content-Type: text/plain' \
  -d 'Task 1: Design API. Task 2: Implement endpoints.'

# Retrieve all Blackboard entries
curl http://localhost:8080/api/blackboard/entries

# Get entries by type
curl http://localhost:8080/api/blackboard/entries/type/PLAN

# Get entries by agent
curl http://localhost:8080/api/blackboard/entries/agent/planner

# Update an entry
curl -X PUT http://localhost:8080/api/blackboard/entries/1 \
  -H 'Content-Type: text/plain' \
  -d 'Updated plan content'

# Create a snapshot (for rollback)
curl -X POST http://localhost:8080/api/blackboard/snapshot
```

### Long-Term Memory (LTM)

```bash
# Store an artifact in Weaviate
curl -X POST http://localhost:8080/api/memory/artifacts \
  -H 'Content-Type: application/json' \
  -d '{
    "agentId": "coder",
    "artifactType": "CODE",
    "title": "User Authentication Module",
    "content": "public class AuthService { ... }"
  }'

# Semantic search (RAG query)
curl 'http://localhost:8080/api/memory/search?query=authentication&limit=5'

# Get artifacts by type
curl http://localhost:8080/api/memory/artifacts/type/CODE?limit=10
```

### Observability

```bash
# Health check
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Application info
curl http://localhost:8080/actuator/info
```

## Testing

### Run All Tests

```bash
mvn test
```

### Run Specific Test Suite

```bash
# Blackboard tests
mvn test -Dtest=BlackboardServiceTest

# Integration tests
mvn test -Dtest=BlackboardControllerIntegrationTest

# Agent registry tests
mvn test -Dtest=AgentRegistryTest
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/therighthandapp/agentmesh/
│   │       ├── AgentMeshApplication.java          # Main entry point
│   │       ├── api/                               # REST controllers
│   │       │   ├── AgentController.java
│   │       │   ├── BlackboardController.java
│   │       │   └── MemoryController.java
│   │       ├── blackboard/                        # Blackboard architecture
│   │       │   ├── BlackboardEntry.java
│   │       │   ├── BlackboardRepository.java
│   │       │   ├── BlackboardService.java
│   │       │   ├── BlackboardSnapshot.java
│   │       │   └── *Event.java
│   │       ├── memory/                            # Long-Term Memory (Weaviate)
│   │       │   ├── MemoryArtifact.java
│   │       │   └── WeaviateService.java
│   │       ├── model/                             # Domain models
│   │       │   ├── Agent.java
│   │       │   ├── AgentMessage.java
│   │       │   └── AgentState.java
│   │       ├── orchestration/                     # Temporal workflows
│   │       │   ├── AgentActivity.java
│   │       │   ├── AgentActivityImpl.java
│   │       │   ├── SdlcWorkflow.java
│   │       │   ├── SdlcWorkflowImpl.java
│   │       │   └── TemporalConfig.java
│   │       └── service/
│   │           └── AgentRegistry.java
│   └── resources/
│       ├── application.yml                        # Configuration
│       └── logback-spring.xml                     # Logging config
└── test/
    └── java/
        └── com/therighthandapp/agentmesh/
            ├── api/
            │   ├── AgentControllerIntegrationTest.java
            │   └── BlackboardControllerIntegrationTest.java
            ├── blackboard/
            │   └── BlackboardServiceTest.java
            └── service/
                └── AgentRegistryTest.java
```

## Key Features Implemented

### ✅ MVP Components (Complete)
- Agent lifecycle management
- Message passing between agents
- REST API for agent coordination
- Basic logging and configuration

### ✅ Blackboard Architecture (Complete)
- Atomic state updates with optimistic locking
- Event-driven coordination
- Snapshot/rollback capability
- Type-based and agent-based queries
- Versioning for concurrency control

### ✅ Long-Term Memory Foundation (Complete)
- Weaviate client integration
- Mock mode for development
- Semantic search interface (RAG-ready)
- Artifact storage with metadata

### ✅ Temporal Orchestration Foundation (Complete)
- Workflow definition for SDLC
- Activity interface for agent tasks
- Worker configuration
- Mock mode for development

### ✅ Observability (Complete)
- Micrometer + Prometheus integration
- Actuator endpoints
- Structured logging with SLF4J

## Next Steps (Roadmap)

### Short-Term (2-4 weeks)
1. **LLM Integration**
   - Create `LLMClient` interface
   - Add MockLLM for deterministic tests
   - Integrate OpenAI/Anthropic adapters

2. **Agent Implementations**
   - Planner/Architect agent
   - Coder/Engineer agent
   - Debugger agent
   - Reviewer/Validator agent
   - Test Agent

3. **MAST Integration**
   - Define 14 failure mode test templates
   - Automated MAST-driven test generation
   - Integrate into self-correction loop

### Medium-Term (1-3 months)
4. **Production Hardening**
   - Switch to PostgreSQL for Blackboard
   - Kafka event bus for high-throughput
   - Security: JWT auth, per-agent credentials
   - HIL (Human-In-Loop) checkpoints

5. **Advanced Orchestration**
   - Dynamic replanning on failure
   - Loop detection and cancellation
   - Timeout enforcement
   - Cost tracking (token accounting)

6. **LLMOps Dashboard**
   - Real-time metrics visualization
   - Pass@N score tracking
   - Token consumption per feature
   - MAST failure frequency heatmap

## Configuration Reference

### Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentmesh  # For PostgreSQL
    username: agentmesh
    password: secret
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway/Liquibase for migrations in prod
```

### Weaviate Configuration

```yaml
agentmesh:
  weaviate:
    enabled: true
    scheme: http
    host: localhost:8080
```

### Temporal Configuration

```yaml
agentmesh:
  temporal:
    enabled: true
    service-address: 127.0.0.1:7233
    namespace: production
    task-queue: agentmesh-tasks
```

## Development

### Running Tests with Coverage

```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

### Building for Production

```bash
mvn clean package -DskipTests
java -jar target/AgentMesh-1.0-SNAPSHOT.jar
```

### Docker Deployment (Future)

```bash
# Build image
docker build -t agentmesh:latest .

# Run with docker-compose
docker-compose up -d
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## License

[Add your license here]

## References

- [ASEM Project Definition](project-definition.txt) - Full architectural blueprint
- [Temporal Documentation](https://docs.temporal.io/)
- [Weaviate Documentation](https://weaviate.io/developers/weaviate)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

