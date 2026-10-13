package com.sam.syncai;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AlarmActivity extends Activity {
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String label = getIntent().getStringExtra(AlarmReceiver.EXTRA_LABEL);
        if (label == null || label.trim().isEmpty()) label = "Wake up";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(7, 8, 14));

        TextView title = new TextView(this);
        title.setText("SYNC AI ALARM");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(this);
        message.setText(label);
        message.setTextColor(Color.WHITE);
        message.setTextSize(22);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 32, 0, 48);

        Button dismiss = new Button(this);
        dismiss.setText("DISMISS");
        dismiss.setOnClickListener(v -> dismissAlarm());

        root.addView(title);
        root.addView(message);
        root.addView(dismiss, new LinearLayout.LayoutParams(-1, 64));
        setContentView(root);
        startAlarmSound();
    }

    private void startAlarmSound() {
        try {
            player = MediaPlayer.create(this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (player != null) {
                player.setLooping(true);
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                player.start();
            }
        } catch (Throwable ignored) {}

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 700, 500};
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void dismissAlarm() {
        stopAlarm();
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(0x534E43);
        finish();
    }

    private void stopAlarm() {
        if (player != null) {
            try { player.stop(); } catch (Throwable ignored) {}
            try { player.release(); } catch (Throwable ignored) {}
            player = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Throwable ignored) {}
        }
    }

    @Override protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }
}
