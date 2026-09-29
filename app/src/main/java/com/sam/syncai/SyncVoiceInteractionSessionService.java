package com.sam.syncai;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public final class SyncVoiceInteractionSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args) {
        return new Session(this);
    }

    private static final class Session extends VoiceInteractionSession {
        Session(android.content.Context context) {
            super(context);
        }

        @Override public void onShow(Bundle args, int showFlags) {
            super.onShow(args, showFlags);
            Intent intent = new Intent(getContext(), MainActivity.class);
            intent.setAction(Intent.ACTION_ASSIST);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startAssistantActivity(intent);
            setUiEnabled(false);
            finish();
        }
    }
}
