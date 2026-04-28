-- V9 — M13.2 AuthN/Z: users table + seed admin (Sprint 13.2)
--
-- Per docs/PLAN_M13.2.md §3 the role values are: admin / developer / viewer.
-- Password hashes are BCrypt (Spring Security default, cost 10). The seed
-- 'admin@agentmesh.local' password is "admin-change-me" — operators MUST
-- rotate via POST /api/auth/users/{id}/password before exposing prod.
--
-- Tenants table is assumed already provisioned by V1. If not, this migration
-- is forward-compatible because the tenant_id column is just a UUID without
-- a hard FK (multi-tenancy isolation is enforced at the application layer
-- via TenantContext + AccessControlService — see security/AccessControlService.java).

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    username        VARCHAR(190) NOT NULL,
    email           VARCHAR(255),
    password_hash   VARCHAR(255) NOT NULL,
    roles           TEXT NOT NULL DEFAULT 'viewer',  -- comma-separated
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    locked          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ,
    CONSTRAINT users_username_per_tenant_uq UNIQUE (tenant_id, username)
);

CREATE INDEX IF NOT EXISTS idx_users_tenant       ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_username     ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_email_lower  ON users (LOWER(email));

-- Seed the bootstrap admin only if no admin yet exists for the default tenant.
-- Default tenant_id mirrors the constant used by ProjectInitializationService /
-- WorkflowController when no X-Tenant-Id header is supplied.
INSERT INTO users (tenant_id, username, email, password_hash, roles)
SELECT
    '00000000-0000-0000-0000-000000000000'::uuid,
    'admin',
    'admin@agentmesh.local',
    -- BCrypt placeholder hash — fixed up by V10 migration with a real hash
    -- of "admin-change-me" (Spring BCryptPasswordEncoder, cost 10).
    -- See V10__fix_admin_password_hash.sql.
    '$2a$10$dxjvLMr4S8PGQMPvB.Qzn.tFVx1sUMzjs/TyV1N9WXQyWZsR9YyZi',
    'admin'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
      AND username = 'admin'
);

COMMENT ON TABLE  users IS 'M13.2 — local IdP users. Roles: admin/developer/viewer (comma-separated).';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash, cost 10 (Spring Security default).';
COMMENT ON COLUMN users.roles         IS 'Comma-separated subset of {admin,developer,viewer}.';

