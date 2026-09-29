package com.sam.syncai;

import android.content.Intent;
import android.os.Build;
import android.service.voice.VoiceInteractionService;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    @Override public void onReady() {
        super.onReady();
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                setInvocationEffectEnabled(true);
            } catch (SecurityException ignored) {
                // The system may not yet consider this instance the active assistant.
            }
        }
    }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        launchVoiceActivity();
    }

    private void launchVoiceActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_ASSIST);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        if (Build.VERSION.SDK_INT >= 27) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        startActivity(intent);
    }
}
