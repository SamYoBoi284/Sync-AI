package com.sam.syncai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class CanvasActivity extends Activity {
    private EditText titleInput;
    private EditText bodyInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 8, 14));
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("SYNC//AI", 20, Color.rgb(240, 242, 250), true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button close = button("CLOSE");
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(72), dp(44)));
        root.addView(header);

        TextView subtitle = text("AI CANVAS  •  EDITABLE WORKSPACE", 10, Color.rgb(80, 215, 255), true);
        subtitle.setPadding(0, 0, 0, dp(10));
        root.addView(subtitle);

        titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setTextColor(Color.rgb(240, 242, 250));
        titleInput.setHintTextColor(Color.rgb(92, 100, 123));
        titleInput.setTextSize(21);
        titleInput.setHint("Canvas title");
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        titleInput.setBackground(round(Color.rgb(21, 25, 38), dp(14)));
        root.addView(titleInput, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView hint = text("Sync AI can read and update this canvas through its workspace tools.", 11,
                Color.rgb(145, 153, 177), false);
        hint.setPadding(dp(4), dp(7), dp(4), dp(9));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        bodyInput = new EditText(this);
        bodyInput.setTextColor(Color.rgb(240, 242, 250));
        bodyInput.setHintTextColor(Color.rgb(92, 100, 123));
        bodyInput.setTextSize(15);
        bodyInput.setGravity(Gravity.TOP | Gravity.START);
        bodyInput.setHint("Start writing, planning, outlining, or drafting here…");
        bodyInput.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bodyInput.setSingleLine(false);
        bodyInput.setPadding(dp(15), dp(15), dp(15), dp(15));
        bodyInput.setBackground(round(Color.rgb(15, 18, 28), dp(16)));
        scroll.addView(bodyInput, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(9), 0, 0);

        Button save = button("SAVE");
        save.setOnClickListener(v -> saveCanvas());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button clear = button("CLEAR");
        clear.setOnClickListener(v -> {
            titleInput.setText("");
            bodyInput.setText("");
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearLp.leftMargin = dp(6);
        actions.addView(clear, clearLp);

        root.addView(actions);
        setContentView(root);
        loadCanvas();
    }

    @Override protected void onResume() {
        super.onResume();
        // AI may have edited the canvas while this Activity was paused/backgrounded.
        if (bodyInput != null) loadCanvas();
    }

    private void loadCanvas() {
        try {
            String content = CanvasManager.read(this);
            if (content == null || content.trim().isEmpty()) {
                if (titleInput != null && titleInput.getText().length() == 0) {
                    titleInput.setText("Untitled Canvas");
                }
                return;
            }
            int split = content.indexOf("\n\n");
            if (split >= 0) {
                titleInput.setText(content.substring(0, split).trim());
                bodyInput.setText(content.substring(split + 2));
            } else {
                titleInput.setText("Untitled Canvas");
                bodyInput.setText(content);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Canvas load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCanvas() {
        try {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) title = "Untitled Canvas";
            String body = bodyInput.getText().toString();
            CanvasManager.write(this, title, body);
            Toast.makeText(this, "Canvas saved.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Canvas save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        t.setTypeface(android.graphics.Typeface.DEFAULT, bold
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
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
