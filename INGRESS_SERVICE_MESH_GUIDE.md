# Ingress & Service Mesh Setup Guide

## Overview

This guide covers two approaches for external traffic management in AgentMesh:
1. **NGINX Ingress Controller** (simpler, recommended for getting started)
2. **Istio Service Mesh** (advanced, for production with traffic management needs)

## NGINX Ingress Controller Setup

### Installation

```bash
# Install NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.9.4/deploy/static/provider/cloud/deploy.yaml

# Verify installation
kubectl get pods -n ingress-nginx
kubectl get svc -n ingress-nginx

# Get external IP
kubectl get svc ingress-nginx-controller -n ingress-nginx
```

### Cert-Manager Installation

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.2/cert-manager.yaml

# Verify installation
kubectl get pods -n cert-manager

# Apply ClusterIssuers and Certificates
kubectl apply -f k8s/cert-manager.yaml
```

### Configure DNS

Add DNS records pointing to the Ingress external IP:

```
# Development (local)
127.0.0.1    agentmesh-dev.local

# Staging
A    agentmesh-staging.example.com    -> INGRESS_EXTERNAL_IP
A    api-staging.agentmesh.example.com -> INGRESS_EXTERNAL_IP

# Production
A    agentmesh.example.com             -> INGRESS_EXTERNAL_IP
A    api.agentmesh.example.com         -> INGRESS_EXTERNAL_IP
```

### Deploy with Ingress

```bash
# Development
kubectl apply -f k8s/overlays/dev/ingress-dev.yaml

# Staging
kubectl apply -f k8s/overlays/staging/ingress-staging.yaml

# Production
kubectl apply -f k8s/overlays/prod/ingress-production.yaml
```

### Verify TLS

```bash
# Check certificate status
kubectl get certificate -n agentmesh-prod
kubectl describe certificate agentmesh-prod-cert -n agentmesh-prod

# Test HTTPS
curl -v https://agentmesh.example.com/actuator/health
```

## Istio Service Mesh Setup

### Prerequisites

- Kubernetes cluster with 4+ CPU and 8GB+ RAM
- kubectl configured
- helm installed

### Installation

```bash
# Download Istio
curl -L https://istio.io/downloadIstio | sh -
cd istio-1.20.0
export PATH=$PWD/bin:$PATH

# Install Istio with production profile
istioctl install --set profile=production -y

# Verify installation
kubectl get pods -n istio-system

# Enable automatic sidecar injection
kubectl label namespace agentmesh-prod istio-injection=enabled
```

### Deploy Istio Configuration

```bash
# Apply Istio Gateway and VirtualService
kubectl apply -f k8s/istio/istio-config.yaml

# Apply traffic management rules
kubectl apply -f k8s/istio/traffic-management.yaml

# Verify
kubectl get gateway -n agentmesh-prod
kubectl get virtualservice -n agentmesh-prod
kubectl get destinationrule -n agentmesh-prod
```

### Enable mTLS

```bash
# Apply PeerAuthentication
kubectl apply -f k8s/istio/istio-config.yaml

# Verify mTLS is enforced
istioctl authn tls-check agentmesh-0.agentmesh-prod
```

### Observability with Kiali

```bash
# Install Kiali dashboard
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/kiali.yaml

# Access Kiali
kubectl port-forward -n istio-system svc/kiali 20001:20001
# Open http://localhost:20001
```

### Jaeger for Distributed Tracing

```bash
# Install Jaeger
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/jaeger.yaml

# Access Jaeger UI
kubectl port-forward -n istio-system svc/tracing 16686:16686
# Open http://localhost:16686
```

## Traffic Management Patterns

### Canary Deployments

**Scenario**: Deploy new version to 10% of traffic

```bash
# 1. Deploy canary version
kubectl set image deployment/agentmesh agentmesh=agentmesh/agentmesh:v1.1.0-canary -n agentmesh-prod
kubectl label pods -l app=agentmesh version=canary -n agentmesh-prod

# 2. Apply canary VirtualService
kubectl apply -f k8s/istio/traffic-management.yaml

# 3. Monitor metrics
kubectl exec -n istio-system deploy/grafana -- curl http://localhost:3000

# 4. Gradual rollout (adjust weights)
# Edit traffic-management.yaml: increase canary weight to 25%, 50%, 75%, 100%

