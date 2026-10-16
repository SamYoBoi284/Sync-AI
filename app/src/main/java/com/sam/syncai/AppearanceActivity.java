package com.sam.syncai;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Full-screen Appearance studio, independent of personality and assistant behavior. */
public final class AppearanceActivity extends Activity {
    private AppPreferences preferences;
    private AppTheme palette;
    private LinearLayout root;
    private SyncCoreView coreOrb;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = new AppPreferences(this);
        render();
    }

    private void render() {
        palette = AppTheme.from(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(dp(18), dp(10), dp(18), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, palette.text, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(palette.surface2, dp(14)));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        heading.addView(text("APPEARANCE", 17, palette.text, true));
        heading.addView(text("CHOOSE YOUR SYNC IDENTITY", 10, palette.secondary, true));
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(12));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addHero(content);
        addSection(content, "MODE", "Light, dark, or follow your phone. Applies to every identity below.");
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setPadding(0, dp(4), 0, dp(12));
        String currentMode = preferences.getThemeMode();
        for (int i = 0; i < AppPreferences.THEME_MODE_IDS.length; i++) {
            final String modeId = AppPreferences.THEME_MODE_IDS[i];
            boolean on = modeId.equals(currentMode);
            Button modeButton = new Button(this);
            modeButton.setText(AppPreferences.THEME_MODE_NAMES[i]);
            modeButton.setAllCaps(false);
            modeButton.setTextSize(12);
            modeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            modeButton.setTextColor(on ? AppTheme.onAccent(palette.primary) : palette.text);
            GradientDrawable modeBg = round(on ? palette.primary : palette.surface2, dp(14));
            if (!on) modeBg.setStroke(dp(1), palette.border);
            modeButton.setBackground(modeBg);
            Motion.pressable(modeButton);
            modeButton.setOnClickListener(v -> {
                if (modeId.equals(preferences.getThemeMode())) return;
                preferences.setThemeMode(modeId);
                SyncEventLogger.record(this, "Appearance", "THEME_MODE_CHANGED", "INFO", "mode=" + modeId);
                render();
            });
            LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) modeLp.leftMargin = dp(8);
            modeRow.addView(modeButton, modeLp);
        }
        content.addView(modeRow, new LinearLayout.LayoutParams(-1, -2));

        addSection(content, "IDENTITY THEMES",
                "Choose a visual identity. Tools, chats, and model settings remain unchanged.");
        for (int i=0;i<AppTheme.IDS.length;i+=2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (i>0) row.setPadding(0,dp(8),0,0);
            addThemeCard(row, AppTheme.IDS[i]);
            if (i+1<AppTheme.IDS.length) addThemeCard(row, AppTheme.IDS[i+1]);
            content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }

        addSection(content,"ACCENT COLOR","Accent color is independent from the selected identity.");
        HorizontalScrollView accentScroll = new HorizontalScrollView(this);
        accentScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout accents = new LinearLayout(this);
        accents.setGravity(Gravity.CENTER_VERTICAL);
        accents.setPadding(0,dp(4),0,dp(8));
        int selectedAccent = preferences.getAccent();
        for (int i=0;i<AppPreferences.ACCENTS.length;i++) {
            final int index=i;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8),dp(7),dp(8),dp(7));
            View swatch = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(AppPreferences.ACCENTS[i]);
            dot.setStroke(dp(i==selectedAccent?3:1),i==selectedAccent?palette.text:palette.border);
            swatch.setBackground(dot);
            chip.addView(swatch,new LinearLayout.LayoutParams(dp(28),dp(28)));
            TextView name=text(AppPreferences.ACCENT_NAMES[i],10,
                    i==selectedAccent?palette.text:palette.muted,i==selectedAccent);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(-2,-2);
            nameLp.topMargin=dp(5);
            chip.addView(name,nameLp);
            chip.setContentDescription("Accent "+AppPreferences.ACCENT_NAMES[i]);
            chip.setOnClickListener(v->{
                preferences.setAccent(index);
                SyncEventLogger.record(this,"Appearance","ACCENT_CHANGED","INFO",
                        "accent="+AppPreferences.ACCENT_NAMES[index]);
                render();
            });
            accents.addView(chip,new LinearLayout.LayoutParams(-2,-2));
        }
        accentScroll.addView(accents);
        content.addView(accentScroll);

        addSection(content,"ANIMATION INTENSITY","Choose how much movement Sync uses.");
        RadioGroup motion = new RadioGroup(this);
        motion.setOrientation(RadioGroup.VERTICAL);
        String currentMotion=preferences.getMotionIntensity();
        for (String item:AppPreferences.MOTION_INTENSITIES) {
            RadioButton radio=new RadioButton(this);
            radio.setText(item+(item.equals("Minimal")?" · reduced motion":
                    item.equals("Balanced")?" · subtle transitions":" · reactive effects"));
            radio.setTextSize(13);
            radio.setTextColor(palette.text);
            radio.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                    new int[]{palette.primary,palette.muted}));
            radio.setChecked(item.equals(currentMotion));
            radio.setPadding(dp(3),dp(5),dp(3),dp(5));
            radio.setOnClickListener(v->{
                preferences.setMotionIntensity(item);
                SyncEventLogger.record(this,"Appearance","MOTION_CHANGED","INFO","intensity="+item);
                if (coreOrb != null) coreOrb.refreshMotionSettings();
            });
            motion.addView(radio);
        }
        content.addView(motion);

        LinearLayout preview=panel();
        preview.addView(text("LIVE PREVIEW",10,palette.secondary,true));
        LinearLayout sample=new LinearLayout(this);
        sample.setGravity(Gravity.CENTER_VERTICAL);
        View smallCore=makeCore();
        sample.addView(smallCore,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12),0,0,0);
        copy.addView(text("Sync AI",17,palette.text,true));
        copy.addView(text("Local intelligence · Ready",11,palette.muted,false));
        sample.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        preview.addView(sample);
        TextView response=text("“I'm ready. What are we working on today?”",13,palette.body,false);
        response.setPadding(0,dp(10),0,0);
        preview.addView(response);
        content.addView(preview,new LinearLayout.LayoutParams(-1,-2));

        Button done=new Button(this);
        done.setText("DONE"); done.setTextColor(AppTheme.onAccent(palette.primary)); done.setTextSize(12);
        done.setTypeface(Typeface.DEFAULT,Typeface.BOLD); done.setAllCaps(false);
        done.setBackground(round(palette.primary,dp(15)));
        Motion.pressable(done); done.setOnClickListener(v->finish());
        LinearLayout.LayoutParams doneLp=new LinearLayout.LayoutParams(-1,dp(48));
        doneLp.topMargin=dp(8);
        root.addView(done,doneLp);
        setContentView(root);
        AppTheme.applySystemBars(this, palette);
        final int baseLeft = dp(18);
        final int baseTop = dp(10);
        final int baseRight = dp(18);
        final int baseBottom = dp(12);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            androidx.core.graphics.Insets safeInsets = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(baseLeft + safeInsets.left, baseTop + safeInsets.top,
                    baseRight + safeInsets.right, baseBottom + safeInsets.bottom);
            return windowInsets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }

    private void addHero(LinearLayout content) {
        LinearLayout hero=panel();
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        coreOrb=makeCore();
        hero.addView(coreOrb,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14),0,0,0);
        copy.addView(text("Sync AI",20,palette.text,true));
        copy.addView(text("Appearance",13,palette.secondary,true));
        TextView desc=text("Personalize the signal, not the intelligence.",11,palette.muted,false);
        desc.setPadding(0,dp(5),0,0);
        copy.addView(desc);
        hero.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
    }

    private void addThemeCard(LinearLayout row,String id) {
        AppTheme preview=AppTheme.forId(this,id);
        boolean selected=preferences.getTheme().equals(id);
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11),dp(11),dp(11),dp(11));
        GradientDrawable bg=round(preview.surface,dp(17));
        bg.setStroke(dp(selected?2:1),selected?preview.primary:preview.border);
        card.setBackground(bg);
        card.setClickable(true); card.setFocusable(true);
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text(selected?"✓":"◉",18,selected?preview.primary:preview.secondary,true);
        icon.setGravity(Gravity.CENTER);
        top.addView(icon,new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView name=text(preview.name,13,preview.text,true);
        LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,-2,1);
        nameLp.leftMargin=dp(5);
        top.addView(name,nameLp);
        card.addView(top);
        TextView desc=text(preview.description,10,preview.muted,false);
        desc.setPadding(0,dp(5),0,dp(9));
        card.addView(desc);
        LinearLayout swatches=new LinearLayout(this);
        swatches.addView(colorBar(preview.background),new LinearLayout.LayoutParams(0,dp(6),1));
        swatches.addView(colorBar(preview.primary),new LinearLayout.LayoutParams(0,dp(6),1));
        swatches.addView(colorBar(preview.secondary),new LinearLayout.LayoutParams(0,dp(6),1));
        card.addView(swatches);
        card.setContentDescription(preview.name+" theme. "+preview.description
                +(selected?". Selected.":". Double-tap to select."));
        card.setOnClickListener(v->{
            preferences.setTheme(id);
            SyncEventLogger.record(this,"Appearance","THEME_CHANGED","INFO","theme="+id);
            render();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        if(row.getChildCount()>0) lp.leftMargin=dp(8);
        row.addView(card,lp);
    }

    private View colorBar(int color) {
        View bar=new View(this); bar.setBackground(round(color,dp(4)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(6),1);
        lp.rightMargin=dp(4); bar.setLayoutParams(lp); return bar;
    }

    private LinearLayout panel() {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(13),dp(14),dp(13));
        GradientDrawable bg=round(palette.surface,dp(20));
        bg.setStroke(dp(1),palette.border);
        card.setBackground(bg); card.setElevation(dp(2)); return card;
    }

    private void addSection(LinearLayout content,String title,String description) {
        TextView heading=text(title,11,palette.secondary,true);
        heading.setLetterSpacing(0.1f);
        heading.setPadding(dp(2),dp(18),dp(2),dp(5));
        content.addView(heading);
        TextView sub=text(description,11,palette.muted,false);
        sub.setPadding(dp(2),0,dp(2),dp(8));
        content.addView(sub);
    }

    private SyncCoreView makeCore() {
        SyncCoreView core = new SyncCoreView(this);
        core.setState(SyncCoreView.State.READY);
        return core;
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL); return t;
    }

    private GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onDestroy(){super.onDestroy();}
}
