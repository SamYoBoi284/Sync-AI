package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import java.util.Map;

public final class TimerTool implements SyncTool {
    private final Context context;

    public TimerTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "set_timer"; }

    @Override public String getDescription() {
        return "Start a countdown timer in seconds.";
    }

    @Override public String getInputSchema() {
        return "{\"seconds\":300,\"message\":\"Timer\"}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        String raw = arguments.get("seconds");
        if (raw == null) throw new IllegalArgumentException("Missing seconds.");
        int seconds = Integer.parseInt(raw);
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException("seconds must be between 1 and 86400.");
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
        intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        String message = arguments.get("message");
        if (message != null && !message.trim().isEmpty()) {
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
        }
        new ScheduledActionStore(context).record(
                "timer",
                message == null || message.trim().isEmpty() ? "Timer" : message.trim(),
                System.currentTimeMillis() + (seconds * 1000L));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            throw new IllegalStateException("No timer application can handle this request.");
        }
        context.startActivity(intent);
        return "Timer started for " + seconds + " seconds.";
    }
}
