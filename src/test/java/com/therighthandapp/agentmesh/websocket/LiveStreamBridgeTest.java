package com.therighthandapp.agentmesh.websocket;

import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntryPostedEvent;
import com.therighthandapp.agentmesh.mast.MASTFailureMode;
import com.therighthandapp.agentmesh.mast.MASTViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LiveStreamBridge} — Sprint 13.1.
 * Validates that internal Spring events translate cleanly into WebSocket frames.
 */
@ExtendWith(MockitoExtension.class)
class LiveStreamBridgeTest {

    @Mock
    AgentMeshWebSocketHandler webSocketHandler;

    @InjectMocks
    LiveStreamBridge bridge;

    @Test
    void onBlackboardEntryPosted_broadcastsId_type_and_agent() {
        BlackboardEntry entry = new BlackboardEntry("planner", "PLAN", "draft", "{}");
        entry.setId(42L);

        bridge.onBlackboardEntryPosted(new BlackboardEntryPostedEvent(this, entry));

        verify(webSocketHandler).broadcastBlackboardUpdate("42", "PLAN", "planner");
        verifyNoMoreInteractions(webSocketHandler);
    }

    @Test
    void onBlackboardEntryPosted_swallowsExceptions() {
        BlackboardEntry entry = new BlackboardEntry("dev", "CODE", "t", "c");
        entry.setId(7L);
        doThrow(new RuntimeException("boom"))
                .when(webSocketHandler)
                .broadcastBlackboardUpdate(anyString(), anyString(), anyString());

        // Must not propagate — listener stability is mandatory
        bridge.onBlackboardEntryPosted(new BlackboardEntryPostedEvent(this, entry));

        verify(webSocketHandler).broadcastBlackboardUpdate("7", "CODE", "dev");
    }

    @Test
    void onBlackboardEntryPosted_nullEntry_noop() {
        bridge.onBlackboardEntryPosted(new BlackboardEntryPostedEvent(this, null));
        verifyNoInteractions(webSocketHandler);
    }

    @Test
    void broadcastMastViolation_mapsFailureModeAndSeverity() {
        MASTViolation v = new MASTViolation(
                "tester",
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task-123",
                "Output diverged from SRS section 3.2"
        );
        v.setSeverity("HIGH");

        bridge.broadcastMastViolation(v);

        verify(webSocketHandler).broadcastMASTViolation(
                "HIGH",
                "FM_1_1_SPECIFICATION_VIOLATION",
                "Output diverged from SRS section 3.2"
        );
    }

    @Test
    void broadcastMastViolation_nullSafe() {
        bridge.broadcastMastViolation(null);
        verifyNoInteractions(webSocketHandler);
    }
}


