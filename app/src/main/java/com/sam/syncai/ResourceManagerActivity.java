package com.sam.syncai;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight, on-demand process and component resource dashboard. */
public final class ResourceManagerActivity extends Activity {
    private AppTheme palette;
    private LinearLayout content;
    private TextView cpuValue;
    private TextView ramValue;
    private TextView systemRamValue;
    private TextView storageValue;
    private TextView llmValue;
    private TextView voskValue;
    private TextView wakeValue;
    private TextView voiceValue;
    private TextView notesValue;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastCpuTimeMs;
    private long lastWallTimeMs;
    private boolean resumed;
    private volatile long appDataBytes = -1L;
    private volatile long voskBytes = -1L;
    private final ExecutorService storageScanner = Executors.newSingleThreadExecutor();

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshMetrics();
            handler.postDelayed(this, 1500L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = AppTheme.from(this);
        AppTheme.applySystemBars(this, palette);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(dp(18), dp(10), dp(18), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, palette.text, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(palette.surface2, dp(14)));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        heading.addView(text("RESOURCE MANAGER", 16, palette.text, true));
        heading.addView(text("LIVE PROCESS MONITOR", 10, palette.secondary, true));
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = actionButton("↻");
        refresh.setContentDescription("Refresh resources");
        refresh.setOnClickListener(v -> refreshMetrics());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(12));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSection("LIVE OVERVIEW", "Sampled every 1.5 seconds while this screen is open.");
        cpuValue = metricCard("APP CPU", "Measuring…");
        ramValue = metricCard("APP MEMORY (PSS)", "Measuring…");
        systemRamValue = metricCard("SYSTEM RAM", "Measuring…");
        storageValue = metricCard("APP STORAGE", "Measuring…");

        addSection("COMPONENTS", "Component rows show runtime state and on-disk model size. Android does not expose exact per-library RAM or CPU accounting to ordinary apps.");
        llmValue = componentCard("LOCAL LLM • LLAMA.CPP", "Checking model…");
        voskValue = componentCard("VOSK • CONVERSATIONAL STT", "Checking model…");
        wakeValue = componentCard("VOSK • WAKE WORD", "Checking service…");
        voiceValue = componentCard("VOICE PIPELINE • ANDROID STT / TTS", "Checking voice mode…");

        addSection("HOW TO READ THIS", "App CPU is process-wide CPU time as a percentage of one CPU core; values can exceed 100% when multiple threads run. App memory is total process PSS, not a fabricated split between libraries.");
        notesValue = text("", 12, palette.muted, false);
        notesValue.setPadding(dp(12), dp(10), dp(12), dp(10));
        notesValue.setBackground(round(palette.surface, dp(14)));
        content.addView(notesValue, new LinearLayout.LayoutParams(-1, -2));

