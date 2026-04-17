# Prometheus Alert Rules Guide

## Overview

This guide documents the Prometheus alert rules for AgentMesh, covering critical system conditions, performance degradation, and operational issues.

## Alert Severity Levels

### Critical (Immediate Response Required)
- **Response Time**: Within 5 minutes
- **Impact**: System down or severe degradation
- **Escalation**: Page on-call engineer
- **Examples**: Pod failures, database down, critical MAST violations

### High (Response Within Hours)
- **Response Time**: Within 1-2 hours
- **Impact**: Degraded performance or approaching limits
- **Escalation**: Notify team channel
- **Examples**: High latency, low cache hit rate, resource pressure

### Warning (Should Be Investigated)
- **Response Time**: Within 1 business day
- **Impact**: Potential future issues
- **Escalation**: Create ticket
- **Examples**: Success rate degradation, accumulating violations

### Info (Informational Only)
- **Response Time**: No immediate action
- **Impact**: Awareness of system state
- **Escalation**: Log only
- **Examples**: High agent count, cache eviction rate

## Critical Alerts

### HighMASTViolationRate
**Trigger**: > 10 violations/minute for 5 minutes

**Meaning**: Agents are experiencing widespread issues

**Investigation Steps**:
1. Check MAST dashboard for violation breakdown
2. Review recent deployments or config changes
3. Check LLM provider status
4. Review agent logs for common error patterns

**Resolution**:
```bash
# Check violation details
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/prometheus | grep mast_violations

# Review recent violations
kubectl logs -l app=agentmesh --tail=100 | grep MAST

# Check self-correction status
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/agentmesh.selfcorrection
```

**Prevention**: Monitor violation trends, implement gradual rollouts, enhance input validation

---

### CriticalMASTViolations
**Trigger**: > 2 critical violations/minute for 2 minutes

**Meaning**: Agents experiencing severe failures (safety violations, hallucinations)

**Investigation Steps**:
1. Identify affected failure modes
2. Check if specific agent types affected
3. Review LLM responses for quality issues
4. Verify MAST validation rules functioning

**Resolution**:
```bash
# Check critical violations
kubectl logs -l app=agentmesh | grep "severity=CRITICAL"

# Disable affected agent types if needed
kubectl exec -it agentmesh-0 -- curl -X POST localhost:8080/admin/agents/disable/TYPE_NAME

# Review MAST validation config
kubectl get configmap agentmesh-config -o yaml | grep -A 20 mast
```

**Prevention**: Enhance prompt engineering, implement stricter validation, increase self-correction sensitivity

---

### AgentMeshPodsDown
**Trigger**: < 2 pods available for 2 minutes

**Meaning**: High availability compromised

**Investigation Steps**:
1. Check pod status and events
2. Review pod logs for crash reasons
3. Check resource availability (CPU, memory, storage)
4. Verify database and Redis connectivity

**Resolution**:
```bash
# Check pod status
kubectl get pods -l app=agentmesh

# Describe failing pod
kubectl describe pod agentmesh-X

# Check recent events
kubectl get events --sort-by='.lastTimestamp' | grep agentmesh

# Force restart if needed
kubectl rollout restart deployment agentmesh
```

**Prevention**: Increase resource limits, implement graceful shutdown, improve health checks

---

### DatabaseConnectionPoolExhausted
**Trigger**: > 5 threads waiting for connections for 2 minutes

**Meaning**: Database connection pool saturated

**Investigation Steps**:
1. Check active connection count
2. Identify long-running queries
3. Review connection pool configuration
4. Check for connection leaks

**Resolution**:
```bash
# Check HikariCP metrics
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/hikaricp.connections.active
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/hikaricp.connections.pending

# Check PostgreSQL active connections
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT count(*) FROM pg_stat_activity;"

# Identify slow queries
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT pid, now() - query_start as duration, query FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC;"

# Increase pool size temporarily
kubectl set env deployment/agentmesh SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50
```

**Prevention**: Optimize queries, implement connection timeout, increase pool size, add connection leak detection

---

### AgentTaskFailureSpike
**Trigger**: > 20% failure rate for 5 minutes

**Meaning**: Widespread agent execution failures

**Investigation Steps**:
1. Check error logs for common failures
2. Verify LLM provider availability
3. Check database and cache health
4. Review recent code deployments

