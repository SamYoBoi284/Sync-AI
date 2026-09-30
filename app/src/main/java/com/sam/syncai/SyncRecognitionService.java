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
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            sendError(callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            return;
        }
        sendError(callback, SpeechRecognizer.ERROR_CLIENT);
    }

    @Override
    protected void onStopListening(Callback callback) {
        sendError(callback, SpeechRecognizer.ERROR_CLIENT);
    }

    @Override
    protected void onCancel(Callback callback) {
        // Nothing to cancel: Sync's Voice Mode owns its recognizer directly.
    }

    private void sendError(Callback callback, int error) {
        try {
            callback.error(error);
        } catch (RemoteException ignored) {
        }
    }
}
