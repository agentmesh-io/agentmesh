package com.therighthandapp.agentmesh.blackboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * BlackboardService provides atomic, transactional access to the shared Blackboard.
 * All agent reads/writes go through this service to ensure consistency.
 */
@Service
public class BlackboardService {
    private static final Logger log = LoggerFactory.getLogger(BlackboardService.class);

    private final BlackboardRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public BlackboardService(BlackboardRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Post a new entry to the Blackboard (atomic operation with event notification)
     */
    @Transactional
    public BlackboardEntry post(String agentId, String entryType, String title, String content) {
        BlackboardEntry entry = new BlackboardEntry(agentId, entryType, title, content);
        BlackboardEntry saved = repository.save(entry);

        log.info("Blackboard: Agent {} posted entry [{}] type={}", agentId, title, entryType);

        // Publish event for subscribers
        eventPublisher.publishEvent(new BlackboardEntryPostedEvent(this, saved));

        return saved;
    }

    /**
     * Read entries by type (for agents to retrieve relevant context)
     */
    public List<BlackboardEntry> readByType(String entryType) {
        return repository.findByEntryTypeOrderedByTimestamp(entryType);
    }

    /**
     * Read all entries ordered by timestamp
     */
    public List<BlackboardEntry> readAll() {
        return repository.findAllOrderedByTimestamp();
    }

    /**
     * Read entries posted by a specific agent
     */
    public List<BlackboardEntry> readByAgent(String agentId) {
        return repository.findByAgentId(agentId);
    }

    /**
     * Get a specific entry by ID
     */
    public Optional<BlackboardEntry> getById(Long id) {
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

