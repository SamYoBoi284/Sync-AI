package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        runtime = SyncRuntime.get(this);
        accent = AppPreferences.ACCENTS[runtime.preferences().getAccent()];
        activeChat = getActiveChat();
        buildUi();
        renderChat();
        restoreLoadedModel();
    }

    @Override protected void onResume() {
        super.onResume();
        if (runtime != null) refreshStatus();
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("SYNC//AI", 27, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button chats = actionButton("CHATS");
        chats.setOnClickListener(v -> showChatsDialog());
        header.addView(chats, new LinearLayout.LayoutParams(dp(82), dp(44)));

        Button settings = actionButton("SETTINGS");
        settings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(92), dp(44));
        settingsLp.leftMargin = dp(6);
        header.addView(settings, settingsLp);
        root.addView(header);

        LinearLayout statusCard = card();
        LinearLayout statusInner = new LinearLayout(this);
        statusInner.setOrientation(LinearLayout.VERTICAL);
        statusInner.setPadding(dp(14), dp(10), dp(14), dp(10));

        modelText = text("NO MODEL", 13, TEXT, true);
        statusText = text("LOCAL RUNTIME • IMPORT A GGUF MODEL", 11, MUTED, false);
        statusInner.addView(modelText);
        statusInner.addView(statusText, new LinearLayout.LayoutParams(-1, dp(24)));
        statusCard.addView(statusInner);
        root.addView(statusCard, new LinearLayout.LayoutParams(-1, dp(72)));

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
        messageContainer.setPadding(dp(2), dp(12), dp(2), dp(12));
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
        input.setMaxLines(5);
        input.setBackground(round(SURFACE_2, dp(15)));
        composer.addView(input, new LinearLayout.LayoutParams(0, dp(56), 1));

        sendButton = actionButton("SEND");
        sendButton.setOnClickListener(v -> sendMessage());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(82), dp(56));
        sendLp.leftMargin = dp(7);
        composer.addView(sendButton, sendLp);

        composerCard.addView(composer);
        root.addView(composerCard, new LinearLayout.LayoutParams(-1, dp(70)));

        setContentView(root);
        applyAccent();
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
        b.setTextSize(10.5f);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(7), 0, dp(7), 0);
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
        if (requestCode == REQ_IMPORT_MODEL && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importModel(uri);
            return;
        }
        if (requestCode == REQ_IMPORT_MEMORY && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importMemory(uri);
        }
    }

    private void importModel(Uri uri) {
        setBusy(true);
        progress.setVisibility(View.VISIBLE);
        ioExecutor.execute(() -> {
            try {
                String id = runtime.modelManager().importModel(uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    progress.setVisibility(View.GONE);
                    ModelInfo model = findModel(id);
                    if (model != null) loadModel(model);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    progress.setVisibility(View.GONE);
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
        progress.setVisibility(View.VISIBLE);
        modelText.setText("LOADING • " + model.name);
        statusText.setText("GGUF • INITIALIZING LOCAL CPU RUNTIME…");

        runtime.backend().load(model, new LocalModelBackend.LoadCallback() {
            @Override public void onLoaded() {
                runtime.modelManager().markLoaded(model.id);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setBusy(false);
                    refreshStatus();
                    showToast("Loaded " + model.name);
                });
            }

            @Override public void onError(Exception error) {
                runtime.modelManager().clearLoaded();
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
        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        input.setText("");
        hideKeyboard();
        runtime.chatStore().maybeTitle(activeChat, message);
        ChatMessage user = new ChatMessage(ChatMessage.Role.USER, message);
        runtime.chatStore().add(activeChat, user);
        addMessageView(user);

        sendButton.setEnabled(false);
        importButton.setEnabled(false);

        long started = System.currentTimeMillis();
        ToolEngine.Result tool = runtime.tools().handle(message);
        if (tool.handled) {
            DiagnosticRecord diag = new DiagnosticRecord(
                    started, tool.durationMs, "DETERMINISTIC TOOL",
                    tool.toolName, tool.durationMs, "", "", "",
                    tool.success ? "" : tool.response);
            ChatMessage assistant = new ChatMessage(
                    ChatMessage.Role.ASSISTANT, tool.response,
                    System.currentTimeMillis(), diag.format());
            runtime.chatStore().add(activeChat, assistant);
            addMessageView(assistant);
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
            addMessageView(assistant);
            setBusy(false);
            return;
        }

        activeAssistantBubble = addMessageView(
                new ChatMessage(ChatMessage.Role.ASSISTANT, "Thinking…"));
        activeAssistantBubble.setTextColor(MUTED);

        List<ChatMessage> context = buildModelContext(message);
        GenerationConfig config = new GenerationConfig();
        StringBuilder response = new StringBuilder();

        runtime.backend().generate(context, config, new LocalModelBackend.GenerateCallback() {
            @Override public void onToken(String token) {
                response.append(token);
                runOnUiThread(() -> {
                    if (activeAssistantBubble != null) {
                        activeAssistantBubble.setText(response.toString());
                        activeAssistantBubble.setTextColor(TEXT);
                    }
                    scrollToBottom();
                });
            }

            @Override public void onComplete() {
                long total = System.currentTimeMillis() - started;
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), runtime.backend().diagnostics(), "", "");
                String answer = response.toString().trim();
                if (answer.isEmpty()) answer = "I got no response from the local model.";
                ChatMessage assistant = new ChatMessage(
                        ChatMessage.Role.ASSISTANT, answer,
                        System.currentTimeMillis(), diag.format());
                runtime.chatStore().add(activeChat, assistant);
                runOnUiThread(() -> {
                    replaceStreamingBubble(assistant);
                    setBusy(false);
                });
            }

            @Override public void onError(Exception error) {
                long total = System.currentTimeMillis() - started;
                String nativeDetails = runtime.backend().diagnostics();
                DiagnosticRecord diag = new DiagnosticRecord(
                        started, total, "LOCAL LLM", "", 0,
                        currentModelName(), nativeDetails,
                        "generation", error.getMessage());
                String failure = "I couldn't generate that response, bro. Runtime: " + total + " ms.";
                ChatMessage assistant = new ChatMessage(
                        ChatMessage.Role.ASSISTANT, failure,
                        System.currentTimeMillis(), diag.format());
                runtime.chatStore().add(activeChat, assistant);
                runOnUiThread(() -> {
                    replaceStreamingBubble(assistant);
                    setBusy(false);
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
        String system = "You are Sync//AI, a concise offline Android companion. "
                + "Talk naturally and casually. Understand slang, typos, shorthand, and bro/bfam wording. "
                + "Do not volunteer robotic identity disclaimers. "
                + "Deterministic Android tools handle device actions before the model.";
        if (!memory.trim().isEmpty()) {
            system += "\nRelevant user memory:\n" + memory.trim();
        }
        messages.add(new ChatMessage(ChatMessage.Role.SYSTEM, system));
        int start = Math.max(0, activeChat.messages.size() - 10);
        for (int i = start; i < activeChat.messages.size(); i++) {
            ChatMessage m = activeChat.messages.get(i);
            messages.add(new ChatMessage(m.role, m.text));
        }
        messages.add(new ChatMessage(ChatMessage.Role.USER, current));
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
            return "I'm Sync//AI — your local Android AI shell. The model runs on-device.";
        }
        return "I'm here, bro. Import a local GGUF model for full conversational replies.";
    }

    private String currentModelName() {
        ModelInfo model = runtime.modelManager().getLoadedModel();
        return model == null ? "" : model.name;
    }

    private void replaceStreamingBubble(ChatMessage message) {
        if (activeAssistantBubble != null) {
            View row = (View) activeAssistantBubble.getParent().getParent();
            messageContainer.removeView(row);
            activeAssistantBubble = null;
        }
        addMessageView(message);
        scrollToBottom();
    }

    private TextView addMessageView(ChatMessage message) {
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
        scrollToBottom();
        return body;
    }

    private void renderChat() {
        messageContainer.removeAllViews();
        for (ChatMessage message : activeChat.messages) addMessageView(message);
        if (activeChat.messages.isEmpty()) {
            addMessageView(new ChatMessage(
                    ChatMessage.Role.ASSISTANT,
                    "Sync//AI is ready.\nImport a GGUF model for local conversation, or use the deterministic Android tools."));
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

        new AlertDialog.Builder(this)
                .setTitle("CHATS")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        newChat();
                    } else {
                        showChatActions(chats.get(which - 1));
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showChatActions(ChatRecord chat) {
        String[] actions = {"Open", "Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(chat.title)
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        activeChat = chat;
                        runtime.preferences().setActiveChatId(chat.id);
                        runtime.tools().clearContext();
                        renderChat();
                    } else if (which == 1) {
                        renameChat(chat);
                    } else {
                        deleteChat(chat);
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("RENAME CHAT")
                .setView(name)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            runtime.chatStore().rename(chat.id, name.getText().toString());
            if (chat.id.equals(activeChat.id)) activeChat = runtime.chatStore().get(chat.id);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void deleteChat(ChatRecord chat) {
        new AlertDialog.Builder(this)
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

        Button voice = sectionButton("VOICE MODE", "Side-button assistant + voice output");
        voice.setOnClickListener(v -> showVoiceSettings());
        content.addView(voice);

        Button models = sectionButton("LOCAL MODELS", "Import, load, inspect, remove");
        models.setOnClickListener(v -> showModelsDialog());
        content.addView(models);

        AlertDialog dialog = new AlertDialog.Builder(this)
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

        new AlertDialog.Builder(this)
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

        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
                "Select Sync//AI as the default digital assistant");
        assistant.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            } catch (Exception e) {
                showError("Assistant settings unavailable", e);
            }
        });
        content.addView(assistant);

        new AlertDialog.Builder(this)
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
                "Sync//AI\n\n" +
                "Version: " + version + "\n" +
                "Created By Sam\n" +
                "صنعه حسام\n\n" +
                "Runtime: local GGUF + llama.cpp\n" +
                "Target ABI: arm64-v8a\n" +
                "Network required for inference: no\n\n" +
                "Runtime diagnostics:\n" + runtimeInfo +
                "\n\nLatest request diagnostics:\n" +
                (latest.isEmpty() ? "No request recorded yet." : latest);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ABOUT & DIAGNOSTICS")
                .setMessage(message)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(message))
                .setNeutralButton("ASSISTANT", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
                    } catch (Exception e) {
                        showError("Assistant settings unavailable", e);
                    }
                })
                .setNegativeButton("CLOSE", null)
                .create();
        dialog.show();
    }

    private String latestDiagnostics() {
        DiagnosticRecord latest = null;
        String text = "";
        for (ChatRecord chat : runtime.chatStore().all()) {
            for (ChatMessage message : chat.messages) {
                if (message.hasDiagnostics() &&
                        (latest == null || message.timestamp > latest.startedAt)) {
                    text = message.diagnostics;
                }
            }
        }
        return text;
    }

    private void copyDiagnostics(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Sync//AI diagnostics", text));
            showToast("Diagnostics copied.");
        }
    }

    private void showDiagnostic(String diagnostics) {
        new AlertDialog.Builder(this)
                .setTitle("REQUEST DIAGNOSTICS")
                .setMessage(diagnostics)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(diagnostics))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showRuntimeInfo() {
        String info = "Sync//AI " + BuildConfig.VERSION_NAME + "\n\n" +
                "Runtime: llama.cpp\n" +
                "Model format: GGUF\n" +
                "Execution: local CPU\n" +
                "ABI: arm64-v8a\n" +
                "Network required: no\n\n" +
                runtime.backend().diagnostics();
        new AlertDialog.Builder(this)
                .setTitle("SYNC//AI RUNTIME")
                .setMessage(info)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(info))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showModelsDialog() {
        List<ModelInfo> models = runtime.modelManager().getModels();
        if (models.isEmpty()) {
            new AlertDialog.Builder(this)
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
        String info = "Name: " + model.name +
                "\nFormat: " + model.format.toUpperCase(Locale.US) +
                "\nSize: " + model.sizeLabel() +
                "\nSHA-256: " + model.sha256 +
                (runtime.backend().isLoaded() ? "\n\nRUNTIME\n" + runtime.backend().diagnostics() : "");
        new AlertDialog.Builder(this)
                .setTitle("MODEL DIAGNOSTICS")
                .setMessage(info)
                .setPositiveButton("COPY", (d, w) -> copyDiagnostics(info))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void confirmDelete(ModelInfo model) {
        new AlertDialog.Builder(this)
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
            modelText.setText(loaded.name);
            statusText.setText("READY • GGUF • LOCAL CPU INFERENCE");
        } else if (loaded != null) {
            modelText.setText(loaded.name);
            statusText.setText("SELECTED • READY TO LOAD");
        } else {
            modelText.setText("NO MODEL");
            statusText.setText("LOCAL RUNTIME • TOOLS AVAILABLE WITHOUT MODEL");
        }
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        importButton.setEnabled(!busy);
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
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
