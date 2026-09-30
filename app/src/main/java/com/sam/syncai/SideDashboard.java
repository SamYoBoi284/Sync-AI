package com.sam.syncai;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
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
    private FrameLayout pages;
    private LinearLayout mainContent;
    private LinearLayout settingsContent;
    private View scrim;
    private boolean open;
    private boolean dragging;
    private boolean settingsShown;
    private float progress; // 0 = fully hidden, 1 = fully open
    private ValueAnimator drawerAnim;
    private int topInset;

    // touch tracking for drag-to-close
    private float downX, downY;
    private boolean closeDrag;
    private VelocityTracker velocity;
    private final int touchSlop;

    public SideDashboard(Activity activity, FrameLayout host, Actions actions) {
        super(activity);
        this.activity = activity;
        this.actions = actions;
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        setClickable(true);
        build();
        host.addView(this, new FrameLayout.LayoutParams(-1, -1));
        setVisibility(INVISIBLE);
    }

    private int hiddenOffset() { return dp(340); }

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
        panel.setElevation(dp(16));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(320), -1, Gravity.START);
        pp.setMargins(0, dp(8), 0, dp(8));
        addView(panel, pp);

        // Both screens live in the same surface so Settings <-> Main feels like navigation.
        pages = new FrameLayout(activity);
        buildMainContent();
        buildSettingsContent();
        pages.addView(mainContent, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(settingsContent, new FrameLayout.LayoutParams(-1, -1));
        settingsContent.setVisibility(GONE);
        panel.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));

        applyProgress(0f);
    }

    /** Push the drawer below the system status bar (+ a little breathing room). */
    public void setTopInset(int px) {
        topInset = px;
        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        pp.topMargin = dp(8) + px;
        panel.setLayoutParams(pp);
    }

    private void buildMainContent() {
        mainContent = new LinearLayout(activity);
        mainContent.setOrientation(LinearLayout.VERTICAL);

        TextView brand = label("SYNC AI", 24, Color.rgb(240,242,250), true);
        mainContent.addView(brand, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView subtitle = label("LOCAL AGENT", 10, Color.rgb(80,215,255), true);
        mainContent.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(28)));

        section(mainContent, "CHATS");
        item(mainContent, "＋  New chat", v -> actions.newChat(), true);
        item(mainContent, "☷  Chat history", v -> actions.chats(), true);

        section(mainContent, "WORKSPACE");
        item(mainContent, "◉  Voice mode", v -> actions.assistant(), true);
        item(mainContent, "✎  Workspace", v -> actions.files(), true);

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
        Motion.pressable(back);
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
        item(settingsContent, "📎  Files / memory", v -> actions.files(), true);
        item(settingsContent, "▣  Local models", v -> actions.models(), true);
        item(settingsContent, "↓  Import model", v -> actions.importModel(), true);
        item(settingsContent, "▤  Runtime", v -> actions.runtime(), true);

        section(settingsContent, "PERSONALIZATION");
        item(settingsContent, "🎨  Accent colors + memory", v -> actions.personalization(), true);
        item(settingsContent, "◍  Assistant settings", v -> actions.tone(), true);

        section(settingsContent, "ABOUT");
        item(settingsContent, "ⓘ  About & diagnostics", v -> actions.about(), true);

        TextView hint = label("Local models, memory, and preferences are kept on this phone.", 11,
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
        Motion.pressable(close);
        target.addView(close, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    // ---------------------------------------------------------------- Settings <-> Main

    private void showSettings() {
        if (settingsShown) return;
        settingsShown = true;
        slidePages(mainContent, settingsContent, -1);
    }

    private void showMain() {
        if (!settingsShown) return;
        settingsShown = false;
        slidePages(settingsContent, mainContent, 1);
    }

    /** Instantly reset to the main page (used while the drawer is hidden). */
    private void showMainInstant() {
        settingsShown = false;
        for (View v : new View[]{mainContent, settingsContent}) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationX(0f);
        }
        settingsContent.setVisibility(GONE);
        mainContent.setVisibility(VISIBLE);
        resetChildren(mainContent);
    }

    /** direction: -1 = outgoing slides left / incoming from right; +1 = the reverse. */
    private void slidePages(View out, View in, int direction) {
        if (Motion.reduced(activity)) {
            out.setVisibility(GONE);
            in.setVisibility(VISIBLE);
            in.setAlpha(1f);
            in.setTranslationX(0f);
            return;
        }
        float dist = dp(48);
        out.animate().cancel();
        in.animate().cancel();

        in.setVisibility(VISIBLE);
        in.setAlpha(0f);
        in.setTranslationX(-direction * dist);

        out.animate().alpha(0f).translationX(direction * dist * 0.6f)
                .setDuration(Motion.FAST).setInterpolator(Motion.EASE_IN)
                .withEndAction(() -> {
                    out.setVisibility(GONE);
                    out.setAlpha(1f);
                    out.setTranslationX(0f);
                }).start();
        in.animate().alpha(1f).translationX(0f)
                .setStartDelay(50).setDuration(Motion.NORMAL).setInterpolator(Motion.EASE_OUT).start();
        if (in instanceof LinearLayout) staggerChildren((LinearLayout) in, 60);
    }

    // ---------------------------------------------------------------- Layout helpers

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
        t.setOnClickListener(v -> {
            listener.onClick(v);
            if (closeAfter) close();
        });
        Motion.pressable(t);
        target.addView(t, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)t.getLayoutParams();
        lp.bottomMargin = dp(5);
        t.setLayoutParams(lp);
    }

    private void staggerChildren(LinearLayout group, long baseDelay) {
        Motion.stagger(group, baseDelay, dp(10));
    }

    private void resetChildren(LinearLayout group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            c.animate().cancel();
            c.setAlpha(1f);
            c.setTranslationY(0f);
        }
    }

    // ---------------------------------------------------------------- Drawer motion

    /** Single source of truth: panel position and scrim are both derived from progress. */
    private void applyProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        panel.setTranslationX(-hiddenOffset() * (1f - progress));
        scrim.setAlpha(progress);
    }

    private void animateTo(float target, long duration, Interpolator interp) {
        if (drawerAnim != null) drawerAnim.cancel();
        if (Motion.reduced(activity)) {
            applyProgress(target);
            finishIfHidden();
            return;
        }
        float from = progress;
        // Shorter run if the panel is already most of the way there (keeps velocity feel).
        long d = Math.max(90, (long) (duration * Math.abs(target - from)));
        drawerAnim = ValueAnimator.ofFloat(from, target);
        drawerAnim.setDuration(d);
        drawerAnim.setInterpolator(interp);
        drawerAnim.addUpdateListener(a -> applyProgress((float) a.getAnimatedValue()));
        drawerAnim.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;
            @Override public void onAnimationCancel(Animator animation) { canceled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                applyProgress(target);
                finishIfHidden();
            }
        });
        drawerAnim.start();
    }

    private void finishIfHidden() {
        if (progress <= 0f && !open) {
            setVisibility(INVISIBLE);
            showMainInstant();
        }
    }

    /** Called by the edge-swipe zone when a drag begins. */
    public void beginDrag() {
        if (open) return;
        if (drawerAnim != null) drawerAnim.cancel();
        dragging = true;
        showMainInstant();
        setVisibility(VISIBLE);
        applyProgress(progress);
    }

    /** Called with the finger's horizontal distance (px) from where the drag started. */
    public void dragTo(float dxPx) {
        if (!dragging) return;
        applyProgress(dxPx / hiddenOffset());
    }

    /** Release: settle open or closed depending on distance + fling velocity (px/s). */
    public void endDrag(float velocityX) {
        if (!dragging) return;
        dragging = false;
        boolean shouldOpen = velocityX > dp(500) || (progress > 0.4f && velocityX > -dp(500));
        if (shouldOpen) {
            open = true;
            animateTo(1f, Motion.DRAWER_OPEN, Motion.EASE_OUT);
            staggerChildren(mainContent, 70);
        } else {
            open = false;
            animateTo(0f, Motion.DRAWER_CLOSE, Motion.EASE_IN_OUT);
        }
    }

    public void open() {
        if (open) return;
        if (drawerAnim != null) drawerAnim.cancel();
        showMainInstant();
        open = true;
        dragging = false;
        setVisibility(VISIBLE);
        animateTo(1f, Motion.DRAWER_OPEN, Motion.EASE_OUT);
        if (!Motion.reduced(activity)) {
            // Items enter just after the panel starts moving.
            for (int i = 0; i < mainContent.getChildCount(); i++) {
                mainContent.getChildAt(i).setAlpha(0f);
            }
            staggerChildren(mainContent, 70);
        }
    }

    public void close() {
        if (!open && progress <= 0f) {
            setVisibility(INVISIBLE);
            return;
        }
        open = false;
        dragging = false;
        animateTo(0f, Motion.DRAWER_CLOSE, Motion.EASE_IN_OUT);
    }

    public boolean isOpen() { return open; }

    // ---------------------------------------------------------------- Drag-to-close touch handling

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!open) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                closeDrag = false;
                resetVelocity(e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) velocity.addMovement(e);
                float dx = e.getX() - downX;
                float dy = Math.abs(e.getY() - downY);
                if (!closeDrag && dx < -touchSlop && Math.abs(dx) > dy * 1.3f) {
                    closeDrag = true;
                    if (drawerAnim != null) drawerAnim.cancel();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!open) return super.onTouchEvent(e);
        if (velocity == null) resetVelocity(e);
        velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (closeDrag) {
                    float dx = e.getX() - downX;
                    applyProgress(1f + Math.min(0f, dx + touchSlop) / hiddenOffset());
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (closeDrag) {
                    velocity.computeCurrentVelocity(1000);
                    float vx = velocity.getXVelocity();
                    closeDrag = false;
                    if (vx < -dp(500) || progress < 0.6f) {
                        open = false;
                        animateTo(0f, Motion.DRAWER_CLOSE, Motion.EASE_IN_OUT);
                    } else {
                        animateTo(1f, Motion.DRAWER_OPEN, Motion.EASE_OUT);
                    }
                }
                velocity.recycle();
                velocity = null;
                return true;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    private void resetVelocity(MotionEvent e) {
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    // ---------------------------------------------------------------- Utils

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
