package com.sam.syncai;

import android.content.Context;

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

    private AppTheme(String id, String name, String description, int background,
                     int surface, int surface2, int button, int text, int body,
                     int muted, int border, int userBubble, int assistantBubble,
                     int primary, int secondary) {
        this.id=id; this.name=name; this.description=description; this.background=background;
        this.surface=surface; this.surface2=surface2; this.button=button; this.text=text;
        this.body=body; this.muted=muted; this.border=border; this.userBubble=userBubble;
        this.assistantBubble=assistantBubble; this.primary=primary; this.secondary=secondary;
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

    public static AppTheme forId(Context context, String id) {
        int primary = AppPreferences.ACCENTS[new AppPreferences(context).getAccent()];
        String safe = isKnownTheme(id) ? id : OBSIDIAN;
        switch (safe) {
            case NULLVEROX:
                return new AppTheme(safe,NAMES[1],DESCRIPTIONS[1],0xFF100813,0xFF1B1022,
                        0xFF2A1632,0xFF26102D,0xFFF9F5FF,0xFFE9DDED,0xFFB8A3C3,
                        0xFF63345D,0xFF3A173C,0xFF201324,primary,0xFF36D8F2);
            case MINIMAL:
                return new AppTheme(safe,NAMES[2],DESCRIPTIONS[2],0xFF15171A,0xFF202328,
                        0xFF2A2E34,0xFF292D33,0xFFF5F6F7,0xFFD9DDE2,0xFFA3ABB5,
                        0xFF414851,0xFF30333A,0xFF202328,primary,0xFFD0D6DF);
            case HOLOGRAPHIC:
                return new AppTheme(safe,NAMES[3],DESCRIPTIONS[3],0xFF0D1527,0xFF17243C,
                        0xFF223554,0xFF1D2C49,0xFFF3F7FF,0xFFD8E6FF,0xFF9FB4D5,
                        0xFF354F75,0xFF273C62,0xFF192741,primary,0xFF75E2FF);
            case COMMAND:
                return new AppTheme(safe,NAMES[4],DESCRIPTIONS[4],0xFF08131C,0xFF102633,
                        0xFF173543,0xFF15303D,0xFFF2FAFC,0xFFD1E7EC,0xFF8BA8B2,
                        0xFF2C515E,0xFF1D4654,0xFF102A37,primary,0xFFA3E635);
            case AURORA:
                return new AppTheme(safe,NAMES[5],DESCRIPTIONS[5],0xFF10152A,0xFF1B2140,
                        0xFF282E50,0xFF242844,0xFFF6F5FF,0xFFE2E0F5,0xFFAAA9CF,
                        0xFF3E4168,0xFF343158,0xFF1C2039,primary,0xFF5EEAD4);
            default:
                return new AppTheme(OBSIDIAN,NAMES[0],DESCRIPTIONS[0],0xFF080811,0xFF11121F,
                        0xFF1B1B2B,0xFF202032,0xFFF5F7FF,0xFFD8DBEA,0xFF979DB8,
                        0xFF39334E,0xFF332448,0xFF151621,primary,0xFF67E8F9);
        }
    }
}
