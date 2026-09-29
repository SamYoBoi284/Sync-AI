package com.sam.syncai;

import java.util.ArrayList;
import java.util.List;

public final class ChatRecord {
    public final String id;
    public String title;
    public final long createdAt;
    public long updatedAt;
    public final List<ChatMessage> messages = new ArrayList<>();

    public ChatRecord(String id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }
}