# 5. Promote to stable
kubectl set image deployment/agentmesh agentmesh=agentmesh/agentmesh:v1.1.0 -n agentmesh-prod
```

### A/B Testing

**Scenario**: Route specific tenants to new version

```yaml
# Already configured in traffic-management.yaml
# Beta tenants (tenant-001, tenant-002, tenant-003) get v2
# Others get v1
```

Test:
```bash
# Request with beta tenant
curl -H "X-Tenant-ID: tenant-001" https://api.agentmesh.example.com/api/v1/agents

# Request with regular tenant
curl -H "X-Tenant-ID: tenant-999" https://api.agentmesh.example.com/api/v1/agents
```

### Circuit Breaking

**Scenario**: Prevent cascading failures

Already configured in `istio-config.yaml`:
- Max connections: 100
- Consecutive errors trigger: 5
- Ejection time: 30s

Test:
```bash
# Generate load to trigger circuit breaker
kubectl run -i --tty load-generator --rm --image=busybox --restart=Never -- /bin/sh
# Inside pod:
while true; do wget -q -O- http://agentmesh-service:8080/api/v1/agents; done
```

### Fault Injection

**Scenario**: Test resilience with delays and errors

```bash
# Apply fault injection (staging only)
kubectl apply -f k8s/istio/traffic-management.yaml -n agentmesh-staging

# Test with delays
curl -w "@curl-format.txt" https://agentmesh-staging.example.com/api/v1/agents
# 5% of requests will have 5s delay

# Test with errors
# 1% of requests will return HTTP 500
```

## Rate Limiting

### NGINX Ingress

Rate limiting configured in ingress annotations:
- `nginx.ingress.kubernetes.io/rate-limit: "100"` - 100 requests/min per IP
- `nginx.ingress.kubernetes.io/limit-rps: "10"` - 10 requests/sec

### Istio EnvoyFilter

Configured in `traffic-management.yaml`:
- 100 tokens per second per pod
- Token bucket algorithm

Test:
```bash
# Generate rapid requests
for i in {1..200}; do curl https://api.agentmesh.example.com/api/v1/agents; done
# Should see 429 Too Many Requests after 100 requests
```

## Security

### TLS Configuration

**NGINX Ingress**:
- TLS termination at ingress
- Cert-manager automatic certificate renewal
- HTTP → HTTPS redirect enforced

**Istio**:
- TLS termination at gateway
- mTLS between services (STRICT mode)
- Certificate rotation handled by Istio

### Access Control

**Network Policies** (NGINX):
```bash
kubectl apply -f k8s/overlays/prod/network-policy.yaml
```

**AuthorizationPolicy** (Istio):
- Allow from ingress gateway
- Allow from Prometheus (metrics)
- Allow inter-service communication
- JWT validation for API requests

### JWT Authentication

Configure in `istio-config.yaml`:

```yaml
# RequestAuthentication
jwtRules:
- issuer: "https://auth.agentmesh.example.com"
  jwksUri: "https://auth.agentmesh.example.com/.well-known/jwks.json"
```

Test with JWT:
```bash
# Get JWT token
TOKEN=$(curl -s -X POST https://auth.agentmesh.example.com/token \
  -d "client_id=agentmesh" \
  -d "client_secret=secret" | jq -r .access_token)

# Make authenticated request
curl -H "Authorization: Bearer $TOKEN" https://api.agentmesh.example.com/api/v1/agents
```

## Monitoring

### Istio Metrics

Prometheus metrics automatically exposed:

- Request rate: `istio_requests_total`
- Request duration: `istio_request_duration_milliseconds`
- Request size: `istio_request_bytes`
- Response size: `istio_response_bytes`

Query examples:
```promql
# Request rate by service
rate(istio_requests_total{destination_service="agentmesh-service.agentmesh-prod.svc.cluster.local"}[5m])

# P95 latency
histogram_quantile(0.95, 
  rate(istio_request_duration_milliseconds_bucket[5m])
)

# Error rate
rate(istio_requests_total{response_code=~"5.."}[5m])
```

### Distributed Tracing

View traces in Jaeger:
- End-to-end request flow
- Service dependencies
- Latency breakdown

### Service Graph

View in Kiali:
- Service topology
- Traffic flow
- Health status

## Troubleshooting

### Ingress Issues

**No external IP assigned**:
```bash
# Check LoadBalancer service
kubectl get svc -n ingress-nginx

