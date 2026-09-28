package com.sam.syncai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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

    private final List<ChatMessage> conversation = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ModelManager modelManager;
    private LocalModelBackend backend;
    private ToolRegistry toolRegistry;

    private TextView statusText;
    private TextView chatText;
    private EditText input;
    private Button sendButton;
    private ProgressBar importProgress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        modelManager = new ModelManager(this);
        backend = new MockModelBackend();
        toolRegistry = new ToolRegistry();

        buildUi();
        addChat(ChatMessage.Role.ASSISTANT,
                "Sync//AI online.\nImport a local model to begin.");
        restoreLoadedModel();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 22, 22, 16);
        root.setBackgroundColor(Color.rgb(7, 9, 16));

        TextView title = new TextView(this);
        title.setText("SYNC//AI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(29);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, 48));

        statusText = new TextView(this);
        statusText.setText("NO MODEL LOADED");
        statusText.setTextColor(Color.rgb(180, 190, 215));
        statusText.setTextSize(13);
        statusText.setPadding(0, 0, 0, 10);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, 34));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button importButton = makeButton("IMPORT MODEL");
        Button modelsButton = makeButton("MODELS");

        controls.addView(importButton, weightParams(1));
        controls.addView(modelsButton, weightParams(1));

        importButton.setOnClickListener(v -> openModelPicker());
        modelsButton.setOnClickListener(v -> showModelsDialog());

        root.addView(controls, new LinearLayout.LayoutParams(-1, 52));

        importProgress = new ProgressBar(this);
        importProgress.setVisibility(View.GONE);
        root.addView(importProgress, new LinearLayout.LayoutParams(-1, 4));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        chatText = new TextView(this);
        chatText.setTextColor(Color.rgb(230, 234, 245));
        chatText.setTextSize(15);
        chatText.setLineSpacing(0, 1.15f);
        chatText.setPadding(4, 18, 4, 18);
        scroll.addView(chatText);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setHint("Message Sync//AI…");
        input.setHintTextColor(Color.rgb(110, 118, 140));
        input.setTextColor(Color.WHITE);
        input.setBackgroundColor(Color.rgb(20, 24, 35));
        input.setPadding(16, 10, 16, 10);

        sendButton = makeButton("SEND");
        sendButton.setOnClickListener(v -> sendMessage());

        composer.addView(input, weightParams(1));
        composer.addView(sendButton, new LinearLayout.LayoutParams(110, -1));
        root.addView(composer, new LinearLayout.LayoutParams(-1, 62));

        setContentView(root);
    }

    private LinearLayout.LayoutParams weightParams(float weight) {
        return new LinearLayout.LayoutParams(0, -1, weight);
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(28, 35, 52));
        return b;
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

        importProgress.setVisibility(View.VISIBLE);
        setBusy(true);

        ioExecutor.execute(() -> {
            try {
                String id = modelManager.importModel(uri);
                runOnUiThread(() -> {
                    importProgress.setVisibility(View.GONE);
                    setBusy(false);
                    ModelInfo model = findModel(id);
                    if (model != null) {
                        showToast("Imported " + model.name);
                        loadModel(model);
                    } else {
                        showToast("Model imported.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    importProgress.setVisibility(View.GONE);
                    setBusy(false);
                    showError("Model import failed", e);
                });
            }
        });
    }

    private ModelInfo findModel(String id) {
        for (ModelInfo m : modelManager.getModels()) {
            if (m.id.equals(id)) return m;
        }
        return null;
    }

    private void showModelsDialog() {
        List<ModelInfo> models = modelManager.getModels();
        if (models.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("MODELS")
                    .setMessage("No local models imported yet.\n\nModel files stay outside the GitHub repository and are imported on-device.")
                    .setPositiveButton("IMPORT MODEL", (d, w) -> openModelPicker())
                    .setNegativeButton("CLOSE", null)
                    .show();
            return;
        }

        String[] items = new String[models.size()];
        for (int i = 0; i < models.size(); i++) {
            ModelInfo m = models.get(i);
            String active = (modelManager.getLoadedModel() != null &&
                    m.id.equals(modelManager.getLoadedModel().id)) ? "  [LOADED]" : "";
            items[i] = m.name + "  •  " + m.format.toUpperCase(Locale.US) +
                    "  •  " + m.sizeLabel() + active;
        }

        new AlertDialog.Builder(this)
                .setTitle("LOCAL MODELS")
                .setItems(items, (dialog, which) -> showModelActions(models.get(which)))
                .setPositiveButton("IMPORT", (d, w) -> openModelPicker())
                .show();
    }

    private void showModelActions(ModelInfo model) {
        String[] actions = {"Load model", "Model info", "Remove model"};
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
        String text =
                "Name: " + model.name + "\n" +
                "Format: " + model.format.toUpperCase(Locale.US) + "\n" +
                "Size: " + model.sizeLabel() + "\n" +
                "SHA-256: " + model.sha256 + "\n" +
                "Path: " + model.path;
        new AlertDialog.Builder(this)
                .setTitle("MODEL INFO")
                .setMessage(text)
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
                            modelManager.getLoadedModel().id.equals(model.id)) {
                        backend.unload();
                    }
                    modelManager.removeModel(model.id);
                    refreshStatus();
                    showToast("Model removed.");
                })
                .show();
    }

    private void restoreLoadedModel() {
        ModelInfo model = modelManager.getLoadedModel();
        if (model == null) {
            refreshStatus();
            return;
        }
        loadModel(model);
    }

    private void loadModel(ModelInfo model) {
        setBusy(true);
        statusText.setText("LOADING " + model.name + "…");

        backend.unload();
        backend.load(model, new LocalModelBackend.LoadCallback() {
            @Override
            public void onLoaded() {
                modelManager.markLoaded(model.id);
                runOnUiThread(() -> {
                    setBusy(false);
                    refreshStatus();
                    addChat(ChatMessage.Role.ASSISTANT,
                            "Loaded model: " + model.name);
                });
            }

            @Override
            public void onError(Exception error) {
                modelManager.clearLoaded();
                runOnUiThread(() -> {
                    setBusy(false);
                    refreshStatus();
                    showError("Model load failed", error);
                });
            }
        });
    }

    private void sendMessage() {
        if (!backend.isLoaded()) {
            showToast("Import and load a model first.");
            return;
        }

        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;

        input.setText("");
        addChat(ChatMessage.Role.USER, "YOU\n" + text);

        ChatMessage active = new ChatMessage(ChatMessage.Role.USER, text);
        conversation.add(active);

        sendButton.setEnabled(false);
        chatText.append("\nSYNC//AI\n");

        GenerationConfig config = new GenerationConfig();
        backend.generate(conversation, config, new LocalModelBackend.GenerateCallback() {
            @Override
            public void onToken(String token) {
                runOnUiThread(() -> chatText.append(token));
            }

            @Override
            public void onComplete() {
                conversation.add(new ChatMessage(ChatMessage.Role.ASSISTANT,
                        "[mock backend response]"));
                runOnUiThread(() -> {
                    chatText.append("\n\n");
                    sendButton.setEnabled(true);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    showError("Generation failed", error);
                });
            }
        });
    }

    private void addChat(ChatMessage.Role role, String text) {
        if (chatText == null) return;
        String prefix = role == ChatMessage.Role.USER ? "YOU" : "SYNC//AI";
        chatText.append("\n" + prefix + "\n" + text + "\n");
    }

    private void refreshStatus() {
        ModelInfo loaded = modelManager.getLoadedModel();
        if (loaded != null && backend.isLoaded()) {
            statusText.setText("LOADED • " + loaded.name + " • " + loaded.sizeLabel());
        } else if (loaded != null) {
            statusText.setText("SELECTED • " + loaded.name);
        } else {
            statusText.setText("NO MODEL LOADED");
        }
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
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

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        backend.unload();
        super.onDestroy();
    }
}
