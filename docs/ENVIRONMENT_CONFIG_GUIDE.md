# Environment Configuration Management Guide

## Overview

AgentMesh uses **Kustomize** for environment-specific configuration management, enabling:
- Environment-specific settings (dev, staging, prod)
- Secrets management
- Resource adjustments per environment
- Configuration inheritance and overrides

## Directory Structure

```
k8s/
├── base/                           # Base configuration (shared)
│   └── kustomization.yaml          # Base resources and defaults
├── overlays/                       # Environment-specific overlays
│   ├── dev/                        # Development environment
│   │   └── kustomization.yaml
│   ├── staging/                    # Staging environment
│   │   └── kustomization.yaml
│   └── prod/                       # Production environment
│       ├── kustomization.yaml
│       ├── ingress-production.yaml
│       ├── network-policy.yaml
│       ├── secrets.env.template
│       └── postgres-secrets.env.template
└── *.yaml                          # Base manifests
```

## Environments

### Development

**Purpose**: Local development and testing

**Characteristics**:
- Single replica
- Reduced resources (512Mi RAM, 250m CPU)
- Debug logging enabled
- Short cache TTL (5 minutes)
- Synchronous MAST detection
- NodePort service (port 30080)
- No HPA
- Latest tag

**Deploy**:
```bash
kubectl apply -k k8s/overlays/dev
```

**Access**:
```bash
# Via NodePort
curl http://localhost:30080/actuator/health

# Via port-forward
kubectl port-forward -n agentmesh-dev svc/agentmesh-service 8080:8080
```

---

### Staging

**Purpose**: Pre-production testing with production-like settings

**Characteristics**:
- 3 replicas (HA)
- 80% of production resources (1.6Gi RAM, 800m CPU)
- Info logging with debug for application code
- Production cache TTL (2 hours violations, 30 min blackboard)
- Async MAST detection
- Full Redis cluster (6 nodes)
- HPA: 2-6 replicas
- Release candidate tags (v1.0.0-rc)

**Deploy**:
```bash
kubectl apply -k k8s/overlays/staging
```

**Use Cases**:
- Integration testing
- Performance testing
- UAT (User Acceptance Testing)
- Production deployment rehearsal

---

### Production

**Purpose**: Production deployment with full resources and HA

**Characteristics**:
- 5 replicas (HA with load distribution)
- Full resources (2Gi RAM, 1 CPU)
- Warn logging (info for application)
- Production cache TTL
- Async MAST detection
- Full Redis cluster (6 nodes)
- HPA: 3-10 replicas (aggressive scaling)
- TLS-enabled Ingress
- Network policies (restricted traffic)
- Pod anti-affinity (spread across nodes)
- Specific version tags (v1.0.0)

**Deploy**:
```bash
# First, create secrets
cp k8s/overlays/prod/secrets.env.template k8s/overlays/prod/secrets.env
cp k8s/overlays/prod/postgres-secrets.env.template k8s/overlays/prod/postgres-secrets.env

# Edit secrets with actual values
vi k8s/overlays/prod/secrets.env
vi k8s/overlays/prod/postgres-secrets.env

# Deploy
kubectl apply -k k8s/overlays/prod
```

**Security**:
- Network policies restrict inter-pod traffic
- TLS for external access
- Sealed Secrets recommended for production
- Pod Security Standards enforced

## Configuration Parameters

### Application Settings

| Parameter | Dev | Staging | Prod | Description |
|-----------|-----|---------|------|-------------|
| `SPRING_PROFILES_ACTIVE` | dev | staging | prod | Spring profile |
| `LOGGING_LEVEL_ROOT` | DEBUG | INFO | WARN | Root log level |
| `LOGGING_LEVEL_COM_THERIGHTHANDAPP` | DEBUG | DEBUG | INFO | App log level |
| `CACHE_TTL_VIOLATION` | 300s | 7200s | 7200s | Violation cache TTL |
| `CACHE_TTL_BLACKBOARD` | 300s | 1800s | 1800s | Blackboard cache TTL |
| `MAST_ASYNC_ENABLED` | false | true | true | Async MAST detection |
| `SELFCORRECTION_MAX_ATTEMPTS` | 2 | 3 | 3 | Max correction attempts |
| `DATABASE_POOL_SIZE` | 10 | 20 | 30 | HikariCP pool size |
| `REDIS_MAX_CONNECTIONS` | 20 | 50 | 100 | Redis connection pool |
| `LLM_MAX_CONCURRENT` | 3 | 10 | 20 | Concurrent LLM calls |
| `LLM_TIMEOUT_SECONDS` | 30 | 60 | 60 | LLM API timeout |

