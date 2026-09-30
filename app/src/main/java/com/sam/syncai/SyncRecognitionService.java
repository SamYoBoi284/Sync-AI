package com.sam.syncai;

import android.content.Intent;
import android.speech.RecognitionService;

public final class SyncRecognitionService extends RecognitionService {
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        listener.error(5);
    }

    @Override
    protected void onStopListening(Callback listener) {
        listener.error(5);
    }

    @Override
    protected void onCancel(Callback listener) {
        listener.error(5);
    }
}
