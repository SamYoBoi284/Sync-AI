package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class RecorderActivity extends Activity {
    private static final int REQ_MIC = 7401;
    private TextView stateLabel;
    private TextView timerLabel;
    private Button recordButton;
    private LinearLayout recordingsList;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long recordingStartedAt;
    private boolean recording;
    private MediaPlayer player;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long seconds = Math.max(0, (System.currentTimeMillis() - recordingStartedAt) / 1000L);
            timerLabel.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
            handler.postDelayed(this, 500L);
        }
    };
    private final BroadcastReceiver recordingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (SyncRecorderService.ACTION_STATE.equals(action)) {
                boolean active = intent.getBooleanExtra(SyncRecorderService.EXTRA_RECORDING, false);
                String message = intent.getStringExtra(SyncRecorderService.EXTRA_MESSAGE);
                setRecording(active);
                if (message != null && !message.isEmpty()) stateLabel.setText(message);
                refreshRecordings();
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppTheme palette = AppTheme.from(this);
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        AppTheme.applySystemBars(this, palette);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        root.setBackgroundColor(palette.background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, palette.text, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView title = text("SYNC RECORDER", 19, palette.text, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        titleLp.leftMargin = dp(8);
        header.addView(title, titleLp);
        root.addView(header);

        TextView subtitle = text("Recording starts only when you press Record.", 13, palette.muted, false);
        subtitle.setPadding(dp(2), dp(6), dp(2), dp(16));
        root.addView(subtitle);

        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(24), dp(16), dp(24));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(palette.surface);
        surface.setCornerRadius(dp(24));
        surface.setStroke(dp(1), palette.border);
        panel.setBackground(surface);

        stateLabel = text("READY TO RECORD", 14, palette.text, true);
        stateLabel.setGravity(Gravity.CENTER);
        panel.addView(stateLabel, new LinearLayout.LayoutParams(-1, dp(32)));

        timerLabel = text("00:00", 42, palette.text, true);
        timerLabel.setGravity(Gravity.CENTER);
        timerLabel.setPadding(0, dp(8), 0, dp(16));
        panel.addView(timerLabel, new LinearLayout.LayoutParams(-1, dp(70)));

        recordButton = new Button(this);
        styleButton(recordButton, true);
        recordButton.setText("●  Start recording");
        recordButton.setOnClickListener(v -> onRecordPressed());
        panel.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(panel);

        TextView savedTitle = text("SAVED RECORDINGS", 12, palette.secondary, true);
        savedTitle.setPadding(dp(2), dp(22), dp(2), dp(8));
        root.addView(savedTitle);

        ScrollView scroll = new ScrollView(this);
        recordingsList = new LinearLayout(this);
        recordingsList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(recordingsList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        AppTheme.applyStatusBarInset(root);

        IntentFilter filter = new IntentFilter(SyncRecorderService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(recordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(recordingReceiver, filter);
        setRecording(SyncRecorderService.isRecording());
        refreshRecordings();
    }

    private void onRecordPressed() {
        if (recording || SyncRecorderService.isRecording()) {
            startService(new Intent(this, SyncRecorderService.class).setAction(SyncRecorderService.ACTION_STOP));
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        Intent start = new Intent(this, SyncRecorderService.class).setAction(SyncRecorderService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start);
        stateLabel.setText("STARTING…");
        recordButton.setEnabled(false);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            onRecordPressed();
        } else if (requestCode == REQ_MIC) {
            stateLabel.setText("Microphone permission is required to record.");
            Toast.makeText(this, "Microphone permission was not granted.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setRecording(boolean active) {
        recording = active;
        recordButton.setEnabled(true);
        recordButton.setText(active ? "■  Stop recording" : "●  Start recording");
        stateLabel.setText(active ? "RECORDING • MICROPHONE IN USE" : "READY TO RECORD");
        if (active) {
            if (recordingStartedAt == 0) recordingStartedAt = System.currentTimeMillis();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else {
            recordingStartedAt = 0;
            handler.removeCallbacks(ticker);
            timerLabel.setText("00:00");
        }
    }

    private File recordingsDirectory() {
        File base = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
        File dir = new File(base == null ? getFilesDir() : base, "Sync AI Recordings");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void refreshRecordings() {
        if (recordingsList == null) return;
        recordingsList.removeAllViews();
        File[] files = recordingsDirectory().listFiles((dir, name) -> name.endsWith(".m4a"));
        if (files == null || files.length == 0) {
            TextView empty = text("No recordings yet.", 13, AppTheme.from(this).muted, false);
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            recordingsList.addView(empty);
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(6), dp(8), dp(6));
            row.setBackground(round(AppTheme.from(this).surface, dp(14)));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(7);
            TextView name = text(file.getName().replace(".m4a", ""), 13, AppTheme.from(this).text, true);
            name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button play = new Button(this);
            styleButton(play, false);
            play.setText("Play");
            play.setOnClickListener(v -> playRecording(file));
            row.addView(play, new LinearLayout.LayoutParams(dp(76), dp(44)));
            recordingsList.addView(row, rowLp);
        }
    }

    private void playRecording(File file) {
        try {
            if (player != null) { player.release(); player = null; }
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> { mp.release(); if (player == mp) player = null; });
            player.prepare();
            player.start();
            Toast.makeText(this, "Playing " + file.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            if (player != null) { player.release(); player = null; }
            Toast.makeText(this, "Couldn't play this recording.", Toast.LENGTH_SHORT).show();
            SyncEventLogger.recordException(this, "RecorderActivity", "PLAYBACK_FAILED", error, file.getName());
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }
    private void styleButton(Button button, boolean primary) {
        AppTheme palette = AppTheme.from(this);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setTextColor(primary ? AppTheme.onAccent(palette.primary) : palette.text);
        GradientDrawable background = round(primary ? palette.primary : palette.surface2, dp(15));
        if (!primary) background.setStroke(dp(1), palette.border);
        button.setBackground(background);
        button.setPadding(dp(12), 0, dp(12), 0);
        Motion.pressable(button);
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        try { unregisterReceiver(recordingReceiver); } catch (Exception ignored) {}
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