### Resource Allocation

| Environment | Memory Request | Memory Limit | CPU Request | CPU Limit | Replicas |
|-------------|---------------|--------------|-------------|-----------|----------|
| Development | 512Mi | 1Gi | 250m | 500m | 1 |
| Staging | 1.6Gi | 3.2Gi | 800m | 1600m | 3 |
| Production | 2Gi | 4Gi | 1000m | 2000m | 5 |

### HPA Configuration

| Environment | Enabled | Min | Max | CPU Target |
|-------------|---------|-----|-----|------------|
| Development | No | - | - | - |
| Staging | Yes | 2 | 6 | 70% |
| Production | Yes | 3 | 10 | 60% |

## Secrets Management

### Development

Secrets stored in Kustomize (non-sensitive values):
```yaml
secretGenerator:
- name: agentmesh-secrets
  literals:
  - DATABASE_PASSWORD=dev_password_123
  - LLM_API_KEY=sk-dev-placeholder
```

### Staging/Production

**Option 1: Sealed Secrets (Recommended)**

```bash
# Install Sealed Secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# Create secret
kubectl create secret generic agentmesh-secrets \
  --from-env-file=secrets.env \
  --dry-run=client -o yaml | \
  kubeseal -o yaml > agentmesh-sealed-secrets.yaml

# Apply sealed secret
kubectl apply -f agentmesh-sealed-secrets.yaml
```

**Option 2: External Secrets Operator**

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: agentmesh-secrets
  namespace: agentmesh-prod
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: agentmesh-secrets
  data:
  - secretKey: DATABASE_PASSWORD
    remoteRef:
      key: agentmesh/prod/database
      property: password
  - secretKey: LLM_API_KEY
    remoteRef:
      key: agentmesh/prod/llm
      property: api_key
```

**Option 3: Cloud Provider Secrets**

- **AWS**: AWS Secrets Manager + External Secrets Operator
- **GCP**: Google Secret Manager + External Secrets Operator
- **Azure**: Azure Key Vault + External Secrets Operator

### Generating Secure Secrets

```bash
# Generate random password
openssl rand -base64 32

# Generate random 256-bit key
openssl rand -hex 32

# Generate JWT secret
openssl rand -base64 64
```

## Deployment Workflow

### Development Deployment

```bash
# 1. Build and tag image
docker build -t agentmesh/agentmesh:dev-latest .

# 2. Push to registry
docker push agentmesh/agentmesh:dev-latest

# 3. Deploy to dev namespace
kubectl apply -k k8s/overlays/dev

# 4. Verify deployment
kubectl get pods -n agentmesh-dev
kubectl logs -n agentmesh-dev -l app=agentmesh -f
```

### Staging Deployment

```bash
# 1. Build and tag release candidate
docker build -t agentmesh/agentmesh:v1.0.0-rc .

# 2. Push to registry
docker push agentmesh/agentmesh:v1.0.0-rc

# 3. Deploy to staging
kubectl apply -k k8s/overlays/staging

# 4. Run integration tests
./test-scripts/run-all-tests.sh staging

# 5. Performance testing
kubectl run loadtest --image=loadtest:latest \
  --env="TARGET=http://agentmesh-service.agentmesh-staging:8080"
```

### Production Deployment

```bash
# 1. Create production secrets
cp k8s/overlays/prod/secrets.env.template k8s/overlays/prod/secrets.env
# Edit with actual values
vi k8s/overlays/prod/secrets.env

# 2. Seal secrets
kubectl create secret generic agentmesh-secrets \
  --from-env-file=k8s/overlays/prod/secrets.env \
  --dry-run=client -o yaml | \
  kubeseal -o yaml > k8s/overlays/prod/agentmesh-sealed-secrets.yaml

# 3. Update image tag to stable version
cd k8s/overlays/prod
kustomize edit set image agentmesh/agentmesh:v1.0.0

# 4. Preview changes
kubectl diff -k k8s/overlays/prod

# 5. Apply with approval
kubectl apply -k k8s/overlays/prod

# 6. Monitor rollout
kubectl rollout status deployment/agentmesh -n agentmesh-prod

# 7. Verify health
kubectl exec -n agentmesh-prod agentmesh-0 -- curl localhost:8080/actuator/health

