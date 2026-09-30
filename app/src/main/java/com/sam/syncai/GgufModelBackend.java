package com.sam.syncai;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GgufModelBackend implements LocalModelBackend {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile ModelInfo loaded;
    private volatile String lastGenerationDiagnostics = "";
    private volatile String runtimeDiagnostics = "No model loaded.";

    @Override
    public void load(ModelInfo model, LoadCallback callback) {
        executor.execute(() -> {
            try {
                if (!"gguf".equalsIgnoreCase(model.format)) {
                    throw new IllegalArgumentException(
                            "Sync AI's native runtime currently supports GGUF models.\n\n" +
                            "The model you selected is " + model.format.toUpperCase() + "."
                    );
                }
                int result = GgufNative.nativeLoad(model.path);
                if (result != 0) {
                    String details = GgufNative.nativeDiagnostics();
                    throw new IllegalStateException(
                            "The GGUF model could not be loaded.\n\n" + details
                    );
                }
                loaded = model;
                lastGenerationDiagnostics = "";
                runtimeDiagnostics = GgufNative.nativeDiagnostics();
                callback.onLoaded();
            } catch (Exception e) {
                loaded = null;
                runtimeDiagnostics = "No model loaded.\n\n" +
                        (e.getMessage() == null ? e.toString() : e.getMessage());
                callback.onError(e);
            }
        });
    }

    @Override
    public void unload() {
        executor.execute(() -> {
            try {
                GgufNative.nativeUnload();
            } finally {
                loaded = null;
                lastGenerationDiagnostics = "";
                runtimeDiagnostics = "No model loaded.";
            }
        });
    }

    @Override
    public boolean isLoaded() {
        return loaded != null;
    }

    @Override
    public void generate(List<ChatMessage> messages, GenerationConfig config, GenerateCallback callback) {
        ModelInfo model = loaded;
        if (model == null) {
            callback.onError(new IllegalStateException("No model is loaded."));
            return;
        }

        executor.execute(() -> {
            try {
                GgufNative.generate(messages, config, new GgufNative.Callback() {
                    @Override public void onToken(String token) { callback.onToken(token); }
                    @Override public void onComplete(String diagnostics) {
                        lastGenerationDiagnostics = diagnostics == null ? "" : diagnostics;
                        runtimeDiagnostics = diagnostics == null || diagnostics.isEmpty()
                                ? runtimeDiagnostics : diagnostics;
                        callback.onComplete();
                    }
                    @Override public void onError(String message) {
                        runtimeDiagnostics = message == null ? "Inference error." : message;
                        callback.onError(new IllegalStateException(runtimeDiagnostics));
                    }
                });
            } catch (Throwable t) {
                runtimeDiagnostics = t.getMessage() == null ? t.toString() : t.getMessage();
                callback.onError(new IllegalStateException(runtimeDiagnostics, t));
            }
        });
    }

    public String lastGenerationDiagnostics() {
        return lastGenerationDiagnostics;
    }

    /**
     * This method is intentionally non-blocking with respect to llama.cpp.
     * Native diagnostics takes the same mutex used by generation; calling it
     * from a native generation callback can deadlock the UI/voice flow.
     */
    public String diagnostics() {
        return runtimeDiagnostics;
    }
}
