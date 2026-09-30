package com.sam.syncai;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public final class SyncVoiceSessionService extends VoiceInteractionSessionService {
    private static final String TAG = "SyncAI";

    @Override public void onCreate() {
        super.onCreate();
        android.util.Log.d(TAG, "VoiceInteractionSessionService.onCreate");
        AppLog.log("VOICE_SESSION_SERVICE", "onCreate", "created");
    }

    @Override public VoiceInteractionSession onNewSession(Bundle args) {
        android.util.Log.d(TAG, "VoiceInteractionSessionService.onNewSession args=" + args);
        AppLog.log("VOICE_SESSION_SERVICE", "onNewSession", "args=" + args);
        SyncVoiceSession session = new SyncVoiceSession(this);
        android.util.Log.d(TAG, "VoiceInteractionSessionService created session=" + session);
        return session;
    }

    @Override public void onDestroy() {
        android.util.Log.d(TAG, "VoiceInteractionSessionService.onDestroy");
        AppLog.log("VOICE_SESSION_SERVICE", "onDestroy", "destroyed");
        super.onDestroy();
    }
}