# 8. Check metrics
kubectl port-forward -n monitoring svc/grafana 3000:80
# Open http://localhost:3000
```

## Configuration Updates

### Update ConfigMap

```bash
# Development
cd k8s/overlays/dev
# Edit kustomization.yaml
kubectl apply -k .
kubectl rollout restart deployment/agentmesh -n agentmesh-dev

# Production
cd k8s/overlays/prod
# Edit kustomization.yaml
kubectl apply -k .
kubectl rollout restart deployment/agentmesh -n agentmesh-prod
```

### Update Secrets

```bash
# Update secret file
vi k8s/overlays/prod/secrets.env

# Regenerate sealed secret
kubectl create secret generic agentmesh-secrets \
  --from-env-file=k8s/overlays/prod/secrets.env \
  --dry-run=client -o yaml | \
  kubeseal -o yaml > k8s/overlays/prod/agentmesh-sealed-secrets.yaml

# Apply
kubectl apply -f k8s/overlays/prod/agentmesh-sealed-secrets.yaml

# Restart pods to pick up new secret
kubectl rollout restart deployment/agentmesh -n agentmesh-prod
```

## Environment Variables Reference

### Complete List

```bash
# Spring Boot
SPRING_PROFILES_ACTIVE=prod
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_COM_THERIGHTHANDAPP=INFO

# Database
DATABASE_URL=jdbc:postgresql://postgres-service:5432/agentmesh_prod
DATABASE_USERNAME=agentmesh_prod
DATABASE_PASSWORD=${DATABASE_PASSWORD}
DATABASE_POOL_SIZE=30

# Redis
REDIS_HOST=redis-cluster-service
REDIS_PORT=6379
REDIS_PASSWORD=${REDIS_PASSWORD}
REDIS_MAX_CONNECTIONS=100

# Cache
CACHE_ENABLED=true
CACHE_TTL_VIOLATION=7200
CACHE_TTL_BLACKBOARD=1800

# LLM
LLM_PROVIDER=openai
LLM_API_KEY=${LLM_API_KEY}
LLM_MODEL=gpt-4o
LLM_MAX_CONCURRENT=20
LLM_TIMEOUT_SECONDS=60

# MAST
MAST_ENABLED=true
MAST_ASYNC_ENABLED=true

# Self-Correction
SELFCORRECTION_ENABLED=true
SELFCORRECTION_MAX_ATTEMPTS=3

# Metrics
METRICS_EXPORT_PROMETHEUS_ENABLED=true
METRICS_EXPORT_INTERVAL=30s

# Weaviate
WEAVIATE_URL=http://weaviate:8080
WEAVIATE_API_KEY=${WEAVIATE_API_KEY}

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka-service:9092
```

## Troubleshooting

### View Applied Configuration

```bash
# View rendered manifests
kubectl kustomize k8s/overlays/prod

# View specific resource
kubectl kustomize k8s/overlays/prod | grep -A 20 "kind: Deployment"
```

### Check ConfigMap

```bash
kubectl get configmap agentmesh-config -n agentmesh-prod -o yaml
```

### Check Secrets

```bash
# List secrets
kubectl get secrets -n agentmesh-prod

# Decode secret (be careful with production)
kubectl get secret agentmesh-secrets -n agentmesh-prod -o jsonpath='{.data.DATABASE_PASSWORD}' | base64 -d
```

### Common Issues

**Issue**: Pods not picking up ConfigMap changes
**Solution**: Restart deployment
```bash
kubectl rollout restart deployment/agentmesh -n agentmesh-prod
```

**Issue**: Kustomize not finding resources
**Solution**: Check paths in kustomization.yaml
```bash
kustomize build k8s/overlays/prod
```

**Issue**: Secret not found
**Solution**: Verify secret exists
```bash
kubectl get secrets -n agentmesh-prod
```

## Best Practices

1. **Never commit secrets** - Use `.gitignore` for `secrets.env` files
2. **Use specific tags in production** - Never use `latest` tag
3. **Test in staging first** - Always deploy to staging before production
4. **Version your configs** - Track kustomization.yaml changes in Git
5. **Use Sealed Secrets** - For production secret management
6. **Monitor deployments** - Watch rollout status and pod logs
7. **Gradual rollouts** - Use progressive delivery (Flagger, Argo Rollouts)
8. **Document changes** - Update this guide when adding new configuration

## References

- [Kustomize Documentation](https://kustomize.io/)
- [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)
- [External Secrets Operator](https://external-secrets.io/)
- [Kubernetes ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
