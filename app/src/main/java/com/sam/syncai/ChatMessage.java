package com.sam.syncai;

public final class ChatMessage {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public final Role role;
    public final String text;
    public final String diagnostics;
    public final long timestamp;

    public ChatMessage(Role role, String text) {
        this(role, text, "", System.currentTimeMillis());
    }

    public ChatMessage(Role role, String text, String diagnostics) {
        this(role, text, diagnostics, System.currentTimeMillis());
    }

    public ChatMessage(Role role, String text, String diagnostics, long timestamp) {
        this.role = role;
        this.text = text;
        this.diagnostics = diagnostics == null ? "" : diagnostics;
        this.timestamp = timestamp;
    }
}
