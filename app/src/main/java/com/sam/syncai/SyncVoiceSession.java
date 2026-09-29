package com.sam.syncai;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.service.voice.VoiceInteractionSession;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncVoiceSession extends VoiceInteractionSession {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SyncRuntime runtime;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private TextView stateView;
    private TextView transcriptView;
    private TextView responseView;
    private View micButton;
    private int accent;
    private boolean ttsReady;
    private boolean destroyed;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public SyncVoiceSession(Context context) {
        super(context);
        runtime = SyncRuntime.get(context);
        accent = AppPreferences.ACCENTS[runtime.preferences().getAccent()];
        setKeepAwake(true);
    }

    @Override public View onCreateContentView() {
        FrameLayout root = new FrameLayout(getContext());
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x660B0D16);
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        panel.setBackground(bg);
        panel.setElevation(dp(14));

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("SYNC // AI", 18, Color.WHITE, true);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(35), 1));

        Button close = smallButton("×");
        close.setOnClickListener(v -> exit());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(42)));
        panel.addView(top);

        stateView = text("LISTENING", 11, accent, true);
        stateView.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.addView(stateView, new LinearLayout.LayoutParams(-1, dp(26)));

        micButton = new Button(getContext());
        ((Button) micButton).setText("◉");
        ((Button) micButton).setTextSize(28);
        ((Button) micButton).setTextColor(Color.WHITE);
        ((Button) micButton).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ((Button) micButton).setAllCaps(false);
        ((Button) micButton).setBackground(round(accent, 90));
        micButton.setOnClickListener(v -> {
            if (processing.get()) return;
            startListening();
        });
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(92), dp(92));
        micLp.gravity = Gravity.CENTER_HORIZONTAL;
        micLp.topMargin = dp(8);
        micLp.bottomMargin = dp(10);
        panel.addView(micButton, micLp);

        transcriptView = text("Say something…", 15, Color.WHITE, false);
        transcriptView.setGravity(Gravity.CENTER);
        transcriptView.setPadding(dp(6), dp(4), dp(6), dp(10));
        panel.addView(transcriptView);

        ScrollView responseScroll = new ScrollView(getContext());
        responseView = text("", 14, Color.rgb(215, 219, 232), false);
        responseView.setPadding(dp(4), dp(8), dp(4), dp(4));
        responseScroll.addView(responseView);
        panel.addView(responseScroll, new LinearLayout.LayoutParams(-1, dp(105)));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(330), dp(355), Gravity.CENTER);
        root.addView(panel, panelLp);

        return root;
    }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        configureWindow();
        initSpeech();
        initTts();
        restoreSelectedModel();
        startListeningOrRequestPermission();
    }

    private void configureWindow() {
        Dialog dialog = getWindow();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.dimAmount = 0f;
        window.setAttributes(lp);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    private void initSpeech() {
        if (recognizer != null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            updateState("SPEECH UNAVAILABLE");
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                updateState("LISTENING");
            }
            @Override public void onBeginningOfSpeech() {
                updateState("LISTENING…");
            }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                updateState("THINKING");
            }
            @Override public void onError(int error) {
                if (destroyed || processing.get()) return;
                updateState("LISTENING");
                if (error != SpeechRecognizer.ERROR_CLIENT &&
                        error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    main.postDelayed(() -> startListeningOrRequestPermission(), 350);
                }
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> values = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (values == null || values.isEmpty()) {
                    startListening();
                    return;
                }
                handleVoiceText(values.get(0));
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> values = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty()) transcriptView.setText(values.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void initTts() {
        if (tts != null) return;
        tts = new TextToSpeech(getContext(), status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(Locale.getDefault());
                tts.setSpeechRate(1.03f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        main.postDelayed(() -> {
                            processing.set(false);
                            if (!destroyed) startListening();
                        }, 350);
                    }
                    @Override public void onError(String id) {
                        main.postDelayed(() -> {
                            if (!destroyed && !processing.get()) startListening();
                        }, 350);
                    }
                });
            }
        });
    }

    private void restoreSelectedModel() {
        if (runtime.backend().isLoaded()) return;
        ModelInfo model = runtime.modelManager().getLoadedModel();
        if (model == null) return;
        updateState("LOADING MODEL");
        runtime.backend().load(model, new LocalModelBackend.LoadCallback() {
            @Override public void onLoaded() {
                main.post(() -> updateState("LISTENING"));
            }
            @Override public void onError(Exception error) {
                main.post(() -> responseView.setText(
                        "Model load failed; deterministic tools remain available."));
            }
        });
    }

    private void startListeningOrRequestPermission() {
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateState("MIC PERMISSION");
            transcriptView.setText("Sync needs microphone access only while Voice Mode is active.");
            try {
                startAssistantActivity(new Intent(getContext(), MicPermissionActivity.class));
            } catch (Exception e) {
                responseView.setText("Open Sync//AI once and enable microphone permission in Android Settings.");
            }
            main.postDelayed(() -> {
                if (!destroyed && getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) startListening();
            }, 900);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (destroyed || recognizer == null ||
                getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        processing.set(false);
        transcriptView.setText("Listening…");
        updateState("LISTENING");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            updateState("MIC ERROR");
            responseView.setText(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void handleVoiceText(String text) {
        if (text == null || text.trim().isEmpty()) {
            startListening();
            return;
        }
        String clean = text.trim();
        transcriptView.setText("“" + clean + "”");

        String lower = clean.toLowerCase(Locale.US);
        if (lower.matches(".*\\b(?:stop listening|goodbye|exit|cancel|close sync|that's all|thats all)\\b.*")) {
            speakAndMaybeListen("Alright bro.", true);
            return;
        }

        processing.set(true);
        updateState("THINKING");
        ToolEngine.Result tool = runtime.tools().handle(clean);
        if (tool.handled) {
            appendVoiceChat(clean, tool.response, new DiagnosticRecord(
                    System.currentTimeMillis() - tool.durationMs,
                    tool.durationMs,
                    "DETERMINISTIC TOOL",
                    tool.toolName,
                    tool.durationMs,
                    "",
                    "",
                    tool.success ? "" : "tool",
                    tool.success ? "" : tool.response
            ).format());
            processing.set(false);
            updateState("RESPONDING");
            speakAndMaybeListen(tool.response, false);
            return;
        }

        if (!runtime.backend().isLoaded()) {
            String fallback = casualFallback(clean);
            appendVoiceChat(clean, fallback, null);
            processing.set(false);
            updateState("RESPONDING");
            speakAndMaybeListen(fallback, false);
            return;
        }

        List<ChatMessage> messages = buildModelContext(clean);
        long started = System.currentTimeMillis();
        StringBuilder response = new StringBuilder();
        runtime.backend().generate(messages, new GenerationConfig(), new LocalModelBackend.GenerateCallback() {
            @Override public void onToken(String token) {
                response.append(token);
                main.post(() -> responseView.setText(response.toString()));
            }
            @Override public void onComplete() {
                long total = System.currentTimeMillis() - started;
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), runtime.backend().diagnostics(), "", "");
                String text = response.toString().trim();
                if (text.isEmpty()) text = "I got nothing back from the local model.";
                appendVoiceChat(clean, text, diag.format());
                main.post(() -> {
                    processing.set(false);
                    updateState("RESPONDING");
                    responseView.setText(text);
                });
                speakAndMaybeListen(text, false);
            }
            @Override public void onError(Exception error) {
                long total = System.currentTimeMillis() - started;
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), runtime.backend().diagnostics(),
                        "generation", error.getMessage());
                String failure = "I couldn't generate that response yet, bro. Runtime was "
                        + total + " ms.";
                appendVoiceChat(clean, failure, diag.format());
                main.post(() -> {
                    processing.set(false);
                    updateState("ERROR");
                    responseView.setText(failure + "\n\n" + diag.format());
                });
                speakAndMaybeListen(failure, false);
            }
        });
    }

    private List<ChatMessage> buildModelContext(String current) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        String memory = runtime.preferences().getMemory();
        String system = "You are Sync//AI, a concise offline Android companion. "
                + "Talk naturally and casually. Avoid robotic disclaimers. "
                + "The phone's deterministic tools handle device commands. "
                + "User language may include slang, typos, shorthand, and bro/bfam wording.";
        if (!memory.trim().isEmpty()) system += "\nRelevant user memory:\n" + memory.trim();
        messages.add(new ChatMessage(ChatMessage.Role.SYSTEM, system));
        ChatRecord active = activeChat();
        int start = Math.max(0, active.messages.size() - 8);
        for (int i = start; i < active.messages.size(); i++) {
            ChatMessage m = active.messages.get(i);
            messages.add(new ChatMessage(m.role, m.text));
        }
        messages.add(new ChatMessage(ChatMessage.Role.USER, current));
        return messages;
    }

    private ChatRecord activeChat() {
        ChatStore store = runtime.chatStore();
        String id = runtime.preferences().getActiveChatId();
        ChatRecord chat = store.get(id);
        if (chat != null) return chat;
        chat = store.create();
        runtime.preferences().setActiveChatId(chat.id);
        return chat;
    }

    private void appendVoiceChat(String user, String assistant, String diagnostics) {
        ChatRecord chat = activeChat();
        runtime.chatStore().maybeTitle(chat, user);
        runtime.chatStore().add(chat, new ChatMessage(ChatMessage.Role.USER, user));
        runtime.chatStore().add(chat, new ChatMessage(
                ChatMessage.Role.ASSISTANT, assistant, System.currentTimeMillis(), diagnostics));
    }

    private String casualFallback(String text) {
        String l = text.toLowerCase(Locale.US).trim();
        if (l.matches("^(yo|hey|hi|hello|sup|wassup|what's up|whats up)[!. ]*$")) {
            return "Yo bro 😭 I'm here. Load a GGUF model when you want the full offline brain.";
        }
        if (l.contains("how are you") || l.contains("how ya doing") || l.contains("how are u")) {
            return "I'm good bro. Local Sync is alive and kicking.";
        }
        return "I'm here, bro. Import a local GGUF model for full conversational replies.";
    }

    private String currentModelName() {
        ModelInfo model = runtime.modelManager().getLoadedModel();
        return model == null ? "" : model.name;
    }

    private void speakAndMaybeListen(String text, boolean exitAfter) {
        if (exitAfter || !runtime.preferences().isVoiceOutputEnabled()) {
            if (exitAfter) {
                main.postDelayed(this::exit, 450);
            } else {
                main.postDelayed(() -> {
                    processing.set(false);
                    if (!destroyed) startListening();
                }, 650);
            }
            return;
        }
        if (!ttsReady) {
            processing.set(false);
            main.postDelayed(() -> {
                if (!destroyed) startListening();
            }, 650);
            return;
        }
        String utteranceId = "sync-" + System.currentTimeMillis();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    private void exit() {
        processing.set(false);
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
        hide();
        finish();
    }

    private void updateState(String text) {
        main.post(() -> {
            if (!destroyed && stateView != null) stateView.setText(text);
        });
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private Button smallButton(String value) {
        Button b = new Button(getContext());
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(22);
        b.setAllCaps(false);
        b.setBackground(round(0x40262A3A, 40));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    @Override public void onHide() {
        super.onHide();
        cleanup();
    }

    @Override public void onDestroy() {
        destroyed = true;
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        setKeepAwake(false);
    }
}
