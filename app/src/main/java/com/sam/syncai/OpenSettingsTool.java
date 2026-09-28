package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.Map;

public final class OpenSettingsTool implements SyncTool {
    private final Context context;

    public OpenSettingsTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "open_settings"; }

    @Override public String getDescription() {
        return "Open the Android Settings app.";
    }

    @Override public String getInputSchema() {
        return "{}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Android Settings opened.";
    }
}
