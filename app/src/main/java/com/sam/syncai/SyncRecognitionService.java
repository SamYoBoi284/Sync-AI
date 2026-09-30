package com.sam.syncai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/**
 * Sync AI's recognition provider for the Android voice-interaction service.
 *
 * This is a real recognition bridge rather than an always-error placeholder.
 */
public final class SyncRecognitionService extends RecognitionService {
    private SpeechRecognizer recognizer;

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            sendError(callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            return;
        }

        destroyRecognizer();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendError(callback, SpeechRecognizer.ERROR_CLIENT);
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                try { callback.readyForSpeech(params); } catch (RemoteException ignored) {}
            }
            @Override public void onBeginningOfSpeech() {
                try { callback.beginningOfSpeech(); } catch (RemoteException ignored) {}
            }
            @Override public void onRmsChanged(float rmsdB) {
                try { callback.rmsChanged(rmsdB); } catch (RemoteException ignored) {}
            }
            @Override public void onBufferReceived(byte[] buffer) {
                try { callback.bufferReceived(buffer); } catch (RemoteException ignored) {}
            }
            @Override public void onEndOfSpeech() {
                try { callback.endOfSpeech(); } catch (RemoteException ignored) {}
            }
            @Override public void onError(int error) {
                sendError(callback, error);
            }
            @Override public void onResults(Bundle results) {
                try { callback.results(results); } catch (RemoteException ignored) {}
            }
            @Override public void onPartialResults(Bundle partialResults) {
                try { callback.partialResults(partialResults); } catch (RemoteException ignored) {}
            }
        });

        Intent request = recognizerIntent == null
                ? new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                : new Intent(recognizerIntent);
        request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            recognizer.startListening(request);
        } catch (Exception e) {
            sendError(callback, SpeechRecognizer.ERROR_CLIENT);
        }
    }

    @Override
    protected void onStopListening(Callback callback) {
        if (recognizer == null) {
            sendError(callback, SpeechRecognizer.ERROR_CLIENT);
            return;
        }
        try {
            recognizer.stopListening();
        } catch (Exception e) {
            sendError(callback, SpeechRecognizer.ERROR_CLIENT);
        }
    }

    @Override
    protected void onCancel(Callback callback) {
        destroyRecognizer();
    }

    private void sendError(Callback callback, int error) {
        try { callback.error(error); } catch (RemoteException ignored) {}
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    @Override public void onDestroy() {
        destroyRecognizer();
        super.onDestroy();
    }
}
