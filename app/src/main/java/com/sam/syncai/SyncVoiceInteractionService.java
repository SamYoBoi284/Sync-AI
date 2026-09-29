package com.sam.syncai;

import android.content.ComponentName;
import android.content.Intent;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public final class SyncVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "SyncVoiceService";
    private static SyncVoiceInteractionService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "VoiceInteractionService ready; no background microphone or wake-word listener is active.");
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.i(TAG, "System assistant service is active.");
    }

    @Override
    public void onLaunchVoiceAssistFromKeyguard() {
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

    @Override
    public void onShutdown() {
        instance = null;
        super.onShutdown();
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
