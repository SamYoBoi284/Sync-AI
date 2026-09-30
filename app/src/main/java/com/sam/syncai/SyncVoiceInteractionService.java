package com.sam.syncai;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    @Override public void onReady() {
        super.onReady();
    }

    @Override public void onPrepareToShowSession(Bundle args, int flags) {
        super.onPrepareToShowSession(args, flags);
    }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        launchVoiceMode();
    }

    private void launchVoiceMode() {
        try {
            Intent intent = new Intent(this, VoiceModeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
