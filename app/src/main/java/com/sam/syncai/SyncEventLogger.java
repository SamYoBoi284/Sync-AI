package com.sam.syncai;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent Sync AI flight recorder.
 *
 * Records important application, activity, service, assistant-session, tool,
 * inference, export and crash events to app-private storage so the timeline
 * survives ordinary UI failures and can be exported after a restart.
 */
public final class SyncEventLogger {
    private static final String TAG = "SyncAI";
    private static final String FILE_NAME = "sync_ai_events.log";
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_LINES = 12000;

    private static final Object LOCK = new Object();
    private static final AtomicLong EVENT_ID = new AtomicLong(0L);
    private static volatile Context appContext;
    private static volatile boolean installed;
    private static volatile Thread.UncaughtExceptionHandler previousHandler;

    private SyncEventLogger() {}

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (appContext == null) appContext = app;
            if (installed) return;

            previousHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                recordException(app, "PROCESS", "UNCAUGHT_EXCEPTION", error,
                        "thread=" + thread.getName() + " id=" + thread.getId());
                Thread.UncaughtExceptionHandler previous = previousHandler;
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            });

            installed = true;
            record(app, "PROCESS", "PROCESS_START", "INFO",
                    "pid=" + Process.myPid()
                            + " appVersion=" + safeVersion(app)
                            + " android=" + Build.VERSION.RELEASE
                            + " api=" + Build.VERSION.SDK_INT
                            + " device=" + Build.MANUFACTURER + " " + Build.MODEL);
        }
    }

    public static void record(Context context, String component, String event,
                              String severity, String details) {
        Context app = context == null ? appContext
                : context.getApplicationContext();
        if (app == null) return;

        long id = EVENT_ID.incrementAndGet();
        long now = System.currentTimeMillis();
        long uptime = android.os.SystemClock.elapsedRealtime();
        String line = "{"
                + ""id":" + id
                + ","time":"" + escape(timestamp(now)) + """
                + ","uptimeMs":" + uptime
                + ","pid":" + Process.myPid()
                + ","thread":"" + escape(Thread.currentThread().getName()) + """
                + ","component":"" + escape(component) + """
                + ","event":"" + escape(event) + """
                + ","severity":"" + escape(severity) + """
                + ","details":"" + escape(details == null ? "" : details) + """
                + "}";
        append(app, line);
        if ("ERROR".equalsIgnoreCase(severity) || "FATAL".equalsIgnoreCase(severity)) {
            Log.e(TAG, component + " :: " + event + " :: " + (details == null ? "" : details));
        } else {
            Log.d(TAG, component + " :: " + event + " :: " + (details == null ? "" : details));
        }
    }

    public static void record(Context context, String component, String event, String details) {
        record(context, component, event, "INFO", details);
    }

    public static void recordException(Context context, String component, String event,
                                        Throwable error, String details) {
        String stack = stackTrace(error);
        String combined = (details == null ? "" : details)
                + " | exception=" + (error == null ? "null" : error.getClass().getName())
                + " message=" + (error == null ? "" : String.valueOf(error.getMessage()))
                + " stack=" + stack;
        record(context, component, event, "ERROR", combined);
    }

    public static void recordIntent(Context context, String component, String event,
                                    android.content.Intent intent) {
        if (intent == null) {
            record(context, component, event, "INFO", "intent=null");
            return;
        }
        String detail = "action=" + intent.getAction()
                + " component=" + intent.getComponent()
                + " flags=0x" + Integer.toHexString(intent.getFlags())
                + " categories=" + intent.getCategories();
        record(context, component, event, "INFO", detail);
    }

    public static String readAll(Context context) {
        Context app = context.getApplicationContext();
        File file = new File(app.getFilesDir(), FILE_NAME);
        if (!file.exists()) return "[No Sync AI flight-recorder events have been recorded.]\n";

        StringBuilder out = new StringBuilder();
        synchronized (LOCK) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            } catch (Exception e) {
                out.append("[Unable to read persistent event log: ")
                        .append(e).append("]\n");
            }
        }
        return out.toString();
    }

    public static String captureLogcat() {
        StringBuilder out = new StringBuilder();
        String[] commands = {
                "logcat -d -v threadtime -s SyncAI:D SyncAI:I SyncAI:W SyncAI:E",
                "logcat -d -v threadtime"
        };
        for (String command : commands) {
            try {
                java.lang.Process process = Runtime.getRuntime().exec(command.split(" "));
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("SyncAI") || out.length() == 0) {
                            out.append(line).append('\n');
                        }
                    }
                }
                process.waitFor();
                if (out.length() > 0) return out.toString();
            } catch (Exception ignored) {
                // Android may deny third-party access to global logcat.
            }
        }
        return "[Android did not expose readable logcat output to Sync AI. "
                + "The persistent Sync AI flight recorder is included above.]\n";
    }

    public static String fileName() {
        return FILE_NAME;
    }

    private static void append(Context app, String line) {
        synchronized (LOCK) {
            File file = new File(app.getFilesDir(), FILE_NAME);
            try {
                if (file.exists() && file.length() >= MAX_BYTES) trim(file);

                try (FileOutputStream fos = new FileOutputStream(file, true);
                     BufferedWriter writer = new BufferedWriter(
                             new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    fos.getFD().sync();
                }
            } catch (Exception error) {
                // Do not crash the app because diagnostics failed.
                Log.e(TAG, "Unable to persist diagnostics", error);
            }
        }
    }

    private static void trim(File file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        int from = Math.max(0, lines.size() - MAX_LINES);
        try (FileOutputStream fos = new FileOutputStream(file, false);
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            for (int i = from; i < lines.size(); i++) {
                writer.write(lines.get(i));
                writer.newLine();
            }
            writer.flush();
            fos.getFD().sync();
        }
    }

    private static String timestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US)
                .format(new Date(millis));
    }

    private static String safeVersion(Context app) {
        try {
            android.content.pm.PackageInfo info = app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String stackTrace(Throwable error) {
        if (error == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        error.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(""", "\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
