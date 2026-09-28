package com.sam.syncai;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MockModelBackend implements LocalModelBackend {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile ModelInfo loaded;

    @Override
    public void load(ModelInfo model, LoadCallback callback) {
        executor.execute(() -> {
            loaded = model;
            callback.onLoaded();
        });
    }

    @Override
    public void unload() {
        loaded = null;
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

        String userText = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role == ChatMessage.Role.USER) {
                userText = messages.get(i).text;
                break;
            }
        }

        String response =
                "[Sync//AI foundation]\n" +
                "Model: " + model.name + "\n\n" +
                "I received: " + userText + "\n\n" +
                "The model import/registry layer is working. " +
                "This build uses a mock backend until the native offline inference runtime is plugged in.";

        executor.execute(() -> {
            try {
                String[] chunks = response.split(" ");
                for (int i = 0; i < chunks.length; i++) {
                    callback.onToken((i == 0 ? "" : " ") + chunks[i]);
                    Thread.sleep(12);
                }
                callback.onComplete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onError(e);
            }
        });
    }
}
