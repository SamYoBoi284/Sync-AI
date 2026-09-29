package com.sam.syncai;

import android.content.Context;
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
    private OpenWakeWord detector;
    private volatile boolean running;

    public WakeWordController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running) return;

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
        if (!running) return;
        running = false;
        try {
            if (detector != null) detector.stop();
        } catch (Throwable error) {
            Log.w(TAG, "Wake-word stop failed", error);
        }
    }

    public synchronized void release() {
        running = false;
        if (detector != null) {
            try {
                detector.release();
            } catch (Throwable error) {
                Log.w(TAG, "Wake-word release failed", error);
            }
            detector = null;
        }
    }
}
