package com.sam.syncai;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "SyncAI";

    @Override public void onCreate() {
        SyncEventLogger.install(this);
        super.onCreate();
        SyncEventLogger.record(this, "SyncVoiceInteractionService", "onCreate", "INFO",
                "service created");
        Log.d(TAG, "VoiceInteractionService.onCreate");
    }

    @Override public void onReady() {
        super.onReady();
        SyncEventLogger.record(this, "SyncVoiceInteractionService", "onReady", "INFO",
                "assistant service ready");
        Log.d(TAG, "VoiceInteractionService.onReady");
    }

    @Override public void onShutdown() {
        SyncEventLogger.record(this, "SyncVoiceInteractionService", "onShutdown", "INFO",
                "assistant service shutdown");
        Log.d(TAG, "VoiceInteractionService.onShutdown");
        super.onShutdown();
    }

    @Override public void onPrepareToShowSession(Bundle args, int flags) {
        super.onPrepareToShowSession(args, flags);
        SyncEventLogger.record(this, "SyncVoiceInteractionService",
                "onPrepareToShowSession", "INFO", "flags=" + flags + " args=" + args);
        Log.d(TAG, "VoiceInteractionService.onPrepareToShowSession flags=" + flags
                + " args=" + args + " thread=" + Thread.currentThread().getName());
    }

    @Override public void onShowSessionFailed(Bundle args) {
        super.onShowSessionFailed(args);
        SyncEventLogger.record(this, "SyncVoiceInteractionService",
                "onShowSessionFailed", "ERROR", "args=" + args);
        Log.e(TAG, "VoiceInteractionService.onShowSessionFailed args=" + args);
        // Fall back to the exact same Voice Mode Activity rather than leaving the
        // hardware assistant button with no visible result.
        launchVoiceMode("session-failed");
    }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        SyncEventLogger.record(this, "SyncVoiceInteractionService",
                "onLaunchVoiceAssistFromKeyguard", "INFO", "keyguard voice-assist callback");
        Log.d(TAG, "VoiceInteractionService.onLaunchVoiceAssistFromKeyguard");
        launchVoiceMode("keyguard");
    }

    private void launchVoiceMode(String source) {
        try {
            Intent intent = new Intent(this, VoiceModeActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            SyncEventLogger.record(this, "SyncVoiceInteractionService",
                    "VOICE_MODE_LAUNCH_ATTEMPT", "INFO", "source=" + source);
            Log.d(TAG, "Launching Voice Mode from " + source
                    + " action=" + intent.getAction());
            startActivity(intent);
            SyncEventLogger.record(this, "SyncVoiceInteractionService",
                    "VOICE_MODE_LAUNCH_SUCCESS", "INFO", "source=" + source);
            Log.d(TAG, "VoiceModeActivity startActivity returned successfully from " + source);
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "SyncVoiceInteractionService",
                    "VOICE_MODE_LAUNCH_ERROR", error, "source=" + source);
            Log.e(TAG, "Voice Mode launch failed from " + source, error);
        }
    }
}