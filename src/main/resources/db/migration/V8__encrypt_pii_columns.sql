-- V8: Alter PII columns to TEXT type for AES-256-GCM encrypted ciphertext storage
-- Per Architect Protocol v7.17 §5: OWASP Top 10 + AES-256 for PII on SSD

-- Tenant PII fields: name, organization_id, data_region
-- Encrypted values are Base64-encoded and prefixed with "ENC::" — can be longer than VARCHAR(255)

-- Drop unique constraints that won't work with encrypted values
-- (encrypted values are non-deterministic due to random IV — same plaintext produces different ciphertext)
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS uk_tenants_name;
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS uk_tenants_organization_id;

-- Alter column types to TEXT
ALTER TABLE tenants ALTER COLUMN name TYPE TEXT;
ALTER TABLE tenants ALTER COLUMN organization_id TYPE TEXT;
ALTER TABLE tenants ALTER COLUMN data_region TYPE TEXT;

-- Add a comment for documentation
COMMENT ON COLUMN tenants.name IS 'PII: Encrypted at rest with AES-256-GCM when PII_ENCRYPTION_KEY is set';
COMMENT ON COLUMN tenants.organization_id IS 'PII: Encrypted at rest with AES-256-GCM when PII_ENCRYPTION_KEY is set';
COMMENT ON COLUMN tenants.data_region IS 'PII: Encrypted at rest with AES-256-GCM when PII_ENCRYPTION_KEY is set';

