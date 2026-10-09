package com.sam.syncai;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync AI's own dialog. Drop-in replacement for the parts of AlertDialog the app uses,
 * styled to match the drawer/cards (dark surface, rounded, accent buttons) with the
 * same motion system as the rest of the app instead of the stock Android popup.
 */
public final class SyncDialog implements DialogInterface {
    private static final int SURFACE = Color.rgb(14, 18, 31);
    private static final int SURFACE_2 = Color.rgb(21, 27, 44);
    private static final int BUTTON = Color.rgb(23, 30, 49);
    private static final int BORDER = Color.rgb(42, 52, 78);
    private static final int TEXT = Color.rgb(245, 247, 255);
    private static final int BODY = Color.rgb(216, 223, 241);
    private static final int MUTED = Color.rgb(151, 164, 190);
    private static final float DIM = 0.62f;

    private final Context ctx;
    private final int accent;
    private final Dialog dialog;
    private final FrameLayout root;
    private final LinearLayout card;
    private final TextView[] buttons = new TextView[3]; // positive, negative, neutral
    private final List<View> rows = new ArrayList<>();
    private DialogInterface.OnShowListener showListener;
    private boolean dismissing;

    // ------------------------------------------------------------------ Builder

    public static final class Builder {
        private final Context ctx;
        private final int accent;
        private CharSequence title;
        private CharSequence message;
        private CharSequence[] items;
        private CharSequence[] choiceItems;
        private int checked = -1;
        private View view;
        private OnClickListener itemListener;
        private OnClickListener choiceListener;
        private final CharSequence[] buttonText = new CharSequence[3];
        private final OnClickListener[] buttonListener = new OnClickListener[3];

        public Builder(Context ctx, int accent) {
            this.ctx = ctx;
            this.accent = accent;
        }

        public Builder setTitle(CharSequence t) { title = t; return this; }
        public Builder setMessage(CharSequence m) { message = m; return this; }
        public Builder setView(View v) { view = v; return this; }

        public Builder setItems(CharSequence[] i, OnClickListener l) {
            items = i;
            itemListener = l;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] i, int checkedItem, OnClickListener l) {
            choiceItems = i;
            checked = checkedItem;
            choiceListener = l;
            return this;
        }

        public Builder setPositiveButton(CharSequence t, OnClickListener l) {
            buttonText[0] = t; buttonListener[0] = l; return this;
        }

        public Builder setNegativeButton(CharSequence t, OnClickListener l) {
            buttonText[1] = t; buttonListener[1] = l; return this;
        }

        public Builder setNeutralButton(CharSequence t, OnClickListener l) {
            buttonText[2] = t; buttonListener[2] = l; return this;
        }

        public SyncDialog create() { return new SyncDialog(this); }

