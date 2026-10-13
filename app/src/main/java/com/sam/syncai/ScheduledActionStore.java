package com.sam.syncai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ScheduledActionStore {
    private static final String PREFS = "sync_scheduled_actions";
    private static final String KEY = "items";
    private static final int MAX_ITEMS = 100;

    private final android.content.SharedPreferences prefs;

    public ScheduledActionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void record(String type, String label, long triggerAtMs) {
        JSONArray existing = readArray();
        JSONArray next = new JSONArray();
        try {
            JSONObject item = new JSONObject();
            item.put("type", type);
            item.put("label", label == null ? "" : label);
            item.put("triggerAt", triggerAtMs);
            item.put("recordedAt", System.currentTimeMillis());
            next.put(item);

            for (int i = 0; i < existing.length() && next.length() < MAX_ITEMS; i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old != null) next.put(old);
            }
            prefs.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public synchronized String describe(String type) {
        JSONArray all = readArray();
        JSONArray active = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item == null) continue;
            if (item.optLong("triggerAt", 0L) <= now) continue;
            if (type == null || type.equals(item.optString("type", ""))) {
                active.put(item);
            }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < active.length(); i++) {
            JSONObject item = active.optJSONObject(i);
            if (item == null) continue;
            if (out.length() > 0) out.append("\n");
            out.append(item.optString("type", "scheduled"))
                    .append(": ")
                    .append(item.optString("label", ""))
                    .append(" @ ")
                    .append(new java.text.SimpleDateFormat(
                            "EEE, MMM d h:mm a",
                            java.util.Locale.getDefault())
                            .format(new java.util.Date(item.optLong("triggerAt", 0L))));
        }

        prefs.edit().putString(KEY, active.toString()).apply();
        return out.length() == 0 ? "No active " + (type == null ? "" : type + " ") + "scheduled by Sync AI." : out.toString();
    }

    private JSONArray readArray() {
        try {
            return new JSONArray(prefs.getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
