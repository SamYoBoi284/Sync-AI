package com.sam.syncai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_IMPORT_MODEL = 1201;
    private static final int REQ_CAMERA_PERMISSION = 1301;
    private static final int MAX_TOOL_CALLS = 4;
    private static final int BG = Color.rgb(7, 8, 14);
    private static final int SURFACE = Color.rgb(15, 18, 28);
    private static final int SURFACE_2 = Color.rgb(21, 25, 38);
    private static final int PURPLE = Color.rgb(154, 96, 255);
    private static final int CYAN = Color.rgb(80, 215, 255);
    private static final int TEXT = Color.rgb(240, 242, 250);
    private static final int MUTED = Color.rgb(145, 153, 177);

    private final List<ChatMessage> conversation = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ModelManager modelManager;
    private GgufModelBackend backend;
    private ToolRegistry toolRegistry;
    private ToolCall pendingPermissionToolCall;
    private List<ChatMessage> pendingWorkingMessages;
    private TextView pendingToolBubble;
    private int pendingToolDepth;

    private TextView statusText;
    private TextView modelText;
    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText input;
    private Button sendButton;
    private Button importButton;
    private ProgressBar progress;
    private TextView activeAssistantBubble;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        modelManager = new ModelManager(this);
        backend = new GgufModelBackend(this);
        toolRegistry = new ToolRegistry(this);
        buildUi();
        addMessageView(ChatMessage.Role.ASSISTANT,
                "Sync//AI is ready.\nImport a GGUF model to start chatting locally.");
        restoreLoadedModel();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("SYNC//AI", 27, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button modelButton = actionButton("MODELS");
        modelButton.setOnClickListener(v -> showModelsDialog());
        header.addView(modelButton, new LinearLayout.LayoutParams(dp(100), dp(44)));

        root.addView(header);

        LinearLayout statusCard = card();
        LinearLayout statusInner = new LinearLayout(this);
        statusInner.setOrientation(LinearLayout.VERTICAL);
        statusInner.setPadding(dp(14), dp(11), dp(14), dp(11));

        modelText = text("NO MODEL", 13, TEXT, true);
        statusText = text("LOCAL RUNTIME • WAITING FOR MODEL", 11, MUTED, false);
        statusInner.addView(modelText);
        statusInner.addView(statusText, new LinearLayout.LayoutParams(-1, dp(24)));
        statusCard.addView(statusInner);
        root.addView(statusCard, new LinearLayout.LayoutParams(-1, dp(74)));

        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(0, dp(9), 0, dp(7));

        importButton = actionButton("IMPORT MODEL");
        importButton.setOnClickListener(v -> openModelPicker());
        Button infoButton = actionButton("RUNTIME");
        infoButton.setOnClickListener(v -> showRuntimeInfo());

        controls.addView(importButton, new LinearLayout.LayoutParams(0, dp(45), 1));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, dp(45), 1);
        infoLp.leftMargin = dp(7);
        controls.addView(infoButton, infoLp);
        root.addView(controls);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);

        messageContainer = new LinearLayout(this);
        messageContainer.setOrientation(LinearLayout.VERTICAL);
        messageContainer.setPadding(dp(2), dp(14), dp(2), dp(18));
        chatScroll.addView(messageContainer);
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composerCard = card();
        composerCard.setPadding(dp(7), dp(7), dp(7), dp(7));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);

        input = new EditText(this);
        input.setHint("Message Sync//AI…");
        input.setHintTextColor(Color.rgb(92, 100, 123));
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(14), dp(11), dp(14), dp(10));
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setBackground(round(SURFACE_2, dp(15)));
        composer.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1));

        sendButton = actionButton("SEND");
        sendButton.setOnClickListener(v -> sendMessage());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(82), dp(52));
        sendLp.leftMargin = dp(7);
        composer.addView(sendButton, sendLp);

        composerCard.addView(composer);
        root.addView(composerCard, new LinearLayout.LayoutParams(-1, dp(66)));

        setContentView(root);
        refreshStatus();
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(round(SURFACE, dp(16)));
        return layout;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(round(Color.rgb(28, 33, 49), dp(13)));
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

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_MODEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_MODEL || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        progress.setVisibility(View.VISIBLE);
        setBusy(true);

        ioExecutor.execute(() -> {
            try {
                String id = modelManager.importModel(uri);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setBusy(false);
                    ModelInfo model = findModel(id);
                    if (model != null) {
                        showToast("Imported " + model.name);
                        loadModel(model);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setBusy(false);
                    showError("Import failed", e);
                });
            }
        });
    }

    private ModelInfo findModel(String id) {
        for (ModelInfo m : modelManager.getModels()) if (m.id.equals(id)) return m;
        return null;
    }

    private void restoreLoadedModel() {
        ModelInfo model = modelManager.getLoadedModel();
        if (model != null) loadModel(model);
    }

    private void loadModel(ModelInfo model) {
        setBusy(true);
        progress.setVisibility(View.VISIBLE);
        modelText.setText("LOADING • " + model.name);
        statusText.setText("GGUF • INITIALIZING CPU INFERENCE RUNTIME…");

        backend.unload();
        backend.load(model, new LocalModelBackend.LoadCallback() {
            @Override public void onLoaded() {
                modelManager.markLoaded(model.id);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setBusy(false);
                    refreshStatus();
                    addMessageView(ChatMessage.Role.ASSISTANT,
                            "Model loaded. You are now running inference locally on this device.");
                });
            }

            @Override public void onError(Exception error) {
                modelManager.clearLoaded();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setBusy(false);
                    refreshStatus();
                    showError("Model load failed", error);
                });
            }
        });
    }

    private void sendMessage() {
        if (!backend.isLoaded()) {
            showToast("Load a GGUF model first.");
            return;
        }

        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        input.setText("");
        hideKeyboard();
        conversation.add(new ChatMessage(ChatMessage.Role.USER, message));
        addMessageView(ChatMessage.Role.USER, message);

        activeAssistantBubble = addMessageView(ChatMessage.Role.ASSISTANT, "");
        activeAssistantBubble.setText("Thinking…");
        activeAssistantBubble.setTextColor(MUTED);

        sendButton.setEnabled(false);
        importButton.setEnabled(false);

        List<ChatMessage> working = new ArrayList<>();
        working.add(new ChatMessage(ChatMessage.Role.SYSTEM, toolRegistry.systemPrompt()));
        working.addAll(conversation);
        runGeneration(working, 0, activeAssistantBubble);
    }

    private void runGeneration(List<ChatMessage> working, int toolDepth, TextView bubble) {
        StringBuilder response = new StringBuilder();
        GenerationConfig config = new GenerationConfig();

        backend.generate(working, config, new LocalModelBackend.GenerateCallback() {
            @Override public void onToken(String token) {
                response.append(token);
                runOnUiThread(() -> {
                    bubble.setText(response.toString());
                    bubble.setTextColor(TEXT);
                    scrollToBottom();
                });
            }

            @Override public void onComplete() {
                String text = response.toString().trim();
                ToolCall toolCall = ToolCallParser.parse(text);

                if (toolCall != null) {
                    SyncTool tool = toolRegistry.get(toolCall.name);
                    if (tool == null) {
                        finishGenerationWithError(
                                bubble,
                                "Unknown tool requested: " + toolCall.name + ".",
                                null);
                        return;
                    }
                    if (toolDepth >= MAX_TOOL_CALLS) {
                        finishGenerationWithError(
                                bubble,
                                "Tool-call limit reached for this request.",
                                null);
                        return;
                    }

                    working.add(new ChatMessage(ChatMessage.Role.ASSISTANT, text));
                    if ("flashlight".equals(toolCall.name) &&
                            checkSelfPermission(android.Manifest.permission.CAMERA)
                                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        pendingPermissionToolCall = toolCall;
                        pendingWorkingMessages = working;
                        pendingToolBubble = bubble;
                        pendingToolDepth = toolDepth;
                        runOnUiThread(() -> requestPermissions(
                                new String[]{android.Manifest.permission.CAMERA},
                                REQ_CAMERA_PERMISSION));
                        return;
                    }

                    executeToolAndContinue(toolCall, working, toolDepth, bubble);
                    return;
                }

                if (text.isEmpty()) {
                    finishGenerationWithError(bubble, "Model returned an empty response.", null);
                    return;
                }

                conversation.add(new ChatMessage(ChatMessage.Role.ASSISTANT, text));
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    importButton.setEnabled(true);
                    scrollToBottom();
                });
            }

            @Override public void onError(Exception error) {
                finishGenerationWithError(bubble, "Generation failed.", error);
            }
        });
    }

    private void executeToolAndContinue(
            ToolCall toolCall,
            List<ChatMessage> working,
            int toolDepth,
            TextView bubble) {
        SyncTool tool = toolRegistry.get(toolCall.name);
        String result;
        try {
            result = tool.execute(toolCall.arguments);
        } catch (Throwable t) {
            String message = t.getMessage();
            result = "ERROR: " + (message == null ? t.toString() : message);
        }

        working.add(new ChatMessage(
                ChatMessage.Role.SYSTEM,
                "Tool result for " + toolCall.name + ": " + result));

        runOnUiThread(() -> {
            bubble.setText("Using " + toolCall.name + "…");
            bubble.setTextColor(MUTED);
            scrollToBottom();
        });

        runGeneration(working, toolDepth + 1, bubble);
    }

    private void finishGenerationWithError(TextView bubble, String message, Exception error) {
        runOnUiThread(() -> {
            bubble.setText(message);
            bubble.setTextColor(Color.rgb(255, 130, 145));
            sendButton.setEnabled(true);
            importButton.setEnabled(true);
            if (error != null) showError("Generation failed", error);
            scrollToBottom();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA_PERMISSION) return;

        ToolCall toolCall = pendingPermissionToolCall;
        List<ChatMessage> working = pendingWorkingMessages;
        TextView bubble = pendingToolBubble;
        int depth = pendingToolDepth;

        pendingPermissionToolCall = null;
        pendingWorkingMessages = null;
        pendingToolBubble = null;
        pendingToolDepth = 0;

        if (toolCall == null || working == null || bubble == null) return;

        boolean granted = grantResults.length > 0 &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!granted) {
            working.add(new ChatMessage(
                    ChatMessage.Role.SYSTEM,
                    "Tool result for flashlight: ERROR: CAMERA permission was denied."));
            runGeneration(working, depth + 1, bubble);
            return;
        }

        executeToolAndContinue(toolCall, working, depth, bubble);
    }

    private TextView addMessageView(ChatMessage.Role role, String value) {
        boolean user = role == ChatMessage.Role.USER;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(round(user ? Color.rgb(48, 31, 76) : Color.rgb(18, 22, 33), dp(16)));

        TextView label = text(user ? "YOU" : "SYNC//AI", 10,
                user ? Color.rgb(210, 176, 255) : CYAN, true);
        bubble.addView(label);

        TextView body = text(value, 15, TEXT, false);
        body.setLineSpacing(0, 1.12f);
        bubble.addView(body);

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                user ? (int)(getResources().getDisplayMetrics().widthPixels * 0.78f) :
                        (int)(getResources().getDisplayMetrics().widthPixels * 0.88f),
                -2);
        row.addView(bubble, bubbleLp);
        messageContainer.addView(row);
        scrollToBottom();
        return body;
    }

    private void scrollToBottom() {
        if (chatScroll == null) return;
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showModelsDialog() {
        List<ModelInfo> models = modelManager.getModels();
        if (models.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("LOCAL MODELS")
                    .setMessage("No models imported yet.\n\nModel binaries stay on your phone and are never committed to GitHub.")
                    .setPositiveButton("IMPORT", (d, w) -> openModelPicker())
                    .setNegativeButton("CLOSE", null)
                    .show();
            return;
        }

        String[] items = new String[models.size()];
        for (int i = 0; i < models.size(); i++) {
            ModelInfo m = models.get(i);
            boolean loaded = modelManager.getLoadedModel() != null &&
                    m.id.equals(modelManager.getLoadedModel().id) && backend.isLoaded();
            items[i] = m.name + "  •  " + m.format.toUpperCase(Locale.US) +
                    "  •  " + m.sizeLabel() + (loaded ? "  ✓" : "");
        }

        new AlertDialog.Builder(this)
                .setTitle("LOCAL MODELS")
                .setItems(items, (d, which) -> showModelActions(models.get(which)))
                .setPositiveButton("IMPORT", (d, w) -> openModelPicker())
                .show();
    }

    private void showModelActions(ModelInfo model) {
        String[] actions = {"Load model", "Model diagnostics", "Remove model"};
        new AlertDialog.Builder(this)
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
        String nativeInfo = modelManager.getLoadedModel() != null &&
                model.id.equals(modelManager.getLoadedModel().id) && backend.isLoaded()
                ? "\n\nRUNTIME DIAGNOSTICS\n" + backend.diagnostics() : "";

        String info = "Name: " + model.name +
                "\nFormat: " + model.format.toUpperCase(Locale.US) +
                "\nSize: " + model.sizeLabel() +
                "\nSHA-256: " + model.sha256 + nativeInfo;

        new AlertDialog.Builder(this)
                .setTitle("MODEL DIAGNOSTICS")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showRuntimeInfo() {
        String info = "Sync//AI 0.2.0\n\n" +
                "Runtime: llama.cpp\n" +
                "Model format: GGUF\n" +
                "Execution: local CPU\n" +
                "ABI: arm64-v8a\n" +
                "Network required for inference: no\n\n" +
                "Models are imported into app-private storage.";
        new AlertDialog.Builder(this)
                .setTitle("SYNC//AI RUNTIME")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmDelete(ModelInfo model) {
        new AlertDialog.Builder(this)
                .setTitle("Remove model?")
                .setMessage("Delete " + model.name + " from this phone?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REMOVE", (d, w) -> {
                    if (modelManager.getLoadedModel() != null &&
                            model.id.equals(modelManager.getLoadedModel().id)) {
                        backend.unload();
                        modelManager.clearLoaded();
                    }
                    modelManager.removeModel(model.id);
                    refreshStatus();
                    showToast("Model removed.");
                })
                .show();
    }

    private void refreshStatus() {
        ModelInfo loaded = modelManager.getLoadedModel();
        if (loaded != null && backend.isLoaded()) {
            modelText.setText(loaded.name);
            statusText.setText("READY • GGUF • LOCAL CPU INFERENCE");
        } else if (loaded != null) {
            modelText.setText(loaded.name);
            statusText.setText("SELECTED • READY TO LOAD");
        } else {
            modelText.setText("NO MODEL");
            statusText.setText("LOCAL RUNTIME • IMPORT A GGUF MODEL");
        }
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        importButton.setEnabled(!busy);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, Exception error) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        ioExecutor.shutdownNow();
        backend.unload();
        super.onDestroy();
    }
}
