package com.sam.syncai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolIntentRouter {
    private static final Pattern FLASHLIGHT = Pattern.compile(
            "(?i)\\b(?:turn\\s+)?(on|off)\\s+(?:(?:the|my|phone)\\s+)?(?:flashlight|torch)\\b");
    private static final Pattern OPEN_APP = Pattern.compile(
            "(?i)\\b(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?=\\s+(?:please|now|for me)\\b|[.!?,;]|$)");
    private static final Pattern CALCULATE = Pattern.compile(
            "(?i)\\b(?:calculate|compute|work\\s+out|what\\s+is|what's)\\s+(.+?)(?=\\s+(?:please|now)\\b|[?!;]|$)");
    private static final Pattern CREATE_FILE = Pattern.compile(
            "(?is)\\b(?:create|make|write)\\s+(?:a\\s+)?(?:text\\s+)?file\\s+(?:named|called)\\s+([^:,.!?]+?)\\s*:\\s*(.*)$");

    private static final Pattern NEGATION = Pattern.compile(
            "(?i)\\b(?:don't|do not|didn't|did not|cannot|can't|won't|will not|wouldn't|shouldn't|should not|never|why)\\b");

    private ToolIntentRouter() {}

    /**
     * Fast local command classifier.
     *
     * It deliberately runs before the LLM so common Android actions do not
     * spend 30–40 seconds asking a language model to rediscover an intent.
     * Conversational/ambiguous text falls through to the normal LLM path.
     */
    public static ToolCall parse(String userText) {
        if (userText == null) return null;
        String text = userText.trim();
        if (text.isEmpty()) return null;

        Matcher m = FLASHLIGHT.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            Map<String, String> args = new LinkedHashMap<>();
            args.put("enabled", Boolean.toString("on".equalsIgnoreCase(m.group(1))));
            return new ToolCall("flashlight", args);
        }

        m = CREATE_FILE.matcher(text);
        if (m.find() && isCommandContext(text, m.start())) {
            String name = m.group(1).trim();
            String body = m.group(2);
            if (!name.isEmpty()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("name", name);
                args.put("content", body);
                return new ToolCall("write_workspace_file", args);
            }
        }

        m = OPEN_APP.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String app = cleanTrailingWords(m.group(1));
            if (!app.isEmpty() && !looksLikeConversationObject(app)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("app", app);
                return new ToolCall("open_app", args);
            }
        }

        m = CALCULATE.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String expression = m.group(1).trim();
            if (looksLikeMath(expression)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("expression", expression);
                return new ToolCall("calculator", args);
            }
        }

        return null;
    }

    private static boolean isCommandContext(String text, int commandStart) {
        String prefix = text.substring(0, Math.max(0, commandStart)).trim();
        if (prefix.isEmpty()) return true;

        // Allow natural conversational lead-ins such as:
        // "okay, turn on my flashlight"
        // "can you open Discord"
        // "hey, calculate 12 * 8"
        if (NEGATION.matcher(prefix).find()) return false;

        String lower = prefix.toLowerCase(Locale.US);
        return lower.matches(
                "(?s)(?:okay|ok|alright|all right|hey|yo|please|can you|could you|would you|"
                        + "can u|could u|i need you to|i want you to|go ahead and|then|and|also|"
                        + "please can you|please could you)[\\s,;:-]*");
    }

    private static String cleanTrailingWords(String value) {
        String result = value == null ? "" : value.trim();
        result = result.replaceAll("(?i)\\s+(?:please|now|for me)$", "").trim();
        result = result.replaceAll("(?i)\\s+app$", "").trim();
        return result;
    }

    private static boolean looksLikeConversationObject(String value) {
        String lower = value.toLowerCase(Locale.US).trim();
        return lower.isEmpty() ||
                lower.equals("it") || lower.equals("that") || lower.equals("this") ||
                lower.equals("something") || lower.equals("the app");
    }

    private static boolean looksLikeMath(String value) {
        String normalized = value.toLowerCase(Locale.US)
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("times", "*")
                .replace("multiplied by", "*")
                .replace("divided by", "/")
                .replace("over", "/")
                .replaceAll("\\bwhat\\s+is\\b", "")
                .trim();
        return normalized.matches("[0-9.\\s()+*/%^-]+");
    }
}
