package com.sam.syncai;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SyncAssistantSettingsActivity extends Activity {
    private static final int BG = Color.rgb(7, 8, 14);
    private static final int TEXT = Color.rgb(240, 242, 250);
    private static final int MUTED = Color.rgb(145, 153, 177);
    private static final int CYAN = Color.rgb(80, 215, 255);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(28), dp(24), dp(28), dp(24));
        root.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("SYNC AI");
        title.setTextColor(TEXT);
        title.setTextSize(28);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Digital assistant settings");
        subtitle.setTextColor(CYAN);
        subtitle.setTextSize(14);
        root.addView(subtitle);

        TextView body = new TextView(this);
        body.setText("\nSync AI uses Android's VoiceInteractionService for system assistant invocation. Microphone access is requested only when Voice Mode is active.\n\nSelect Sync AI as the default digital assistant to use the Side button / system assistant gesture.");
        body.setTextColor(MUTED);
        body.setTextSize(15);
        body.setLineSpacing(0, 1.2f);
        root.addView(body);

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
