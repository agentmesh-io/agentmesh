package com.therighthandapp.agentmesh.blackboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of Blackboard state for rollback capability
 */
public class BlackboardSnapshot {
    private final Instant snapshotTime;
    private final List<BlackboardEntry> entries;

    public BlackboardSnapshot(List<BlackboardEntry> entries) {
        this.snapshotTime = Instant.now();
        this.entries = new ArrayList<>(entries);
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public List<BlackboardEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public int getEntryCount() {
        return entries.size();
    }
}