        root.addView(text("Monitoring stops when you leave this screen.", 10, palette.muted, false));
        setContentView(root);
        lastCpuTimeMs = Process.getElapsedCpuTime();
        lastWallTimeMs = android.os.SystemClock.elapsedRealtime();
        refreshMetrics();
        storageScanner.execute(() -> {
            long appBytes = directoryBytes(getFilesDir());
            long modelBytes = directoryBytes(VoskModelInstaller.modelDirectory(this));
            appDataBytes = appBytes;
            voskBytes = modelBytes;
            runOnUiThread(() -> {
                if (!isFinishing()) refreshMetrics();
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(refreshTask);
        handler.postDelayed(refreshTask, 1500L);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void refreshMetrics() {
        long nowCpu = Process.getElapsedCpuTime();
        long nowWall = android.os.SystemClock.elapsedRealtime();
        long cpuDelta = Math.max(0L, nowCpu - lastCpuTimeMs);
        long wallDelta = Math.max(1L, nowWall - lastWallTimeMs);
        double cpuPercent = 100.0 * cpuDelta / wallDelta;
        lastCpuTimeMs = nowCpu;
        lastWallTimeMs = nowWall;

        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);
        long pssKb = Math.max(0, memoryInfo.getTotalPss());
        long privateDirtyKb = Math.max(0, memoryInfo.getTotalPrivateDirty());
        ramValue.setText(formatBytes(pssKb * 1024L) + " PSS\n"
                + formatBytes(privateDirtyKb * 1024L) + " private dirty • Java heap "
                + formatBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                + " • native heap " + formatBytes(Debug.getNativeHeapAllocatedSize()));

        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo systemMemory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(systemMemory);
        systemRamValue.setText(formatBytes(systemMemory.availMem) + " available / "
                + formatBytes(systemMemory.totalMem) + " total"
                + (systemMemory.lowMemory ? "\nAndroid reports low-memory pressure." : ""));

        cpuValue.setText(String.format(Locale.US, "%.1f%% of one core", cpuPercent)
                + (SyncRuntime.get(this).backend().isGenerating() ? " • LLM generating" : ""));
        storageValue.setText(appDataBytes < 0L ? "Scanning app-private files…" :
                formatBytes(appDataBytes) + " app-private data • "
                        + formatBytes(getFilesDir().getUsableSpace()) + " volume space available");

        SyncRuntime runtime = SyncRuntime.get(this);
        ModelInfo model = runtime.modelManager().getLoadedModel();
        boolean loaded = runtime.backend().isLoaded();
        if (loaded && model != null) {
            llmValue.setText("RUNNING / READY: " + model.name
                    + "\nModel file on disk: " + formatBytes(model.file().length())
                    + "\n" + (runtime.backend().isGenerating()
                    ? "Inference is active; CPU above is process-wide."
                    : "Model is loaded; inference currently idle."));
        } else {
            llmValue.setText("IDLE • no model loaded"
                    + (model == null ? "" : "\nSelected model file: " + formatBytes(model.file().length())));
        }

        File voskDir = VoskModelInstaller.modelDirectory(this);
        long voskBytesSnapshot = voskBytes;
        boolean voskInstalled = VoskModelInstaller.isInstalled(this);
        boolean conversational = VoskVoiceRecognitionSession.isSessionActive();
        voskValue.setText((conversational ? "ACTIVE • conversational session" : "IDLE")
                + "\nModel installed: " + (voskInstalled ? "yes" : "no")
                + " • model files on disk: " + (voskBytesSnapshot < 0L ? "scanning…" : formatBytes(voskBytesSnapshot))
                + "\nVosk's exact in-memory allocation is included in app PSS above, not separately exposed by Android.");

        boolean wakeRunning = SyncAssistantService.isRunning();
        wakeValue.setText((wakeRunning ? "SERVICE RUNNING" : "STOPPED")
                + "\nWake-word listener is " + (wakeRunning ? "enabled / background-capable" : "not running")
                + "\nUses the installed Vosk model when active; its memory is included in app PSS.");

        boolean voiceActive = VoiceModeActivity.isVoiceModeActive();
        voiceValue.setText((voiceActive ? "VOICE MODE OPEN" : "VOICE MODE CLOSED")
                + "\nAndroid SpeechRecognizer / TextToSpeech resources are managed by Android."
                + (voiceActive ? "\nCPU above includes voice work and any concurrent app activity." : ""));

        notesValue.setText("Last sample: " + android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
                + "\nThis monitor samples the Sync AI process only. Android does not provide trustworthy per-library CPU/RAM splits here, so component-specific memory is intentionally not guessed.");
    }

    private void addSection(String title, String subtitle) {
        TextView h = text(title, 11, palette.secondary, true);
        h.setLetterSpacing(0.1f);
        h.setPadding(0, dp(16), 0, dp(4));
        content.addView(h);
        TextView sub = text(subtitle, 11, palette.muted, false);
        sub.setPadding(0, 0, 0, dp(8));
        content.addView(sub);
    }

    private TextView metricCard(String title, String initial) {
        LinearLayout card = makeCard();
        TextView heading = text(title, 10, palette.secondary, true);
        heading.setLetterSpacing(0.08f);
        card.addView(heading);
        TextView value = text(initial, 17, palette.text, true);
        value.setPadding(0, dp(5), 0, 0);
        card.addView(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        content.addView(card, lp);
        return value;
    }

    private TextView componentCard(String title, String initial) {
        LinearLayout card = makeCard();
        TextView heading = text(title, 11, palette.text, true);
        heading.setLetterSpacing(0.02f);
        card.addView(heading);
        TextView value = text(initial, 12, palette.muted, false);
        value.setPadding(0, dp(6), 0, 0);
        card.addView(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        content.addView(card, lp);
        return value;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(palette.surface, dp(16)));
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.DEFAULT, bold
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return view;
    }

    private Button actionButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(palette.text);
        button.setBackground(round(palette.surface2, dp(13)));
        Motion.pressable(button);
        return button;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        shape.setStroke(dp(1), palette.border);
        return shape;
    }

    private long directoryBytes(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directoryBytes(child);
        return total;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0L) bytes = 0L;
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        storageScanner.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
