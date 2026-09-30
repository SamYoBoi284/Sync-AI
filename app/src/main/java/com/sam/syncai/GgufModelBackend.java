package com.sam.syncai;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GgufModelBackend implements LocalModelBackend {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile ModelInfo loaded;
    private volatile String lastGenerationDiagnostics = "";

    @Override
    public void load(ModelInfo model, LoadCallback callback) {
        executor.execute(() -> {
            try {
                if (!"gguf".equalsIgnoreCase(model.format)) {
                    throw new IllegalArgumentException(
                            "Sync//AI's native runtime currently supports GGUF models.\n\n" +
                            "The model you selected is " + model.format.toUpperCase() + "."
                    );
                }
                int result = GgufNative.nativeLoad(model.path);
                if (result != 0) {
                    String details = GgufNative.nativeDiagnostics();
                    throw new IllegalStateException(
                            "The GGUF model could not be loaded.\\n\\n" +
                            details
                    );
                }
                loaded = model;
                callback.onLoaded();
            } catch (Exception e) {
                loaded = null;
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
                        callback.onComplete();
                    }
                    @Override public void onError(String message) {
                        callback.onError(new IllegalStateException(message));
                    }
                });
            } catch (Throwable t) {
                callback.onError(new IllegalStateException(
                        t.getMessage() == null ? t.toString() : t.getMessage(), t));
            }
        });
    }

    public String lastGenerationDiagnostics() {
        return lastGenerationDiagnostics;
    }

    public String diagnostics() {
        if (!isLoaded()) return "No model loaded.";
        return GgufNative.nativeDiagnostics();
    }
}
