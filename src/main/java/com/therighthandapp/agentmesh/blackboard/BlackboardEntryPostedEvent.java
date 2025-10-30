package com.therighthandapp.agentmesh.blackboard;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when a new entry is posted to the Blackboard
 */
public class BlackboardEntryPostedEvent extends ApplicationEvent {
    private final BlackboardEntry entry;

    public BlackboardEntryPostedEvent(Object source, BlackboardEntry entry) {
        super(source);
        this.entry = entry;
    }

    public BlackboardEntry getEntry() {
        return entry;
    }
}

