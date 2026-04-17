-- Initial AgentMesh Database Schema
-- Creates all core tables for tenants, projects, blackboard, MAST, billing, and token usage

-- ============================================================================
-- TENANTS TABLE
-- ============================================================================
CREATE TABLE tenants (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    organization_id VARCHAR(255) NOT NULL UNIQUE,
    tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    k8s_namespace VARCHAR(255) NOT NULL UNIQUE,
    
    -- Resource limits
    max_projects INTEGER NOT NULL DEFAULT 10,
    max_agents INTEGER NOT NULL DEFAULT 50,
    max_storage_mb BIGINT NOT NULL DEFAULT 10240,
    
    -- Data sovereignty
    data_region VARCHAR(100),
    requires_data_locality BOOLEAN DEFAULT FALSE,
    
    -- Billing
    outcome_based_billing BOOLEAN DEFAULT TRUE,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CHECK (tier IN ('FREE', 'STANDARD', 'PREMIUM', 'ENTERPRISE')),
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE INDEX idx_tenants_org_id ON tenants(organization_id);
CREATE INDEX idx_tenants_status ON tenants(status);

-- ============================================================================
-- TENANT LORA ADAPTERS TABLE (ElementCollection)
-- ============================================================================
CREATE TABLE tenant_lora_adapters (
    tenant_id VARCHAR(36) NOT NULL,
    adapter_name VARCHAR(255) NOT NULL,
    adapter_path TEXT NOT NULL,
    
    PRIMARY KEY (tenant_id, adapter_name),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- ============================================================================
-- PROJECTS TABLE
-- ============================================================================
CREATE TABLE projects (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    project_key VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    
    -- Kubernetes
    k8s_label VARCHAR(255),
    
    -- Resource limits
    max_agents INTEGER,
    max_storage_mb BIGINT,
    
    -- Data isolation
    data_partition_key VARCHAR(255) NOT NULL,
    vector_namespace VARCHAR(255) NOT NULL,
    
    -- Billing
    track_costs BOOLEAN DEFAULT TRUE,
    
    -- Temporal
    workflow_id VARCHAR(255),
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT unique_tenant_project_key UNIQUE (tenant_id, project_key),
    CHECK (status IN ('ACTIVE', 'ARCHIVED', 'SUSPENDED'))
);

CREATE INDEX idx_projects_tenant ON projects(tenant_id);
CREATE INDEX idx_projects_key ON projects(project_key);
CREATE INDEX idx_projects_status ON projects(status);

-- ============================================================================
-- BLACKBOARD ENTRIES TABLE
-- ============================================================================
CREATE TABLE blackboard_entry (
    id BIGSERIAL PRIMARY KEY,
    
    -- Multi-tenancy
    tenant_id VARCHAR(36),
    project_id VARCHAR(36),
    data_partition_key VARCHAR(255),
    
    -- Entry data
    agent_id VARCHAR(255) NOT NULL,
    entry_type VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    -- Concurrency control
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Relationships
    parent_entry_id VARCHAR(255)
);

CREATE INDEX idx_blackboard_tenant_project ON blackboard_entry(tenant_id, project_id);
CREATE INDEX idx_blackboard_data_partition ON blackboard_entry(data_partition_key);
CREATE INDEX idx_blackboard_agent ON blackboard_entry(agent_id);
CREATE INDEX idx_blackboard_type ON blackboard_entry(entry_type);
CREATE INDEX idx_blackboard_timestamp ON blackboard_entry(timestamp DESC);

-- ============================================================================
-- MAST VIOLATIONS TABLE
-- ============================================================================
CREATE TABLE mast_violations (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL,
    failure_mode VARCHAR(100) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    evidence TEXT,
    detected_at TIMESTAMP NOT NULL,
    severity VARCHAR(50),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolution TEXT,
    
    CHECK (failure_mode IN (
        'FM_1_1', 'FM_1_2', 'FM_1_3', 'FM_1_4', 
        'FM_2_1', 'FM_2_2', 'FM_2_3', 'FM_2_4',
        'FM_3_1', 'FM_3_2', 'FM_3_3', 'FM_3_4', 'FM_3_5', 'FM_3_6'
    )),
    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX idx_mast_agent ON mast_violations(agent_id);
CREATE INDEX idx_mast_mode ON mast_violations(failure_mode);
CREATE INDEX idx_mast_detected ON mast_violations(detected_at DESC);
CREATE INDEX idx_mast_resolved ON mast_violations(resolved);
CREATE INDEX idx_mast_severity ON mast_violations(severity);

-- ============================================================================
-- BILLING RECORDS TABLE
-- ============================================================================
CREATE TABLE billing_records (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(255),
    billing_type VARCHAR(50) NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    success BOOLEAN,
    iterations INTEGER,
    timestamp TIMESTAMP NOT NULL,
    description VARCHAR(1000),
    
    CHECK (billing_type IN ('OUTCOME_BASED', 'TOKEN_BASED', 'SUBSCRIPTION'))
);

CREATE INDEX idx_billing_tenant_timestamp ON billing_records(tenant_id, timestamp DESC);
CREATE INDEX idx_billing_project_timestamp ON billing_records(project_id, timestamp DESC);
CREATE INDEX idx_billing_type ON billing_records(billing_type);

-- ============================================================================
-- TOKEN USAGE RECORDS TABLE
-- ============================================================================
CREATE TABLE token_usage_records (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    estimated_cost DOUBLE PRECISION,
    duration_ms BIGINT,
    operation_type VARCHAR(50),
    task_id VARCHAR(255)
);

CREATE INDEX idx_token_tenant_timestamp ON token_usage_records(tenant_id, timestamp DESC);
CREATE INDEX idx_token_project_timestamp ON token_usage_records(project_id, timestamp DESC);
CREATE INDEX idx_token_timestamp ON token_usage_records(timestamp DESC);
CREATE INDEX idx_token_model ON token_usage_records(model);

-- ============================================================================
-- VIEWS FOR ANALYTICS
-- ============================================================================

-- Tenant usage summary
CREATE VIEW v_tenant_usage AS
SELECT 
    t.id AS tenant_id,
    t.name AS tenant_name,
    t.organization_id,
    t.tier,
    COUNT(DISTINCT p.id) AS project_count,
    SUM(CASE WHEN tur.timestamp > DATEADD('HOUR', -24, NOW()) THEN tur.total_tokens ELSE 0 END) AS tokens_24h,
    SUM(CASE WHEN br.timestamp > DATEADD('HOUR', -24, NOW()) THEN br.amount ELSE 0 END) AS cost_24h
FROM tenants t
LEFT JOIN projects p ON p.tenant_id = t.id
LEFT JOIN token_usage_records tur ON tur.tenant_id = t.id
LEFT JOIN billing_records br ON br.tenant_id = t.id
GROUP BY t.id, t.name, t.organization_id, t.tier;

-- MAST violation summary by agent
CREATE VIEW v_mast_summary AS
SELECT 
    agent_id,
    failure_mode,
    COUNT(*) AS total_violations,
    COUNT(CASE WHEN resolved THEN 1 END) AS resolved_count,
    COUNT(CASE WHEN NOT resolved THEN 1 END) AS unresolved_count,
    MAX(detected_at) AS last_violation
FROM mast_violations
GROUP BY agent_id, failure_mode;

-- Daily token usage by project
CREATE VIEW v_daily_token_usage AS
SELECT 
    DATE(timestamp) AS usage_date,
    tenant_id,
    project_id,
    model,
    SUM(total_tokens) AS total_tokens,
    SUM(estimated_cost) AS total_cost,
    COUNT(*) AS request_count
FROM token_usage_records
GROUP BY DATE(timestamp), tenant_id, project_id, model;

COMMENT ON TABLE tenants IS 'Multi-tenant organizations with isolated resources';
COMMENT ON TABLE projects IS 'Logical workspaces within tenants for agent groups';
COMMENT ON TABLE blackboard_entry IS 'Shared memory space where agents read/write context';
COMMENT ON TABLE mast_violations IS 'MAST failure mode violations for monitoring';
COMMENT ON TABLE billing_records IS 'Billable events (outcome-based or token-based)';
COMMENT ON TABLE token_usage_records IS 'LLM token usage for billing and analytics';
