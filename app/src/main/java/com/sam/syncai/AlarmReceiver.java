package com.sam.syncai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;


public final class AlarmReceiver extends BroadcastReceiver {
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_TRIGGER_AT = "trigger_at";
    private static final String CHANNEL_ID = "sync_alarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        String label = intent.getStringExtra(EXTRA_LABEL);
        if (label == null || label.trim().isEmpty()) label = "Wake up";

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Sync AI alarms",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alarms scheduled by Sync AI");
            channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    null);
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        Intent alarmIntent = new Intent(context, AlarmActivity.class);
        alarmIntent.putExtra(EXTRA_LABEL, label);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(
                context, (int) (System.currentTimeMillis() & 0x7fffffff),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(com.sam.syncai.R.drawable.sync_ai_icon)
                    .setContentTitle("Sync AI alarm")
                    .setContentText(label)
                    .setCategory(android.app.Notification.CATEGORY_ALARM)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setFullScreenIntent(fullScreen, true)
                    .build();
        } else {
            notification = new android.app.Notification.Builder(context)
                    .setSmallIcon(com.sam.syncai.R.drawable.sync_ai_icon)
                    .setContentTitle("Sync AI alarm")
                    .setContentText(label)
                    .setCategory(android.app.Notification.CATEGORY_ALARM)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(fullScreen)
                    .build();
        }

        manager.notify(0x534E43, notification);
    }
}
