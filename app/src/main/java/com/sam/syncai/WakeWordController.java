package com.sam.syncai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.openwakeword.OpenWakeWord;

/**
 * On-device wake-word wrapper.
 *
 * The selected VoiceInteractionService owns the background detector.
 * MainActivity may use a short-lived instance while the app is visible so the
 * feature can still be tested before Samsung has attached the app as the
 * system assistant.
 */
public final class WakeWordController {
    public interface Listener {
        void onWakeWordDetected(float score);
        void onWakeWordError(String message);
    }

    private static final String TAG = "SyncWakeWord";

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OpenWakeWord detector;
    private volatile boolean running;
    private volatile boolean stopping;

    public WakeWordController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStopping() {
        return stopping;
    }

    public synchronized void start() {
        if (running) return;
        if (stopping) {
            mainHandler.postDelayed(this::start, 350L);
            return;
        }

        try {
            if (detector == null) {
                detector = new OpenWakeWord.Builder(context)
                        .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                        .setThreshold(0.30f)
                        .setDebounceMs(1800L)
                        .build();
            }

            running = true;
            Log.i(TAG, "Starting HEY_JARVIS detector.");
            detector.start(score -> {
                if (!running) return;
                Log.i(TAG, "HEY_JARVIS detected, score=" + score);
                listener.onWakeWordDetected(score);
            });
        } catch (Throwable error) {
            running = false;
            Log.e(TAG, "Wake-word detector failed to start", error);
            listener.onWakeWordError(
                    error.getMessage() == null ? "Wake-word detector failed to start." : error.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running || stopping) return;

        running = false;
        stopping = true;

        final OpenWakeWord current = detector;
        if (current == null) {
            stopping = false;
            return;
        }

        // openWakeWord waits for its processing thread during stop(). Never
        // block Android's main thread after a hotword detection.
        Thread stopThread = new Thread(() -> {
            try {
                current.stop();
            } catch (Throwable error) {
                Log.w(TAG, "Wake-word stop failed", error);
            } finally {
                stopping = false;
            }
        }, "SyncWakeWordStop");
        stopThread.start();
    }

    public synchronized void release() {
        running = false;
        stopping = true;

        final OpenWakeWord current = detector;
        detector = null;

        if (current == null) {
            stopping = false;
            return;
        }

        Thread releaseThread = new Thread(() -> {
            try {
                current.release();
            } catch (Throwable error) {
                Log.w(TAG, "Wake-word release failed", error);
            } finally {
                stopping = false;
            }
        }, "SyncWakeWordRelease");
        releaseThread.start();
    }
}
