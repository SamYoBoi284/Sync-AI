package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;

public final class VoiceController {
    public interface Listener {
        void onListeningChanged(boolean listening);
        void onSpeechStarted();
        void onPartialText(String text);
        void onFinalText(String text);
        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean speakingEnabled = false;
    private Runnable speechDoneCallback;

    public VoiceController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        // SpeechRecognizer is created lazily only when Voice Mode starts.
        tts = new TextToSpeech(this.context, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(Locale.getDefault());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    private void finishSpeech() {
                        Runnable done = speechDoneCallback;
                        speechDoneCallback = null;
                        if (done != null) {
                            new Handler(Looper.getMainLooper()).post(done);
                        }
                    }

                    @Override public void onStart(String utteranceId) { }

                    @Override public void onDone(String utteranceId) {
                        finishSpeech();
                    }

                    @Override public void onError(String utteranceId) {
                        finishSpeech();
                    }
                });
            }
        });
    }

    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(this.context);
    }

    private void ensureRecognizer() {
        if (recognizer != null || !SpeechRecognizer.isRecognitionAvailable(this.context)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this.context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listener.onListeningChanged(true);
            }
            @Override public void onBeginningOfSpeech() {
                listener.onSpeechStarted();
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                listener.onListeningChanged(false);
            }
            @Override public void onError(int error) {
                listener.onListeningChanged(false);
                listener.onError(errorMessage(error));
            }
            @Override public void onResults(Bundle results) {
                listener.onListeningChanged(false);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) listener.onFinalText(matches.get(0));
            }
            @Override public void onPartialResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) listener.onPartialText(matches.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    public void startListening() {
        ensureRecognizer();
        if (recognizer == null) {
            listener.onError("No speech recognition service is available on this device.");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        // Give the user a 5-second silence buffer before the recognizer considers
        // the utterance complete. Recognition implementations may clamp/ignore
        // these hints, so VoiceController remains ready to be wrapped by the
        // conversation loop later.
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        recognizer.startListening(intent);
    }

    public void stopListening() {
        if (recognizer != null) recognizer.stopListening();
    }

    public void releaseRecognition() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
    }

    public void setSpeakingEnabled(boolean enabled) {
        speakingEnabled = enabled;
    }

    public boolean isSpeakingEnabled() {
        return speakingEnabled;
    }

    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, Runnable onDone) {
        if (!speakingEnabled || !ttsReady || text == null || text.trim().isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        speechDoneCallback = onDone;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "syncai-response");
    }

    public void shutdown() {
        releaseRecognition();
        speechDoneCallback = null;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private static String errorMessage(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: return "Microphone audio error.";
            case SpeechRecognizer.ERROR_CLIENT: return "Speech recognition client error.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Microphone permission is required.";
            case SpeechRecognizer.ERROR_NETWORK: return "Speech recognition network error.";
            case SpeechRecognizer.ERROR_NO_MATCH: return "I couldn't understand that.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Speech recognition is busy.";
            case SpeechRecognizer.ERROR_SERVER: return "Speech recognition service error.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech was detected.";
            default: return "Speech recognition failed.";
        }
    }
}
