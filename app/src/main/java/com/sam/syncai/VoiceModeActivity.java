package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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

public final class VoiceModeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SyncRuntime runtime;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private TextView stateView;
    private TextView transcriptView;
    private TextView responseView;
    private ScrollView responseScroll;
    private LinearLayout transcriptContainer;
    private View micButton;
    private View panelView;
    private android.animation.ValueAnimator micPulse;
    private boolean exiting;
    private int accent;
    private boolean ttsReady;
    private boolean destroyed;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    @Override protected void onCreate(Bundle state) {
        SyncEventLogger.install(this);
        super.onCreate(state);
        SyncEventLogger.record(this, "VoiceModeActivity", "onCreate", "INFO",
                "savedState=" + (state != null) + " taskId=" + getTaskId()
                        + " intent=" + getIntent());
        android.util.Log.d("SyncAI", "VoiceModeActivity.onCreate savedState="
                + (state != null) + " taskId=" + getTaskId()
                + " intent=" + getIntent());
        if (SyncAssistantService.isRunning()) {
            sendBroadcast(new Intent(SyncAssistantService.ACTION_VOICE_MODE_STARTED)
                    .setPackage(getPackageName()));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        runtime = SyncRuntime.get(this);
        accent = AppPreferences.ACCENTS[runtime.preferences().getAccent()];
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View content = onCreateContentView();
        setContentView(content);
        initSpeech();
        initTts();
        restoreSelectedModel();
        startListeningOrRequestPermission();
    }

    private View onCreateContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB30A0E1A);
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xCC000000);
        panel.setBackground(bg);
        panel.setElevation(dp(20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("SYNC AI", 18, Color.WHITE, true);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(35), 1));

        Button close = smallButton("×");
        close.setOnClickListener(v -> exit());
        Motion.pressable(close);
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(42)));
        panel.addView(top);

        stateView = text("LISTENING", 11, accent, true);
        stateView.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.addView(stateView, new LinearLayout.LayoutParams(-1, dp(26)));

        micButton = new Button(this);
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
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        micLp.gravity = Gravity.CENTER_HORIZONTAL;
        micLp.topMargin = dp(8);
        micLp.bottomMargin = dp(10);
        panel.addView(micButton, micLp);

        transcriptView = text("Listening for your voice…", 12, Color.rgb(175, 185, 207), false);
        transcriptView.setGravity(Gravity.CENTER);
        transcriptView.setMaxLines(2);
        transcriptView.setPadding(dp(6), dp(4), dp(6), dp(8));
        panel.addView(transcriptView);

        responseScroll = new ScrollView(this);
        responseScroll.setFillViewport(false);
        responseScroll.setClipToPadding(false);
        transcriptContainer = new LinearLayout(this);
        transcriptContainer.setOrientation(LinearLayout.VERTICAL);
        transcriptContainer.setPadding(dp(2), dp(4), dp(2), dp(6));
        responseScroll.addView(transcriptContainer,
                new ScrollView.LayoutParams(-1, -2));
        responseView = null;
        panel.addView(responseScroll, new LinearLayout.LayoutParams(-1, dp(135)));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(330), dp(385), Gravity.CENTER);
        root.addView(panel, panelLp);
        panelView = panel;
        if (!Motion.reduced(this)) {
            panel.setAlpha(0f); panel.setTranslationY(dp(28)); panel.setScaleX(0.94f); panel.setScaleY(0.94f);
            panel.post(() -> panel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(Motion.SLOW).setInterpolator(Motion.EASE_OUT).start());
        }
        return root;
    }

    @Override protected void onStart() {
        super.onStart();
        SyncEventLogger.record(this, "VoiceModeActivity", "onStart", "INFO",
                "taskId=" + getTaskId());
        android.util.Log.d("SyncAI", "VoiceModeActivity.onStart taskId=" + getTaskId()
                + " intent=" + getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        SyncEventLogger.record(this, "VoiceModeActivity", "onResume", "INFO",
                "taskId=" + getTaskId());
        android.util.Log.d("SyncAI", "VoiceModeActivity.onResume taskId=" + getTaskId());
    }

    @Override protected void onPause() {
        SyncEventLogger.record(this, "VoiceModeActivity", "onPause", "INFO",
                "taskId=" + getTaskId());
        android.util.Log.d("SyncAI", "VoiceModeActivity.onPause taskId=" + getTaskId());
        super.onPause();
    }

    @Override protected void onStop() {
        SyncEventLogger.record(this, "VoiceModeActivity", "onStop", "INFO",
                "taskId=" + getTaskId());
        android.util.Log.d("SyncAI", "VoiceModeActivity.onStop finishing=" + isFinishing()
                + " taskId=" + getTaskId());
        super.onStop();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        SyncEventLogger.recordIntent(this, "VoiceModeActivity", "onNewIntent", intent);
        android.util.Log.d("SyncAI", "VoiceModeActivity.onNewIntent intent=" + intent);
    }

    private void initSpeech() {
        if (recognizer != null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateState("SPEECH UNAVAILABLE");
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                SyncEventLogger.record(VoiceModeActivity.this, "SpeechRecognizer",
                        "onReadyForSpeech", "INFO", "params=" + params);
                updateState("LISTENING");
            }
            @Override public void onBeginningOfSpeech() {
                SyncEventLogger.record(VoiceModeActivity.this, "SpeechRecognizer",
                        "onBeginningOfSpeech", "INFO", "");
                updateState("LISTENING…");
            }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                SyncEventLogger.record(VoiceModeActivity.this, "SpeechRecognizer",
                        "onEndOfSpeech", "INFO", "");
                updateState("THINKING");
            }
            @Override public void onError(int error) {
                SyncEventLogger.record(VoiceModeActivity.this, "SpeechRecognizer",
                        "onError", "ERROR", "code=" + error);
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
                SyncEventLogger.record(VoiceModeActivity.this, "SpeechRecognizer",
                        "onResults", "INFO",
                        "hasResults=" + (values != null && !values.isEmpty()));
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
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            SyncEventLogger.record(VoiceModeActivity.this, "TextToSpeech",
                    "INITIALIZED", ttsReady ? "INFO" : "ERROR",
                    "status=" + status);
            if (ttsReady) {
                tts.setLanguage(Locale.getDefault());
                tts.setSpeechRate(1.03f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        SyncEventLogger.record(VoiceModeActivity.this, "TextToSpeech",
                                "UTTERANCE_DONE", "INFO", "id=" + id);
                        main.postDelayed(() -> {
                            processing.set(false);
                            if (!destroyed) startListening();
                        }, 350);
                    }
                    @Override public void onError(String id) {
                        SyncEventLogger.record(VoiceModeActivity.this, "TextToSpeech",
                                "UTTERANCE_ERROR", "ERROR", "id=" + id);
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
                main.post(() -> showResponse(
                        "Model load failed; deterministic tools remain available."));
            }
        });
    }

    private void startListeningOrRequestPermission() {
        if (this.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateState("MIC PERMISSION");
            transcriptView.setText("Sync needs microphone access only while Voice Mode is active.");
            try {
                startActivity(new Intent(this, MicPermissionActivity.class));
            } catch (Exception e) {
                showResponse("Open Sync AI once and enable microphone permission in Android Settings.");
            }
            main.postDelayed(() -> {
                if (!destroyed && this.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) startListening();
            }, 900);
            return;
        }
        startListening();
    }

    private void startListening() {
        SyncEventLogger.record(this, "SpeechRecognizer", "START_LISTENING_ATTEMPT",
                "INFO", "destroyed=" + destroyed);
        if (destroyed || recognizer == null ||
                this.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
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
            showResponse(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void handleVoiceText(String text) {
        if (text == null || text.trim().isEmpty()) {
            startListening();
            return;
        }
        String clean = text.trim();
        SyncEventLogger.record(this, "VoiceModeActivity", "VOICE_TEXT_RECEIVED", "INFO",
                "length=" + clean.length());
        transcriptView.setText("Processing your message…");
        addTranscriptEntry("YOU", clean, true);
        responseView = addTranscriptEntry("SYNC AI", "Thinking…", false);

        String lower = clean.toLowerCase(Locale.US);
        if (lower.matches(".*\\b(?:stop listening|goodbye|exit|cancel|close sync|that's all|thats all)\\b.*")) {
            appendVoiceChat(clean, "Alright bro.", null);
            speakAndMaybeListen("Alright bro.", true);
            return;
        }

        processing.set(true);
        updateState("THINKING");
        ToolEngine.Result tool = runtime.tools().handle(clean);
        SyncEventLogger.record(this, "ToolEngine", "VOICE_ROUTE_RESULT", "INFO",
                "handled=" + tool.handled + " tool=" + tool.toolName
                        + " success=" + tool.success + " durationMs=" + tool.durationMs);
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
            showResponse(tool.response);
            speakAndMaybeListen(tool.response, false);
            return;
        }

        if (!runtime.backend().isLoaded()) {
            String fallback = casualFallback(clean);
            appendVoiceChat(clean, fallback, null);
            processing.set(false);
            updateState("RESPONDING");
            showResponse(fallback);
            speakAndMaybeListen(fallback, false);
            return;
        }

        List<ChatMessage> messages = buildModelContext(clean);
        long started = System.currentTimeMillis();
        SyncEventLogger.record(this, "GgufModelBackend", "VOICE_GENERATION_START",
                "INFO", "model=" + currentModelName());
        StringBuilder response = new StringBuilder();
        runtime.backend().generate(messages, new GenerationConfig(), new LocalModelBackend.GenerateCallback() {
            @Override public void onToken(String token) {
                response.append(token);
                main.post(() -> {
                    if (responseView != null) responseView.setText(response.toString());
                    scrollTranscriptToBottom();
                });
            }
            @Override public void onComplete() {
                long total = System.currentTimeMillis() - started;
                SyncEventLogger.record(VoiceModeActivity.this, "GgufModelBackend",
                        "VOICE_GENERATION_COMPLETE", "INFO",
                        "totalMs=" + total + " responseChars=" + response.length());
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), runtime.backend() instanceof GgufModelBackend
                                ? ((GgufModelBackend) runtime.backend()).lastGenerationDiagnostics()
                                : "Inference completed.", "", "");
                String generated = response.toString().trim();
                final String finalText = generated.isEmpty()
                        ? "I got nothing back from the local model."
                        : generated;
                appendVoiceChat(clean, finalText, diag.format());
                main.post(() -> {
                    processing.set(false);
                    updateState("RESPONDING");
                    if (responseView != null) responseView.setText(finalText);
                    scrollTranscriptToBottom();
                });
                speakAndMaybeListen(finalText, false);
            }
            @Override public void onError(Exception error) {
                long total = System.currentTimeMillis() - started;
                SyncEventLogger.recordException(VoiceModeActivity.this,
                        "GgufModelBackend", "VOICE_GENERATION_ERROR", error,
                        "totalMs=" + total);
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), "Inference error callback.",
                        "generation", error.getMessage());
                String failure = "I couldn't generate that response yet, bro. Runtime was "
                        + total + " ms.";
                appendVoiceChat(clean, failure, diag.format());
                main.post(() -> {
                    processing.set(false);
                    updateState("ERROR");
                    if (responseView != null) responseView.setText(failure + "\n\n" + diag.format());
                    scrollTranscriptToBottom();
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

    private TextView addTranscriptEntry(String speaker, String message, boolean fromUser) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(10), dp(7), dp(10), dp(8));
        bubble.setBackground(round(fromUser ? 0x443C6EA8 : 0x443D3266, 16));
        TextView heading = text(speaker, 10, fromUser ? 0xFFB9D8FF : accent, true);
        TextView body = text(message == null ? "" : message, 13, Color.WHITE, false);
        body.setMaxWidth(dp(270));
        bubble.addView(heading);
        bubble.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = fromUser ? Gravity.END : Gravity.START;
        lp.bottomMargin = dp(7);
        transcriptContainer.addView(bubble, lp);
        scrollTranscriptToBottom();
        return body;
    }

    private void scrollTranscriptToBottom() {
        if (responseScroll != null) {
            responseScroll.post(() -> responseScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showResponse(String text) {
        main.post(() -> {
            if (destroyed) return;
            if (responseView == null && transcriptContainer != null) {
                responseView = addTranscriptEntry("SYNC AI", "", false);
            }
            if (responseView == null) return;
            responseView.setText(text == null ? "" : text);
            scrollTranscriptToBottom();
        });
    }

    private void speakAndMaybeListen(String text, boolean exitAfter) {
        if (exitAfter) {
            main.postDelayed(this::exit, 450);
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
        SyncEventLogger.record(this, "VoiceModeActivity", "EXIT_REQUEST", "INFO",
                "exiting=" + exiting);
        android.util.Log.d("SyncAI", "VoiceModeActivity.exit requested");
        processing.set(false);
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
        stopMicPulse();
        if (exiting) return;
        exiting = true;
        if (panelView == null || destroyed || Motion.reduced(this)) {
            finish(); return;
        }
        panelView.animate().alpha(0f).translationY(dp(20)).scaleX(0.96f).scaleY(0.96f)
                .setDuration(Motion.FAST + 40).setInterpolator(Motion.EASE_IN)
                .withEndAction(() -> { if (!destroyed) finish(); }).start();
    }

    private void updateState(String text) {
        main.post(() -> {
            if (destroyed || stateView == null) return;
            Motion.swapText(stateView, text);
            applyStateMotion(text);
        });
    }

    private void applyStateMotion(String state) {
        if (micButton == null) return;
        if (state.startsWith("LISTENING")) {
            micButton.animate().alpha(1f).setDuration(Motion.FAST).start();
            startMicPulse();
        } else if (state.equals("THINKING") || state.equals("RESPONDING") || state.equals("LOADING MODEL")) {
            stopMicPulse();
            micButton.animate().alpha(0.55f).setDuration(Motion.NORMAL).start();
        } else {
            stopMicPulse();
            micButton.animate().alpha(1f).setDuration(Motion.FAST).start();
        }
    }

    private void startMicPulse() {
        if (micPulse != null || Motion.reduced(this)) return;
        micPulse = android.animation.ValueAnimator.ofFloat(1f, 1.07f);
        micPulse.setDuration(1100);
        micPulse.setInterpolator(Motion.EASE_IN_OUT);
        micPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        micPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        micPulse.addUpdateListener(a -> {
            if (micButton == null) return;
            float s = (float) a.getAnimatedValue();
            micButton.setScaleX(s); micButton.setScaleY(s);
        });
        micPulse.start();
    }

    private void stopMicPulse() {
        if (micPulse != null) { micPulse.cancel(); micPulse = null; }
        if (micButton != null) micButton.animate().scaleX(1f).scaleY(1f).setDuration(Motion.FAST).start();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private Button smallButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(22);
        b.setAllCaps(false);
        b.setBackground(round(0x40262A3A, 40));
        Motion.pressable(b);
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * this.getResources().getDisplayMetrics().density);
    }

    @Override public void onDestroy() {
        SyncEventLogger.record(this, "VoiceModeActivity", "onDestroy", "INFO",
                "finishing=" + isFinishing() + " changingConfigurations="
                        + isChangingConfigurations() + " taskId=" + getTaskId());
        android.util.Log.d("SyncAI", "VoiceModeActivity.onDestroy finishing="
                + isFinishing() + " changingConfigurations=" + isChangingConfigurations()
                + " taskId=" + getTaskId());
        destroyed = true;
        if (SyncAssistantService.isRunning()) {
            sendBroadcast(new Intent(SyncAssistantService.ACTION_VOICE_MODE_FINISHED)
                    .setPackage(getPackageName()));
        }
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        stopMicPulse();
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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
