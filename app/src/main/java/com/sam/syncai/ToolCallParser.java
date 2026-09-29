package com.sam.syncai;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolCallParser {
    private static final Pattern TAGGED = Pattern.compile(
            "<tool_call>\\s*(.*?)\\s*</tool_call>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private ToolCallParser() {}

    public static ToolCall parse(String text) {
        if (text == null) return null;
        String json = null;
        Matcher matcher = TAGGED.matcher(text);
        if (matcher.find()) json = matcher.group(1);

        if (json == null) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}") &&
                    (trimmed.contains("\"name\"") || trimmed.contains("\"tool\""))) {
                json = trimmed;
            }
        }
        if (json == null || json.trim().isEmpty()) return null;

        // Small local models sometimes emit a near-tool syntax such as:
        // {open_app, "light"}{"arguments": {"enabled":true}}
        // Accept only tightly recognizable variants; never execute arbitrary text.
        if (json.trim().startsWith("{") && json.contains("}{")) {
            String compact = json.trim();
            int split = compact.indexOf("}{");
            if (split > 0) {
                String head = compact.substring(0, split + 1);
                String tail = compact.substring(split + 1);
                String name = head.replace("{", "").replace("}", "").replace("\"", "").trim();
                name = name.replaceFirst("(?i)^tool\\s*[:=]\\s*", "");
                if (!name.isEmpty() && tail.trim().startsWith("{")) {
                    json = "{\"name\":\"" + name + "\",\"arguments\":" + tail.trim() + "}";
                }
            }
        }

        try {
            JSONObject root = new JSONObject(json.trim());
            String name = root.optString("name", root.optString("tool", "")).trim();
            if (name.isEmpty()) return null;

            JSONObject argsObject = root.optJSONObject("arguments");
            if (argsObject == null) argsObject = root.optJSONObject("args");
            if (argsObject == null) argsObject = root.optJSONObject("parameters");

            Map<String, String> args = new LinkedHashMap<>();
            if (argsObject != null) {
                Iterator<String> keys = argsObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = argsObject.opt(key);
                    if (value != null && value != JSONObject.NULL) {
                        args.put(key, String.valueOf(value));
                    }
                }
            }
            return new ToolCall(name, args);
        } catch (Exception ignored) {
            return null;
        }
    }
}
