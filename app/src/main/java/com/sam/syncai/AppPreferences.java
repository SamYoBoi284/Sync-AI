package com.sam.syncai;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {
    private static final String FILE = "sync-preferences";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_MEMORY = "memory";
    private static final String KEY_VOICE_OUTPUT = "voice_output";
    private static final String KEY_ACTIVE_CHAT = "active_chat";
    private static final String KEY_WAKE_WORD = "wake_word_enabled";
    private static final String KEY_THEME = "appearance_theme";
    private static final String KEY_MOTION = "motion_intensity";
    private static final String KEY_PERSONALITY = "assistant_personality";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_SPEECH_VOICE = "speech_voice";

    public static final int[] ACCENTS = {
            0xFF9A60FF, 0xFF4B8BFF, 0xFF50D7FF, 0xFF35C987,
            0xFFB8E34A, 0xFFFF9D45, 0xFFFF536D, 0xFFFF63C7
    };

    public static final String[] ACCENT_NAMES = {
            "Purple", "Blue", "Cyan", "Green", "Lime", "Orange", "Red", "Pink"
    };

    public static final String[] MOTION_INTENSITIES = {"Minimal", "Balanced", "Immersive"};
    public static final String[] PERSONALITY_IDS = {
            "balanced", "concise", "warm", "professional", "playful"
    };
    public static final String[] PERSONALITY_NAMES = {
            "Balanced companion", "Concise", "Warm & supportive", "Professional", "Playful"
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

    public boolean isWakeWordEnabled() {
        return prefs.getBoolean(KEY_WAKE_WORD, false);
    }

    public void setWakeWordEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply();
    }

    public String getTheme() {
        String value = prefs.getString(KEY_THEME, AppTheme.OBSIDIAN);
        return AppTheme.isKnownTheme(value) ? value : AppTheme.OBSIDIAN;
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME,
                AppTheme.isKnownTheme(theme) ? theme : AppTheme.OBSIDIAN).apply();
    }

    public String getMotionIntensity() {
        String value = prefs.getString(KEY_MOTION, "Balanced");
        for (String option : MOTION_INTENSITIES) if (option.equalsIgnoreCase(value)) return option;
        return "Balanced";
    }

    public void setMotionIntensity(String intensity) {
        String accepted = "Balanced";
        if (intensity != null) {
            for (String option : MOTION_INTENSITIES) {
                if (option.equalsIgnoreCase(intensity)) accepted = option;
            }
        }
        prefs.edit().putString(KEY_MOTION, accepted).apply();
    }

    public String getPersonality() {
        String value = prefs.getString(KEY_PERSONALITY, "balanced");
        for (String option : PERSONALITY_IDS) if (option.equals(value)) return value;
        return "balanced";
    }

    public void setPersonality(String personality) {
        String accepted = "balanced";
        if (personality != null) {
            for (String option : PERSONALITY_IDS) if (option.equals(personality)) accepted = option;
        }
        prefs.edit().putString(KEY_PERSONALITY, accepted).apply();
    }

    public String getPersonalityName() {
        String id = getPersonality();
        for (int i = 0; i < PERSONALITY_IDS.length; i++) {
            if (PERSONALITY_IDS[i].equals(id)) return PERSONALITY_NAMES[i];
        }
        return PERSONALITY_NAMES[0];
    }

    public String getPersonalityInstruction() {
        switch (getPersonality()) {
            case "concise":
                return "Keep replies brief and direct unless the user asks for detail.";
            case "warm":
                return "Be warm, patient, supportive, and attentive without becoming overly sentimental.";
            case "professional":
                return "Use a clear, composed, professional tone while remaining approachable.";
            case "playful":
                return "Use light humor and relaxed conversation when it fits; keep serious tasks accurate.";
            default:
                return "Be natural, warm, and conversational; match the user's tone without forcing slang.";
        }
    }

    public float getSpeechRate() {
        return Math.max(0.75f, Math.min(1.25f, prefs.getFloat(KEY_SPEECH_RATE, 1.03f)));
    }

    public void setSpeechRate(float rate) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, Math.max(0.75f, Math.min(1.25f, rate))).apply();
    }

    /** Empty means let Android's selected TTS engine choose its default voice. */
    public String getSpeechVoiceName() {
        return prefs.getString(KEY_SPEECH_VOICE, "");
    }

    public void setSpeechVoiceName(String voiceName) {
        prefs.edit().putString(KEY_SPEECH_VOICE, voiceName == null ? "" : voiceName.trim()).apply();
    }
}
