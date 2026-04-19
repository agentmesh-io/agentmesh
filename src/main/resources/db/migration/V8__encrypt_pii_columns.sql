-- V8: Alter PII columns to TEXT type for AES-256-GCM encrypted ciphertext storage
-- Per Architect Protocol v7.17 §5: OWASP Top 10 + AES-256 for PII on SSD

-- Tenant PII fields: name, organization_id, data_region
-- Encrypted values are Base64-encoded and prefixed with "ENC::" — can be longer than VARCHAR(255)

-- Step 1: Drop views that depend on the columns we're altering
DROP VIEW IF EXISTS v_tenant_usage;

-- Step 2: Drop unique constraints that won't work with encrypted values
-- (encrypted values are non-deterministic due to random IV)
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_name_key;
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_organization_id_key;

-- Step 3: Alter column types to TEXT
ALTER TABLE tenants ALTER COLUMN name TYPE TEXT;
ALTER TABLE tenants ALTER COLUMN organization_id TYPE TEXT;
ALTER TABLE tenants ALTER COLUMN data_region TYPE TEXT;

-- Step 4: Recreate the view with the altered columns
CREATE VIEW v_tenant_usage AS
SELECT
    t.id AS tenant_id,
    t.name AS tenant_name,
    t.organization_id,
    t.tier,
    COUNT(DISTINCT p.id) AS project_count,
    SUM(CASE WHEN tur.timestamp > NOW() - INTERVAL '24 hours' THEN tur.total_tokens ELSE 0 END) AS tokens_24h,
    SUM(CASE WHEN br.timestamp > NOW() - INTERVAL '24 hours' THEN br.amount ELSE 0 END) AS cost_24h
FROM tenants t
LEFT JOIN projects p ON p.tenant_id = t.id
LEFT JOIN token_usage_records tur ON tur.tenant_id = t.id
LEFT JOIN billing_records br ON br.tenant_id = t.id
GROUP BY t.id, t.name, t.organization_id, t.tier;

-- Step 5: Add comments for documentation
COMMENT ON COLUMN tenants.name IS 'PII: Encrypted at rest with AES-256-GCM when PII_ENCRYPTION_KEY is set';
COMMENT ON COLUMN tenants.organization_id IS 'PII: Encrypted at rest with AES-256-GCM when PII_ENCRYPTION_KEY is set';
COMMENT ON COLUMN tenants.data_region IS 'PII: Encrypted at rest with AES-256-GCM when PII_ENCRYPTION_KEY is set';

