package com.sam.syncai;

import android.content.Intent;
import android.service.voice.VoiceInteractionService;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    @Override public void onReady() {
        super.onReady();
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
        startActivity(intent);
    }
}
