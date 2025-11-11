package com.therighthandapp.agentmesh.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler for real-time AgentMesh updates
 * Broadcasts workflow progress, agent status, and MAST violations to connected clients
 */
@Component
public class AgentMeshWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AgentMeshWebSocketHandler.class);
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private static final Map<String, Set<WebSocketSession>> workflowSubscriptions = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {} (total: {})", session.getId(), sessions.size());
        
        // Send welcome message
        sendMessage(session, Map.of(
            "type", "connection.established",
            "timestamp", Instant.now().toString(),
            "message", "Connected to AgentMesh WebSocket"
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        workflowSubscriptions.values().forEach(subs -> subs.remove(session));
        log.info("WebSocket disconnected: {} - {} (remaining: {})", 
                session.getId(), status, sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from {}: {}", session.getId(), payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");
            
            if ("subscribe.workflow".equals(type)) {
                String workflowId = (String) msg.get("workflowId");
                subscribeToWorkflow(session, workflowId);
            } else if ("unsubscribe.workflow".equals(type)) {
                String workflowId = (String) msg.get("workflowId");
                unsubscribeFromWorkflow(session, workflowId);
            } else if ("ping".equals(type)) {
                sendMessage(session, Map.of(
                    "type", "pong",
                    "timestamp", Instant.now().toString()
                ));
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", payload, e);
        }
    }

    /**
     * Subscribe a session to workflow updates
     */
    private void subscribeToWorkflow(WebSocketSession session, String workflowId) {
        workflowSubscriptions.computeIfAbsent(workflowId, k -> new CopyOnWriteArraySet<>())
                             .add(session);
        log.info("Session {} subscribed to workflow {}", session.getId(), workflowId);
    }

    /**
     * Unsubscribe a session from workflow updates
     */
    private void unsubscribeFromWorkflow(WebSocketSession session, String workflowId) {
        Set<WebSocketSession> subs = workflowSubscriptions.get(workflowId);
        if (subs != null) {
            subs.remove(session);
            if (subs.isEmpty()) {
                workflowSubscriptions.remove(workflowId);
            }
        }
    }

    /**
     * Broadcast workflow update to all subscribed clients
     */
    public void broadcastWorkflowUpdate(String workflowId, String status, String phase, int progress, String message) {
        Map<String, Object> update = Map.of(
            "type", "workflow.update",
            "timestamp", Instant.now().toString(),
            "data", Map.of(
                "workflowId", workflowId,
                "status", status,
                "phase", phase,
                "progress", progress,
                "message", message != null ? message : ""
            )
        );
        
        // Send to workflow-specific subscribers
        Set<WebSocketSession> subscribers = workflowSubscriptions.get(workflowId);
        if (subscribers != null) {
            subscribers.forEach(session -> sendMessage(session, update));
        }
        
        // Also broadcast to all connected sessions
        broadcast(update);
    }

    /**
     * Broadcast agent status update
     */
    public void broadcastAgentStatus(String agentId, String agentName, String status) {
        Map<String, Object> update = Map.of(
            "type", "agent.status",
            "timestamp", Instant.now().toString(),
            "data", Map.of(
                "agentId", agentId,
                "agentName", agentName,
                "status", status
            )
        );
        broadcast(update);
    }

    /**
     * Broadcast MAST violation
     */
    public void broadcastMASTViolation(String severity, String category, String description) {
        Map<String, Object> update = Map.of(
            "type", "mast.violation",
            "timestamp", Instant.now().toString(),
            "data", Map.of(
                "severity", severity,
                "category", category,
                "description", description
            )
        );
        broadcast(update);
    }

    /**
     * Broadcast blackboard update
     */
    public void broadcastBlackboardUpdate(String entryId, String type, String agentId) {
        Map<String, Object> update = Map.of(
            "type", "blackboard.update",
            "timestamp", Instant.now().toString(),
            "data", Map.of(
                "entryId", entryId,
                "type", type,
                "agentId", agentId
            )
        );
        broadcast(update);
    }

    /**
     * Broadcast message to all connected sessions
     */
    private void broadcast(Map<String, Object> message) {
        sessions.forEach(session -> sendMessage(session, message));
    }

    /**
     * Send message to specific session
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        if (session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Failed to send message to session {}", session.getId(), e);
            }
        }
    }

    /**
     * Get number of connected sessions
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
