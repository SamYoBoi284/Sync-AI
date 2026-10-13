package com.sam.syncai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SideDashboard extends FrameLayout {
    public interface Actions {
        void newChat();
        void chats();
        void models();
        void importModel();
        void runtime();
        void memory();
        void personalization();
        void tone();
        void about();
        void assistant();
        void files();
        void canvas();
        void toggleVoiceOutput();
    }

    private final Activity activity;
    private final Actions actions;
    private LinearLayout panel;
    private LinearLayout mainContent;
    private LinearLayout settingsContent;
    private View scrim;
    private boolean open;

    public SideDashboard(Activity activity, FrameLayout host, Actions actions) {
        super(activity);
        this.activity = activity;
        this.actions = actions;
        setClickable(true);
        build();
        host.addView(this, new FrameLayout.LayoutParams(-1, -1));
        setVisibility(INVISIBLE);
    }

    private void build() {
        setBackgroundColor(Color.TRANSPARENT);

        scrim = new View(activity);
        scrim.setBackgroundColor(Color.argb(150, 0, 0, 0));
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> close());
        addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(22), dp(14), dp(18));
        panel.setBackground(round(Color.rgb(14, 17, 27), dp(24)));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(320), -1, Gravity.START);
        pp.setMargins(0, dp(8), 0, dp(8));
        addView(panel, pp);

        buildMainContent();
        buildSettingsContent();
        panel.addView(mainContent, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(settingsContent, new LinearLayout.LayoutParams(-1, 0, 1));
        settingsContent.setVisibility(GONE);
        panel.setTranslationX(-dp(340));
    }

    private void buildMainContent() {
        mainContent = new LinearLayout(activity);
        mainContent.setOrientation(LinearLayout.VERTICAL);

        TextView brand = label("SYNC//AI", 24, Color.rgb(240,242,250), true);
        mainContent.addView(brand, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView subtitle = label("LOCAL AGENT", 10, Color.rgb(80,215,255), true);
        mainContent.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(28)));

        section(mainContent, "CHATS");
        item(mainContent, "＋  New chat", v -> actions.newChat(), true);
        item(mainContent, "☷  Chat history", v -> actions.chats(), true);

        section(mainContent, "WORKSPACE");
        item(mainContent, "◎  Default assistant", v -> actions.assistant(), true);
        item(mainContent, "✎  Canvas", v -> actions.canvas(), true);

        section(mainContent, "SETTINGS");
        item(mainContent, "⚙  Settings", v -> showSettings(), false);

        TextView hint = label("Everything stays local unless a tool explicitly opens another Android app.", 11,
                Color.rgb(145,153,177), false);
        hint.setPadding(0, dp(16), dp(8), 0);
        mainContent.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1));

        addCloseButton(mainContent);
    }

    private void buildSettingsContent() {
        settingsContent = new LinearLayout(activity);
        settingsContent.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = label("‹", 30, Color.rgb(240,242,250), true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.rgb(21,25,38), dp(13)));
        back.setOnClickListener(v -> showMain());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(48), dp(46)));

        TextView title = label("SETTINGS", 18, Color.rgb(240,242,250), true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        titleLp.leftMargin = dp(10);
        titleRow.addView(title, titleLp);
        settingsContent.addView(titleRow);

        section(settingsContent, "ASSISTANT");
        item(settingsContent, "◉  Voice output", v -> actions.toggleVoiceOutput(), true);

        section(settingsContent, "WORKSPACE");
        item(settingsContent, "📎  Files", v -> actions.files(), true);
        item(settingsContent, "▣  Models", v -> actions.models(), true);
        item(settingsContent, "↓  Import model", v -> actions.importModel(), true);
        item(settingsContent, "▤  Runtime", v -> actions.runtime(), true);

        section(settingsContent, "PERSONALIZATION");
        item(settingsContent, "🎨  Accent colors", v -> actions.personalization(), true);
        item(settingsContent, "◍  Assistant tone", v -> actions.tone(), true);
        item(settingsContent, "🧠  Memory", v -> actions.memory(), true);

        section(settingsContent, "ABOUT");
        item(settingsContent, "ⓘ  About & diagnostics", v -> actions.about(), true);

        TextView hint = label("Local models, memory, workspace files, and preferences are kept on this phone.", 11,
                Color.rgb(145,153,177), false);
        hint.setPadding(0, dp(16), dp(8), 0);
        settingsContent.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1));

        addCloseButton(settingsContent);
    }

    private void addCloseButton(LinearLayout target) {
        TextView close = label("CLOSE", 11, Color.rgb(240,242,250), true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(Color.rgb(28,33,49), dp(13)));
        close.setOnClickListener(v -> close());
        target.addView(close, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private void showSettings() {
        mainContent.setVisibility(GONE);
        settingsContent.setVisibility(VISIBLE);
    }

    private void showMain() {
        settingsContent.setVisibility(GONE);
        mainContent.setVisibility(VISIBLE);
    }

    private void section(LinearLayout target, String title) {
        TextView t = label(title, 9, Color.rgb(145,153,177), true);
        t.setPadding(0, dp(16), 0, dp(6));
        target.addView(t, new LinearLayout.LayoutParams(-1, dp(28)));
    }

    private void item(LinearLayout target, String title, View.OnClickListener listener, boolean closeAfter) {
        TextView t = label(title, 14, Color.rgb(240,242,250), false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(12), 0, dp(8), 0);
        t.setBackground(round(Color.rgb(21,25,38), dp(13)));
        if (listener != null) {
            t.setOnClickListener(v -> {
                listener.onClick(v);
                if (closeAfter) close();
            });
        }
        target.addView(t, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)t.getLayoutParams();
        lp.bottomMargin = dp(5);
        t.setLayoutParams(lp);
    }

    public void open() {
        if (open) return;
        showMain();
        open = true;
        setVisibility(VISIBLE);
        scrim.animate().alpha(1f).setDuration(180).start();
        panel.animate().translationX(0f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
    }

    public void close() {
        if (!open) {
            setVisibility(INVISIBLE);
            return;
        }
        open = false;
        showMain();
        scrim.animate().alpha(0f).setDuration(160).start();
        panel.animate().translationX(-dp(340)).setDuration(220).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> setVisibility(INVISIBLE)).start();
    }

    public boolean isOpen() { return open; }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
