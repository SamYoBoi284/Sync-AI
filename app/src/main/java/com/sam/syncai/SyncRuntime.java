package com.sam.syncai;

import android.content.Context;

public final class SyncRuntime {
    private static volatile SyncRuntime instance;

    private final AppPreferences preferences;
    private final ChatStore chatStore;
    private final ModelManager modelManager;
    private final GgufModelBackend backend;
    private final ToolEngine toolEngine;

    private SyncRuntime(Context context) {
        Context app = context.getApplicationContext();
        preferences = new AppPreferences(app);
        chatStore = new ChatStore(app);
        modelManager = new ModelManager(app);
        backend = new GgufModelBackend();
        toolEngine = new ToolEngine(app);
    }

    public static SyncRuntime get(Context context) {
        if (instance == null) {
            synchronized (SyncRuntime.class) {
                if (instance == null) instance = new SyncRuntime(context);
            }
        }
        return instance;
    }

    public AppPreferences preferences() { return preferences; }
    public ChatStore chatStore() { return chatStore; }
    public ModelManager modelManager() { return modelManager; }
    public GgufModelBackend backend() { return backend; }
    public ToolEngine tools() { return toolEngine; }
}
