package com.sam.syncai;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLog {
    private static final Object LOCK = new Object();
    private static volatile boolean installed;
    private static File file;

    private AppLog() {}

    public static void init(Context context) {
        synchronized (LOCK) {
            if (installed) return;
            file = new File(context.getApplicationContext().getFilesDir(), "sync-ai-runtime.log");
            installed = true;
            log("APP", "init", "SDK=" + Build.VERSION.SDK_INT
                    + " device=" + Build.MANUFACTURER + " " + Build.MODEL
                    + " release=" + Build.VERSION.RELEASE);
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                logException("CRASH", "uncaught", error);
                if (previous != null) previous.uncaughtException(thread, error);
            });
        }
    }

    public static void log(String category, String event, String details) {
        synchronized (LOCK) {
            if (!installed || file == null) return;
            String timestamp = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US)
                    .format(new Date());
            String line = timestamp + " | " + safe(category) + " | "
                    + safe(event) + " | " + safe(details) + "\n";
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
    }

    public static void logException(String category, String event, Throwable error) {
        StringBuilder details = new StringBuilder();
        if (error != null) {
            details.append(error.getClass().getName());
            if (error.getMessage() != null) details.append(": ").append(error.getMessage());
            for (StackTraceElement element : error.getStackTrace()) {
                details.append("\n    at ").append(element);
            }
            Throwable cause = error.getCause();
            if (cause != null) details.append("\nCaused by: ").append(cause);
        }
        log(category, event, details.toString());
    }

    public static String readAll() {
        synchronized (LOCK) {
            if (!installed || file == null || !file.exists()) return "[No persistent runtime log recorded.]";
            try {
                byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                return new String(data, StandardCharsets.UTF_8);
            } catch (Exception error) {
                return "[Could not read runtime log: " + error + "]";
            }
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
