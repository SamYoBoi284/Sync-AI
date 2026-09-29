package com.sam.syncai;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "SyncVoiceService";

    private static SyncVoiceInteractionService instance;
@Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wakeWordController = new WakeWordController(this, new WakeWordController.Listener() {
            @Override public void onWakeWordDetected(float score) {
                stopWakeWord();
                Log.i(TAG, "HEY_JARVIS detected, score=" + score + ". Showing voice session.");
                try {
                    showSession(new Bundle(), 0);
                } catch (Throwable error) {
                    Log.e(TAG, "Failed to show voice session after wake word", error);
                    startWakeWordIfAllowed();
                }
            }

            @Override public void onWakeWordError(String message) {
                Log.e(TAG, "Wake-word detector error: " + message);
            }
        });
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.i(TAG, "VoiceInteractionService ready.");
        startWakeWordIfAllowed();
    }

    @Override
    public void onPrepareToShowSession(Bundle args, int flags) {
        super.onPrepareToShowSession(args, flags);
        stopWakeWord();
        Log.i(TAG, "Preparing voice session. flags=" + flags);
    }

    @Override
    public void onShowSessionFailed(Bundle args) {
        super.onShowSessionFailed(args);
        Log.e(TAG, "Voice session failed to show.");
        startWakeWordIfAllowed();
    }

    @Override
    public void onLaunchVoiceAssistFromKeyguard() {
        stopWakeWord();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_ASSIST);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    static boolean hasLiveInstance() {
        return instance != null;
    }

    static boolean isActiveVoiceInteractionService(android.content.Context context) {
        ComponentName component = new ComponentName(context, SyncVoiceInteractionService.class);
        return VoiceInteractionService.isActiveService(context, component);
    }

    static void startWakeWord() {
        if (instance != null) {
            instance.wakeWordPaused = false;
            instance.startWakeWordIfAllowed();
        }
    }

    static void stopWakeWord() {
        if (instance != null) {
            instance.wakeWordPaused = true;
            if (instance.wakeWordController != null) {
                instance.wakeWordController.stop();
            }
        }
    }

    private void startWakeWordIfAllowed() {
        if (wakeWordPaused) return;
        if (wakeWordController == null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted; wake word remains disabled.");
            return;
        }
        try {
            wakeWordController.start();
            Log.i(TAG, "Wake-word start requested. running=" + wakeWordController.isRunning());
        } catch (Throwable error) {
            Log.e(TAG, "Unable to start wake-word detector", error);
        }
    }

    @Override
    public void onShutdown() {
        if (wakeWordController != null) {
            wakeWordController.release();
        }
        instance = null;
        super.onShutdown();
    }

    @Override
    public void onDestroy() {
        if (wakeWordController != null) {
            wakeWordController.release();
            wakeWordController = null;
        }
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
