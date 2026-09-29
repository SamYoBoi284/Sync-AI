package com.sam.syncai;

import java.util.List;

public final class GgufNative {
    static {
        System.loadLibrary("syncai_native");
    }

    private GgufNative() {}

    public interface Callback {
        void onToken(String token);
        void onComplete();
        void onError(String message);
    }

    public static native int nativeLoad(String path);
    public static native void nativeUnload();
    public static native String nativeInfo();
    public static native String nativeDiagnostics();

    public static void generate(
            List<ChatMessage> messages,
            GenerationConfig config,
            Callback callback
    ) {
        String[] roles = new String[messages.size()];
        String[] texts = new String[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            roles[i] = message.role == ChatMessage.Role.USER ? "user" :
                    message.role == ChatMessage.Role.ASSISTANT ? "assistant" : "system";
            texts[i] = message.text;
        }
        nativeGenerate(roles, texts, config.maxTokens, config.temperature, config.topP, callback);
    }

    private static native void nativeGenerate(
            String[] roles,
            String[] texts,
            int maxTokens,
            float temperature,
            float topP,
            Callback callback
    );
}
