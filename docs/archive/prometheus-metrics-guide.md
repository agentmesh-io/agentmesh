# AgentMesh Prometheus Metrics Guide

## Overview

AgentMesh exposes comprehensive metrics via Micrometer/Prometheus for monitoring, alerting, and performance analysis. All metrics are available at `/actuator/prometheus`.

## Metrics Categories

### 1. Agent Lifecycle Metrics

Track agent creation, execution, and health.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `agentmesh_agents_created_total` | Counter | Total agents created | `tenant_id` |
| `agentmesh_agents_destroyed_total` | Counter | Total agents destroyed | `tenant_id` |
| `agentmesh_agents_active` | Gauge | Currently active agents | - |
| `agentmesh_agents_by_tenant` | Gauge | Active agents per tenant | `tenant` |
| `agentmesh_agent_tasks_total` | Counter | Total tasks executed | `agent_type`, `tenant_id` |
| `agentmesh_agent_tasks_success` | Counter | Successful tasks | `agent_type`, `tenant_id` |
| `agentmesh_agent_tasks_failure` | Counter | Failed tasks | `agent_type`, `tenant_id`, `error_type` |
| `agentmesh_agent_execution_duration_seconds` | Histogram | Task execution time | `agent_type`, `tenant_id`, `status` |
| `agentmesh_agent_health` | Gauge | Agent health score (0-100) | `agent_id` |

**Example Queries**:
```promql
# Agent success rate (last 5 minutes)
100 * sum(rate(agentmesh_agent_tasks_success[5m])) / sum(rate(agentmesh_agent_tasks_total[5m]))

# Average execution time by agent type
rate(agentmesh_agent_execution_duration_seconds_sum[5m]) / rate(agentmesh_agent_execution_duration_seconds_count[5m])

# Active agents trend
agentmesh_agents_active
```

### 2. MAST Violation Metrics

Monitor failure modes and agent health.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `agentmesh_mast_violations_total` | Counter | Total violations detected | `failure_mode`, `category` |
| `agentmesh_mast_violations_by_mode` | Counter | Violations by failure mode | `mode` |
| `agentmesh_mast_violations_by_severity` | Counter | Violations by severity | `severity` |
| `agentmesh_mast_unresolved_violations` | Gauge | Unresolved violations count | - |

**Failure Modes**:
- `FM_1_1`, `FM_1_2`, `FM_1_3`, `FM_1_4` - Specification Issues
- `FM_2_1`, `FM_2_2`, `FM_2_3`, `FM_2_4` - Inter-Agent Misalignment
- `FM_3_1`, `FM_3_2`, `FM_3_3`, `FM_3_4`, `FM_3_5`, `FM_3_6` - Task Verification

**Example Queries**:
```promql
# Critical violations per minute
sum(rate(agentmesh_mast_violations_by_severity{severity="CRITICAL"}[1m])) * 60

# Violations by failure mode (last hour)
sum by (mode) (increase(agentmesh_mast_violations_by_mode[1h]))

# Unresolved violations trend
agentmesh_mast_unresolved_violations
```

### 3. LLM Integration Metrics

Track LLM usage, cost, and performance.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `agentmesh_llm_calls_total` | Counter | Total LLM API calls | - |
| `agentmesh_llm_tokens_total` | Counter | Total tokens consumed | - |
| `agentmesh_llm_call_duration_seconds` | Histogram | LLM call latency | - |
| `agentmesh_selfcorrection_attempts_total` | Counter | Self-correction attempts | - |
| `agentmesh_selfcorrection_successes_total` | Counter | Successful self-corrections | - |
| `agentmesh_selfcorrection_failures_total` | Counter | Failed self-corrections | - |
| `agentmesh_selfcorrection_duration_seconds` | Histogram | Self-correction duration | - |

**Example Queries**:
```promql
# Token consumption rate (per second)
rate(agentmesh_llm_tokens_total[5m])

# Average LLM call latency
rate(agentmesh_llm_call_duration_seconds_sum[5m]) / rate(agentmesh_llm_call_duration_seconds_count[5m])

# Self-correction success rate
100 * sum(rate(agentmesh_selfcorrection_successes_total[5m])) / sum(rate(agentmesh_selfcorrection_attempts_total[5m]))

# P95 LLM call latency
histogram_quantile(0.95, sum(rate(agentmesh_llm_call_duration_seconds_bucket[5m])) by (le))
```

