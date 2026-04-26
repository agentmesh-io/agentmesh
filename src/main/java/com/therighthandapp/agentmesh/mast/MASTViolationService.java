package com.therighthandapp.agentmesh.mast;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.therighthandapp.agentmesh.websocket.LiveStreamBridge;

import java.time.Instant;
import java.util.List;

/**
 * Service layer for MAST violations with Redis caching
 * to reduce database load for frequently accessed queries.
 */
@Service
public class MASTViolationService {

    private final MASTViolationRepository repository;

    @Autowired(required = false)
    private LiveStreamBridge liveStreamBridge;

    public MASTViolationService(MASTViolationRepository repository) {
        this.repository = repository;
    }

    /**
     * Save a violation (evicts caches to ensure fresh data)
     */
    @Transactional
    @CacheEvict(value = {"violations"}, allEntries = true)
    public MASTViolation save(MASTViolation violation) {
        MASTViolation saved = repository.save(violation);
        if (liveStreamBridge != null) {
            liveStreamBridge.broadcastMastViolation(saved);
        }
        return saved;
    }

    /**
     * Get violations by agent (cached for 2 hours)
     */
    @Cacheable(value = "violations", key = "'agent-' + #agentId")
    public List<MASTViolation> findByAgentId(String agentId) {
        return repository.findByAgentId(agentId);
    }

    /**
     * Get violations by failure mode (cached for 2 hours)
     */
    @Cacheable(value = "violations", key = "'mode-' + #failureMode")
    public List<MASTViolation> findByFailureMode(MASTFailureMode failureMode) {
        return repository.findByFailureMode(failureMode);
    }

    /**
     * Get unresolved violations (cached for 2 hours)
     */
    @Cacheable(value = "violations", key = "'unresolved'")
    public List<MASTViolation> findUnresolved() {
        return repository.findByResolvedFalse();
    }

    /**
     * Get recent violations (cached for 2 hours)
     */
    @Cacheable(value = "violations", key = "'recent-' + #since.toString()")
    public List<MASTViolation> findRecentViolations(Instant since) {
        return repository.findRecentViolations(since);
    }

    /**
     * Get failure mode frequency statistics (cached for 2 hours)
     */
    @Cacheable(value = "violations", key = "'frequency'")
    public List<Object[]> findFailureModeFrequency() {
        return repository.findFailureModeFrequency();
    }

    /**
     * Get unresolved count by agent (cached for 2 hours)
     */
    @Cacheable(value = "violations", key = "'unresolved-by-agent'")
    public List<Object[]> findUnresolvedCountByAgent() {
        return repository.findUnresolvedCountByAgent();
    }

    /**
     * Mark violation as resolved (evicts cache)
     */
    @Transactional
    @CacheEvict(value = {"violations"}, allEntries = true)
    public void markResolved(Long violationId, String resolution) {
        repository.findById(violationId).ifPresent(violation -> {
            violation.setResolved(true);
            violation.setResolution(resolution);
            repository.save(violation);
        });
    }
}
