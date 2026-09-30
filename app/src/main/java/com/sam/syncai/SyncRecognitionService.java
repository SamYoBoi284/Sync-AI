package com.sam.syncai;

import android.content.Intent;
import android.os.RemoteException;
import android.speech.RecognitionService;

public final class SyncRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        reportError(listener);
    }

    @Override
    protected void onStopListening(Callback listener) {
        reportError(listener);
    }

    @Override
    protected void onCancel(Callback listener) {
        reportError(listener);
    }

    private void reportError(Callback listener) {
        try {
            listener.error(5);
        } catch (RemoteException ignored) {
            // Client went away while the recognition request was being rejected.
        }
    }
}
