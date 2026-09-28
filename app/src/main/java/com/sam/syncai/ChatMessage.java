package com.sam.syncai;

public final class ChatMessage {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public final Role role;
    public final String text;

    public ChatMessage(Role role, String text) {
        this.role = role;
        this.text = text;
    }
}
