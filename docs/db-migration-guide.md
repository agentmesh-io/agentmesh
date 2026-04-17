# Database Migration Guide

## Overview

AgentMesh uses **Flyway** for database schema versioning and migrations. This ensures consistent database state across all environments (dev, staging, production).

## Migration Files

Migration files are located in `src/main/resources/db/migration/` and follow this naming convention:

- `V1__initial_schema.sql` - Initial database schema
- `V2__add_performance_indexes.sql` - Performance optimization indexes
- `V3__add_constraints_and_functions.sql` - Constraints, triggers, and functions

### Naming Convention

```
V<VERSION>__<DESCRIPTION>.sql
```

- `V` - Versioned migration prefix
- `<VERSION>` - Version number (e.g., 1, 2, 3, 1.1, 2.5)
- `__` - Separator (double underscore)
- `<DESCRIPTION>` - Human-readable description (snake_case)
- `.sql` - SQL file extension

## Configuration

### Spring Boot Configuration

Add to `application.yaml`:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
    table: flyway_schema_history
    validate-on-migrate: true
    clean-disabled: true  # CRITICAL: Prevent accidental data loss
```

### Environment Variables

Required for production:

```bash
DB_HOST=postgres-service.agentmesh.svc.cluster.local
DB_PORT=5432
DB_NAME=agentmesh
DB_USER=postgres
DB_PASSWORD=<from-k8s-secret>
```

## Migration Workflow

### 1. Development

Create new migration:

```bash
# Create new migration file
cat > src/main/resources/db/migration/V4__add_new_feature.sql << 'EOF'
-- Add your DDL here
ALTER TABLE tenants ADD COLUMN new_field VARCHAR(255);
EOF
```

Test migration:

```bash
# Start PostgreSQL locally
docker run -d --name postgres-test \
  -e POSTGRES_DB=agentmesh \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine

# Run application (Flyway runs automatically)
mvn spring-boot:run

# Or run Flyway directly
mvn flyway:migrate

# Check migration status
mvn flyway:info
```

### 2. Staging

Deploy to staging:

```bash
# Apply migrations
kubectl exec -n agentmesh-staging deployment/agentmesh -- \
  java -jar app.jar --spring.flyway.migrate

# Verify
kubectl exec -n agentmesh-staging postgres-0 -- \
  psql -U postgres -d agentmesh -c "SELECT * FROM flyway_schema_history;"
```

### 3. Production

**IMPORTANT**: Always test in staging first!

```bash
# Backup database
kubectl exec -n agentmesh postgres-0 -- \
  pg_dump -U postgres agentmesh > backup-$(date +%Y%m%d-%H%M%S).sql

# Apply migrations during maintenance window
kubectl rollout restart deployment/agentmesh -n agentmesh

# Flyway runs automatically on application startup

# Verify migration
kubectl logs -n agentmesh deployment/agentmesh | grep Flyway
```

## Common Commands

### Check Migration Status

```bash
mvn flyway:info
```

Output:
```
+-----------+---------+---------------------+------+---------------------+---------+
| Category  | Version | Description         | Type | Installed On        | State   |
+-----------+---------+---------------------+------+---------------------+---------+
| Versioned | 1       | initial schema      | SQL  | 2024-01-01 10:00:00 | Success |
| Versioned | 2       | add perf indexes    | SQL  | 2024-01-01 10:00:01 | Success |
| Versioned | 3       | add constraints     | SQL  | 2024-01-01 10:00:02 | Success |
+-----------+---------+---------------------+------+---------------------+---------+
```

### Validate Migrations

```bash
mvn flyway:validate
```

### Baseline Existing Database

For databases that already exist:

```bash
mvn flyway:baseline -Dflyway.baseline-version=1
```

### Repair Failed Migration

If a migration fails:

```bash
# Fix the SQL file
# Then repair the metadata
mvn flyway:repair
```

### Clean Database (DEV ONLY!)

**DANGER**: This deletes all data!

```bash
# ONLY for local development
mvn flyway:clean
```

## Migration Best Practices

### 1. Never Modify Applied Migrations

Once a migration is applied to production, **NEVER** modify it. Instead, create a new migration.

❌ **Wrong**:
```sql
-- V2__add_column.sql (already applied)
ALTER TABLE tenants ADD COLUMN email VARCHAR(255); -- Modified!
```

✅ **Correct**:
```sql
-- V4__modify_email_column.sql (new migration)
ALTER TABLE tenants ALTER COLUMN email TYPE VARCHAR(500);
```

### 2. Test Migrations

Always test migrations:

1. **Local database** - Verify syntax and logic
2. **Staging environment** - Test with production-like data
3. **Rollback plan** - Have a rollback migration ready

### 3. Idempotent Migrations

Make migrations safe to re-run:

```sql
-- Good: Idempotent
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS email VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_tenants_email ON tenants(email);