        public SyncDialog show() {
            SyncDialog d = create();
            d.show();
            return d;
        }
    }

    // ------------------------------------------------------------------ Construction

    private SyncDialog(Builder b) {
        this.ctx = b.ctx;
        this.accent = b.accent;

        dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar) {
            @Override public void onBackPressed() { SyncDialog.this.dismiss(); }
        };

        root = new FrameLayout(ctx);
        root.setPadding(dp(24), dp(40), dp(24), dp(40));
        root.setOnClickListener(v -> dismiss()); // tap outside the card

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true); // swallow taps so they don't reach the root
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);
        card.setElevation(dp(18));

        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int cardW = Math.min(screenW - dp(48), dp(420));
        root.addView(card, new FrameLayout.LayoutParams(cardW, -2, Gravity.CENTER));

        if (b.title != null) addTitle(b.title);
        addBody(b);
        addButtons(b);

        dialog.setContentView(root);
        applyInsets();
    }

    private void addTitle(CharSequence title) {
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(22), dp(20), dp(22), dp(12));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addBody(Builder b) {
        boolean hasBody = b.message != null || b.items != null || b.choiceItems != null || b.view != null;
        if (!hasBody) return;

        ScrollView scroll = new ScrollView(ctx);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
        // weight 1 + wrap: shrinks and scrolls when content is taller than the screen.
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));

        if (b.message != null) {
            TextView m = new TextView(ctx);
            m.setText(b.message);
            m.setTextSize(14);
            m.setTextColor(BODY);
            m.setLineSpacing(0, 1.15f);
            m.setTextIsSelectable(true);
            m.setPadding(0, dp(2), 0, dp(6));
            body.addView(m, sideMargins(-2, dp(22), 0));
        }

        if (b.items != null) {
            for (int i = 0; i < b.items.length; i++) {
                final int index = i;
                TextView row = row(b.items[i], TEXT);
                row.setOnClickListener(v -> {
                    if (b.itemListener != null) b.itemListener.onClick(this, index);
                    dismiss();
                });
                body.addView(row, rowParams());
                rows.add(row);
            }
        }

        if (b.choiceItems != null) {
            final TextView[] choiceRows = new TextView[b.choiceItems.length];
            final int[] selected = {b.checked};
            for (int i = 0; i < b.choiceItems.length; i++) {
                final int index = i;
                final CharSequence label = b.choiceItems[i];
                TextView row = row(choiceLabel(label, i == selected[0]), i == selected[0] ? accent : TEXT);
                choiceRows[i] = row;
                row.setOnClickListener(v -> {
                    selected[0] = index;
                    for (int k = 0; k < choiceRows.length; k++) {
                        boolean on = k == index;
                        choiceRows[k].setText(choiceLabel(b.choiceItems[k], on));
                        choiceRows[k].setTextColor(on ? accent : TEXT);
                    }
                    if (b.choiceListener != null) b.choiceListener.onClick(this, index);
                });
                body.addView(row, rowParams());
                rows.add(row);
            }
        }

        if (b.view != null) {
            View v = b.view;
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            if (v instanceof EditText) {
                styleEditText((EditText) v);
                body.addView(v, sideMargins(-2, dp(22), dp(4)));
            } else {
                // Content views bring their own ~18dp side padding.
                body.addView(v, sideMargins(-2, dp(4), 0));
            }
        }

        // breathing room under the body
        View pad = new View(ctx);
        body.addView(pad, new LinearLayout.LayoutParams(-1, dp(4)));
    }

    private void addButtons(Builder b) {
        int count = 0;
        for (CharSequence t : b.buttonText) if (t != null) count++;
        if (count == 0) {
            card.addView(new View(ctx), new LinearLayout.LayoutParams(-1, dp(14)));
            return;
        }

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(18), dp(10), dp(18), dp(18));

        // Android order: neutral (left), negative, positive (right).
        int[] order = {2, 1, 0};
        for (int slot : order) {
            if (b.buttonText[slot] == null) continue;
            final int which = slot == 0 ? BUTTON_POSITIVE : slot == 1 ? BUTTON_NEGATIVE : BUTTON_NEUTRAL;
            final OnClickListener listener = b.buttonListener[slot];

            TextView btn = new TextView(ctx);
            btn.setText(b.buttonText[slot]);
            btn.setTextSize(11);
            btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setSingleLine(true);
            boolean primary = slot == 0;
            btn.setTextColor(primary ? Color.WHITE : TEXT);
            btn.setBackground(round(primary ? accent : BUTTON, dp(13)));
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onClick(this, which);
                dismiss();
            });
            Motion.pressable(btn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.setMargins(dp(4), 0, dp(4), 0);
            bar.addView(btn, lp);
            buttons[slot] = btn;
        }
        card.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    // ------------------------------------------------------------------ Public API

    /** Same contract as AlertDialog.getButton: BUTTON_POSITIVE / NEGATIVE / NEUTRAL. */
    public TextView getButton(int which) {
        int slot = which == BUTTON_POSITIVE ? 0 : which == BUTTON_NEGATIVE ? 1 : 2;
        return buttons[slot];
    }

    public void setOnShowListener(DialogInterface.OnShowListener l) { showListener = l; }

    /** Mirrors AlertDialog.isShowing() for callers using this custom dialog wrapper. */
    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void show() {
        if (ctx instanceof Activity) {
            Activity a = (Activity) ctx;
            if (a.isFinishing() || a.isDestroyed()) return;
        }
        dismissing = false;
        card.setAlpha(0f);
        try {
            dialog.show();
        } catch (Exception e) {
            return;
        }
        configureWindow();
        if (showListener != null) showListener.onShow(this);
        card.post(this::playEnter);
    }

    @Override public void dismiss() {
        if (dismissing || !dialog.isShowing()) return;
        dismissing = true;
        if (Motion.reduced(ctx) || (ctx instanceof Activity && ((Activity) ctx).isFinishing())) {
            finishDismiss();
            return;
        }
        card.animate().cancel();
        card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).translationY(dp(10))
                .setStartDelay(0).setDuration(Motion.FAST + 20)
                .setInterpolator(Motion.EASE_IN)
                .withEndAction(this::finishDismiss).start();
        animateDim(currentDim(), 0f, Motion.FAST + 20);
    }

    /** Skip the exit animation (used right before the activity is recreated). */
    public void dismissNow() {
        dismissing = true;
        finishDismiss();
    }

    @Override public void cancel() { dismiss(); }

    // ------------------------------------------------------------------ Motion + window

    private void configureWindow() {
        Window w = dialog.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0f);
        boolean hasEdit = hasEditText(card);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | (hasEdit ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED));
    }

    private void playEnter() {
        if (dismissing) return;
        if (Motion.reduced(ctx)) {
            card.setAlpha(1f);
            setDim(DIM);
            return;
        }
        card.setPivotX(card.getWidth() / 2f);
        card.setPivotY(card.getHeight() / 2f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setTranslationY(dp(18));
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(0).setDuration(Motion.NORMAL + 30)
                .setInterpolator(Motion.EASE_OUT).start();
        animateDim(0f, DIM, Motion.NORMAL);

        // list rows follow the card in with a light stagger
        for (int i = 0; i < rows.size() && i < 10; i++) {
            Motion.enter(rows.get(i), 50 + i * 22L, dp(8));
        }
    }

    private float dimNow = 0f;

    private float currentDim() { return dimNow; }

    private void setDim(float v) {
        dimNow = v;
        Window w = dialog.getWindow();
        if (w != null) w.setDimAmount(v);
    }

    private void animateDim(float from, float to, long duration) {
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(duration);
        a.setInterpolator(Motion.EASE_OUT);
        a.addUpdateListener(x -> setDim((float) x.getAnimatedValue()));
        a.start();
    }

    private void finishDismiss() {
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
            // Activity already gone; nothing to clean up.
        }
    }

    /** Keep the card clear of the status bar / nav bar / keyboard. */
    private void applyInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(dp(24), dp(24) + top, dp(24), dp(24) + bottom);
            return insets;
        });
    }

    // ------------------------------------------------------------------ Helpers

    private TextView row(CharSequence label, int color) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(46));
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(14), dp(10), dp(12), dp(10));
        t.setBackground(round(SURFACE_2, dp(13)));
        Motion.pressable(t);
        return t;
    }

    private CharSequence choiceLabel(CharSequence label, boolean on) {
        return (on ? "●  " : "○  ") + label;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(18), 0, dp(18), dp(6));
        return lp;
    }

    private LinearLayout.LayoutParams sideMargins(int height, int side, int vertical) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.setMargins(side, vertical, side, vertical);
        return lp;
    }

    private void styleEditText(EditText e) {
        e.setBackground(round(SURFACE_2, dp(15)));
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(92, 100, 123));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
    }

    private boolean hasEditText(View v) {
        if (v instanceof EditText) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasEditText(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
