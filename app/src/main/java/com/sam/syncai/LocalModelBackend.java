package com.sam.syncai;

import java.util.List;

public interface LocalModelBackend {
    interface LoadCallback {
        void onLoaded();
        void onError(Exception error);
    }

    interface GenerateCallback {
        void onToken(String token);
        void onComplete();
        void onError(Exception error);
    }

    void load(ModelInfo model, LoadCallback callback);
    void unload();
    boolean isLoaded();
    void generate(List<ChatMessage> messages, GenerationConfig config, GenerateCallback callback);
}
