package com.sam.syncai;

import android.content.Context;
import java.util.Map;

public final class CanvasWriteTool implements SyncTool {
    private final Context context;
    public CanvasWriteTool(Context context) { this.context = context.getApplicationContext(); }
    @Override public String getName() { return "write_canvas"; }
    @Override public String getDescription() { return "Create or replace the editable AI canvas with a title and document content."; }
    @Override public String getInputSchema() { return "{\"title\":\"Project plan\",\"content\":\"# Plan\\n\\n- Step 1\"}"; }
    @Override public String execute(Map<String, String> args) throws Exception {
        CanvasManager.write(context, args.get("title"), args.get("content"));
        return "SUCCESS: Canvas updated.";
    }
}
