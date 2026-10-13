package com.sam.syncai;

import android.content.Context;
import java.util.Map;

public final class CanvasReadTool implements SyncTool {
    private final Context context;
    public CanvasReadTool(Context context) { this.context = context.getApplicationContext(); }
    @Override public String getName() { return "read_canvas"; }
    @Override public String getDescription() { return "Read the current AI canvas document so you can plan, revise, or continue it."; }
    @Override public String getInputSchema() { return "{}"; }
    @Override public String execute(Map<String, String> args) throws Exception {
        String content = CanvasManager.read(context);
        if (content.length() > 12000) content = content.substring(0, 12000) + "\n[truncated]";
        return content.isEmpty() ? "CANVAS_EMPTY" : "CANVAS:\n" + content;
    }
}
