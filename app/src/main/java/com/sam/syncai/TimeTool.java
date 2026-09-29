package com.sam.syncai;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class TimeTool implements SyncTool {
    private final Context context;

    public TimeTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "time"; }

    @Override public String getDescription() {
        return "Give the phone's current local time, date, day, and timezone without using the LLM.";
    }

    @Override public String getInputSchema() {
        return "{\"query\":\"time|date|day|all\"}";
    }

    @Override public String execute(Map<String, String> arguments) {
        String query = arguments == null ? "all" : arguments.get("query");
        if (query == null || query.trim().isEmpty()) query = "all";

        Date now = new Date();
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();

        String time = new SimpleDateFormat("h:mm:ss a", locale).format(now);
        String date = new SimpleDateFormat("EEEE, MMMM d, yyyy", locale).format(now);
        String zoneName = zone.getDisplayName(false, TimeZone.SHORT, locale);

        switch (query.toLowerCase(Locale.US)) {
            case "time":
                return "It is " + time + " (" + zoneName + ").";
            case "date":
                return "Today is " + date + ".";
            case "day":
                return "Today is " + new SimpleDateFormat("EEEE", locale).format(now) + ".";
            default:
                return "It is " + time + " (" + zoneName + ").\nToday is " + date + ".";
        }
    }
}
