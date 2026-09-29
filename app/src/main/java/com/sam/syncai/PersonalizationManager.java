package com.sam.syncai;

import android.content.Context;
import android.graphics.Color;

public final class PersonalizationManager {
    public static final int DEFAULT_INDEX = 0;

    public static final String[] NAMES = {
            "Purple", "Cyan", "Blue", "Green",
            "Orange", "Red", "Pink", "White"
    };

    private static final int[] COLORS = {
            Color.rgb(154, 96, 255),
            Color.rgb(80, 215, 255),
            Color.rgb(82, 142, 255),
            Color.rgb(85, 220, 145),
            Color.rgb(255, 170, 70),
            Color.rgb(255, 92, 110),
            Color.rgb(255, 105, 190),
            Color.rgb(235, 238, 248)
    };

    private static final String PREFS = "sync_personalization";
    private static final String KEY_ACCENT = "accent_index";
    private static final String KEY_TONE = "tone_index";

    public static final String[] TONE_NAMES = {
            "Casual", "Balanced", "Technical"
    };

    private static final String[] TONE_PROMPTS = {
            "Use a relaxed, natural, bro-like conversational tone while staying clear and useful.",
            "Use a natural balanced tone: warm and conversational, but clear and direct.",
            "Use a precise, technical tone when appropriate while staying conversational and easy to understand."
    };

    private PersonalizationManager() {}

    public static int getAccentIndex(Context context) {
        int value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_ACCENT, DEFAULT_INDEX);
        return Math.max(0, Math.min(COLORS.length - 1, value));
    }

    public static int getAccent(Context context) {
        return COLORS[getAccentIndex(context)];
    }

    public static int getAccentAt(int index) {
        return COLORS[Math.max(0, Math.min(COLORS.length - 1, index))];
    }

    public static String getName(Context context) {
        return NAMES[getAccentIndex(context)];
    }

    public static void setAccent(Context context, int index) {
        int safe = Math.max(0, Math.min(COLORS.length - 1, index));
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ACCENT, safe)
                .apply();
    }

    public static int getToneIndex(Context context) {
        int value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TONE, 0);
        return Math.max(0, Math.min(TONE_PROMPTS.length - 1, value));
    }

    public static String getToneName(Context context) {
        return TONE_NAMES[getToneIndex(context)];
    }

    public static String getTonePrompt(Context context) {
        return TONE_PROMPTS[getToneIndex(context)];
    }

    public static void setTone(Context context, int index) {
        int safe = Math.max(0, Math.min(TONE_PROMPTS.length - 1, index));
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_TONE, safe)
                .apply();
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