# For cloud providers, ensure LoadBalancer type is supported
# For local clusters, use NodePort or MetalLB
```

**TLS certificate not issued**:
```bash
# Check certificate status
kubectl describe certificate agentmesh-prod-cert -n agentmesh-prod

# Check cert-manager logs
kubectl logs -n cert-manager deploy/cert-manager

# Check challenges
kubectl get challenges -n agentmesh-prod
```

**502 Bad Gateway**:
```bash
# Check backend service
kubectl get svc agentmesh-service -n agentmesh-prod

# Check pods are running
kubectl get pods -n agentmesh-prod

# Check ingress logs
kubectl logs -n ingress-nginx deploy/ingress-nginx-controller
```

### Istio Issues

**Sidecar not injected**:
```bash
# Check namespace label
kubectl get namespace agentmesh-prod --show-labels

# Label namespace
kubectl label namespace agentmesh-prod istio-injection=enabled

# Restart pods
kubectl rollout restart deployment/agentmesh -n agentmesh-prod
```

**mTLS errors**:
```bash
# Check mTLS status
istioctl authn tls-check agentmesh-0.agentmesh-prod

# Check peer authentication
kubectl get peerauthentication -n agentmesh-prod

# View Envoy config
istioctl proxy-config secret agentmesh-0.agentmesh-prod
```

**Traffic not routing**:
```bash
# Check VirtualService
kubectl get virtualservice -n agentmesh-prod

# Check DestinationRule
kubectl get destinationrule -n agentmesh-prod

# View Envoy config
istioctl proxy-config routes agentmesh-0.agentmesh-prod
```

## Performance Tuning

### NGINX Ingress

```yaml
# Increase worker connections
nginx.ingress.kubernetes.io/worker-connections: "65536"

# Enable HTTP/2
nginx.ingress.kubernetes.io/http2-push-preload: "true"

# Connection timeouts
nginx.ingress.kubernetes.io/proxy-connect-timeout: "60"
nginx.ingress.kubernetes.io/proxy-send-timeout: "120"
nginx.ingress.kubernetes.io/proxy-read-timeout: "120"
```

### Istio

```bash
# Increase Envoy resources
kubectl set resources deployment/agentmesh -n agentmesh-prod \
  --requests=cpu=100m,memory=128Mi \
  --limits=cpu=2000m,memory=1024Mi

# Tune connection pool
# Edit istio-config.yaml DestinationRule
connectionPool:
  tcp:
    maxConnections: 200
  http:
    http2MaxRequests: 200
```

## Best Practices

1. **Start with NGINX Ingress** - Simpler setup, good for most use cases
2. **Upgrade to Istio** - When you need advanced traffic management
3. **Use TLS everywhere** - Let's Encrypt for staging/prod
4. **Enable mTLS** - For service-to-service encryption
5. **Monitor metrics** - Prometheus + Grafana for both ingress and mesh
6. **Test failover** - Chaos testing with fault injection
7. **Gradual rollouts** - Canary deployments for safety
8. **Rate limit** - Protect against abuse
9. **Circuit breaking** - Prevent cascading failures
10. **Document changes** - Track configuration in Git

## Migration Path

### NGINX → Istio

```bash
# 1. Install Istio alongside NGINX
istioctl install --set profile=production -y

# 2. Enable sidecar injection
kubectl label namespace agentmesh-prod istio-injection=enabled

# 3. Restart pods to inject sidecars
kubectl rollout restart deployment/agentmesh -n agentmesh-prod

# 4. Apply Istio Gateway
kubectl apply -f k8s/istio/istio-config.yaml

# 5. Update DNS to point to Istio ingress gateway
kubectl get svc istio-ingressgateway -n istio-system

# 6. Remove NGINX ingress (after validation)
kubectl delete -f k8s/overlays/prod/ingress-production.yaml
```

## References

- [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/)
- [Cert-Manager](https://cert-manager.io/docs/)
- [Istio Documentation](https://istio.io/latest/docs/)
- [Istio Traffic Management](https://istio.io/latest/docs/concepts/traffic-management/)
- [Istio Security](https://istio.io/latest/docs/concepts/security/)