-- Bad: Fails on re-run
ALTER TABLE tenants ADD COLUMN email VARCHAR(255);
CREATE INDEX idx_tenants_email ON tenants(email);
```

### 4. Backwards Compatibility

Migrations should not break existing code:

```sql
-- Phase 1: Add new column (nullable)
ALTER TABLE tenants ADD COLUMN new_field VARCHAR(255);

-- Deploy code that uses new_field
-- ...

-- Phase 2: Make it required (separate migration)
ALTER TABLE tenants ALTER COLUMN new_field SET NOT NULL;
```

### 5. Performance Considerations

For large tables:

```sql
-- Create index CONCURRENTLY (doesn't lock table)
CREATE INDEX CONCURRENTLY idx_large_table ON large_table(column);

-- Add column with default (PostgreSQL 11+)
ALTER TABLE large_table ADD COLUMN status VARCHAR(50) DEFAULT 'ACTIVE';
```

## Rollback Strategy

### 1. Rollback Migrations

Create paired rollback migrations:

```sql
-- V5__add_feature.sql
ALTER TABLE tenants ADD COLUMN feature_flag BOOLEAN DEFAULT FALSE;

-- V6__rollback_add_feature.sql
ALTER TABLE tenants DROP COLUMN feature_flag;
```

### 2. Application Rollback

If migration succeeds but application fails:

```bash
# Rollback to previous version
kubectl rollout undo deployment/agentmesh -n agentmesh

# Create rollback migration if needed
cat > V7__rollback_feature.sql << 'EOF'
-- Undo V5 changes
ALTER TABLE tenants DROP COLUMN feature_flag;
EOF
```

### 3. Database Restore

If all else fails:

```bash
# Restore from backup
kubectl cp backup-20240101.sql agentmesh/postgres-0:/tmp/
kubectl exec -n agentmesh postgres-0 -- \
  psql -U postgres -d agentmesh < /tmp/backup-20240101.sql
```

## Troubleshooting

### Migration Checksum Mismatch

Error:
```
Migration checksum mismatch for migration version 2
```

**Cause**: Migration file was modified after being applied.

**Solution**:
```bash
# Repair metadata (if you're sure the change is safe)
mvn flyway:repair

# Or revert the file to original state
git checkout src/main/resources/db/migration/V2__*.sql
```

### Failed Migration

Error:
```
Migration V3 failed
```

**Solution**:
```bash
# 1. Fix the SQL error in V3
# 2. Repair Flyway metadata
mvn flyway:repair

# 3. Re-run migration
mvn flyway:migrate
```

### Baseline Existing Database

If you're adding Flyway to an existing project:

```bash
# Mark current state as baseline
mvn flyway:baseline -Dflyway.baseline-version=0

# New migrations will be applied
mvn flyway:migrate
```

## Production Checklist

Before applying migrations in production:

- [ ] Migrations tested in dev and staging
- [ ] Database backup created
- [ ] Rollback plan documented
- [ ] Maintenance window scheduled
- [ ] Team notified
- [ ] Monitoring enabled
- [ ] Post-migration verification queries ready

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL DDL](https://www.postgresql.org/docs/current/ddl.html)
- [Spring Boot Flyway](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)

## Current Migrations

| Version | Description | Status |
|---------|-------------|--------|
| V1 | Initial schema (tenants, projects, blackboard, MAST, billing, tokens) | ✅ Ready |
| V2 | Performance indexes (composite, partial, covering) | ✅ Ready |
| V3 | Constraints, triggers, functions, audit log | ✅ Ready |
