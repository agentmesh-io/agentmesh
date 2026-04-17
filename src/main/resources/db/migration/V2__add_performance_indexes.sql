-- Add indexes for improved query performance
-- Week 4 optimization for production deployment

-- ============================================================================
-- COMPOSITE INDEXES FOR COMMON QUERY PATTERNS
-- ============================================================================

-- Blackboard: Queries often filter by tenant+project+type+timestamp
CREATE INDEX idx_blackboard_tenant_project_type_timestamp 
ON blackboard_entry(tenant_id, project_id, entry_type, timestamp DESC);

-- Blackboard: Queries for recent entries by agent
CREATE INDEX idx_blackboard_agent_timestamp 
ON blackboard_entry(agent_id, timestamp DESC);

-- MAST: Queries for unresolved critical violations
CREATE INDEX idx_mast_unresolved_critical 
ON mast_violations(resolved, severity, detected_at DESC) 
WHERE NOT resolved AND severity = 'CRITICAL';

-- MAST: Queries for agent violations by mode
CREATE INDEX idx_mast_agent_mode_timestamp 
ON mast_violations(agent_id, failure_mode, detected_at DESC);

-- Billing: Queries for monthly billing aggregation
CREATE INDEX idx_billing_tenant_month 
ON billing_records(tenant_id, DATE_TRUNC('month', timestamp));

-- Token Usage: Queries for cost analysis by model
CREATE INDEX idx_token_tenant_model_timestamp 
ON token_usage_records(tenant_id, model, timestamp DESC);

-- ============================================================================
-- PARTIAL INDEXES FOR SPECIFIC QUERIES
-- ============================================================================

-- Only index active projects (most queries filter on status)
CREATE INDEX idx_projects_active 
ON projects(tenant_id, status) 
WHERE status = 'ACTIVE';

-- Index blackboard entries by timestamp
CREATE INDEX idx_blackboard_recent 
ON blackboard_entry(tenant_id, project_id, timestamp DESC);

-- Only index unresolved violations for alerting
CREATE INDEX idx_mast_unresolved_recent 
ON mast_violations(agent_id, severity, detected_at DESC) 
WHERE NOT resolved;

-- ============================================================================
-- COVERING INDEXES FOR ANALYTICS QUERIES
-- ============================================================================

-- Token usage aggregation without table access
CREATE INDEX idx_token_aggregation 
ON token_usage_records(tenant_id, timestamp DESC) 
INCLUDE (model, total_tokens, estimated_cost);

-- Billing aggregation without table access
CREATE INDEX idx_billing_aggregation 
ON billing_records(tenant_id, timestamp DESC) 
INCLUDE (project_id, billing_type, amount, success);

COMMENT ON INDEX idx_blackboard_tenant_project_type_timestamp IS 'Common query pattern: list entries by tenant/project/type';
COMMENT ON INDEX idx_mast_unresolved_critical IS 'Alert queries: critical unresolved violations';
COMMENT ON INDEX idx_billing_tenant_month IS 'Monthly billing reports';