### 4. Blackboard Metrics

Monitor shared memory operations.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `agentmesh_blackboard_posts_total` | Counter | Total posts to blackboard | `tenant_id`, `post_type` |
| `agentmesh_blackboard_queries_total` | Counter | Total queries to blackboard | `tenant_id` |
| `agentmesh_blackboard_query_duration_seconds` | Histogram | Query latency | `tenant_id` |
| `agentmesh_blackboard_entries` | Gauge | Current entry count | - |

**Example Queries**:
```promql
# Blackboard operations per second
rate(agentmesh_blackboard_posts_total[5m]) + rate(agentmesh_blackboard_queries_total[5m])

# Average query time
rate(agentmesh_blackboard_query_duration_seconds_sum[5m]) / rate(agentmesh_blackboard_query_duration_seconds_count[5m])

# Blackboard growth rate
rate(agentmesh_blackboard_entries[5m])
```

### 5. Memory/RAG Metrics

Track vector store and hybrid search.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `agentmesh_memory_operations_total` | Counter | Total memory operations | `tenant_id`, `operation_type` |
| `agentmesh_memory_hybrid_search_total` | Counter | Hybrid searches executed | `tenant_id` |
| `agentmesh_memory_search_duration_seconds` | Histogram | Search latency | `tenant_id` |
| `agentmesh_memory_search_results` | Summary | Search result counts | `tenant_id` |

**Example Queries**:
```promql
# P99 search latency
histogram_quantile(0.99, sum(rate(agentmesh_memory_search_duration_seconds_bucket[5m])) by (le))

# Average search results
rate(agentmesh_memory_search_results_sum[5m]) / rate(agentmesh_memory_search_results_count[5m])

# Search operations per minute
sum(rate(agentmesh_memory_hybrid_search_total[1m])) * 60
```

### 6. Cache Metrics (Week 3)

Monitor Redis caching layer performance.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `agentmesh_cache_hits_total` | Counter | Total cache hits | `cache_name` |
| `agentmesh_cache_misses_total` | Counter | Total cache misses | `cache_name` |
| `agentmesh_cache_operation_duration_seconds` | Histogram | Cache operation time | `cache_name`, `operation` |

**Example Queries**:
```promql
# Cache hit rate (percentage)
100 * sum(rate(agentmesh_cache_hits_total[5m])) / (sum(rate(agentmesh_cache_hits_total[5m])) + sum(rate(agentmesh_cache_misses_total[5m])))

# Hit rate by cache name
100 * sum by (cache_name) (rate(agentmesh_cache_hits_total[5m])) / (sum by (cache_name) (rate(agentmesh_cache_hits_total[5m])) + sum by (cache_name) (rate(agentmesh_cache_misses_total[5m])))

# Cache operation latency P95
histogram_quantile(0.95, sum(rate(agentmesh_cache_operation_duration_seconds_bucket[5m])) by (le, cache_name))
```

### 7. Database Metrics

Monitor HikariCP connection pool and queries.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `hikaricp_connections_active` | Gauge | Active connections | `pool` |
| `hikaricp_connections_idle` | Gauge | Idle connections | `pool` |
| `hikaricp_connections_pending` | Gauge | Pending connection requests | `pool` |
| `hikaricp_connections_max` | Gauge | Maximum pool size | `pool` |
| `hikaricp_connections_timeout_total` | Counter | Connection timeouts | `pool` |

**Example Queries**:
```promql
# Pool utilization (percentage)
100 * hikaricp_connections_active / hikaricp_connections_max

# Connection wait time
rate(hikaricp_connections_acquire_seconds_sum[5m]) / rate(hikaricp_connections_acquire_seconds_count[5m])

# Connection timeouts per minute
rate(hikaricp_connections_timeout_total[1m]) * 60
```

### 8. Kafka Metrics