**Resolution**:
```bash
# Check failure rate by agent type
kubectl logs -l app=agentmesh | grep "task_status=FAILURE" | awk '{print $3}' | sort | uniq -c

# Check LLM provider status
kubectl exec -it agentmesh-0 -- curl localhost:8080/admin/llm/status

# Rollback to previous version if needed
kubectl rollout undo deployment/agentmesh

# Scale down temporarily if needed
kubectl scale deployment agentmesh --replicas=1
```

**Prevention**: Implement circuit breakers, add retry logic with backoff, enhance error handling

---

### RedisClusterDown
**Trigger**: Redis cluster unreachable for 1 minute

**Meaning**: Cache layer unavailable

**Investigation Steps**:
1. Check Redis pod status
2. Verify network connectivity
3. Check Redis Sentinel status
4. Review Redis logs

**Resolution**:
```bash
# Check Redis pods
kubectl get pods -l app=redis-cluster

# Check Sentinel status
kubectl exec -it redis-sentinel-0 -- redis-cli -p 26379 SENTINEL masters

# Test Redis connectivity
kubectl exec -it agentmesh-0 -- redis-cli -h redis-cluster-service -p 6379 ping

# Force failover if needed
kubectl exec -it redis-sentinel-0 -- redis-cli -p 26379 SENTINEL failover mymaster
```

**Prevention**: Monitor Redis health, implement automatic failover testing, add redundant cache nodes

---

### PostgreSQLDown
**Trigger**: PostgreSQL unreachable for 1 minute

**Meaning**: Database unavailable - critical outage

**Investigation Steps**:
1. Check PostgreSQL pod status
2. Review PostgreSQL logs
3. Check persistent volume status
4. Verify network connectivity

**Resolution**:
```bash
# Check PostgreSQL pod
kubectl get pods -l app=postgres

# Check pod logs
kubectl logs postgres-0 --tail=100

# Describe pod for events
kubectl describe pod postgres-0

# Check PVC status
kubectl get pvc postgres-data

# Restart pod if needed
kubectl delete pod postgres-0  # StatefulSet will recreate
```

**Prevention**: Implement database replication, regular backups, persistent volume monitoring

## High Severity Alerts

### HighDatabaseLatency
**Trigger**: P95 query time > 500ms for 10 minutes

**Investigation Steps**:
1. Identify slow queries
2. Check database load
3. Review query plans
4. Check for missing indexes

**Resolution**:
```bash
# Enable slow query log
kubectl exec -it postgres-0 -- psql -U agentmesh -c "ALTER SYSTEM SET log_min_duration_statement = 500;"
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT pg_reload_conf();"

# Check slow queries
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT query, mean_exec_time, calls FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"

# Check for missing indexes
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT schemaname, tablename, attname, null_frac, avg_width, n_distinct FROM pg_stats WHERE schemaname = 'public' ORDER BY tablename;"

# Analyze table statistics
kubectl exec -it postgres-0 -- psql -U agentmesh -c "ANALYZE VERBOSE;"
```

**Prevention**: Regular query optimization, index maintenance, connection pooling tuning

---

### LowCacheHitRate
**Trigger**: < 50% hit rate for 15 minutes

**Investigation Steps**:
1. Check cache configuration
2. Review cache eviction policies
3. Analyze cache key patterns
4. Check Redis memory usage

**Resolution**:
```bash
# Check cache stats
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/agentmesh.cache.hits
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/agentmesh.cache.misses

# Check Redis memory
kubectl exec -it redis-cluster-0 -- redis-cli INFO memory

# Check eviction stats
kubectl exec -it redis-cluster-0 -- redis-cli INFO stats | grep evicted

# Adjust cache TTL
kubectl set env deployment/agentmesh CACHE_TTL_VIOLATION=7200  # 2 hours
```

**Prevention**: Tune cache TTL, increase Redis memory, implement cache warming

---

### HighLLMLatency
**Trigger**: P95 LLM call time > 10 seconds for 10 minutes

**Investigation Steps**:
1. Check LLM provider status
2. Review request sizes
3. Verify network latency
4. Check concurrent request count

**Resolution**:
```bash
# Check LLM metrics
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/agentmesh.llm.call.duration

# Check active LLM calls
kubectl logs -l app=agentmesh | grep "LLM call started" | wc -l

# Reduce concurrent calls temporarily
kubectl set env deployment/agentmesh LLM_MAX_CONCURRENT=5

# Switch to faster model if available
kubectl set env deployment/agentmesh LLM_MODEL=gpt-4o-mini
```

**Prevention**: Implement request queuing, use streaming responses, cache common responses

