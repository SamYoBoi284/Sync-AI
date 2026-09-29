package com.sam.syncai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolIntentRouter {
    private static final Pattern FLASHLIGHT = Pattern.compile(
            "^\\s*(?:please\\s+)?(?:turn\\s+)?(on|off)\\s+(?:the\\s+)?(?:phone\\s+)?(?:flashlight|torch)\\s*[.!?]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_APP = Pattern.compile(
            "^\\s*(?:please\\s+)?(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?\\s*[.!?]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CALCULATE = Pattern.compile(
            "^\\s*(?:please\\s+)?(?:calculate|compute|work out|what is|what's)\\s+(.+?)\\s*[?!.]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_FILE = Pattern.compile(
            "^\\s*(?:please\\s+)?(?:create|make|write)\\s+(?:a\\s+)?(?:text\\s+)?file\\s+(?:named|called)\\s+([^:]+?)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ToolIntentRouter() {}

    public static ToolCall parse(String userText) {
        if (userText == null) return null;
        String text = userText.trim();

        Matcher m = FLASHLIGHT.matcher(text);
        if (m.matches()) {
            Map<String, String> args = new LinkedHashMap<>();
            args.put("enabled", Boolean.toString("on".equalsIgnoreCase(m.group(1))));
            return new ToolCall("flashlight", args);
        }

        m = CREATE_FILE.matcher(text);
        if (m.matches()) {
            String name = m.group(1).trim();
            String content = m.group(2);
            if (!name.isEmpty()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("name", name);
                args.put("content", content);
                return new ToolCall("write_workspace_file", args);
            }
        }

        m = OPEN_APP.matcher(text);
        if (m.matches()) {
            String app = m.group(1).trim();
            if (!app.isEmpty()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("app", app);
                return new ToolCall("open_app", args);
            }
        }

        m = CALCULATE.matcher(text);
        if (m.matches()) {
            String expression = m.group(1).trim();
            if (looksLikeMath(expression)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("expression", expression);
                return new ToolCall("calculator", args);
            }
        }

        return null;
    }

    private static boolean looksLikeMath(String value) {
        String normalized = value.toLowerCase(Locale.US)
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("times", "*")
                .replace("multiplied by", "*")
                .replace("divided by", "/")
                .replace("over", "/");
        return normalized.matches("[0-9.\\s()+*/%^-]+");
    }
}
