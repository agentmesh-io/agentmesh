package com.therighthandapp.agentmesh.blackboard;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when an existing Blackboard entry is updated
 */
public class BlackboardEntryUpdatedEvent extends ApplicationEvent {
    private final BlackboardEntry entry;

    public BlackboardEntryUpdatedEvent(Object source, BlackboardEntry entry) {
        super(source);
        this.entry = entry;
    }

    public BlackboardEntry getEntry() {
        return entry;
    }
}

