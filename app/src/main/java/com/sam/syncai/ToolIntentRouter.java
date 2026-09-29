package com.sam.syncai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolIntentRouter {
    private static final Pattern FLASHLIGHT = Pattern.compile(
            "^\\s*(?:please\\s+)?(?:turn\\s+)?(on|off)\\s+(?:the\\s+)?(?:phone\\s+)?(?:flashlight|torch)\\s*[.!?]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FLASHLIGHT_SHORT = Pattern.compile(
            "^\\s*(?:flashlight|torch)\\s+(on|off)\\s*[.!?]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    private ToolIntentRouter() {}

    public static ToolCall parse(String userText) {
        if (userText == null) return null;
        String text = userText.trim().toLowerCase(Locale.US);
        Matcher m = FLASHLIGHT.matcher(text);
        if (!m.matches()) m = FLASHLIGHT_SHORT.matcher(text);
        if (!m.matches()) return null;

        java.util.Map<String, String> args = new java.util.LinkedHashMap<>();
        args.put("enabled", Boolean.toString("on".equalsIgnoreCase(m.group(1))));
        return new ToolCall("flashlight", args);
    }
}
