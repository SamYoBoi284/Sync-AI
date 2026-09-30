package com.sam.syncai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/**
 * Optional recognition provider declaration for Android voice-assistant discovery.
 *
 * Sync's actual voice UI owns its SpeechRecognizer directly. This provider does
 * not create another SpeechRecognizer, which would recursively bind back into
 * itself when Sync is the active voice interaction service.
 */
public final class SyncRecognitionService extends RecognitionService {
    @Override
    public void onCreate() {
        SyncEventLogger.install(this);
        super.onCreate();
        SyncEventLogger.record(this, "SyncRecognitionService", "onCreate", "INFO", "");
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        SyncEventLogger.recordIntent(this, "SyncRecognitionService",
                "onStartListening", recognizerIntent);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            sendError(callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            return;
        }
        sendError(callback, SpeechRecognizer.ERROR_CLIENT);
    }

    @Override
    protected void onStopListening(Callback callback) {
        SyncEventLogger.record(this, "SyncRecognitionService", "onStopListening", "INFO", "");
        sendError(callback, SpeechRecognizer.ERROR_CLIENT);
    }

    @Override
    protected void onCancel(Callback callback) {
        SyncEventLogger.record(this, "SyncRecognitionService", "onCancel", "INFO", "");
        // Nothing to cancel: Sync's Voice Mode owns its recognizer directly.
    }

    @Override
    public void onDestroy() {
        SyncEventLogger.record(this, "SyncRecognitionService", "onDestroy", "INFO", "");
        super.onDestroy();
    }

    private void sendError(Callback callback, int error) {
        try {
            callback.error(error);
        } catch (RemoteException ignored) {
        }
    }
}
