-- Add database-level constraints and functions
-- Week 4: Production hardening

-- ============================================================================
-- CONSTRAINTS
-- ============================================================================

-- Ensure tenant resource limits are positive
ALTER TABLE tenants 
ADD CONSTRAINT check_positive_max_projects CHECK (max_projects > 0),
ADD CONSTRAINT check_positive_max_agents CHECK (max_agents > 0),
ADD CONSTRAINT check_positive_max_storage CHECK (max_storage_mb > 0);

-- Ensure billing amounts are non-negative
ALTER TABLE billing_records 
ADD CONSTRAINT check_positive_amount CHECK (amount >= 0);

-- Ensure token counts are non-negative
ALTER TABLE token_usage_records 
ADD CONSTRAINT check_positive_tokens CHECK (prompt_tokens >= 0 AND completion_tokens >= 0 AND total_tokens >= 0),
ADD CONSTRAINT check_token_sum CHECK (total_tokens = prompt_tokens + completion_tokens);

-- ============================================================================
-- FUNCTIONS FOR DATA INTEGRITY
-- ============================================================================

-- Function to validate project belongs to tenant
CREATE OR REPLACE FUNCTION validate_project_tenant()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM tenants WHERE id = NEW.tenant_id
    ) THEN
        RAISE EXCEPTION 'Tenant % does not exist', NEW.tenant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_project_tenant
BEFORE INSERT OR UPDATE ON projects
FOR EACH ROW EXECUTE FUNCTION validate_project_tenant();

-- Function to auto-generate partition key
CREATE OR REPLACE FUNCTION generate_partition_key()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.data_partition_key IS NULL THEN
        NEW.data_partition_key := NEW.tenant_id || '#' || NEW.project_key;
    END IF;
    IF NEW.vector_namespace IS NULL THEN
        SELECT organization_id INTO NEW.vector_namespace FROM tenants WHERE id = NEW.tenant_id;
        NEW.vector_namespace := NEW.vector_namespace || '_' || LOWER(NEW.project_key);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_generate_partition_key
BEFORE INSERT ON projects
FOR EACH ROW EXECUTE FUNCTION generate_partition_key();

-- ============================================================================
-- AUTOMATIC TIMESTAMP MANAGEMENT
-- ============================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tenants_updated_at
BEFORE UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trigger_projects_updated_at
BEFORE UPDATE ON projects
FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- ============================================================================
-- RESOURCE LIMIT ENFORCEMENT
-- ============================================================================

-- Function to check tenant resource limits
CREATE OR REPLACE FUNCTION check_tenant_limits()
RETURNS TRIGGER AS $$
DECLARE
    current_count INTEGER;
    max_allowed INTEGER;
BEGIN
    -- Check project limit
    SELECT COUNT(*), t.max_projects 
    INTO current_count, max_allowed
    FROM projects p
    JOIN tenants t ON t.id = NEW.tenant_id
    WHERE p.tenant_id = NEW.tenant_id AND p.status = 'ACTIVE'
    GROUP BY t.max_projects;
    
    IF current_count >= max_allowed THEN
        RAISE EXCEPTION 'Tenant % has reached maximum project limit (%)', 
            NEW.tenant_id, max_allowed;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_check_tenant_project_limit
BEFORE INSERT ON projects
FOR EACH ROW EXECUTE FUNCTION check_tenant_limits();

-- ============================================================================
-- AUDIT LOGGING
-- ============================================================================

-- Audit table for sensitive changes
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    record_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL, -- INSERT, UPDATE, DELETE
    old_values JSONB,
    new_values JSONB,
    changed_by VARCHAR(255),
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_table_record ON audit_log(table_name, record_id);
CREATE INDEX idx_audit_timestamp ON audit_log(changed_at DESC);

-- Function to log tenant changes
CREATE OR REPLACE FUNCTION audit_tenant_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO audit_log (table_name, record_id, action, old_values)
        VALUES ('tenants', OLD.id, 'DELETE', row_to_json(OLD));
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit_log (table_name, record_id, action, old_values, new_values)
        VALUES ('tenants', NEW.id, 'UPDATE', row_to_json(OLD), row_to_json(NEW));
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO audit_log (table_name, record_id, action, new_values)
        VALUES ('tenants', NEW.id, 'INSERT', row_to_json(NEW));
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_audit_tenants
AFTER INSERT OR UPDATE OR DELETE ON tenants
FOR EACH ROW EXECUTE FUNCTION audit_tenant_changes();

-- ============================================================================
-- PARTITIONING PREPARATION (for future scaling)
-- ============================================================================

-- Comments for future table partitioning strategy
COMMENT ON TABLE blackboard_entry IS 
'Candidate for partitioning by timestamp (monthly) or data_partition_key (tenant-based)';

COMMENT ON TABLE token_usage_records IS 
'Candidate for time-based partitioning (monthly) for archival';

COMMENT ON TABLE billing_records IS 
'Candidate for time-based partitioning (monthly) for compliance/archival';

COMMENT ON TABLE mast_violations IS 
'Consider partitioning by detected_at or archiving resolved violations > 90 days';
