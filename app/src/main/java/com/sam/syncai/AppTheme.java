package com.sam.syncai;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

/** Lightweight shared palette. Theme and accent are independent preferences. */
public final class AppTheme {
    public static final String OBSIDIAN = "obsidian";
    public static final String NULLVEROX = "nullverox";
    public static final String MINIMAL = "minimal";
    public static final String HOLOGRAPHIC = "holographic";
    public static final String COMMAND = "command";
    public static final String AURORA = "aurora";

    public static final String[] IDS = {
            OBSIDIAN, NULLVEROX, MINIMAL, HOLOGRAPHIC, COMMAND, AURORA
    };
    public static final String[] NAMES = {
            "Obsidian", "Nullverox", "Minimal", "Holographic", "Command", "Aurora"
    };
    public static final String[] DESCRIPTIONS = {
            "Premium dark sci-fi",
            "Cyberpunk signal and glitchcore",
            "Clean graphite and focus",
            "Glass-like translucent layers",
            "Technical system HUD",
            "Ambient violet and teal"
    };

    public final String id, name, description;
    public final int background, surface, surface2, button, text, body, muted, border;
    public final int userBubble, assistantBubble, primary, secondary;
    public final boolean isLight;

    private AppTheme(String id, String name, String description, int background,
                     int surface, int surface2, int button, int text, int body,
                     int muted, int border, int userBubble, int assistantBubble,
                     int primary, int secondary, boolean isLight) {
        this.id=id; this.name=name; this.description=description; this.background=background;
        this.surface=surface; this.surface2=surface2; this.button=button; this.text=text;
        this.body=body; this.muted=muted; this.border=border; this.userBubble=userBubble;
        this.assistantBubble=assistantBubble; this.primary=primary; this.secondary=secondary; this.isLight=isLight;
    }

    public static AppTheme from(Context context) {
        return forId(context, new AppPreferences(context).getTheme());
    }

    public static boolean isKnownTheme(String id) {
        if (id == null) return false;
        for (String candidate : IDS) if (candidate.equals(id)) return true;
        return false;
    }

    public static String displayName(String id) {
        for (int i=0;i<IDS.length;i++) if (IDS[i].equals(id)) return NAMES[i];
        return NAMES[0];
    }

    public static String description(String id) {
        for (int i=0;i<IDS.length;i++) if (IDS[i].equals(id)) return DESCRIPTIONS[i];
        return DESCRIPTIONS[0];
    }

    public static boolean isLightMode(Context context) {
        String mode = new AppPreferences(context).getThemeMode();
        if ("light".equals(mode)) return true;
        if ("dark".equals(mode)) return false;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_YES;
    }

    public static AppTheme forId(Context context, String id) {
        int accent = AppPreferences.ACCENTS[new AppPreferences(context).getAccent()];
        String safe = isKnownTheme(id) ? id : OBSIDIAN;
        if (isLightMode(context)) return light(safe, accent);
        return dark(safe, accent);
    }

    // ------------------------------------------------------------ helpers

