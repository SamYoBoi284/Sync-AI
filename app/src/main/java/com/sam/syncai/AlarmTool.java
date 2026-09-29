package com.sam.syncai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Calendar;

public final class AlarmTool implements SyncTool {
    private final Context context;

    public AlarmTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "set_alarm"; }

    @Override public String getDescription() {
        return "Set a native Sync AI alarm for a specified hour and minute.";
    }

    @Override public String getInputSchema() {
        return "{\"hour\":7,\"minute\":30,\"message\":\"Wake up\"}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        int hour = parseInt(arguments, "hour");
        int minute = parseInt(arguments, "minute");
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be 0-23.");
        if (minute < 0 || minute > 59) throw new IllegalArgumentException("minute must be 0-59.");

        String message = arguments.get("message");
        String label = message == null || message.trim().isEmpty() ? "Wake up" : message.trim();

        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, hour);
        when.set(Calendar.MINUTE, minute);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (!when.after(Calendar.getInstance())) {
            when.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent receiverIntent = new Intent(context, AlarmReceiver.class);
        receiverIntent.putExtra(AlarmReceiver.EXTRA_LABEL, label);
        receiverIntent.putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, when.getTimeInMillis());

        int requestCode = (int) (when.getTimeInMillis() ^ (when.getTimeInMillis() >>> 32));
        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                requestCode,
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) throw new IllegalStateException("Alarm service is unavailable.");

        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            AlarmManager.AlarmClockInfo info =
                    new AlarmManager.AlarmClockInfo(when.getTimeInMillis(), operation);
            alarmManager.setAlarmClock(info, operation);
        } else if (Build.VERSION.SDK_INT >= 23) {
            // Keep the feature functional even when Android's exact-alarm access is
            // unavailable. The OS may deliver this slightly later.
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), operation);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), operation);
        }

        new ScheduledActionStore(context).record("alarm", label, when.getTimeInMillis());

        String time = new SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
                .format(new Date(when.getTimeInMillis()));
        return "Alarm set for " + time + " — " + label + ".";
    }

    private static int parseInt(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key + ".");
        return Integer.parseInt(value);
    }
}
