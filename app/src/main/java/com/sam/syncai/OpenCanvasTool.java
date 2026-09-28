package com.sam.syncai;

import android.content.Context;
import android.content.Intent;

import java.util.Map;

public final class OpenCanvasTool implements SyncTool {
    private final Context context;

    public OpenCanvasTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "open_canvas"; }

    @Override public String getDescription() {
        return "Open Sync//AI's local drawing canvas.";
    }

    @Override public String getInputSchema() {
        return "{}";
    }

    @Override public String execute(Map<String, String> args) {
        Intent intent = new Intent(context, CanvasActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "SUCCESS: Opened the Sync//AI canvas.";
    }
}