    public static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) * (1 - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    private static double lum(int c) {
        double[] v = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    public static double contrast(int a, int b) {
        double l1 = lum(a), l2 = lum(b);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    /** Readable label color for text drawn on top of a solid accent fill. */
    public static int onAccent(int accent) {
        return contrast(accent, Color.WHITE) >= contrast(accent, 0xFF0B0B14) ? Color.WHITE : 0xFF0B0B14;
    }

    private static int darkenForLight(int accent, int bg) {
        int c = accent;
        for (int i = 0; i < 12 && contrast(c, bg) < 3.4; i++) c = mix(c, Color.BLACK, 0.12f);
        return c;
    }

    /** Status/navigation bar colors and icon tint for the current theme. Call after setContentView. */
    public static void applySystemBars(Activity a, AppTheme t) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(t.background));
        w.setStatusBarColor(t.background);
        w.setNavigationBarColor(t.background);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(t.isLight ? m : 0, m);
            }
        } else {
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            int m = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            d.setSystemUiVisibility(t.isLight ? (f | m) : (f & ~m));
        }
    }

    private static AppTheme light(String safe, int accent) {
        int p = darkenForLight(accent, 0xFFFFFFFF);
        switch (safe) {
            case NULLVEROX:
                return new AppTheme(safe,NAMES[1],DESCRIPTIONS[1],0xFFFBF4FC,0xFFFFFFFF,
                        0xFFF5E8F7,0xFFF1DFF4,0xFF2A0F2F,0xFF4A2E50,0xFF7A6580,
                        0xFFE3C9E8,0xFFF3D9F5,0xFFFFFFFF,p,0xFF0E7490,true);
            case MINIMAL:
                return new AppTheme(safe,NAMES[2],DESCRIPTIONS[2],0xFFF4F5F7,0xFFFFFFFF,
                        0xFFECEEF1,0xFFE6E9ED,0xFF15171A,0xFF3B4048,0xFF69717C,
                        0xFFD5D9DF,0xFFE3E7ED,0xFFFFFFFF,p,0xFF4B5563,true);
            case HOLOGRAPHIC:
                return new AppTheme(safe,NAMES[3],DESCRIPTIONS[3],0xFFEEF4FF,0xFFFFFFFF,
                        0xFFE3ECFA,0xFFDCE7F8,0xFF0D1A33,0xFF31496E,0xFF5E7399,
                        0xFFC9D7EE,0xFFD9E7FF,0xFFFFFFFF,p,0xFF0284C7,true);
            case COMMAND:
                return new AppTheme(safe,NAMES[4],DESCRIPTIONS[4],0xFFEDF6F8,0xFFFFFFFF,
                        0xFFE0EEF2,0xFFD8E9EE,0xFF0A1F29,0xFF2E5360,0xFF587985,
                        0xFFC3DCE3,0xFFD3ECF1,0xFFFFFFFF,p,0xFF4D7C0F,true);
            case AURORA:
                return new AppTheme(safe,NAMES[5],DESCRIPTIONS[5],0xFFF3F3FC,0xFFFFFFFF,
                        0xFFE9E9F7,0xFFE3E3F4,0xFF15183A,0xFF3A3E6C,0xFF666A98,
                        0xFFD3D4EC,0xFFE0DEFA,0xFFFFFFFF,p,0xFF0D9488,true);
            default:
                return new AppTheme(OBSIDIAN,NAMES[0],DESCRIPTIONS[0],0xFFF6F5FB,0xFFFFFFFF,
                        0xFFEEEDF6,0xFFEAE8F4,0xFF14131F,0xFF3A3850,0xFF6B6884,
                        0xFFD9D6E8,0xFFE8DEFF,0xFFFFFFFF,p,0xFF0891B2,true);
        }
    }

    private static AppTheme dark(String safe, int primary) {
        switch (safe) {
            case NULLVEROX:
                return new AppTheme(safe,NAMES[1],DESCRIPTIONS[1],0xFF100813,0xFF1B1022,
                        0xFF2A1632,0xFF26102D,0xFFF9F5FF,0xFFE9DDED,0xFFB8A3C3,
                        0xFF63345D,0xFF3A173C,0xFF201324,primary,0xFF36D8F2,false);
            case MINIMAL:
                return new AppTheme(safe,NAMES[2],DESCRIPTIONS[2],0xFF15171A,0xFF202328,
                        0xFF2A2E34,0xFF292D33,0xFFF5F6F7,0xFFD9DDE2,0xFFA3ABB5,
                        0xFF414851,0xFF30333A,0xFF202328,primary,0xFFD0D6DF,false);
            case HOLOGRAPHIC:
                return new AppTheme(safe,NAMES[3],DESCRIPTIONS[3],0xFF0D1527,0xFF17243C,
                        0xFF223554,0xFF1D2C49,0xFFF3F7FF,0xFFD8E6FF,0xFF9FB4D5,
                        0xFF354F75,0xFF273C62,0xFF192741,primary,0xFF75E2FF,false);
            case COMMAND:
                return new AppTheme(safe,NAMES[4],DESCRIPTIONS[4],0xFF08131C,0xFF102633,
                        0xFF173543,0xFF15303D,0xFFF2FAFC,0xFFD1E7EC,0xFF8BA8B2,
                        0xFF2C515E,0xFF1D4654,0xFF102A37,primary,0xFFA3E635,false);
            case AURORA:
                return new AppTheme(safe,NAMES[5],DESCRIPTIONS[5],0xFF10152A,0xFF1B2140,
                        0xFF282E50,0xFF242844,0xFFF6F5FF,0xFFE2E0F5,0xFFAAA9CF,
                        0xFF3E4168,0xFF343158,0xFF1C2039,primary,0xFF5EEAD4,false);
            default:
                return new AppTheme(OBSIDIAN,NAMES[0],DESCRIPTIONS[0],0xFF080811,0xFF11121F,
                        0xFF1B1B2B,0xFF202032,0xFFF5F7FF,0xFFD8DBEA,0xFF979DB8,
                        0xFF39334E,0xFF332448,0xFF151621,primary,0xFF67E8F9,false);
        }
    }
}
