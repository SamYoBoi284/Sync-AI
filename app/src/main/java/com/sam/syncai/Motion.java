package com.sam.syncai;

import android.content.Context;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Central motion system for Sync AI.
 * All durations / easing live here so the whole app can be tuned from one place.
 * Respects Android's "Remove animations" / animator duration scale = 0.
 */
public final class Motion {
    private Motion() {}

    // ---- Durations (ms) ----
    public static final long FAST = 140;
    public static final long NORMAL = 240;
    public static final long SLOW = 320;
    public static final long DRAWER_OPEN = 300;
    public static final long DRAWER_CLOSE = 230;
    public static final long STAGGER = 28;

    // ---- Easing ----
    /** Smooth ease-out with a strong start, used for most entrances. */
    public static final Interpolator EASE_OUT = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    /** Symmetric ease for state changes. */
    public static final Interpolator EASE_IN_OUT = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    /** Accelerating ease for exits. */
    public static final Interpolator EASE_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    /** Tiny settle used for the drawer (very subtle overshoot, not bouncy). */
    public static final Interpolator SETTLE = new OvershootInterpolator(0.55f);
    public static final Interpolator DECEL = new DecelerateInterpolator(1.6f);

    /** True when the user disabled animations system-wide. */
    public static boolean reduced(Context c) {
        try {
            float scale = Settings.Global.getFloat(
                    c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    /** Fade in from nothing with a small upward translation. */
    public static void enter(View v, long delay, float dyPx) {
        if (v == null) return;
        if (reduced(v.getContext())) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            return;
        }
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dyPx);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(NORMAL)
                .setInterpolator(EASE_OUT)
                .withEndAction(() -> v.animate().setStartDelay(0)).start();
    }

    /** Fade in from nothing (used for chat messages). Slight rise + tiny scale. */
    public static void messageIn(View v, boolean fromUser) {
        if (v == null) return;
        if (reduced(v.getContext())) return;
        float dy = v.getResources().getDisplayMetrics().density * 10f;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dy);
        v.setScaleX(0.97f);
        v.setScaleY(0.97f);
        v.setPivotX(fromUser ? v.getWidth() : 0f);
        v.setPivotY(v.getHeight());
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(0).setDuration(SLOW).setInterpolator(EASE_OUT).start();
    }

    /** Stagger the direct children of a group in. */
    public static void stagger(ViewGroup group, long baseDelay, float dyPx) {
        if (group == null) return;
        int n = group.getChildCount();
        for (int i = 0; i < n; i++) {
            enter(group.getChildAt(i), baseDelay + i * STAGGER, dyPx);
        }
    }

    /** Subtle pressed scale + alpha on touch; does NOT consume the event, so clicks still fire. */
    public static void pressable(View v) {
        if (v == null) return;
        v.setOnTouchListener((view, e) -> {
            if (reduced(view.getContext())) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().cancel();
                    view.animate().scaleX(0.96f).scaleY(0.96f)
                            .alpha(view.isEnabled() ? 0.85f : view.getAlpha())
                            .setStartDelay(0).setDuration(90).setInterpolator(EASE_OUT).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f)
                            .alpha(view.isEnabled() ? 1f : 0.45f)
                            .setStartDelay(0).setDuration(180).setInterpolator(EASE_OUT).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Animated enabled/disabled look (used by SEND / IMPORT). */
    public static void setEnabledAnimated(View v, boolean enabled) {
        if (v == null) return;
        v.setEnabled(enabled);
        float target = enabled ? 1f : 0.45f;
        if (reduced(v.getContext())) {
            v.setAlpha(target);
            return;
        }
        v.animate().cancel();
        v.animate().alpha(target).setStartDelay(0).setDuration(FAST).setInterpolator(EASE_IN_OUT).start();
    }

    /** Crossfade a TextView's text (used for voice state + model status). */
    public static void swapText(android.widget.TextView t, String value) {
        if (t == null) return;
        if (value.contentEquals(t.getText())) return;
        if (reduced(t.getContext())) {
            t.setText(value);
            return;
        }
        t.animate().cancel();
        t.animate().alpha(0f).setStartDelay(0).setDuration(80).withEndAction(() -> {
            t.setText(value);
            t.animate().alpha(1f).setStartDelay(0).setDuration(FAST).setInterpolator(EASE_OUT).start();
        }).start();
    }
}
