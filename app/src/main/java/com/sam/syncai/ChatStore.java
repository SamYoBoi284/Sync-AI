package com.sam.syncai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatStore {
    private final File file;
    private final List<ChatRecord> chats = new ArrayList<>();

    public ChatStore(Context context) {
        file = new File(context.getApplicationContext().getFilesDir(), "chats.json");
        load();
    }

    public synchronized List<ChatRecord> all() {
        return new ArrayList<>(chats);
    }

    public synchronized ChatRecord get(String id) {
        if (id == null) return null;
        for (ChatRecord chat : chats) {
            if (id.equals(chat.id)) return chat;
        }
        return null;
    }

    public synchronized ChatRecord getOrCreate(String id) {
        ChatRecord found = get(id);
        if (found != null) return found;
        return create();
    }

    public synchronized ChatRecord create() {
        long now = System.currentTimeMillis();
        ChatRecord chat = new ChatRecord(UUID.randomUUID().toString(), "New chat", now);
        chats.add(0, chat);
        save();
        return chat;
    }

    public synchronized void touch(ChatRecord chat) {
        chat.updatedAt = System.currentTimeMillis();
        save();
    }

    public synchronized void maybeTitle(ChatRecord chat, String userText) {
        if (chat == null || userText == null || userText.trim().isEmpty()) return;
        if (!"New chat".equals(chat.title)) return;
        String clean = userText.trim().replaceAll("\\s+", " ");
        chat.title = clean.length() <= 34 ? clean : clean.substring(0, 31) + "…";
        chat.updatedAt = System.currentTimeMillis();
        save();
    }

    public synchronized void add(ChatRecord chat, ChatMessage message) {
        chat.messages.add(message);
        chat.updatedAt = System.currentTimeMillis();
        save();
    }

    public synchronized void remove(String id) {
        for (int i = 0; i < chats.size(); i++) {
            if (id.equals(chats.get(i).id)) {
                chats.remove(i);
                save();
                return;
            }
        }
    }

    public synchronized void rename(String id, String title) {
        ChatRecord chat = get(id);
        if (chat == null) return;
        chat.title = title == null || title.trim().isEmpty() ? "New chat" : title.trim();
        chat.updatedAt = System.currentTimeMillis();
        save();
    }

    public synchronized void save() {
        try {
            JSONArray array = new JSONArray();
            for (ChatRecord chat : chats) {
                JSONObject c = new JSONObject();
                c.put("id", chat.id);
                c.put("title", chat.title);
                c.put("createdAt", chat.createdAt);
                c.put("updatedAt", chat.updatedAt);
                JSONArray messages = new JSONArray();
                for (ChatMessage message : chat.messages) {
                    JSONObject m = new JSONObject();
                    m.put("role", message.role.name());
                    m.put("text", message.text);
                    m.put("timestamp", message.timestamp);
                    m.put("diagnostics", message.diagnostics == null ? JSONObject.NULL : message.diagnostics);
                    messages.put(m);
                }
                c.put("messages", messages);
                array.put(c);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not save chat history.", e);
        }
    }

    private synchronized void load() {
        chats.clear();
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = in.read(data);
            if (read <= 0) return;
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject c = array.getJSONObject(i);
                ChatRecord chat = new ChatRecord(
                        c.getString("id"),
                        c.optString("title", "New chat"),
                        c.optLong("createdAt", System.currentTimeMillis())
                );
                chat.updatedAt = c.optLong("updatedAt", chat.createdAt);
                JSONArray messages = c.optJSONArray("messages");
                if (messages != null) {
                    for (int j = 0; j < messages.length(); j++) {
                        JSONObject m = messages.getJSONObject(j);
                        ChatMessage.Role role = ChatMessage.Role.valueOf(
                                m.optString("role", "ASSISTANT"));
                        String diagnostics = m.isNull("diagnostics")
                                ? null : m.optString("diagnostics", null);
                        chat.messages.add(new ChatMessage(
                                role,
                                m.optString("text", ""),
                                m.optLong("timestamp", chat.updatedAt),
                                diagnostics
                        ));
                    }
                }
                chats.add(chat);
            }
        } catch (Exception ignored) {
            chats.clear();
        }
    }
}
