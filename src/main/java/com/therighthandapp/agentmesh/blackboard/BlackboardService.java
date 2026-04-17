package com.therighthandapp.agentmesh.blackboard;

import com.therighthandapp.agentmesh.mast.MASTDetector;
import com.therighthandapp.agentmesh.security.AccessControlService;
import com.therighthandapp.agentmesh.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * BlackboardService provides atomic, transactional access to the shared Blackboard.
 * All agent reads/writes go through this service to ensure consistency.
 *
 * Multi-tenant aware: All operations are scoped to tenant and project from TenantContext.
 */
@Service
public class BlackboardService {
    private static final Logger log = LoggerFactory.getLogger(BlackboardService.class);

    private final BlackboardRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final MASTDetector mastDetector;

    @Value("${agentmesh.multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    @Autowired(required = false)
    private AccessControlService accessControl;

    public BlackboardService(BlackboardRepository repository, 
                            ApplicationEventPublisher eventPublisher,
                            MASTDetector mastDetector) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.mastDetector = mastDetector;
    }

    /**
     * Post a new entry to the Blackboard (atomic operation with event notification)
     * Multi-tenant: Automatically scoped to current tenant/project from TenantContext
     */
    @Transactional
    @CacheEvict(value = {"blackboardEntries", "recentEntries"}, allEntries = true)
    public BlackboardEntry post(String agentId, String entryType, String title, String content) {
        log.info("BlackboardService.post() called with content length: {} chars", content != null ? content.length() : 0);
        log.debug("BlackboardService.post() content preview (first 200 chars): {}", 
            content != null && content.length() > 0 ? content.substring(0, Math.min(200, content.length())) : "null or empty");
        
        BlackboardEntry entry = new BlackboardEntry(agentId, entryType, title, content);
        log.info("BlackboardEntry created - content length before save: {} chars", entry.getContent() != null ? entry.getContent().length() : 0);

        // Apply multi-tenant context if enabled
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                entry.setTenantId(context.getTenantId());
                entry.setProjectId(context.getProjectId());
                entry.setDataPartitionKey(context.getDataPartitionKey());

                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(context.getTenantId(), context.getProjectId());
                }
            }
        }

        BlackboardEntry saved = repository.save(entry);
        log.info("BlackboardEntry saved - content length after save: {} chars", saved.getContent() != null ? saved.getContent().length() : 0);

        log.info("Blackboard: Agent {} posted entry [{}] type={} (tenant={}, project={})",
            agentId, title, entryType, saved.getTenantId(), saved.getProjectId());

        // Analyze for MAST violations
        mastDetector.analyzeBlackboardEntry(saved);

        // Publish event for subscribers
        eventPublisher.publishEvent(new BlackboardEntryPostedEvent(this, saved));

        return saved;
    }

    /**
     * Read entries by type (for agents to retrieve relevant context)
     * Multi-tenant: Only returns entries within current tenant/project
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "blackboardEntries", key = "#entryType + '-' + T(com.therighthandapp.agentmesh.security.TenantContext).getOrNull()?.getTenantId() + '-' + T(com.therighthandapp.agentmesh.security.TenantContext).getOrNull()?.getProjectId()")
    public List<BlackboardEntry> readByType(String entryType) {
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(context.getTenantId(), context.getProjectId());
                }
                return repository.findByTenantIdAndProjectIdAndEntryType(
                    context.getTenantId(), context.getProjectId(), entryType);
            }
        }
        return repository.findByEntryTypeOrderedByTimestamp(entryType);
    }

    /**
     * Read all entries ordered by timestamp
     * Multi-tenant: Only returns entries within current tenant/project
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "recentEntries", key = "T(com.therighthandapp.agentmesh.security.TenantContext).getOrNull()?.getTenantId() + '-' + T(com.therighthandapp.agentmesh.security.TenantContext).getOrNull()?.getProjectId()")
    public List<BlackboardEntry> readAll() {
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(context.getTenantId(), context.getProjectId());
                }
                return repository.findRecentByTenantAndProject(
                    context.getTenantId(), context.getProjectId());
            }
        }
        return repository.findAllOrderedByTimestamp();
    }

    /**
     * Read entries posted by a specific agent
     */
    @Transactional(readOnly = true)
    public List<BlackboardEntry> readByAgent(String agentId) {
        return repository.findByAgentId(agentId);
    }

    /**
     * Get a specific entry by ID
     * Multi-tenant: Enforces tenant/project boundary check
     */
    public Optional<BlackboardEntry> getById(Long id) {
        if (multitenancyEnabled) {
            TenantContext context = TenantContext.getOrNull();
            if (context != null) {
                // Enforce access control
                if (accessControl != null) {
                    accessControl.checkAccess(context.getTenantId(), context.getProjectId());
                }
                return repository.findByIdAndTenantIdAndProjectId(
                    id, context.getTenantId(), context.getProjectId());
            }
        }
        return repository.findById(id);
    }

    /**
     * Update an existing entry (with optimistic locking to prevent conflicts)
     */
    @Transactional
    public BlackboardEntry update(Long id, String content) {
        BlackboardEntry entry = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blackboard entry not found: " + id));

        entry.setContent(content);
        BlackboardEntry updated = repository.save(entry);

        log.info("Blackboard: Entry {} updated by version {}", id, updated.getVersion());

        eventPublisher.publishEvent(new BlackboardEntryUpdatedEvent(this, updated));

        return updated;
    }

    /**
     * Create a snapshot of the current Blackboard state (for rollback capability)
     */
    public BlackboardSnapshot createSnapshot() {
        List<BlackboardEntry> allEntries = repository.findAll();
        log.info("Blackboard: Created snapshot with {} entries", allEntries.size());
        return new BlackboardSnapshot(allEntries);
    }
}

