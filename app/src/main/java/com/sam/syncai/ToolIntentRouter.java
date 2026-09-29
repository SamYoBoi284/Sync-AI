package com.sam.syncai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolIntentRouter {
    private static final Pattern FLASHLIGHT = Pattern.compile(
            "(?i)\\b(?:(?:turn|switch|toggle)\\s+)?(on|off)\\s+(?:(?:the|my|phone)\\s+)?(?:flashlight|torch)\\b|\\b(?:turn|switch|toggle)\\s+(?:(?:the|my|phone)\\s+)?(?:flashlight|torch)\\s+(on|off)\\b|\\b(?:flashlight|torch)\\s+(on|off)\\b");
    private static final Pattern CALL_CONTACT = Pattern.compile(
            "(?i)\\b(?:call|phone|ring|dial)\\s+(?:the\\s+)?(.+?)(?=\\s+(?:please|now|for me)\\b|\\s+and\\s+(?=(?:set|start|create|make|open|launch|call|phone|ring|dial|calculate|compute|what|what's|whats|how much|how many)\\b)|[.!?,;]|$)");
    private static final Pattern OPEN_APP = Pattern.compile(
            "(?i)\\b(?:open|launch|start|run)\\s+(?:the\\s+)?(.+?)(?=\\s+(?:please|now|for me)\\b|\\s+and\\s+(?=(?:set|start|create|make|open|launch|call|phone|ring|dial|calculate|compute|what|what's|whats|how much|how many)\\b)|[.!?,;]|$)");
    private static final Pattern CALCULATE = Pattern.compile(
            "(?i)\\b(?:calculate|compute|work\\s+out|solve|what\\s+is|what's|whats|how\\s+much\\s+is|how\\s+many)\\s+(.+?)(?=\\s+(?:please|now|for me)\\b|[?!;]|$)");
    private static final Pattern ALARM_TIME = Pattern.compile(
            "(?i)(?:(?:set|create|schedule|add)\\s+(?:an?\\s+)?alarm\\s+(?:for|at)|(?:wake\\s+me(?:\\s+up)?|remind\\s+me)\\s+(?:for|at)|(?:and\\s+)?(?:another\\s+(?:alarm|one)|one\\s+more)\\s+(?:for|at)|(?:set)\\s+another\\s+(?:alarm|one)\\s+(?:for|at))\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b");
    private static final Pattern TIMER = Pattern.compile(
            "(?i)\\b(?:set|start|create|make)\\s+(?:a\\s+)?timer(?:\\s+(?:for|of|at))?\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\b|\\b(?:remind|notify)\\s+me\\s+in\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\b");
    private static final Pattern STANDALONE_MATH = Pattern.compile(
            "^[0-9.\\s()+*/%^-]+$");
    private static final Pattern CREATE_FILE = Pattern.compile(
            "(?is)\\b(?:create|make|write)\\s+(?:a\\s+)?(?:text\\s+)?file\\s+(?:named|called)\\s+([^:,.!?]+?)\\s*:\\s*(.*)$");
    private static final Pattern TIME_QUERY = Pattern.compile(
            "(?i)\\b(?:what\\s+time\\s+is\\s+it|what(?:'s|\\s+is)\\s+(?:the\\s+)?(?:time|date|day)(?:\\s+is\\s+it)?|current\\s+(?:time|date)|today(?:'s)?\\s+date|what\\s+day\\s+is\\s+it)\\b");

    private static final Pattern CONTEXT_FLASHLIGHT = Pattern.compile(
            "(?i)^(?:turn|switch|shut)\\s+(?:(?:it|that|this)\\s+)?(?:back\\s+)?(on|off)\\b");
    private static final Pattern CONTEXT_OPEN_APP = Pattern.compile(
            "(?i)^(?:open|launch|start|run)\\s+(?:it|that|this)(?:\\s+again)?\\b");
    private static final Pattern CONTEXT_CALL = Pattern.compile(
            "(?i)^(?:call|phone|ring|dial)\\s+(?:again|them|that\\s+contact|that\\s+person)\\b");
    private static final Pattern CONTEXT_TIMER = Pattern.compile(
            "(?i)^(?:start|restart|run)\\s+(?:it|that|the\\s+timer)(?:\\s+again)?\\b");
    private static final Pattern CONTEXT_CALCULATOR = Pattern.compile(
            "(?i)^(?:again|repeat|do\\s+that\\s+again|calculate\\s+that\\s+again|run\\s+that\\s+again)\\b");

    private static final Pattern NEGATION = Pattern.compile(
            "(?i)\\b(?:don't|do not|didn't|did not|cannot|can't|won't|will not|wouldn't|shouldn't|should not|never|why)\\b");

    // Last successful tool/action target. This is deliberately small and in-memory:
    // it is command context, not long-term memory. New successful tool calls replace it.
    private static String lastToolName;
    private static final Map<String, String> lastToolArguments = new LinkedHashMap<>();

    private ToolIntentRouter() {}

    public static synchronized void rememberSuccessfulTool(ToolCall toolCall) {
        if (toolCall == null || toolCall.name == null || toolCall.name.trim().isEmpty()) return;
        lastToolName = toolCall.name;
        lastToolArguments.clear();
        if (toolCall.arguments != null) lastToolArguments.putAll(toolCall.arguments);
    }

    public static synchronized void clearContext() {
        lastToolName = null;
        lastToolArguments.clear();
    }

    /**
     * Fast local command classifier.
     *
     * It deliberately runs before the LLM so common Android actions do not
     * spend 30–40 seconds asking a language model to rediscover an intent.
     * Conversational/ambiguous text falls through to the normal LLM path.
     */
    public static ToolCall parse(String userText) {
        java.util.List<ToolCall> calls = parseAll(userText);
        return calls.isEmpty() ? null : calls.get(0);
    }

    /**
     * Parse all deterministic actions in one user utterance.
     * The common one-action path still returns one call, while compound
     * requests such as two alarms can execute without invoking the LLM.
     */
    public static java.util.List<ToolCall> parseAll(String userText) {
        java.util.List<ToolCall> calls = new java.util.ArrayList<>();
        if (userText == null) return calls;
        String text = userText.trim();
        if (text.isEmpty()) return calls;

        ToolCall contextual = resolveContextualCommand(text);
        if (contextual != null) {
            calls.add(contextual);
            return calls;
        }

        Matcher timeMatcher = TIME_QUERY.matcher(text);
        while (timeMatcher.find()) {
            if (isCommandContext(text, timeMatcher.start())) {
                Map<String, String> args = new LinkedHashMap<>();
                String lower = text.toLowerCase(Locale.US);
                if (lower.contains("date")) args.put("query", "date");
                else if (lower.contains("day")) args.put("query", "day");
                else args.put("query", "time");
                calls.add(new ToolCall("time", args));
                break;
            }
        }

        ToolCall systemInfo = parseSystemInfo(text);
        if (systemInfo != null) calls.add(systemInfo);

        Matcher m = FLASHLIGHT.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String state = m.group(1) != null ? m.group(1) :
                    (m.group(2) != null ? m.group(2) : m.group(3));
            Map<String, String> args = new LinkedHashMap<>();
            args.put("enabled", Boolean.toString("on".equalsIgnoreCase(state)));
            calls.add(new ToolCall("flashlight", args));
            break;
        }

        m = CALL_CONTACT.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String contact = cleanTrailingWords(m.group(1));
            if (!contact.isEmpty() && !looksLikeConversationObject(contact)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("contact", contact);
                calls.add(new ToolCall("call_contact", args));
            }
            break;
        }

        java.util.List<ToolCall> alarms = parseAlarms(text);
        calls.addAll(alarms);

        m = TIMER.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String amountText = m.group(1) != null ? m.group(1) : m.group(2);
            String unitText = m.group(2) != null ? m.group(2) : m.group(3);
            double amount = Double.parseDouble(amountText);
            String unit = unitText.toLowerCase(Locale.US);
            int seconds;
            if (unit.startsWith("hour") || unit.startsWith("hr")) {
                seconds = (int) Math.round(amount * 3600.0);
            } else if (unit.startsWith("minute") || unit.startsWith("min")) {
                seconds = (int) Math.round(amount * 60.0);
            } else {
                seconds = (int) Math.round(amount);
            }
            if (seconds >= 1 && seconds <= 86400) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("seconds", Integer.toString(seconds));
                args.put("message", extractLabelNear(text, m.end(), "Timer"));
                calls.add(new ToolCall("set_timer", args));
            }
            break;
        }

        m = OPEN_APP.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String app = cleanTrailingWords(m.group(1));
            if (!app.isEmpty() && !looksLikeConversationObject(app)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("app", app);
                calls.add(new ToolCall("open_app", args));
            }
            break;
        }

        m = CALCULATE.matcher(text);
        while (m.find()) {
            if (!isCommandContext(text, m.start())) continue;
            String expression = normalizeMathWords(m.group(1).trim());
            if (looksLikeMath(expression)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("expression", expression);
                calls.add(new ToolCall("calculator", args));
            }
            break;
        }

        if (calls.isEmpty()) {
            String mathCandidate = normalizeMathWords(
                    stripCommandLeadIns(text).replaceAll("(?i)\\?$", "").trim());
            if (STANDALONE_MATH.matcher(mathCandidate).matches() && looksLikeMath(mathCandidate)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("expression", mathCandidate);
                calls.add(new ToolCall("calculator", args));
            }
        }

        return calls;
    }

    private static ToolCall parseSystemInfo(String text) {
        String lower = text.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9%+.-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String query = null;
        if (lower.matches(".*\\b(?:battery|battery level|how much battery|am i charging|is it charging|charging status)\\b.*")) {
            query = "battery";
        } else if (lower.matches(".*\\b(?:wifi|wi fi|wireless|is wifi on)\\b.*")) {
            query = "wifi";
        } else if (lower.matches(".*\\b(?:bluetooth|bt|is bluetooth on)\\b.*")) {
            query = "bluetooth";
        } else if (lower.matches(".*\\b(?:volume|media volume|sound level)\\b.*")) {
            query = "volume";
        } else if (lower.matches(".*\\b(?:brightness|screen brightness)\\b.*")) {
            query = "brightness";
        } else if (lower.matches(".*\\b(?:ram|memory usage|available memory)\\b.*")) {
            query = "ram";
        } else if (lower.matches(".*\\b(?:storage|free space|disk space)\\b.*")) {
            query = "storage";
        } else if (lower.matches(".*\\b(?:device info|phone info|phone specs|device specs|android version)\\b.*")) {
            query = "device";
        } else if (lower.matches(".*\\b(?:network status|network connection|internet connection|am i online)\\b.*")) {
            query = "network";
        } else if (lower.matches(".*\\b(?:flashlight state|is the flashlight on|is my flashlight on|torch state)\\b.*")) {
            query = "flashlight";
        } else if (lower.matches(".*\\b(?:current app|what app am i in|which app is open|what's open|whats open)\\b.*")) {
            query = "current_app";
        } else if (lower.matches(".*\\b(?:alarms|scheduled alarms|my alarms)\\b.*")) {
            query = "alarms";
        } else if (lower.matches(".*\\b(?:timers|active timers|my timers)\\b.*")) {
            query = "timers";
        }

        if (query == null) return null;
        Map<String, String> args = new LinkedHashMap<>();
        args.put("query", query);
        return new ToolCall("system_info", args);
    }

    private static java.util.List<ToolCall> parseAlarms(String text) {
        java.util.List<ToolCall> calls = new java.util.ArrayList<>();
        Matcher matcher = ALARM_TIME.matcher(text);
        java.util.List<MatcherData> matches = new java.util.ArrayList<>();

        while (matcher.find()) {
            matches.add(new MatcherData(
                    matcher.start(), matcher.end(),
                    matcher.group(1), matcher.group(2), matcher.group(3)));
        }

        for (int i = 0; i < matches.size(); i++) {
            MatcherData data = matches.get(i);
            int hour = parseClockHour(data.hour, data.ampm);
            int minute = data.minute == null ? 0 : Integer.parseInt(data.minute);
            if (hour < 0 || minute < 0 || minute > 59) continue;

            int segmentEnd = i + 1 < matches.size() ? matches.get(i + 1).start : text.length();
            String segment = text.substring(data.end, segmentEnd);
            String label = extractLabel(segment, "Wake up");
            Map<String, String> args = new LinkedHashMap<>();
            args.put("hour", Integer.toString(hour));
            args.put("minute", Integer.toString(minute));
            args.put("message", label);
            calls.add(new ToolCall("set_alarm", args));
        }
        return calls;
    }

    private static String extractLabel(String text, String fallback) {
        Matcher m = Pattern.compile(
                "(?is)\\b(?:titled|called|named|label(?:ed)?(?:\\s+as)?)\\s+(.+?)(?=\\s+(?:and|then)\\s+(?:another|one\\s+more|set|create|schedule|start|make|open|launch|call|phone|ring|dial|calculate|compute)|\\s+and\\s+(?=(?:set|start|create|make|open|launch|call|phone|ring|dial|calculate|compute)\\b)|[.!?;]|$)")
                .matcher(text == null ? "" : text);
        if (!m.find()) return fallback;
        String label = m.group(1).trim();
        label = label.replaceAll("(?i)\\s+(?:and|then)\\s*$", "").trim();
        return label.isEmpty() ? fallback : label;
    }

    private static String extractLabelNear(String text, int end, String fallback) {
        if (text == null || end >= text.length()) return fallback;
        return extractLabel(text.substring(end), fallback);
    }

    private static String normalizeMathWords(String value) {
        return value.toLowerCase(Locale.US)
                .replaceAll("(\\d+(?:\\.\\d+)?)\\s*(?:percent|%)\\s+of\\s+(\\d+(?:\\.\\d+)?)", "$1*0.01*$2")
                .replaceAll("\\bmultiplied\\s+by\\b", "*")
                .replaceAll("\\btimes\\b", "*")
                .replaceAll("\\bdivided\\s+by\\b", "/")
                .replaceAll("\\bover\\b", "/")
                .replaceAll("\\bplus\\b", "+")
                .replaceAll("\\bminus\\b", "-")
                .replaceAll("\\bmod(?:ulo)?\\b", "%")
                .replaceAll("\\bto\\s+the\\s+power\\s+of\\b", "^")
                .replaceAll("\\bwhat\\s+is\\b", "")
                .replaceAll("\\bwhat's\\b", "")
                .replaceAll("\\bwhats\\b", "")
                .replaceAll("\\bhow\\s+much\\s+is\\b", "")
                .replaceAll("\\bhow\\s+many\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final class MatcherData {
        final int start;
        final int end;
        final String hour;
        final String minute;
        final String ampm;
        MatcherData(int start, int end, String hour, String minute, String ampm) {
            this.start = start;
            this.end = end;
            this.hour = hour;
            this.minute = minute;
            this.ampm = ampm;
        }
    }

    private static synchronized ToolCall resolveContextualCommand(String text) {
        if (lastToolName == null || text == null) return null;

        String candidate = stripCommandLeadIns(text.trim());
        if (candidate.isEmpty() || NEGATION.matcher(candidate).find()) return null;

        if ("flashlight".equals(lastToolName)) {
            Matcher toggle = CONTEXT_FLASHLIGHT.matcher(candidate);
            if (toggle.find()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("enabled", Boolean.toString("on".equalsIgnoreCase(toggle.group(1))));
                return new ToolCall("flashlight", args);
            }
        }

        if ("open_app".equals(lastToolName) && lastToolArguments.containsKey("app")) {
            Matcher reopen = CONTEXT_OPEN_APP.matcher(candidate);
            if (reopen.find()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("app", lastToolArguments.get("app"));
                return new ToolCall("open_app", args);
            }
        }

        if ("call_contact".equals(lastToolName) && lastToolArguments.containsKey("contact")) {
            Matcher repeatCall = CONTEXT_CALL.matcher(candidate);
            if (repeatCall.find()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("contact", lastToolArguments.get("contact"));
                return new ToolCall("call_contact", args);
            }
        }

        if ("set_timer".equals(lastToolName) && lastToolArguments.containsKey("seconds")) {
            Matcher repeatTimer = CONTEXT_TIMER.matcher(candidate);
            if (repeatTimer.find()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("seconds", lastToolArguments.get("seconds"));
                args.put("message", lastToolArguments.getOrDefault("message", "Timer"));
                return new ToolCall("set_timer", args);
            }
        }

        if ("calculator".equals(lastToolName) && lastToolArguments.containsKey("expression")) {
            Matcher repeatCalculation = CONTEXT_CALCULATOR.matcher(candidate);
            if (repeatCalculation.find()) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("expression", lastToolArguments.get("expression"));
                return new ToolCall("calculator", args);
            }
        }

        return null;
    }

    private static String stripCommandLeadIns(String text) {
        String result = text == null ? "" : text.trim();
        String previous;
        do {
            previous = result;
            result = result.replaceFirst(
                    "(?i)^(?:okay|ok|alright|all right|hey|yo|please|can you|could you|would you|"
                            + "can u|could u|i need you to|i want you to|go ahead and|then|and|also)"
                            + "\\s*(?:[,;:]\\s*)?", "");
        } while (!result.equals(previous));
        return result.trim();
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
        result = result.replaceFirst("(?i)^my\\s+", "").trim();
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
        if (value == null || value.trim().isEmpty()) return false;
        String normalized = normalizeMathWords(value);
        return normalized.matches("[0-9.\\s()+*/%^-]+");
    }

    private static int parseClockHour(String rawHour, String amPm) {
        int hour = Integer.parseInt(rawHour);
        if (amPm == null || amPm.isEmpty()) return hour <= 23 ? hour : -1;
        String meridiem = amPm.toLowerCase(Locale.US);
        if (hour < 1 || hour > 12) return -1;
        if ("am".equals(meridiem)) return hour == 12 ? 0 : hour;
        return hour == 12 ? 12 : hour + 12;
    }
}
