package com.therighthandapp.agentmesh.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for AgentMesh
 * Enables real-time communication between backend and UI
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentMeshWebSocketHandler webSocketHandler;

    public WebSocketConfig(AgentMeshWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:3001", "*");
    }
}
