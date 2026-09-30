package com.sam.syncai;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "SyncAI";

    @Override public void onCreate() {
        super.onCreate();
        AppLog.log("VOICE_SERVICE", "onCreate", "component=" + getClass().getName());
        Log.d(TAG, "VoiceInteractionService.onCreate");
    }

    @Override public void onReady() {
        super.onReady();
        AppLog.log("VOICE_SERVICE", "onReady", "ready");
        Log.d(TAG, "VoiceInteractionService.onReady");
    }

    @Override public void onShutdown() {
        Log.d(TAG, "VoiceInteractionService.onShutdown");
        AppLog.log("VOICE_SERVICE", "onShutdown", "shutdown");
        super.onShutdown();
    }

    @Override public void onPrepareToShowSession(Bundle args, int flags) {
        super.onPrepareToShowSession(args, flags);
        AppLog.log("VOICE_SERVICE", "onPrepareToShowSession", "flags=" + flags + " args=" + args);
        Log.d(TAG, "VoiceInteractionService.onPrepareToShowSession flags=" + flags
                + " args=" + args + " thread=" + Thread.currentThread().getName());
    }

    @Override public void onShowSessionFailed(Bundle args) {
        super.onShowSessionFailed(args);
        Log.e(TAG, "VoiceInteractionService.onShowSessionFailed args=" + args);
        AppLog.log("VOICE_SERVICE", "onShowSessionFailed", "args=" + args);
        // Fall back to the exact same Voice Mode Activity rather than leaving the
        // hardware assistant button with no visible result.
        launchVoiceMode("session-failed");
    }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        Log.d(TAG, "VoiceInteractionService.onLaunchVoiceAssistFromKeyguard");
        AppLog.log("VOICE_SERVICE", "onLaunchVoiceAssistFromKeyguard", "keyguard launch requested");
        launchVoiceMode("keyguard");
    }

    private void launchVoiceMode(String source) {
        try {
            Intent intent = new Intent(this, VoiceModeActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Log.d(TAG, "Launching Voice Mode from " + source
                    + " action=" + intent.getAction());
            startActivity(intent);
            Log.d(TAG, "VoiceModeActivity startActivity returned successfully from " + source);
        } catch (Exception error) {
            Log.e(TAG, "Voice Mode launch failed from " + source, error);
        }
    }
}