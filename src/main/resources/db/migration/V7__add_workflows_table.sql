-- V7: Add workflows table for persistent workflow storage
-- Replaces in-memory workflow storage in WorkflowController

CREATE TABLE IF NOT EXISTS workflows (
    id VARCHAR(36) PRIMARY KEY,
    project_name VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(100) DEFAULT 'default',
    status VARCHAR(50) NOT NULL,
    current_phase VARCHAR(50),
    progress INTEGER DEFAULT 0,
    srs_content TEXT,
    plan_id VARCHAR(100),
    architecture_id VARCHAR(100),
    code_id VARCHAR(100),
    test_id VARCHAR(100),
    review_id VARCHAR(100),
    error_message TEXT,
    phases_json TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_updated_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_workflows_status ON workflows(status);
CREATE INDEX IF NOT EXISTS idx_workflows_tenant_id ON workflows(tenant_id);
CREATE INDEX IF NOT EXISTS idx_workflows_started_at ON workflows(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflows_tenant_status ON workflows(tenant_id, status);

-- Comment
COMMENT ON TABLE workflows IS 'Stores workflow execution state and progress';

