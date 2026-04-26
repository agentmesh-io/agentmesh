package com.therighthandapp.agentmesh.websocket;

import com.therighthandapp.agentmesh.blackboard.BlackboardEntryPostedEvent;
import com.therighthandapp.agentmesh.mast.MASTViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * M13.1 — Live UI Bridge.
 * Translates internal Spring events into WebSocket broadcasts so the UI
 * can render the workflow timeline, agent activity, and MAST toasts in real time.
 *
 * Listeners are @Async to avoid back-pressure on the publishing transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStreamBridge {

    private final AgentMeshWebSocketHandler webSocketHandler;

    /** Blackboard → UI timeline */
    @Async
    @EventListener
    public void onBlackboardEntryPosted(BlackboardEntryPostedEvent event) {
        var entry = event.getEntry();
        if (entry == null) return;
        try {
            webSocketHandler.broadcastBlackboardUpdate(
                    entry.getId() != null ? entry.getId().toString() : "n/a",
                    entry.getEntryType(),
                    entry.getAgentId()
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast blackboard update: {}", e.getMessage());
        }
    }

    /** Programmatic helper used by MASTViolationService.save() */
    public void broadcastMastViolation(MASTViolation violation) {
        if (violation == null) return;
        try {
            webSocketHandler.broadcastMASTViolation(
                    violation.getSeverity() != null ? violation.getSeverity() : "INFO",
                    violation.getFailureMode() != null ? violation.getFailureMode().name() : "UNKNOWN",
                    violation.getEvidence() != null ? violation.getEvidence() : ""
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast MAST violation: {}", e.getMessage());
        }
    }
}


