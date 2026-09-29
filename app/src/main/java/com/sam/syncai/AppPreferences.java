package com.sam.syncai;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {
    private static final String FILE = "sync-preferences";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_MEMORY = "memory";
    private static final String KEY_VOICE_OUTPUT = "voice_output";
    private static final String KEY_ACTIVE_CHAT = "active_chat";

    public static final int[] ACCENTS = {
            0xFF9A60FF, 0xFF4B8BFF, 0xFF50D7FF, 0xFF35C987,
            0xFFB8E34A, 0xFFFF9D45, 0xFFFF536D, 0xFFFF63C7
    };

    public static final String[] ACCENT_NAMES = {
            "Purple", "Blue", "Cyan", "Green", "Lime", "Orange", "Red", "Pink"
    };

    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int getAccent() {
        int index = prefs.getInt(KEY_ACCENT, 0);
        return Math.max(0, Math.min(ACCENTS.length - 1, index));
    }

    public void setAccent(int index) {
        prefs.edit().putInt(KEY_ACCENT, Math.max(0, Math.min(ACCENTS.length - 1, index))).apply();
    }

    public String getMemory() {
        return prefs.getString(KEY_MEMORY, "");
    }

    public void setMemory(String memory) {
        prefs.edit().putString(KEY_MEMORY, memory == null ? "" : memory).apply();
    }

    public boolean isVoiceOutputEnabled() {
        return prefs.getBoolean(KEY_VOICE_OUTPUT, true);
    }

    public void setVoiceOutputEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_OUTPUT, enabled).apply();
    }

    public String getActiveChatId() {
        return prefs.getString(KEY_ACTIVE_CHAT, null);
    }

    public void setActiveChatId(String id) {
        prefs.edit().putString(KEY_ACTIVE_CHAT, id).apply();
    }
}
