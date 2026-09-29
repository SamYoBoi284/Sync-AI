package com.sam.syncai;

import android.content.Context;
import android.util.Log;

import com.openwakeword.OpenWakeWord;

/**
 * Small wrapper around openWakeWord.
 *
 * First pass intentionally uses the bundled HEY_JARVIS model so we can verify
 * the complete on-device wake-word pipeline before training a custom model.
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
    private boolean running;

    public WakeWordController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;

        try {
            if (detector == null) {
                detector = new OpenWakeWord.Builder(context)
                        .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                        .setThreshold(0.5f)
                        .setDebounceMs(2500L)
                        .build();
            }

            running = true;
            detector.start(score -> {
                if (!running) return;
                Log.d(TAG, "HEY_JARVIS detected, score=" + score);
                listener.onWakeWordDetected(score);
            });
        } catch (Throwable error) {
            running = false;
            Log.e(TAG, "Wake-word detector failed", error);
            listener.onWakeWordError(
                    error.getMessage() == null ? "Wake-word detector failed." : error.getMessage());
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            if (detector != null) detector.stop();
        } catch (Throwable error) {
            Log.w(TAG, "Wake-word stop failed", error);
        }
    }

    public void release() {
        running = false;
        if (detector != null) {
            try {
                detector.stop();
            } catch (Throwable ignored) {
            }
            try {
                detector.release();
            } catch (Throwable ignored) {
            }
            detector = null;
        }
    }
}
