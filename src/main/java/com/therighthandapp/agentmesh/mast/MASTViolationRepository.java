package com.therighthandapp.agentmesh.mast;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MASTViolationRepository extends JpaRepository<MASTViolation, Long> {

    List<MASTViolation> findByAgentId(String agentId);

    List<MASTViolation> findByFailureMode(MASTFailureMode failureMode);

    List<MASTViolation> findByResolvedFalse();

    @Query("SELECT v FROM MASTViolation v WHERE v.detectedAt >= :since ORDER BY v.detectedAt DESC")
    List<MASTViolation> findRecentViolations(Instant since);

    @Query("SELECT v.failureMode, COUNT(v) FROM MASTViolation v GROUP BY v.failureMode ORDER BY COUNT(v) DESC")
    List<Object[]> findFailureModeFrequency();

    @Query("SELECT v.agentId, COUNT(v) FROM MASTViolation v WHERE v.resolved = false GROUP BY v.agentId")
    List<Object[]> findUnresolvedCountByAgent();
}

