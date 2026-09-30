package com.sam.syncai;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "SyncAI";

    @Override public void onReady() {
        super.onReady();
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                setInvocationEffectEnabled(true);
            } catch (Exception ignored) {
                // Some vendor SystemUI builds may not expose the invocation effect API.
            }
        }
    }

    @Override public void onPrepareToShowSession(Bundle args, int flags) {
        super.onPrepareToShowSession(args, flags);
    }

    @Override public void onShowSessionFailed(Bundle args) {
        super.onShowSessionFailed(args);
        Log.e(TAG, "Voice interaction session failed to show: " + args);
        // Fall back to the exact same Voice Mode Activity rather than leaving the
        // hardware assistant button with no visible result.
        launchVoiceMode("session-failed");
    }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        launchVoiceMode("keyguard");
    }

    private void launchVoiceMode(String source) {
        try {
            Intent intent = new Intent(this, VoiceModeActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Log.d(TAG, "Launching Voice Mode from " + source);
            startActivity(intent);
        } catch (Exception error) {
            Log.e(TAG, "Voice Mode launch failed from " + source, error);
        }
    }
}