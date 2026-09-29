package com.sam.syncai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class CanvasActivity extends Activity {
    private TextView titleView;
    private TextView bodyView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 8, 14));
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = text("SYNC//AI", 20, Color.rgb(240, 242, 250), true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button close = button("CLOSE");
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(76), dp(44)));
        root.addView(header);

        TextView subtitle = text("AI CANVAS  •  AI-OWNED WORKSPACE", 10,
                Color.rgb(80, 215, 255), true);
        subtitle.setPadding(0, 0, 0, dp(8));
        root.addView(subtitle);

        TextView hint = text(
                "Sync AI writes, updates, and organizes this workspace for you. " +
                "There is no separate chat box here.",
                11, Color.rgb(145, 153, 177), false);
        hint.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(hint);

        titleView = text("Untitled Canvas", 22, Color.rgb(240, 242, 250), true);
        titleView.setPadding(dp(15), dp(13), dp(15), dp(13));
        titleView.setBackground(round(Color.rgb(21, 25, 38), dp(14)));
        root.addView(titleView, new LinearLayout.LayoutParams(-1, dp(60)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        bodyView = text("", 16, Color.rgb(240, 242, 250), false);
        bodyView.setGravity(Gravity.TOP | Gravity.START);
        bodyView.setLineSpacing(0, 1.15f);
        bodyView.setPadding(dp(16), dp(16), dp(16), dp(24));
        bodyView.setTextIsSelectable(true);
        bodyView.setBackground(round(Color.rgb(15, 18, 28), dp(16)));
        scroll.addView(bodyView, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button refresh = button("REFRESH");
        refresh.setOnClickListener(v -> loadCanvas());
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(-1, dp(48));
        refreshLp.topMargin = dp(9);
        root.addView(refresh, refreshLp);

        setContentView(root);
        loadCanvas();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bodyView != null) loadCanvas();
    }

    private void loadCanvas() {
        try {
            String content = CanvasManager.read(this);
            if (content == null || content.trim().isEmpty()) {
                titleView.setText("Untitled Canvas");
                bodyView.setText("Canvas is empty. Tell Sync AI what you want to plan, draft, outline, or revise.");
                return;
            }

            int split = content.indexOf("\n\n");
            if (split >= 0) {
                titleView.setText(content.substring(0, split).trim());
                bodyView.setText(content.substring(split + 2));
            } else {
                titleView.setText("Untitled Canvas");
                bodyView.setText(content);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Canvas load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.rgb(240, 242, 250));
        b.setTextSize(11);
        b.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(Color.rgb(28, 33, 49), dp(13)));
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.DEFAULT,
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return t;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
