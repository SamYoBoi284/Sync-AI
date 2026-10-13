package com.sam.syncai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ChatHistoryStore {
    private static final String FILE_NAME = "sync_chat_history.json";
    private static final int MAX_CHATS = 100;
    private static final int MAX_MESSAGES_PER_CHAT = 2000;

    public static final class ChatSession {
        public final String id;
        public String title;
        public final long createdAt;
        public long updatedAt;
        public final List<ChatMessage> messages = new ArrayList<>();

        ChatSession(String id, String title, long createdAt, long updatedAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String displayTitle() {
            return title == null || title.trim().isEmpty() ? "New chat" : title;
        }
    }

    private final File file;
    private final List<ChatSession> sessions = new ArrayList<>();
    private String activeChatId;

    public ChatHistoryStore(Context context) {
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        load();
        if (sessions.isEmpty()) createChat();
        if (find(activeChatId) == null) activeChatId = sessions.get(0).id;
    }

    public synchronized List<ChatSession> getSessions() {
        List<ChatSession> copy = new ArrayList<>(sessions);
        copy.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return Collections.unmodifiableList(copy);
    }

    public synchronized ChatSession getActiveSession() {
        ChatSession active = find(activeChatId);
        return active != null ? active : sessions.get(0);
    }

    public synchronized ChatSession createChat() {
        long now = System.currentTimeMillis();
        ChatSession chat = new ChatSession(
                UUID.randomUUID().toString(),
                "New chat",
                now,
                now);
        sessions.add(0, chat);
        activeChatId = chat.id;
        trim();
        save();
        return chat;
    }

    public synchronized boolean activateChat(String id) {
        if (find(id) == null) return false;
        activeChatId = id;
        save();
        return true;
    }

    public synchronized void saveActive(List<ChatMessage> messages) {
        ChatSession active = find(activeChatId);
        if (active == null) {
            active = createChat();
        }

        active.messages.clear();
        if (messages != null) {
            int start = Math.max(0, messages.size() - MAX_MESSAGES_PER_CHAT);
            for (int i = start; i < messages.size(); i++) {
                active.messages.add(messages.get(i));
            }
        }

        active.updatedAt = System.currentTimeMillis();
        updateTitle(active);
        sessions.remove(active);
        sessions.add(0, active);
        trim();
        save();
    }

    public synchronized void deleteChat(String id) {
        if (id == null) return;
        if (sessions.size() <= 1) return;
        ChatSession target = find(id);
        if (target == null) return;
        sessions.remove(target);
        if (id.equals(activeChatId)) activeChatId = sessions.get(0).id;
        save();
    }

    private ChatSession find(String id) {
        if (id == null) return null;
        for (ChatSession chat : sessions) {
            if (id.equals(chat.id)) return chat;
        }
        return null;
    }

    private void updateTitle(ChatSession chat) {
        if (!"New chat".equals(chat.title)) return;
        for (ChatMessage message : chat.messages) {
            if (message.role != ChatMessage.Role.USER) continue;
            String text = message.text == null ? "" : message.text.trim().replaceAll("\\s+", " ");
            if (text.isEmpty()) continue;
            chat.title = text.length() > 42 ? text.substring(0, 42).trim() + "…" : text;
            return;
        }
    }

    private void trim() {
        while (sessions.size() > MAX_CHATS) sessions.remove(sessions.size() - 1);
    }

    private synchronized void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("activeChatId", activeChatId);
            JSONArray chats = new JSONArray();

            for (ChatSession chat : sessions) {
                JSONObject item = new JSONObject();
                item.put("id", chat.id);
                item.put("title", chat.title);
                item.put("createdAt", chat.createdAt);
                item.put("updatedAt", chat.updatedAt);

                JSONArray messages = new JSONArray();
                for (ChatMessage message : chat.messages) {
                    JSONObject m = new JSONObject();
                    m.put("role", message.role.name());
                    m.put("text", message.text == null ? "" : message.text);
                    m.put("timestamp", message.timestamp);
                    m.put("diagnostics", message.diagnostics == null ? "" : message.diagnostics);
                    messages.put(m);
                }
                item.put("messages", messages);
                chats.put(item);
            }

            Files.write(file.toPath(),
                    root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private void load() {
        if (!file.exists()) return;
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(raw);
            activeChatId = root.optString("activeChatId", null);

            JSONArray chats = root.optJSONArray("chats");
            if (chats == null) return;

            for (int i = 0; i < chats.length(); i++) {
                JSONObject item = chats.optJSONObject(i);
                if (item == null) continue;

                String id = item.optString("id", "");
                if (id.isEmpty()) continue;

                ChatSession chat = new ChatSession(
                        id,
                        item.optString("title", "New chat"),
                        item.optLong("createdAt", System.currentTimeMillis()),
                        item.optLong("updatedAt", System.currentTimeMillis()));

                JSONArray messages = item.optJSONArray("messages");
                if (messages != null) {
                    for (int j = 0; j < messages.length(); j++) {
                        JSONObject m = messages.optJSONObject(j);
                        if (m == null) continue;
                        try {
                            ChatMessage.Role role = ChatMessage.Role.valueOf(
                                    m.optString("role", ChatMessage.Role.ASSISTANT.name()));
                            chat.messages.add(new ChatMessage(
                                    role,
                                    m.optString("text", ""),
                                    m.optString("diagnostics", ""),
                                    m.optLong("timestamp", System.currentTimeMillis())));
                        } catch (Exception ignored) {
                        }
                    }
                }
                sessions.add(chat);
            }

            sessions.sort(Comparator.comparingLong((ChatSession c) -> c.updatedAt).reversed());
        } catch (Exception ignored) {
            sessions.clear();
            activeChatId = null;
        }
    }
}
