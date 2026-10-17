package com.sam.syncai;

import android.content.Context;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experimental conversational STT session backed by the locally installed Vosk model.
 *
 * <p>This adapter deliberately does not start or stop the wake-word service. The caller
 * must stop the wake listener and release the microphone before calling start(), and
 * must stop this session before allowing the wake listener or another recorder to resume.</p>
 *
 * <p>Model loading is performed by the caller so it can happen on a worker thread and
 * be measured separately. The supplied Model is owned by this session and is closed
 * when the session is stopped.</p>
 */
public final class VoskVoiceRecognitionSession implements AutoCloseable {
    public interface Listener {
        /** Interim text; safe to show as a live transcript, not yet a committed turn. */
        void onPartial(String text);

        /** Finalized utterance text suitable for the voice-mode turn handler. */
        void onFinal(String text);

        void onError(Exception error);

        void onTimeout();
    }

    private final Model model;
    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private SpeechService speechService;
    private Recognizer recognizer;
    private String lastDelivered = "";

    public VoskVoiceRecognitionSession(Model model, Listener listener) {
        if (model == null) throw new IllegalArgumentException("Vosk model is required.");
        if (listener == null) throw new IllegalArgumentException("Listener is required.");
        this.model = model;
        this.listener = listener;
    }

    /**
     * Starts streaming recognition. Call only after microphone ownership has been handed
     * to voice mode. The caller should invoke this method from an appropriate lifecycle
     * path and handle errors on the UI thread.
     */
    public synchronized void start() throws Exception {
        if (!stopped.compareAndSet(true, false)) return;
        try {
            recognizer = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(new org.vosk.android.RecognitionListener() {
                @Override public void onPartialResult(String hypothesis) {
                    String text = extract(hypothesis, "partial");
                    if (!text.isEmpty() && !stopped.get()) listener.onPartial(text);
                }

                @Override public void onResult(String hypothesis) {
                    deliverFinal(extract(hypothesis, "text"));
                }

                @Override public void onFinalResult(String hypothesis) {
                    deliverFinal(extract(hypothesis, "text"));
                }

                @Override public void onError(Exception error) {
                    if (!stopped.get()) listener.onError(error);
                }

                @Override public void onTimeout() {
                    if (!stopped.get()) listener.onTimeout();
                }
            });
        } catch (Exception error) {
            stopInternal();
            throw error;
        }
    }

    private void deliverFinal(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() || stopped.get()) return;
        synchronized (this) {
            if (clean.equals(lastDelivered)) return;
            lastDelivered = clean;
        }
        listener.onFinal(clean);
    }

    private static String extract(String json, String field) {
        if (json == null || json.trim().isEmpty()) return "";
        try {
            return new JSONObject(json).optString(field, "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Stops audio capture and closes this session's recognizer and model. */
    public synchronized void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        stopInternal();
    }

    private void stopInternal() {
        SpeechService service = speechService;
        speechService = null;
        if (service != null) {
            try { service.stop(); } catch (Exception ignored) {}
            try { service.shutdown(); } catch (Exception ignored) {}
        }

        Recognizer oldRecognizer = recognizer;
        recognizer = null;
        if (oldRecognizer != null) {
            try { oldRecognizer.close(); } catch (Exception ignored) {}
        }

        try { model.close(); } catch (Exception ignored) {}
    }

    public boolean isRunning() {
        return !stopped.get();
    }

    @Override public void close() {
        stop();
    }
}
