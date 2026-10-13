package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import java.util.Map;

public final class AlarmTool implements SyncTool {
    private final Context context;

    public AlarmTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "set_alarm"; }

    @Override public String getDescription() {
        return "Set a clock alarm for a specified hour and minute.";
    }

    @Override public String getInputSchema() {
        return "{\"hour\":7,\"minute\":30,\"message\":\"Wake up\"}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        int hour = parseInt(arguments, "hour");
        int minute = parseInt(arguments, "minute");
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0-23.");
        if (minute < 0 || minute > 59) throw new IllegalArgumentException("minute must be 0-59.");

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        String message = arguments.get("message");
        if (message != null && !message.trim().isEmpty()) {
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            throw new IllegalStateException("No alarm application can handle this request.");
        }
        context.startActivity(intent);
        return String.format(java.util.Locale.US, "Alarm request opened for %02d:%02d.", hour, minute);
    }

    private static int parseInt(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key + ".");
        return Integer.parseInt(value);
    }
}
