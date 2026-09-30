package com.sam.syncai;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public final class SyncVoiceSessionService extends VoiceInteractionSessionService {
    private static final String TAG = "SyncAI";

    @Override public void onCreate() {
        SyncEventLogger.install(this);
        super.onCreate();
        SyncEventLogger.record(this, "SyncVoiceSessionService", "onCreate", "INFO",
                "session service created");
        android.util.Log.d(TAG, "VoiceInteractionSessionService.onCreate");
    }

    @Override public VoiceInteractionSession onNewSession(Bundle args) {
        SyncEventLogger.record(this, "SyncVoiceSessionService", "onNewSession",
                "INFO", "args=" + args);
        android.util.Log.d(TAG, "VoiceInteractionSessionService.onNewSession args=" + args);
        SyncVoiceSession session = new SyncVoiceSession(this);
        SyncEventLogger.record(this, "SyncVoiceSessionService", "SESSION_CREATED",
                "INFO", "session=" + session);
        android.util.Log.d(TAG, "VoiceInteractionSessionService created session=" + session);
        return session;
    }

    @Override public void onDestroy() {
        SyncEventLogger.record(this, "SyncVoiceSessionService", "onDestroy", "INFO",
                "session service destroyed");
        android.util.Log.d(TAG, "VoiceInteractionSessionService.onDestroy");
        super.onDestroy();
    }
}
