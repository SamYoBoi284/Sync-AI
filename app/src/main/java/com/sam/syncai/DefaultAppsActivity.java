package com.sam.syncai;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** In-app picker for app preferences used by deterministic routing. */
public final class DefaultAppsActivity extends Activity {
    private static final String PREFS = "sync_media_preferences";
    private final List<AppChoice> launchableApps = new ArrayList<>();
    private final List<Picker> pickers = new ArrayList<>();
    private AppTheme palette;

    private static final class AppChoice {
        final String label;
        final String packageName;
        final String displayLabel;
        AppChoice(String label, String packageName) {
            this(label, packageName, label);
        }
        AppChoice(String label, String packageName, String displayLabel) {
            this.label = label;
            this.packageName = packageName;
            this.displayLabel = displayLabel;
        }
        @Override public String toString() { return displayLabel; }
    }

    private static final class Picker {
        final String key;
        final String labelKey;
        final Spinner spinner;
        Picker(String key, String labelKey, Spinner spinner) {
            this.key = key; this.labelKey = labelKey; this.spinner = spinner;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = AppTheme.from(this);
        loadApps();
        render();
    }

    private void loadApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> matches = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : matches) {
            String packageName = info.activityInfo == null ? null : info.activityInfo.packageName;
            if (packageName == null || packageName.equals(getPackageName())) continue;
            String label = String.valueOf(info.loadLabel(pm));
            boolean duplicate = false;
            for (AppChoice app : launchableApps) {
                if (app.packageName.equals(packageName)) { duplicate = true; break; }
            }
            if (!duplicate) launchableApps.add(new AppChoice(label, packageName));
        }
        Collections.sort(launchableApps, Comparator.comparing(a -> a.label.toLowerCase(java.util.Locale.ROOT)));
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(palette.background);

        TextView title = text("DEFAULT APPS", 22, palette.text, true);
        root.addView(title);
        TextView subtitle = text("Choose which installed apps Sync should prefer. You can change these any time.", 13, palette.muted, false);
        subtitle.setPadding(0, dp(7), 0, dp(16));
        root.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(section("MUSIC PLAYER", "Music-capable apps first; other launchable apps remain available as a manual override.",
                "preferred_music_package", "preferred_music_label", "music"));
        body.addView(section("FILE MANAGER", "Apps that can browse/open documents first, with a manual override list.",
                "preferred_file_manager_package", "preferred_file_manager_label", "files"));
        body.addView(section("CAMERA APP", "Camera-capable apps first; capture intents may still be handled by Android.",
                "preferred_camera_package", "preferred_camera_label", "camera"));
        body.addView(section("AUDIO RECORDER", "Apps that handle Android's recording intent first, with a manual override list.",
                "preferred_recorder_package", "preferred_recorder_label", "recorder"));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button cancel = button("CANCEL", false);
        cancel.setOnClickListener(v -> finish());
        Button save = button("SAVE DEFAULTS", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-2, dp(48));
        saveLp.leftMargin = dp(10);
        buttons.addView(cancel);
        buttons.addView(save, saveLp);
        save.setOnClickListener(v -> save());
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(58)));

        setContentView(root);
    }

    private LinearLayout section(String heading, String description, String key, String labelKey, String category) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.surface);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), palette.border);
        card.setBackground(bg);

        TextView h = text(heading, 12, palette.secondary, true);
        h.setLetterSpacing(0.08f);
        card.addView(h);
        TextView d = text(description, 12, palette.muted, false);
        d.setPadding(0, dp(5), 0, dp(9));
        card.addView(d);

        List<AppChoice> choices = new ArrayList<>();
        choices.add(new AppChoice("Ask Android / no preference", ""));
        List<AppChoice> matches = categoryMatches(category);
        choices.addAll(matches);
        java.util.HashSet<String> matchedPackages = new java.util.HashSet<>();
        for (AppChoice app : matches) matchedPackages.add(app.packageName);
        for (AppChoice app : launchableApps) {
            if (!matchedPackages.contains(app.packageName)) {
                choices.add(new AppChoice(app.label, app.packageName, "Manual override • " + app.label));
            }
        }
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
        String selected = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "");
        int selectedIndex = 0;
        for (int i = 1; i < choices.size(); i++) {
            if (choices.get(i).packageName.equals(selected)) { selectedIndex = i; break; }
        }
        spinner.setSelection(selectedIndex);
        card.addView(spinner);
        pickers.add(new Picker(key, labelKey, spinner));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private List<AppChoice> categoryMatches(String category) {
        java.util.LinkedHashSet<String> packageNames = new java.util.LinkedHashSet<>();
        PackageManager pm = getPackageManager();
        try {
            if ("music".equals(category)) {
                Intent music = new Intent(Intent.ACTION_MAIN);
                music.addCategory(Intent.CATEGORY_APP_MUSIC);
                addResolvedPackages(pm, music, packageNames);
                Intent audioView = new Intent(Intent.ACTION_VIEW);
                audioView.setType("audio/*");
                addResolvedPackages(pm, audioView, packageNames);
            } else if ("files".equals(category)) {
                Intent files = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                files.addCategory(Intent.CATEGORY_OPENABLE);
                files.setType("*/*");
                addResolvedPackages(pm, files, packageNames);
                Intent browse = new Intent(Intent.ACTION_GET_CONTENT);
                browse.addCategory(Intent.CATEGORY_OPENABLE);
                browse.setType("*/*");
                addResolvedPackages(pm, browse, packageNames);
            } else if ("camera".equals(category)) {
                addResolvedPackages(pm, new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), packageNames);
                addResolvedPackages(pm, new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE), packageNames);
                addResolvedPackages(pm, new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), packageNames);
            } else if ("recorder".equals(category)) {
                addResolvedPackages(pm, new Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION), packageNames);
            }
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "DefaultAppsActivity", "CATEGORY_DISCOVERY_FAILED",
                    error, "category=" + category);
        }
        List<AppChoice> result = new ArrayList<>();
        for (AppChoice app : launchableApps) {
            if (packageNames.contains(app.packageName)) result.add(app);
        }
        return result;
    }

    private void addResolvedPackages(PackageManager pm, Intent intent, java.util.Set<String> target) {
        List<ResolveInfo> matches = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo info : matches) {
            if (info.activityInfo != null && info.activityInfo.packageName != null
                    && !info.activityInfo.packageName.equals(getPackageName())) {
                target.add(info.activityInfo.packageName);
            }
        }
    }

    private void save() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        for (Picker picker : pickers) {
            Object selected = picker.spinner.getSelectedItem();
            AppChoice app = selected instanceof AppChoice ? (AppChoice) selected : null;
            if (app == null || app.packageName.isEmpty()) {
                editor.remove(picker.key).remove(picker.labelKey);
            } else {
                editor.putString(picker.key, app.packageName).putString(picker.labelKey, app.label);
            }
        }
        editor.apply();
        Toast.makeText(this, "Default apps saved.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(primary ? AppTheme.onAccent(palette.primary) : palette.text);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? palette.primary : palette.surface2);
        bg.setCornerRadius(dp(24));
        b.setBackground(bg);
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