## Warning Alerts

### AgentSuccessRateDegraded
**Investigation**: Review failure patterns, check for systemic issues

### HighSelfCorrectionFailureRate
**Investigation**: Analyze correction attempts, review validation rules

### UnresolvedViolationsAccumulating
**Investigation**: Check resolution service, review violation severity

## Configuration

### Applying Alerts

```bash
# Apply alert rules
kubectl apply -f k8s/prometheus-alerts.yaml

# Verify rules loaded
kubectl get prometheusrules -n agentmesh

# Check alert status in Prometheus
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Open http://localhost:9090/alerts
```

### Alert Manager Configuration

Create AlertManager configuration for notifications:

```yaml
# k8s/alertmanager-config.yaml
apiVersion: v1
kind: Secret
metadata:
  name: alertmanager-config
  namespace: monitoring
type: Opaque
stringData:
  alertmanager.yml: |
    global:
      resolve_timeout: 5m
    
    route:
      group_by: ['alertname', 'severity']
      group_wait: 10s
      group_interval: 10s
      repeat_interval: 12h
      receiver: 'default'
      routes:
      - match:
          severity: critical
        receiver: 'pagerduty'
        continue: true
      - match:
          severity: high
        receiver: 'slack-high'
      - match:
          severity: warning
        receiver: 'slack-warning'
    
    receivers:
    - name: 'default'
      slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#agentmesh-alerts'
        title: 'AgentMesh Alert'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
    
    - name: 'pagerduty'
      pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_KEY'
    
    - name: 'slack-high'
      slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#agentmesh-alerts-high'
    
    - name: 'slack-warning'
      slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#agentmesh-alerts-warning'
```

### Silencing Alerts

```bash
# Silence alert during maintenance
cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1alpha1
kind: Alertmanager
metadata:
  name: silence-maintenance
  namespace: monitoring
spec:
  matchers:
  - name: alertname
    value: HighDatabaseLatency
  startsAt: "2025-01-15T10:00:00Z"
  endsAt: "2025-01-15T12:00:00Z"
  comment: "Database maintenance window"
EOF
```

## Testing Alerts

### Trigger Test Alerts

```bash
# Create high MAST violation rate
kubectl exec -it agentmesh-0 -- curl -X POST localhost:8080/admin/test/mast-violations?count=100

# Simulate database latency
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT pg_sleep(1);" &

# Simulate pod failure
kubectl delete pod agentmesh-0

# Check alert state
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Navigate to http://localhost:9090/alerts
```

## Best Practices

1. **Alert Tuning**: Adjust thresholds based on production baselines
2. **Alert Fatigue**: Reduce noise by tuning for durations and combining related alerts
3. **Runbooks**: Maintain detailed runbooks for each critical alert
4. **Testing**: Regularly test alert firing and notification delivery
5. **Review**: Weekly review of alert effectiveness and false positive rate
6. **Escalation**: Define clear escalation paths for each severity
7. **Silencing**: Use silences for planned maintenance, not to hide recurring issues
8. **Metrics**: Track alert metrics (time to resolution, false positive rate)

## Troubleshooting

### Alerts Not Firing

**Check PrometheusRule loaded**:
```bash
kubectl get prometheusrules -n agentmesh
kubectl describe prometheusrule agentmesh-alerts -n agentmesh
```

**Check Prometheus configuration**:
```bash
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Navigate to http://localhost:9090/config
# Search for "agentmesh-alerts"
```

**Check rule evaluation**:
```bash
# Open Prometheus
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Navigate to http://localhost:9090/rules
# Find agentmesh rules and check evaluation status
```

### Alert Manager Not Sending Notifications

**Check AlertManager configuration**:
```bash
kubectl get secret alertmanager-config -n monitoring -o yaml
kubectl logs -n monitoring alertmanager-0
```

**Test webhook**:
```bash
curl -X POST YOUR_SLACK_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d '{"text":"Test alert from AgentMesh"}'
```

### False Positives

**Adjust thresholds**: Increase duration or threshold value
**Add filters**: Use label matchers to exclude specific cases
**Combine conditions**: Use AND/OR logic for more specific triggers

## References

- [Prometheus Alerting Rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)
- [AlertManager Configuration](https://prometheus.io/docs/alerting/latest/configuration/)
- [PromQL Query Examples](https://prometheus.io/docs/prometheus/latest/querying/examples/)
- [Alert Best Practices](https://prometheus.io/docs/practices/alerting/)
