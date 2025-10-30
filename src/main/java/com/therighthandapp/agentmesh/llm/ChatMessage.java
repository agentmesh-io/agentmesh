package com.therighthandapp.agentmesh.llm;

/**
 * Represents a chat message in a conversation
 */
public class ChatMessage {
    private final String role; // "system", "user", "assistant"
    private final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}

