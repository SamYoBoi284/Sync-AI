package com.sam.syncai;

public final class ChatMessage {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public final Role role;
    public final String text;
    public final long timestamp;
    public final String diagnostics;

    public ChatMessage(Role role, String text) {
        this(role, text, System.currentTimeMillis(), null);
    }

    public ChatMessage(Role role, String text, long timestamp, String diagnostics) {
        this.role = role;
        this.text = text == null ? "" : text;
        this.timestamp = timestamp <= 0 ? System.currentTimeMillis() : timestamp;
        this.diagnostics = diagnostics;
    }

    public boolean hasDiagnostics() {
        return diagnostics != null && !diagnostics.trim().isEmpty();
    }
}
