package com.therighthandapp.agentmesh.mast;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for <b>M13.3 commit 2 — Finding F2</b>
 * (see {@code docs/ACCEPTANCE_M13.3.md}).
 *
 * <p><b>What this test guards against.</b> {@code MASTViolation.evidence}
 * was originally annotated with both {@link Lob} and
 * {@code @Column(columnDefinition = "TEXT")}. On PostgreSQL, the {@code @Lob}
 * marker steers Hibernate onto the {@code ClobJdbcType} code path which reads
 * the value via {@code LargeObjectManager} — and PostgreSQL refuses Large
 * Objects in auto-commit mode:
 *
 * <pre>
 * org.postgresql.util.PSQLException: Large Objects may not be used in auto-commit mode.
 * </pre>
 *
 * <p>The {@link MASTValidator} read methods
 * ({@link MASTValidator#getRecentViolations()},
 * {@link MASTValidator#getUnresolvedViolations()}) are deliberately
 * non-transactional read paths, so {@link com.therighthandapp.agentmesh.api.MASTController#getRecentViolations()}
 * / {@code getUnresolvedViolations()} / {@code getViolationsByAgent(String)}
 * threw 500 (surfaced as 403 through Spring's exception handling) whenever
 * the table held at least one row.
 *
 * <p>The underlying database column has been {@code text} since Flyway V1, so
 * the fix is to drop the JPA {@code @Lob} marker — Hibernate then uses
 * {@code VarcharJdbcType} and reads the value as a plain {@link String} at
 * result-set time, no transaction required.
 *
 * <p>H2 (used by all controller integration tests) tolerates the original
 * code path silently, so this defect was not caught by
 * {@link com.therighthandapp.agentmesh.api.MASTControllerIntegrationTest}.
 * This reflection-based assertion is intentionally <b>portable across DB
 * engines</b> — it locks in the entity-mapping contract regardless of the
 * test datasource. If anyone re-introduces {@code @Lob} on the {@code evidence}
 * field a regression on PostgreSQL is guaranteed; this test fails first.
 */
class MASTViolationLobMappingTest {

    @Test
    void evidenceFieldMustNotCarryLobAnnotation() throws NoSuchFieldException {
        Field evidence = MASTViolation.class.getDeclaredField("evidence");

        assertThat(evidence.isAnnotationPresent(Lob.class))
                .as("MASTViolation.evidence must NOT be @Lob — see Finding F2 in "
                        + "docs/ACCEPTANCE_M13.3.md. @Lob steers Hibernate onto the "
                        + "PostgreSQL LargeObject path which fails outside a transaction.")
                .isFalse();

        Column column = evidence.getAnnotation(Column.class);
        assertThat(column)
                .as("evidence should still declare columnDefinition = \"TEXT\" so the Flyway "
                        + "schema and the JPA mapping stay aligned (no surprise VARCHAR(255)).")
                .isNotNull();
        assertThat(column.columnDefinition()).isEqualToIgnoringCase("TEXT");
    }

    @Test
    void resolutionFieldMustNotRegressToLob() throws NoSuchFieldException {
        // Sister field — same shape, same risk if @Lob ever creeps in.
        Field resolution = MASTViolation.class.getDeclaredField("resolution");
        assertThat(resolution.isAnnotationPresent(Lob.class))
                .as("MASTViolation.resolution must remain @Lob-free for the same reason as "
                        + "evidence (see Finding F2). The PG column is text; no Lob handling needed.")
                .isFalse();
    }
}

