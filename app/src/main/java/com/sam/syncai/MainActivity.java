package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Rect;
import android.view.Gravity;
import android.service.voice.VoiceInteractionService;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_IMPORT_MODEL = 1201;
    private static final int REQ_IMPORT_MEMORY = 1202;
    private static final int REQ_PERMISSIONS = 1203;
    private static final int REQ_EXPORT_CHAT = 1204;
    private static final int REQ_EXPORT_LOGS = 1205;

    private String pendingChatExport;
    private String pendingLogExport;

    private static final int BG = Color.rgb(7, 8, 14);
    private static final int SURFACE = Color.rgb(15, 18, 28);
    private static final int SURFACE_2 = Color.rgb(21, 25, 38);
    private static final int TEXT = Color.rgb(240, 242, 250);
    private static final int MUTED = Color.rgb(145, 153, 177);
    private static final int ERROR = Color.rgb(255, 130, 145);

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private SyncRuntime runtime;
    private ChatRecord activeChat;
    private TextView modelText;
    private TextView statusText;
    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText input;
    private Button sendButton;
    private Button importButton;
    private ProgressBar progress;
    private TextView activeAssistantBubble;
    private LinearLayout root;
    private int accent;
    private SideDashboard sideDashboard;
    private FrameLayout rootFrame;
    private View composerCard;
    private int activeGenerations;
    private boolean modelBusy;
    private LinearLayout contentRootView;
    private int statusInset;
    private boolean edgeTracking;
    private boolean edgeDragging;
    private float edgeStartX;
    private float edgeStartY;
    private VelocityTracker edgeVelocity;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SyncEventLogger.record(this, "MainActivity", "onCreate", "INFO",
                "savedState=" + (state != null) + " taskId=" + getTaskId());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        runtime = SyncRuntime.get(this);
        accent = AppPreferences.ACCENTS[runtime.preferences().getAccent()];
        activeChat = getActiveChat();
        buildUi();
        renderChat();
        restoreLoadedModel();
    }

    @Override protected void onStart() {
        super.onStart();
        SyncEventLogger.record(this, "MainActivity", "onStart", "INFO",
                "taskId=" + getTaskId());
    }

    @Override protected void onResume() {
        super.onResume();
        SyncEventLogger.record(this, "MainActivity", "onResume", "INFO",
                "taskId=" + getTaskId());
        if (runtime != null) refreshStatus();
    }

    @Override protected void onPause() {
        SyncEventLogger.record(this, "MainActivity", "onPause", "INFO",
                "taskId=" + getTaskId());
        super.onPause();
    }

    @Override protected void onStop() {
        SyncEventLogger.record(this, "MainActivity", "onStop", "INFO",
                "taskId=" + getTaskId());
        super.onStop();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        SyncEventLogger.recordIntent(this, "MainActivity", "onNewIntent", intent);
    }

    private ChatRecord getActiveChat() {
        ChatStore store = runtime.chatStore();
        ChatRecord chat = store.get(runtime.preferences().getActiveChatId());
        if (chat == null) {
            chat = store.create();
            runtime.preferences().setActiveChatId(chat.id);
        }
        return chat;
    }

    private void buildUi() {
        LinearLayout contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setPadding(dp(16), dp(14), dp(16), dp(10));
        contentRoot.setBackgroundColor(BG);
        contentRootView = contentRoot;

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button menuButton = actionButton("☰");
        menuButton.setTextSize(20);
        menuButton.setOnClickListener(v -> {
            if (sideDashboard != null) sideDashboard.open();
        });
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("SYNC AI", 25, TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        contentRoot.addView(header);

        LinearLayout statusCard = card();
        LinearLayout statusInner = new LinearLayout(this);
        statusInner.setOrientation(LinearLayout.VERTICAL);
        statusInner.setPadding(dp(14), dp(10), dp(14), dp(10));
        modelText = text("NO MODEL", 13, TEXT, true);
        statusText = text("LOCAL RUNTIME • IMPORT A GGUF MODEL", 11, MUTED, false);
        statusInner.addView(modelText);
        statusInner.addView(statusText, new LinearLayout.LayoutParams(-1, dp(24)));
        statusCard.addView(statusInner);
        contentRoot.addView(statusCard, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(0, dp(8), 0, dp(7));
        importButton = actionButton("IMPORT MODEL");
        importButton.setOnClickListener(v -> openModelPicker());
        controls.addView(importButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button runtimeButton = actionButton("RUNTIME");
        runtimeButton.setOnClickListener(v -> showRuntimeInfo());
        LinearLayout.LayoutParams runtimeLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        runtimeLp.leftMargin = dp(7);
        controls.addView(runtimeButton, runtimeLp);
        contentRoot.addView(controls);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        showProgress(false);
        contentRoot.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        messageContainer = new LinearLayout(this);
        messageContainer.setOrientation(LinearLayout.VERTICAL);
        messageContainer.setPadding(dp(2), dp(12), dp(2), dp(12));
        chatScroll.addView(messageContainer);
        contentRoot.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composerCard = card();
        this.composerCard = composerCard;
        composerCard.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);

        input = new EditText(this);
        input.setHint("Message Sync AI…");
        input.setHintTextColor(Color.rgb(92, 100, 123));
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(14), dp(11), dp(14), dp(10));
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setSingleLine(false);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        input.setBackground(round(SURFACE_2, dp(15)));
        composer.addView(input, new LinearLayout.LayoutParams(0, dp(56), 1));

        Button composerVoice = actionButton("VOICE");
        composerVoice.setOnClickListener(v -> launchVoiceMode());
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(dp(68), dp(56));
        voiceLp.leftMargin = dp(7);
        composer.addView(composerVoice, voiceLp);

        sendButton = actionButton("SEND");
        sendButton.setOnClickListener(v -> sendMessage());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(76), dp(56));
        sendLp.leftMargin = dp(7);
        composer.addView(sendButton, sendLp);

        composerCard.addView(composer);
        contentRoot.addView(composerCard, new LinearLayout.LayoutParams(-1, dp(70)));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(contentRoot, new FrameLayout.LayoutParams(-1, -1));

        sideDashboard = new SideDashboard(this, frame, new SideDashboard.Actions() {
            @Override public void newChat() { MainActivity.this.newChat(); }
            @Override public void chats() { showChatsDialog(); }
            @Override public void models() { showModelsDialog(); }
            @Override public void importModel() { openModelPicker(); }
            @Override public void runtime() { showRuntimeInfo(); }
            @Override public void memory() { showPersonalization(); }
            @Override public void personalization() { showPersonalization(); }
            @Override public void tone() { showSettings(); }
            @Override public void about() { showAbout(); }
            @Override public void assistant() { launchVoiceMode(); }
            @Override public void files() {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQ_IMPORT_MEMORY);
            }
            @Override public void canvas() { showSettings(); }
            @Override public void toggleVoiceOutput() { showSettings(); }
        });

        rootFrame = frame;
        setContentView(frame);
        rootFrame.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
            int imeBottom = ime.bottom;
            int topNow = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(android.view.WindowInsets.Type.statusBars()
                            | android.view.WindowInsets.Type.displayCutout()).top
                    : insets.getSystemWindowInsetTop();
            applyTopInset(topNow);
            composerCard.setTranslationY(-imeBottom);
            chatScroll.setPadding(
                    chatScroll.getPaddingLeft(),
                    chatScroll.getPaddingTop(),
                    chatScroll.getPaddingRight(),
                    dp(12) + composerCard.getHeight() + imeBottom
            );
            if (imeBottom > 0) chatScroll.post(this::scrollToBottom);
            return insets;
        });
        rootFrame.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        applyAccent();
        installEdgeGestureExclusion();
        playStartupMotion(contentRoot);
    }

    private void applyTopInset(int top) {
        if (top == statusInset || contentRootView == null) return;
        statusInset = top;
        contentRootView.setPadding(dp(16), dp(14) + top + dp(8), dp(16), dp(10));
        if (sideDashboard != null) sideDashboard.setTopInset(top);
    }

    private void playStartupMotion(LinearLayout contentRoot) {
        if (Motion.reduced(this)) return;
        float dy = dp(12);
        Motion.enter(contentRoot.getChildAt(0), 0, dy);
        Motion.enter(contentRoot.getChildAt(1), 50, dy);
        Motion.enter(contentRoot.getChildAt(2), 100, dy);
        Motion.enter(chatScroll, 150, 0);
        Motion.enter(composerCard, 200, 0);
    }

    private void installEdgeGestureExclusion() {
        if (Build.VERSION.SDK_INT < 29) return;
        rootFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int top = (int) (h * 0.30f);
            Rect edge = new Rect(0, top, dp(40), top + dp(200));
            rootFrame.setSystemGestureExclusionRects(java.util.Collections.singletonList(edge));
        });
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (sideDashboard != null && handleEdgeSwipe(ev)) return true;
        return super.dispatchTouchEvent(ev);
    }

    private boolean handleEdgeSwipe(MotionEvent ev) {
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                edgeTracking = !sideDashboard.isOpen() && ev.getRawX() <= dp(40);
                edgeDragging = false;
                if (edgeTracking) {
                    edgeStartX = ev.getRawX(); edgeStartY = ev.getRawY();
                    if (edgeVelocity != null) edgeVelocity.recycle();
                    edgeVelocity = VelocityTracker.obtain(); edgeVelocity.addMovement(ev);
                }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!edgeTracking) return false;
                edgeVelocity.addMovement(ev);
                float dx = ev.getRawX() - edgeStartX;
                float dy = Math.abs(ev.getRawY() - edgeStartY);
                if (!edgeDragging) {
                    if (dx > slop && dx > dy * 1.35f) {
                        edgeDragging = true;
                        MotionEvent cancel = MotionEvent.obtain(ev); cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel); cancel.recycle();
                        hideKeyboard(); sideDashboard.beginDrag();
                    } else if (dy > slop * 2 && dy > dx) { edgeTracking = false; return false; }
                    else return false;
                }
                sideDashboard.dragTo(dx); return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean wasDragging = edgeDragging;
                if (edgeTracking && edgeVelocity != null) {
                    edgeVelocity.addMovement(ev); edgeVelocity.computeCurrentVelocity(1000);
                    if (wasDragging) sideDashboard.endDrag(edgeVelocity.getXVelocity());
                    edgeVelocity.recycle(); edgeVelocity = null;
                }
                edgeTracking = false; edgeDragging = false; return wasDragging;
            }
            default: return edgeDragging;
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(round(SURFACE, dp(16)));
        return layout;
    }

    private Button dashboardButton(String label) {
        Button b = actionButton(label);
        b.setTextSize(9.5f);
        b.setMinHeight(dp(44));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(5);
        b.setLayoutParams(lp);
        return b;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(10.5f);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setBackground(round(Color.rgb(28, 33, 49), dp(13)));
        Motion.pressable(b);
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void applyAccent() {
        if (sendButton != null) {
            sendButton.setBackground(round(accent, dp(13)));
            sendButton.setTextColor(Color.WHITE);
        }
        if (importButton != null) importButton.setTextColor(TEXT);
    }

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_MODEL);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        SyncEventLogger.record(this, "MainActivity", "onActivityResult", "INFO",
                "requestCode=" + requestCode + " resultCode=" + resultCode
                        + " hasData=" + (data != null));
        if (requestCode == REQ_IMPORT_MODEL && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importModel(uri);
            return;
        }
        if (requestCode == REQ_IMPORT_MEMORY && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importMemory(uri);
            return;
        }
        if (requestCode == REQ_EXPORT_CHAT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && pendingChatExport != null) {
                SyncEventLogger.record(this, "MainActivity", "CHAT_EXPORT_WRITE", "INFO",
                        "uri=" + uri);
                writeChatExport(uri, pendingChatExport);
            }
            pendingChatExport = null;
            return;
        }
        if (requestCode == REQ_EXPORT_LOGS && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && pendingLogExport != null) {
                SyncEventLogger.record(this, "MainActivity", "LOG_EXPORT_WRITE", "INFO",
                        "uri=" + uri);
                writeChatExport(uri, pendingLogExport);
            }
            pendingLogExport = null;
        }
    }

    private void importModel(Uri uri) {
        setBusy(true);
        showProgress(true);
        ioExecutor.execute(() -> {
            try {
                String id = runtime.modelManager().importModel(uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    showProgress(false);
                    ModelInfo model = findModel(id);
                    if (model != null) loadModel(model);
                });
            } catch (Exception e) {
                SyncEventLogger.recordException(this, "ModelManager", "MODEL_IMPORT_ERROR",
                        e, "uri=" + uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    showProgress(false);
                    showError("Import failed", e);
                });
            }
        });
    }

    private void importMemory(Uri uri) {
        ioExecutor.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Could not open that file.");
                byte[] data = new byte[1024 * 1024];
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int read;
                while ((read = in.read(data)) != -1) out.write(data, 0, read);
                String memory = new String(out.toByteArray(), StandardCharsets.UTF_8);
                runtime.preferences().setMemory(memory);
                runOnUiThread(() -> {
                    showToast("Memory imported into Personalization.");
                    showSettings();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Memory import failed", e));
            }
        });
    }

    private ModelInfo findModel(String id) {
        for (ModelInfo model : runtime.modelManager().getModels()) {
            if (model.id.equals(id)) return model;
        }
        return null;
    }

    private void restoreLoadedModel() {
        ModelInfo model = runtime.modelManager().getLoadedModel();
        if (model != null && !runtime.backend().isLoaded()) loadModel(model);
        else refreshStatus();
    }

    private void loadModel(ModelInfo model) {
        setBusy(true);
        showProgress(true);
        modelText.setText("LOADING • " + model.name);
        statusText.setText("GGUF • INITIALIZING LOCAL CPU RUNTIME…");
        SyncEventLogger.record(this, "ModelManager", "MODEL_LOAD_START", "INFO",
                "model=" + model.name + " id=" + model.id);

        runtime.backend().load(model, new LocalModelBackend.LoadCallback() {
            @Override public void onLoaded() {
                runtime.modelManager().markLoaded(model.id);
                runOnUiThread(() -> {
                    showProgress(false);
                    setBusy(false);
                    refreshStatus();
                    SyncEventLogger.record(this, "ModelManager", "MODEL_LOAD_COMPLETE", "INFO",
                            "model=" + model.name + " id=" + model.id);
                    showToast("Loaded " + model.name);
                });
            }

            @Override public void onError(Exception error) {
                runtime.modelManager().clearLoaded();
                runOnUiThread(() -> {
                    showProgress(false);
                    setBusy(false);
                    refreshStatus();
                    SyncEventLogger.recordException(this, "ModelManager", "MODEL_LOAD_ERROR",
                            error, "model=" + model.name + " id=" + model.id);
                    showError("Model load failed", error);
                });
            }
        });
    }

    private void sendMessage() {
        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        input.setText("");
        input.requestFocus();
        SyncEventLogger.record(this, "MainActivity", "MESSAGE_SUBMITTED", "INFO",
                "chatId=" + activeChat.id + " length=" + message.length());
        runtime.chatStore().maybeTitle(activeChat, message);
        ChatMessage user = new ChatMessage(ChatMessage.Role.USER, message);
        runtime.chatStore().add(activeChat, user);
        addMessageView(user, true);

        // Keep SEND usable while native inference runs. GgufModelBackend serializes
        // generations on its single executor, so follow-up messages are queued.
        Motion.setEnabledAnimated(importButton, false);

        long started = System.currentTimeMillis();
        ToolEngine.Result tool = runtime.tools().handle(message);
        SyncEventLogger.record(this, "ToolEngine", "ROUTE_RESULT", "INFO",
                "handled=" + tool.handled + " tool=" + tool.toolName
                        + " success=" + tool.success + " durationMs=" + tool.durationMs);
        if (tool.handled) {
            DiagnosticRecord diag = new DiagnosticRecord(
                    started, tool.durationMs, "DETERMINISTIC TOOL",
                    tool.toolName, tool.durationMs, "", "", "",
                    tool.success ? "" : tool.response);
            ChatMessage assistant = new ChatMessage(
                    ChatMessage.Role.ASSISTANT, tool.response,
                    System.currentTimeMillis(), diag.format());
            runtime.chatStore().add(activeChat, assistant);
            addMessageView(assistant, true);
            setBusy(false);
            requestToolPermissionIfNeeded(tool);
            return;
        }

        String fallback = casualFallback(message);
        if (!runtime.backend().isLoaded()) {
            DiagnosticRecord diag = new DiagnosticRecord(
                    started, System.currentTimeMillis() - started,
                    "LOCAL FALLBACK", "", 0, "", "", "", "");
            ChatMessage assistant = new ChatMessage(
                    ChatMessage.Role.ASSISTANT, fallback,
                    System.currentTimeMillis(), diag.format());
            runtime.chatStore().add(activeChat, assistant);
            addMessageView(assistant, true);
            setBusy(false);
            return;
        }

        final TextView streamingBubble = addMessageView(
                new ChatMessage(ChatMessage.Role.ASSISTANT, "Thinking…"), true);
        streamingBubble.setTextColor(MUTED);
        activeGenerations++;
        SyncEventLogger.record(this, "GgufModelBackend", "GENERATION_START", "INFO",
                "chatId=" + activeChat.id + " generationIndex=" + activeGenerations
                        + " model=" + currentModelName());

        List<ChatMessage> context = buildModelContext(message);
        GenerationConfig config = new GenerationConfig();
        StringBuilder response = new StringBuilder();

        runtime.backend().generate(context, config, new LocalModelBackend.GenerateCallback() {
            @Override public void onToken(String token) {
                response.append(token);
                runOnUiThread(() -> {
                    streamingBubble.setText(response.toString());
                    streamingBubble.setTextColor(TEXT);
                    scrollToBottom();
                });
            }

            @Override public void onComplete() {
                long total = System.currentTimeMillis() - started;
                SyncEventLogger.record(MainActivity.this, "GgufModelBackend",
                        "GENERATION_COMPLETE", "INFO",
                        "chatId=" + activeChat.id + " totalMs=" + total
                                + " responseChars=" + response.length());
                // Do not call backend().diagnostics() from this callback.
                // llama.cpp invokes the callback while its native generation mutex is held;
                // synchronously requesting diagnostics here would deadlock the executor.
                String answer = response.toString().trim();
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), runtime.backend() instanceof GgufModelBackend
                                ? ((GgufModelBackend) runtime.backend()).lastGenerationDiagnostics()
                                : "Inference completed.", "", "");
                if (answer.isEmpty()) answer = "I got no response from the local model.";
                ChatMessage assistant = new ChatMessage(
                        ChatMessage.Role.ASSISTANT, answer,
                        System.currentTimeMillis(), diag.format());
                runtime.chatStore().add(activeChat, assistant);
                runOnUiThread(() -> {
                    replaceStreamingBubble(streamingBubble, assistant);
                    activeGenerations = Math.max(0, activeGenerations - 1);
                    Motion.setEnabledAnimated(importButton, !modelBusy && activeGenerations == 0);
                    Motion.setEnabledAnimated(sendButton, !modelBusy);
                });
            }

            @Override public void onError(Exception error) {
                long total = System.currentTimeMillis() - started;
                SyncEventLogger.recordException(MainActivity.this, "GgufModelBackend",
                        "GENERATION_ERROR", error,
                        "chatId=" + activeChat.id + " totalMs=" + total);
                // Same deadlock rule as onComplete(): diagnostics must not be queried
                // synchronously from the native callback.
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), "Inference error callback.", 
                        "generation", error.getMessage());
                String failure = "I couldn't generate that response, bro. Runtime: " + total + " ms.";
                ChatMessage assistant = new ChatMessage(
                        ChatMessage.Role.ASSISTANT, failure,
                        System.currentTimeMillis(), diag.format());
                runtime.chatStore().add(activeChat, assistant);
                runOnUiThread(() -> {
                    replaceStreamingBubble(streamingBubble, assistant);
                    activeGenerations = Math.max(0, activeGenerations - 1);
                    Motion.setEnabledAnimated(importButton, !modelBusy && activeGenerations == 0);
                    Motion.setEnabledAnimated(sendButton, !modelBusy);
                });
            }
        });
    }

    private void requestToolPermissionIfNeeded(ToolEngine.Result result) {
        if (result.permission == null || Build.VERSION.SDK_INT < 23) return;
        requestPermissions(new String[]{result.permission}, REQ_PERMISSIONS);
    }

    private List<ChatMessage> buildModelContext(String current) {
        java.util.ArrayList<ChatMessage> messages = new java.util.ArrayList<>();
        String memory = runtime.preferences().getMemory();
        String system = "You are Sync AI, a helpful local Android AI companion. "
                + "Answer the user's actual question directly and naturally. "
                + "Be conversational, concise when the question is simple, and detailed when detail is useful. "
                + "Understand slang, typos, shorthand, and casual bro/bfam wording without forcing it. "
                + "Do not invent facts, actions, tool results, or personal experiences. "
                + "Do not insult the user or make random assumptions about them. "
                + "Do not produce canned replies when the user's question needs a real answer. "
                + "Do not volunteer robotic identity disclaimers unless identity is relevant. "
                + "Deterministic Android tools handle device actions before the model.";
        if (!memory.trim().isEmpty()) {
            String memoryText = memory.trim();
            if (memoryText.length() > 6000) {
                memoryText = memoryText.substring(0, 6000);
            }
            system += "\nRelevant user memory:\n" + memoryText;
        }
        messages.add(new ChatMessage(ChatMessage.Role.SYSTEM, system));
        int start = Math.max(0, activeChat.messages.size() - 8);
        for (int i = start; i < activeChat.messages.size(); i++) {
            ChatMessage m = activeChat.messages.get(i);
            messages.add(new ChatMessage(m.role, m.text));
        }
        return messages;
    }

    private String casualFallback(String message) {
        String l = message.toLowerCase(Locale.US).trim();
        if (l.matches("^(yo|hey|hi|hello|sup|wassup|what'?s up)[!. ]*$")) {
            return "Yo bro 😭 I'm here. Import a GGUF model when you want the full offline brain.";
        }
        if (l.contains("how are you") || l.contains("how ya doing") || l.contains("how are u")) {
            return "I'm good bro. Local Sync is alive and kicking.";
        }
        if (l.contains("who are you") || l.contains("what are you")) {
            return "I'm Sync AI — your local Android AI shell. The model runs on-device.";
        }
        return "I'm here, bro. Import a local GGUF model for full conversational replies.";
    }

    private String currentModelName() {
        ModelInfo model = runtime.modelManager().getLoadedModel();
        return model == null ? "" : model.name;
    }

    private void replaceStreamingBubble(TextView streamingBubble, ChatMessage message) {
        if (streamingBubble != null && streamingBubble.getParent() != null
                && streamingBubble.getParent().getParent() != null) {
            View row = (View) streamingBubble.getParent().getParent();
            messageContainer.removeView(row);
        }
        addMessageView(message);
        scrollToBottom();
    }

    private TextView addMessageView(ChatMessage message) {
        return addMessageView(message, false);
    }

    private TextView addMessageView(ChatMessage message, boolean animate) {
        boolean user = message.role == ChatMessage.Role.USER;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(round(user ? Color.rgb(48, 31, 76) : Color.rgb(18, 22, 33), dp(16)));

        TextView label = text(user ? "YOU" : "SYNC//AI", 10,
                user ? Color.rgb(210, 176, 255) : accent, true);
        bubble.addView(label);

        TextView body = text(message.text, 15, TEXT, false);
        body.setLineSpacing(0, 1.12f);
        bubble.addView(body);

        if (message.hasDiagnostics()) {
            TextView debug = text("⌁ DIAGNOSTICS", 10, MUTED, true);
            debug.setPadding(0, dp(8), 0, 0);
            debug.setOnClickListener(v -> showDiagnostic(message.diagnostics));
            bubble.addView(debug);
        }

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                user ? (int)(getResources().getDisplayMetrics().widthPixels * 0.78f) :
                        (int)(getResources().getDisplayMetrics().widthPixels * 0.9f),
                -2);
        row.addView(bubble, bubbleLp);
        messageContainer.addView(row);
        if (animate && !Motion.reduced(this)) {
            bubble.setAlpha(0f);
            bubble.post(() -> Motion.messageIn(bubble, user));
        }
        scrollToBottom();
        return body;
    }

    private void renderChat() {
        messageContainer.removeAllViews();
        for (ChatMessage message : activeChat.messages) addMessageView(message);
        if (activeChat.messages.isEmpty()) {
            addMessageView(new ChatMessage(
                    ChatMessage.Role.ASSISTANT,
                    "Sync AI is ready.\nImport a GGUF model for local conversation, or use the deterministic Android tools."));
        }
        scrollToBottom();
    }

    private void showChatsDialog() {
        List<ChatRecord> chats = runtime.chatStore().all();
        String[] items = new String[chats.size() + 1];
        items[0] = "＋  New chat";
        for (int i = 0; i < chats.size(); i++) {
            ChatRecord chat = chats.get(i);
            items[i + 1] = (chat.id.equals(activeChat.id) ? "● " : "○ ") + chat.title;
        }

        new SyncDialog.Builder(this, accent)
                .setTitle("CHATS")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        newChat();
                    } else {
                        showChatActions(chats.get(which - 1));
                    }
                })
                .setPositiveButton("EXPORT ALL", (d, w) -> exportAllChats())
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showChatActions(ChatRecord chat) {
        String[] actions = {"Open", "Rename", "Export", "Delete"};
        new SyncDialog.Builder(this, accent)
                .setTitle(chat.title)
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        activeChat = chat;
                        runtime.preferences().setActiveChatId(chat.id);
                        runtime.tools().clearContext();
                        renderChat();
                    } else if (which == 1) {
                        renameChat(chat);
                    } else if (which == 2) {
                        exportChat(chat);
                    } else {
                        deleteChat(chat);
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void exportChat(ChatRecord chat) {
        if (chat == null) return;
        pendingChatExport = formatChatExport(chat);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, exportFileName(chat.title));
        startActivityForResult(intent, REQ_EXPORT_CHAT);
    }

    private void exportAllChats() {
        pendingChatExport = formatAllChatsExport(runtime.chatStore().all());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "sync-ai-chats.txt");
        startActivityForResult(intent, REQ_EXPORT_CHAT);
    }

    private void exportAllDiagnostics() {
        SyncEventLogger.record(this, "MainActivity", "LOG_EXPORT_REQUEST", "INFO",
                "source=About & Diagnostics");
        ioExecutor.execute(() -> {
            StringBuilder out = new StringBuilder();
            out.append("SYNC AI — FULL DEBUG / FLIGHT RECORDER EXPORT\n");
            out.append("==============================================\n\n");
            out.append("Exported: ").append(formatTimestamp(System.currentTimeMillis())).append('\n');
            out.append("App: Sync AI\n");
            out.append("Version: ").append(BuildConfig.VERSION_NAME).append(" (")
                    .append(BuildConfig.VERSION_CODE).append(")\n");
            out.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            out.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ")
                    .append(Build.VERSION.SDK_INT).append(")\n");
            out.append("ABI: ").append(Build.SUPPORTED_ABIS.length == 0
                    ? "unknown" : Build.SUPPORTED_ABIS[0]).append('\n');
            out.append("PID: ").append(android.os.Process.myPid()).append('\n');
            out.append("Active Chat ID: ").append(runtime.preferences().getActiveChatId()).append('\n');
            out.append("Accent: ").append(AppPreferences.ACCENT_NAMES[
                    runtime.preferences().getAccent()]).append('\n\n');

            out.append("=== PERSONALIZATION MEMORY ===\n");
            out.append(runtime.preferences().getMemory()).append("\n\n");

            out.append("=== RUNTIME DIAGNOSTICS ===\n");
            try {
                out.append(runtime.backend().diagnostics());
            } catch (Exception error) {
                out.append("Runtime diagnostics failed: ").append(error).append('\n');
            }

            out.append("\n\n=== LOADED MODEL ===\n");
            ModelInfo loaded = runtime.modelManager().getLoadedModel();
            out.append(loaded == null ? "None\n"
                    : loaded.name + " | " + loaded.format + " | "
                    + loaded.sizeLabel() + " | sha256=" + loaded.sha256 + "\n");

            out.append("\n=== REGISTERED LOCAL MODELS ===\n");
            for (ModelInfo model : runtime.modelManager().getModels()) {
                out.append(model.name).append(" | ")
                        .append(model.format).append(" | ")
                        .append(model.sizeLabel()).append(" | sha256=")
                        .append(model.sha256).append('\n');
            }

            out.append("\n=== LATEST REQUEST DIAGNOSTICS ===\n");
            String latest = latestDiagnostics();
            out.append(latest.isEmpty() ? "No request diagnostics recorded." : latest)
                    .append("\n\n");

            out.append("=== ALL SAVED CHATS + PER-MESSAGE DIAGNOSTICS ===\n");
            out.append(formatAllChatsExport(runtime.chatStore().all())).append("\n");

            out.append("=== PERSISTENT SYNC AI EVENT TIMELINE ===\n");
            out.append(SyncEventLogger.readAll(this)).append("\n");

            out.append("=== CURRENT/BEST-EFFORT ANDROID LOGCAT ===\n");
            out.append(SyncEventLogger.captureLogcat()).append("\n");

            out.append("=== END FULL DEBUG EXPORT ===\n");

            pendingLogExport = out.toString();
            SyncEventLogger.record(this, "MainActivity", "LOG_EXPORT_READY", "INFO",
                    "bytes=" + pendingLogExport.getBytes(StandardCharsets.UTF_8).length);

            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, "sync-ai-full-debug.txt");
                    startActivityForResult(intent, REQ_EXPORT_LOGS);
                } catch (Exception error) {
                    SyncEventLogger.recordException(this, "MainActivity",
                            "LOG_EXPORT_DOCUMENT_PICKER_FAILED", error, "");
                    showError("Log export unavailable", error);
                }
            });
        });
    }

    private String formatChatExport(ChatRecord chat) {
        StringBuilder out = new StringBuilder();
        out.append("SYNC AI CHAT EXPORT\n");
        out.append("===================\n\n");
        out.append("Title: ").append(chat.title).append('\n');
        out.append("Chat ID: ").append(chat.id).append('\n');
        out.append("Created: ").append(formatTimestamp(chat.createdAt)).append('\n');
        out.append("Updated: ").append(formatTimestamp(chat.updatedAt)).append("\n\n");
        appendMessages(out, chat);
        return out.toString();
    }

    private String formatAllChatsExport(List<ChatRecord> chats) {
        StringBuilder out = new StringBuilder();
        out.append("SYNC AI — ALL CHAT EXPORT\n");
        out.append("=========================\n\n");
        out.append("Chats: ").append(chats.size()).append("\n");
        for (ChatRecord chat : chats) {
            out.append("\n\n############################################################\n\n");
            out.append(formatChatExport(chat));
        }
        return out.toString();
    }

    private void appendMessages(StringBuilder out, ChatRecord chat) {
        if (chat.messages.isEmpty()) {
            out.append("[No saved messages]\n");
            return;
        }
        for (int i = 0; i < chat.messages.size(); i++) {
            ChatMessage message = chat.messages.get(i);
            out.append("------------------------------------------------------------\n");
            out.append("Message ").append(i + 1).append(" | ")
                    .append(message.role.name()).append(" | ")
                    .append(formatTimestamp(message.timestamp)).append("\n\n");
            out.append(message.text).append("\n");
            if (message.role == ChatMessage.Role.ASSISTANT) {
                out.append("\n[AI MESSAGE DIAGNOSTICS]\n");
                out.append(message.hasDiagnostics()
                        ? message.diagnostics
                        : "No diagnostics recorded for this AI message.");
                out.append("\n");
            }
        }
    }

    private String formatTimestamp(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US)
                .format(new Date(timestamp));
    }

    private String exportFileName(String title) {
        String clean = title == null ? "chat" : title.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (clean.isEmpty()) clean = "chat";
        return "sync-ai-" + clean + ".txt";
    }

    private void writeChatExport(Uri uri, String content) {
        ioExecutor.execute(() -> {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Could not create the export file.");
                out.write(content.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> showToast("Chat export saved."));
            } catch (Exception e) {
                runOnUiThread(() -> showError("Export failed", e));
            }
        });
    }

    private void newChat() {
        activeChat = runtime.chatStore().create();
        runtime.preferences().setActiveChatId(activeChat.id);
        runtime.tools().clearContext();
        renderChat();
    }

    private void renameChat(ChatRecord chat) {
        EditText name = new EditText(this);
        name.setText(chat.title);
        name.setTextColor(TEXT);
        SyncDialog dialog = new SyncDialog.Builder(this, accent)
                .setTitle("RENAME CHAT")
                .setView(name)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(x -> {
            runtime.chatStore().rename(chat.id, name.getText().toString());
            if (chat.id.equals(activeChat.id)) activeChat = runtime.chatStore().get(chat.id);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void deleteChat(ChatRecord chat) {
        new SyncDialog.Builder(this, accent)
                .setTitle("DELETE CHAT?")
                .setMessage("This permanently removes the saved conversation and attached diagnostics.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (d, w) -> {
                    boolean deletingActive = chat.id.equals(activeChat.id);
                    runtime.chatStore().remove(chat.id);
                    if (deletingActive) newChat();
                })
                .show();
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(4));

        Button personalization = sectionButton("PERSONALIZATION", "Memory + 8 accent colors");
        personalization.setOnClickListener(v -> showPersonalization());
        content.addView(personalization);

        Button about = sectionButton("ABOUT & DIAGNOSTICS", "Version, creators, runtime evidence");
        about.setOnClickListener(v -> showAbout());
        content.addView(about);

        Button models = sectionButton("LOCAL MODELS", "Import, load, inspect, remove");
        models.setOnClickListener(v -> showModelsDialog());
        content.addView(models);

        SyncDialog dialog = new SyncDialog.Builder(this, accent)
                .setTitle("SETTINGS")
                .setView(content)
                .setNegativeButton("CLOSE", null)
                .create();
        dialog.show();
    }

    private Button sectionButton(String title, String subtitle) {
        Button b = actionButton(title + "\n" + subtitle);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setAllCaps(false);
        b.setMinHeight(dp(62));
        return b;
    }

    private void showPersonalization() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(4));

        TextView summary = text("Memory is kept on-device and is used as optional context for local generation.", 13, MUTED, false);
        summary.setPadding(0, dp(4), 0, dp(12));
        content.addView(summary);

        Button editMemory = sectionButton("MEMORY", "View / edit imported memory");
        editMemory.setOnClickListener(v -> editMemory());
        content.addView(editMemory);

        Button importMemory = sectionButton("IMPORT MEMORY FILE", "Load a text memory file into Sync");
        importMemory.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/*");
            startActivityForResult(intent, REQ_IMPORT_MEMORY);
        });
        content.addView(importMemory);

        Button colors = sectionButton("ACCENT COLOR", AppPreferences.ACCENT_NAMES[runtime.preferences().getAccent()]);
        colors.setOnClickListener(v -> showAccentPicker());
        content.addView(colors);

        new SyncDialog.Builder(this, accent)
                .setTitle("PERSONALIZATION")
                .setView(content)
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void editMemory() {
        EditText editor = new EditText(this);
        editor.setText(runtime.preferences().getMemory());
        editor.setTextColor(TEXT);
        editor.setHintTextColor(MUTED);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(8);
        editor.setMaxLines(14);
        editor.setHint("Things Sync should remember…");
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));

        new SyncDialog.Builder(this, accent)
                .setTitle("MEMORY")
                .setView(editor)
                .setNegativeButton("CANCEL", null)
                .setNeutralButton("CLEAR", (d, w) -> {
                    runtime.preferences().setMemory("");
                    showToast("Memory cleared.");
                })
                .setPositiveButton("SAVE", (d, w) -> {
                    runtime.preferences().setMemory(editor.getText().toString().trim());
                    showToast("Memory saved locally.");
                })
                .show();
    }

    private void showAccentPicker() {
        new SyncDialog.Builder(this, accent)
                .setTitle("SYNC//AI ACCENT")
                .setSingleChoiceItems(AppPreferences.ACCENT_NAMES,
                        runtime.preferences().getAccent(), (d, which) -> {
                            runtime.preferences().setAccent(which);
                            d.dismiss();
                            recreate();
                        })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void launchVoiceMode() {
        SyncEventLogger.record(this, "MainActivity", "VOICE_MODE_LAUNCH_REQUEST", "INFO",
                "source=in-app");
        try {
            // In-app Voice Mode is independent from the system DDA selection.
            // The global side-button/assistant path is handled by
            // SyncVoiceInteractionService + SyncVoiceSession.
            startActivity(new Intent(this, VoiceModeActivity.class));
        } catch (Exception e) {
            showError("Voice mode unavailable", e);
        }
    }

    private void showVoiceSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(4));

        TextView info = text(
                "Voice mode is entered explicitly through Android's assistant entry point. "
                        + "Sync does not run a wake word or background speech listener. "
                        + "Microphone use starts only inside the active voice session.",
                13, MUTED, false);
        info.setPadding(0, 0, 0, dp(12));
        content.addView(info);

        Button output = sectionButton(
                "VOICE OUTPUT",
                runtime.preferences().isVoiceOutputEnabled() ? "On" : "Off");
        output.setOnClickListener(v -> {
            runtime.preferences().setVoiceOutputEnabled(
                    !runtime.preferences().isVoiceOutputEnabled());
            showVoiceSettings();
        });
        content.addView(output);

        Button assistant = sectionButton(
                "ANDROID ASSISTANT SETTINGS",
                "Select Sync AI as the default digital assistant");
        assistant.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            } catch (Exception e) {
                showError("Assistant settings unavailable", e);
            }
        });
        content.addView(assistant);

        new SyncDialog.Builder(this, accent)
                .setTitle("VOICE MODE")
                .setView(content)
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showAbout() {
        String version = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
        String runtimeInfo = runtime.backend().diagnostics();
        String latest = latestDiagnostics();
        String message =
                "Sync AI\n\n" +
                "Version: " + version + "\n" +
                "Created By Sam\n" +
                "صنعه حسام\n\n" +
                "Runtime: local GGUF + llama.cpp\n" +
                "Target ABI: arm64-v8a\n" +
                "Network required for inference: no\n\n" +
                "Runtime diagnostics:\n" + runtimeInfo +
                "\n\nLatest request diagnostics:\n" +
                (latest.isEmpty() ? "No request recorded yet." : latest);

        SyncDialog dialog = new SyncDialog.Builder(this, accent)
                .setTitle("ABOUT & DIAGNOSTICS")
                .setMessage(message)
                .setPositiveButton("EXPORT LOGS", (d, w) -> exportAllDiagnostics())
                .setNeutralButton("COPY", (d, w) -> copyDiagnostics(message))
                .setNegativeButton("CLOSE", null)
                .create();
        dialog.show();
    }

    private String latestDiagnostics() {
        long latestTimestamp = -1L;
        String latestText = "";
        for (ChatRecord chat : runtime.chatStore().all()) {
            for (ChatMessage message : chat.messages) {
                if (message.hasDiagnostics() && message.timestamp > latestTimestamp) {
                    latestTimestamp = message.timestamp;
                    latestText = message.diagnostics;
                }
            }
        }
        return latestText;
    }

    private void copyDiagnostics(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Sync AI diagnostics", text));
            showToast("Diagnostics copied.");
        }
    }

    private void showDiagnostic(String diagnostics) {
        new SyncDialog.Builder(this, accent)
                .setTitle("REQUEST DIAGNOSTICS")
                .setMessage(diagnostics)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(diagnostics))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showRuntimeInfo() {
        String info = "Sync AI " + BuildConfig.VERSION_NAME + "\n\n" +
                "Runtime: llama.cpp\n" +
                "Model format: GGUF\n" +
                "Execution: local CPU\n" +
                "ABI: arm64-v8a\n" +
                "Network required: no\n\n" +
                runtime.backend().diagnostics();
        new SyncDialog.Builder(this, accent)
                .setTitle("SYNC//AI RUNTIME")
                .setMessage(info)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(info))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showModelsDialog() {
        List<ModelInfo> models = runtime.modelManager().getModels();
        if (models.isEmpty()) {
            new SyncDialog.Builder(this, accent)
                    .setTitle("LOCAL MODELS")
                    .setMessage("No models imported yet. Model files stay on this phone and are never committed to GitHub.")
                    .setPositiveButton("IMPORT", (d, w) -> openModelPicker())
                    .setNegativeButton("CLOSE", null)
                    .show();
            return;
        }

        String[] items = new String[models.size()];
        for (int i = 0; i < models.size(); i++) {
            ModelInfo m = models.get(i);
            boolean loaded = m.id.equals(
                    runtime.modelManager().getLoadedModel() == null
                            ? "" : runtime.modelManager().getLoadedModel().id)
                    && runtime.backend().isLoaded();
            items[i] = m.name + " • " + m.sizeLabel() + (loaded ? " ✓" : "");
        }

        new SyncDialog.Builder(this, accent)
                .setTitle("LOCAL MODELS")
                .setItems(items, (d, which) -> showModelActions(models.get(which)))
                .setPositiveButton("IMPORT", (d, w) -> openModelPicker())
                .show();
    }

    private void showModelActions(ModelInfo model) {
        String[] actions = {"Load model", "Model diagnostics", "Remove model"};
        new SyncDialog.Builder(this, accent)
                .setTitle(model.name)
                .setItems(actions, (d, which) -> {
                    if (which == 0) loadModel(model);
                    else if (which == 1) showModelInfo(model);
                    else confirmDelete(model);
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showModelInfo(ModelInfo model) {
        String info = "Name: " + model.name +
                "\nFormat: " + model.format.toUpperCase(Locale.US) +
                "\nSize: " + model.sizeLabel() +
                "\nSHA-256: " + model.sha256 +
                (runtime.backend().isLoaded() ? "\n\nRUNTIME\n" + runtime.backend().diagnostics() : "");
        new SyncDialog.Builder(this, accent)
                .setTitle("MODEL DIAGNOSTICS")
                .setMessage(info)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(info))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void confirmDelete(ModelInfo model) {
        new SyncDialog.Builder(this, accent)
                .setTitle("REMOVE MODEL?")
                .setMessage("Delete " + model.name + " from this phone?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REMOVE", (d, w) -> {
                    if (model.id.equals(runtime.modelManager().getLoadedModel() == null
                            ? "" : runtime.modelManager().getLoadedModel().id)) {
                        runtime.backend().unload();
                        runtime.modelManager().clearLoaded();
                    }
                    runtime.modelManager().removeModel(model.id);
                    refreshStatus();
                })
                .show();
    }

    private void refreshStatus() {
        ModelInfo loaded = runtime.modelManager().getLoadedModel();
        if (loaded != null && runtime.backend().isLoaded()) {
            Motion.swapText(modelText, loaded.name);
            Motion.swapText(statusText, "READY • GGUF • LOCAL CPU INFERENCE");
        } else if (loaded != null) {
            modelText.setText(loaded.name);
            Motion.swapText(statusText, "SELECTED • READY TO LOAD");
        } else {
            Motion.swapText(modelText, "NO MODEL");
            Motion.swapText(statusText, "LOCAL RUNTIME • TOOLS AVAILABLE WITHOUT MODEL");
        }
    }

    private void showProgress(boolean show) {
        if (progress == null) return;
        if (Motion.reduced(this)) { progress.setVisibility(show ? View.VISIBLE : View.GONE); return; }
        progress.animate().cancel();
        if (show) {
            if (progress.getVisibility() != View.VISIBLE) progress.setAlpha(0f);
            progress.setVisibility(View.VISIBLE);
            progress.animate().alpha(1f).setDuration(Motion.NORMAL).setInterpolator(Motion.EASE_OUT).start();
        } else {
            progress.animate().alpha(0f).setDuration(Motion.FAST)
                    .withEndAction(() -> progress.setVisibility(View.GONE)).start();
        }
    }

    private void setBusy(boolean busy) {
        modelBusy = busy;
        Motion.setEnabledAnimated(sendButton, !busy);
        Motion.setEnabledAnimated(importButton, !busy && activeGenerations == 0);
    }

    private void scrollToBottom() {
        if (chatScroll == null) return;
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showToast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, Exception e) {
        new SyncDialog.Builder(this, accent)
                .setTitle(title)
                .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        SyncEventLogger.record(this, "MainActivity", "onDestroy", "INFO",
                "taskId=" + getTaskId());
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