Monitor event streaming.

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `kafka_producer_record_send_total` | Counter | Messages produced | `topic` |
| `kafka_consumer_records_consumed_total` | Counter | Messages consumed | `topic` |
| `kafka_producer_record_error_total` | Counter | Producer errors | `topic` |

## Recording Rules

Pre-calculated metrics for common queries (defined in `prometheus-servicemonitor.yaml`):

| Rule | Expression | Description |
|------|------------|-------------|
| `agentmesh:cache:hit_rate` | Cache hit percentage | Overall cache effectiveness |
| `agentmesh:agent:success_rate` | Agent task success percentage | Agent reliability |
| `agentmesh:llm:avg_duration_seconds` | Average LLM call time | LLM performance |
| `agentmesh:mast:violations_per_minute` | MAST violations rate | System health |
| `agentmesh:database:query_duration_p95` | 95th percentile DB latency | Database performance |
| `agentmesh:tokens:rate_per_second` | Token consumption rate | Cost tracking |
| `agentmesh:hikari:pool_utilization` | Connection pool usage | Resource utilization |

## Accessing Metrics

### 1. Direct Endpoint

```bash
# From within cluster
curl http://agentmesh-service:8080/actuator/prometheus

# From local machine (port-forward)
kubectl port-forward -n agentmesh svc/agentmesh-service 8080:8080
curl http://localhost:8080/actuator/prometheus
```

### 2. Prometheus UI

```bash
# Port-forward to Prometheus
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090

# Access http://localhost:9090
# Query examples in Graph tab
```

### 3. Grafana

See [Grafana Dashboards Guide](./grafana-dashboards.md) (Task 5)

## Alert Configuration

See [Alert Rules Configuration](./prometheus-alerts.yaml) (Task 6)

## Best Practices

### 1. Query Optimization

Use recording rules for frequently-used complex queries:
```promql
# Instead of this complex query:
100 * sum(rate(agentmesh_cache_hits_total[5m])) / (sum(rate(agentmesh_cache_hits_total[5m])) + sum(rate(agentmesh_cache_misses_total[5m])))

# Use the pre-calculated rule:
agentmesh:cache:hit_rate
```

### 2. Label Cardinality

Avoid high-cardinality labels (unique IDs):
```promql
# ❌ Bad: agent_id has unbounded cardinality
agentmesh_agent_tasks_total{agent_id="abc-123-def"}

# ✅ Good: Use agent_type instead
agentmesh_agent_tasks_total{agent_type="ProductBacklogAgent"}
```

### 3. Time Ranges

Choose appropriate time ranges:
- Real-time monitoring: `[1m]` or `[5m]`
- Dashboards: `[5m]` or `[15m]`
- Trending: `[1h]` or `[1d]`

### 4. Aggregation

Use appropriate aggregation functions:
```promql
# Sum for counters
sum(rate(agentmesh_agent_tasks_total[5m]))

# Avg for gauges
avg(agentmesh_agents_active)

# Histogram_quantile for percentiles
histogram_quantile(0.95, sum(rate(agentmesh_llm_call_duration_seconds_bucket[5m])) by (le))
```

## Troubleshooting

### No Metrics Available

Check ServiceMonitor:
```bash
kubectl get servicemonitor -n agentmesh agentmesh-metrics -o yaml
```

Check Prometheus targets:
```bash
# Port-forward Prometheus
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090

# Visit http://localhost:9090/targets
# Verify agentmesh endpoints are "UP"
```

### Metrics Not Updating

Check application logs:
```bash
kubectl logs -n agentmesh deployment/agentmesh | grep -i metric
```

Verify actuator endpoint:
```bash
kubectl exec -n agentmesh deployment/agentmesh -- curl localhost:8080/actuator/prometheus | head -20
```

### High Cardinality Warnings

Check metric cardinality:
```bash
# In Prometheus UI
topk(10, count by (__name__) ({__name__=~".+"}))
```

Identify problematic labels:
```bash
# Check specific metric
count(agentmesh_agent_tasks_total) by (agent_type)
```

## References

- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Operator](https://prometheus-operator.dev/)
