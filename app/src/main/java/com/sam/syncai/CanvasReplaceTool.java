package com.sam.syncai;

import android.content.Context;
import java.util.Map;

public final class CanvasReplaceTool implements SyncTool {
    private final Context context;
    public CanvasReplaceTool(Context context) { this.context = context.getApplicationContext(); }
    @Override public String getName() { return "replace_canvas_text"; }
    @Override public String getDescription() { return "Replace a specific text passage in the AI canvas while preserving the rest of the document."; }
    @Override public String getInputSchema() { return "{\"find\":\"old text\",\"replace\":\"new text\"}"; }
    @Override public String execute(Map<String, String> args) throws Exception {
        String find = args.get("find");
        String replacement = args.get("replace");
        if (find == null || find.isEmpty()) throw new IllegalArgumentException("find cannot be empty.");
        String content = CanvasManager.read(context);
        if (!content.contains(find)) return "ERROR: The requested canvas text was not found.";
        CanvasManager.writeRaw(context, content.replace(find, replacement == null ? "" : replacement));
        return "SUCCESS: Canvas text replaced.";
    }
}
