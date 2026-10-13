package com.sam.syncai;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GgufModelBackend implements LocalModelBackend {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context context;
    private volatile ModelInfo loaded;

    public GgufModelBackend(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void load(ModelInfo model, LoadCallback callback) {
        executor.execute(() -> {
            try {
                if (!"gguf".equalsIgnoreCase(model.format)) {
                    throw new IllegalArgumentException(
                            "Sync//AI's native runtime currently supports GGUF models.\n\n" +
                            "The model you selected is " + model.format.toUpperCase(Locale.US) + "."
                    );
                }

                String memoryWarning = memoryWarning(model.sizeBytes);
                String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
                int result = GgufNative.nativeLoad(model.path, nativeLibDir);
                if (result != 0) {
                    String nativeError = GgufNative.nativeLastError();
                    StringBuilder message = new StringBuilder();
                    message.append("GGUF load failed (code ").append(result).append(").");
                    if (!nativeError.isEmpty()) {
                        message.append("\n\nNative runtime: ").append(nativeError);
                    }
                    if (!memoryWarning.isEmpty()) {
                        message.append("\n\n").append(memoryWarning);
                    }
                    throw new IllegalStateException(message.toString());
                }
                loaded = model;
                callback.onLoaded();
            } catch (Exception e) {
                loaded = null;
                callback.onError(e);
            }
        });
    }

    private String memoryWarning(long modelBytes) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return "";
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        double modelGiB = modelBytes / 1024.0 / 1024.0 / 1024.0;
        double availGiB = info.availMem / 1024.0 / 1024.0 / 1024.0;
        if (modelBytes > info.availMem * 0.70) {
            return String.format(Locale.US,
                    "Memory check: model file %.2f GiB, currently available RAM %.2f GiB. " +
                    "A 4 GB device may not have enough headroom for this model plus the inference context.",
                    modelGiB, availGiB);
        }
        return "";
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
                    @Override public void onComplete() { callback.onComplete(); }
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

    public String diagnostics() {
        if (!isLoaded()) return "No model loaded.";
        return GgufNative.nativeInfo();
    }
}
