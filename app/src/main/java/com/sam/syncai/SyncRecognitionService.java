package com.sam.syncai;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * RecognitionService required for a complete VoiceInteractionService.
 *
 * Sync AI uses the framework recognizer for speech input. When Android selects
 * Sync AI as the assistant, this service avoids recursively binding to itself by
 * delegating recognition to another installed RecognitionService.
 */
public final class SyncRecognitionService extends RecognitionService {
    private static final String TAG = "SyncRecognitionService";

    private SpeechRecognizer delegate;
    private Callback activeCallback;

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        activeCallback = listener;
        ComponentName component = findDelegateComponent();

        if (component == null) {
            try {
                listener.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (RemoteException ignored) {
            }
            return;
        }

        try {
            destroyDelegate();
            delegate = SpeechRecognizer.createSpeechRecognizer(this, component);
            delegate.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    try { listener.readyForSpeech(params); } catch (RemoteException ignored) { }
                }

                @Override public void onBeginningOfSpeech() {
                    try { listener.beginningOfSpeech(); } catch (RemoteException ignored) { }
                }

                @Override public void onRmsChanged(float rmsdB) {
                    try { listener.rmsChanged(rmsdB); } catch (RemoteException ignored) { }
                }

                @Override public void onBufferReceived(byte[] buffer) {
                    try { listener.bufferReceived(buffer); } catch (RemoteException ignored) { }
                }

                @Override public void onEndOfSpeech() {
                    try { listener.endOfSpeech(); } catch (RemoteException ignored) { }
                }

                @Override public void onError(int error) {
                    try { listener.error(error); } catch (RemoteException ignored) { }
                    destroyDelegate();
                    activeCallback = null;
                }

                @Override public void onResults(Bundle results) {
                    try { listener.results(results); } catch (RemoteException ignored) { }
                    destroyDelegate();
                    activeCallback = null;
                }

                @Override public void onPartialResults(Bundle results) {
                    try { listener.partialResults(results); } catch (RemoteException ignored) { }
                }

                @Override public void onEvent(int eventType, Bundle params) {
                    try { listener.event(eventType, params); } catch (RemoteException ignored) { }
                }
            });

            Intent forwarded = new Intent(recognizerIntent);
            if (!forwarded.hasExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)) {
                forwarded.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            }
            delegate.startListening(forwarded);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to delegate speech recognition", error);
            try {
                listener.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (RemoteException ignored) {
            }
            destroyDelegate();
            activeCallback = null;
        }
    }

    @Override
    protected void onStopListening(Callback listener) {
        if (delegate != null) {
            try {
                delegate.stopListening();
            } catch (Throwable error) {
                Log.w(TAG, "Delegate stopListening failed", error);
            }
        }
    }

    @Override
    protected void onCancel(Callback listener) {
        if (delegate != null) {
            try {
                delegate.cancel();
            } catch (Throwable error) {
                Log.w(TAG, "Delegate cancel failed", error);
            }
        }
        activeCallback = null;
    }

    @Override
    public void onDestroy() {
        destroyDelegate();
        activeCallback = null;
        super.onDestroy();
    }

    private void destroyDelegate() {
        if (delegate != null) {
            try {
                delegate.destroy();
            } catch (Throwable ignored) {
            }
            delegate = null;
        }
    }

    private ComponentName findDelegateComponent() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(RecognitionService.SERVICE_INTERFACE);
        List<ResolveInfo> services = pm.queryIntentServices(query, PackageManager.MATCH_ALL);

        String defaultComponent = null;
        try {
            defaultComponent = android.provider.Settings.Secure.getString(
                    getContentResolver(), "voice_recognition_service");
        } catch (Throwable ignored) {
        }

        // Prefer the currently configured recognizer when it isn't Sync AI.
        if (defaultComponent != null && !defaultComponent.isEmpty()) {
            try {
                ComponentName configured = ComponentName.unflattenFromString(defaultComponent);
                if (configured != null &&
                        !getPackageName().equals(configured.getPackageName())) {
                    return configured;
                }
            } catch (Throwable ignored) {
            }
        }

        // Otherwise choose any installed recognizer outside Sync AI.
        for (ResolveInfo info : services) {
            if (info == null || info.serviceInfo == null) continue;
            String packageName = info.serviceInfo.packageName;
            if (!getPackageName().equals(packageName)) {
                return new ComponentName(packageName, info.serviceInfo.name);
            }
        }

        return null;
    }
}
