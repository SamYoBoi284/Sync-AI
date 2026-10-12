package com.sam.syncai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persists generated code/document artifacts independently from chat history. */
public final class WorkspaceStore {
    public enum State { IN_PROGRESS, COMPLETED, FAILED }

    public static final class Artifact {
        public final String id;
        public final String responseId;
        public final String chatId;
        public final String title;
        public final String language;
        public final String content;
        public final State state;
        public final long createdAt;
        public final long updatedAt;

        Artifact(String id, String responseId, String chatId, String title,
                 String language, String content, State state, long createdAt, long updatedAt) {
            this.id = id;
            this.responseId = responseId;
            this.chatId = chatId;
            this.title = title;
            this.language = language;
            this.content = content;
            this.state = state;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private static final String FENCE = new String(new char[]{96, 96, 96});
    private static final Pattern OPEN_FENCE = Pattern.compile("(?m)^[ ]*" + FENCE + "([^\\r\\n]*)\\r?\\n");
    private static final Pattern CLOSE_FENCE = Pattern.compile("(?m)^[ ]*" + FENCE + "[ ]*(?:\\r?\\n|$)");
    private static final int MAX_ARTIFACTS = 100;

    private final File file;
    private final List<Artifact> artifacts = new ArrayList<>();

    public WorkspaceStore(Context context) {
        File directory = new File(context.getApplicationContext().getFilesDir(), "workspace");
        if (!directory.exists()) directory.mkdirs();
        file = new File(directory, "artifacts.json");
        load();
    }

    public synchronized void reload() {
        load();
    }

    public synchronized List<Artifact> all() {
        ArrayList<Artifact> result = new ArrayList<>(artifacts);
        Collections.sort(result, (left, right) -> Long.compare(right.updatedAt, left.updatedAt));
        return result;
    }

    public synchronized Artifact get(String id) {
        if (id == null) return null;
        for (Artifact artifact : artifacts) if (id.equals(artifact.id)) return artifact;
        return null;
    }

    public synchronized void delete(String id) {
        if (id == null) return;
        for (int i = artifacts.size() - 1; i >= 0; i--) {
            if (id.equals(artifacts.get(i).id)) {
                artifacts.remove(i);
                save();
                return;
            }
        }
    }

    public synchronized void updateResponse(String responseId, String chatId,
                                            String response, State state) {
        updateResponse(responseId, chatId, response, state, "");
    }

    public synchronized void updateResponse(String responseId, String chatId,
                                            String response, State state,
                                            String preferredTitle) {
        if (responseId == null || responseId.trim().isEmpty() || response == null) return;
        List<ExtractedBlock> blocks = extractBlocks(response);
        if (blocks.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (int i = 0; i < blocks.size(); i++) {
            ExtractedBlock block = blocks.get(i);
            String id = responseId + "-" + i;
            Artifact old = get(id);
            String title = preferredTitle == null || preferredTitle.trim().isEmpty()
                    ? titleFor(response, block.start, block.language, i + 1)
                    : preferredTitle.trim();
            State storedState = state;
            if (state == State.COMPLETED && !passesBasicQualityChecks(block)) {
                storedState = State.FAILED;
            }
            Artifact next = new Artifact(id, responseId, chatId == null ? "" : chatId,
                    title, block.language.isEmpty() ? "text" : block.language,
                    block.content, storedState, old == null ? now : old.createdAt, now);
            replace(next);
        }

        if (state != State.IN_PROGRESS) {
            for (int i = artifacts.size() - 1; i >= 0; i--) {
                Artifact item = artifacts.get(i);
                if (responseId.equals(item.responseId)) {
                    try {
                        int index = Integer.parseInt(item.id.substring(responseId.length() + 1));
                        if (index >= blocks.size()) artifacts.remove(i);
                    } catch (Exception ignored) {
                        artifacts.remove(i);
                    }
                }
            }
        }

        trimOldArtifacts();
        save();
    }

    private void replace(Artifact artifact) {
        for (int i = 0; i < artifacts.size(); i++) {
            if (artifact.id.equals(artifacts.get(i).id)) {
                artifacts.set(i, artifact);
                return;
            }
        }
        artifacts.add(artifact);
    }

    private void trimOldArtifacts() {
        if (artifacts.size() <= MAX_ARTIFACTS) return;
        Collections.sort(artifacts, Comparator.comparingLong(item -> item.updatedAt));
        while (artifacts.size() > MAX_ARTIFACTS) artifacts.remove(0);
    }

    private static final class ExtractedBlock {
        final int start;
        final String language;
        final String content;
        final boolean closed;
        ExtractedBlock(int start, String language, String content, boolean closed) {
            this.start = start;
            this.language = language;
            this.content = content;
            this.closed = closed;
        }
    }

    /** Basic structural checks; this is a guard against obvious truncation, not a full runtime test. */
    public static boolean passesBasicQualityChecks(String response) {
        List<ExtractedBlock> blocks = extractBlocks(response == null ? "" : response);
        if (blocks.isEmpty()) return false;
        for (ExtractedBlock block : blocks) {
            if (!passesBasicQualityChecks(block)) return false;
        }
        return true;
    }

    private static boolean passesBasicQualityChecks(ExtractedBlock block) {
        String content = block.content == null ? "" : block.content.trim();
        if (!block.closed || content.isEmpty()) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        String language = block.language == null ? "" : block.language.toLowerCase(Locale.ROOT);
        boolean sourceCode = !("text".equals(language) || "txt".equals(language)
                || "markdown".equals(language) || "md".equals(language) || "document".equals(language));
        if (sourceCode && (lower.contains("your code here") || lower.contains("implement me")
                || lower.contains("todo: implement") || lower.contains("replace this with code"))) return false;

        if ("html".equalsIgnoreCase(block.language) || "htm".equalsIgnoreCase(block.language)) {
            if (!lower.contains("<html") || !lower.contains("</html>")) return false;
            if (lower.contains("<script") != lower.contains("</script>")) return false;
            // Catch the exact class of broken "game" artifact seen in v0.4.0:
            // controls and headings existed, but no result logic updated the UI.
            if (lower.contains("function playround") || lower.contains("playround(")) {
                boolean hasChoices = lower.contains("<button") || lower.contains("<input");
                boolean hasRules = lower.contains("if") || lower.contains("switch");
                boolean updatesUi = lower.contains("textcontent") || lower.contains("innertext")
                        || lower.contains("innerhtml") || lower.contains("insertadjacenthtml");
                String quote = String.valueOf((char) 34);
                boolean brokenSeparateRadioNames = lower.contains("name=" + quote + "rock" + quote)
                        && lower.contains("name=" + quote + "paper" + quote)
                        && lower.contains("name=" + quote + "scissors" + quote);
                if (!hasChoices || !hasRules || !updatesUi || brokenSeparateRadioNames
                        || !lower.contains("rock") || !lower.contains("paper")
                        || !lower.contains("scissors")) return false;
            }
        }
        return true;
    }

    private static List<ExtractedBlock> extractBlocks(String text) {
        List<ExtractedBlock> result = new ArrayList<>();
        Matcher open = OPEN_FENCE.matcher(text);
        int searchFrom = 0;
        while (searchFrom < text.length() && open.find(searchFrom)) {
            int contentStart = open.end();
            Matcher close = CLOSE_FENCE.matcher(text);
            int closeStart = -1;
            int closeEnd = -1;
            if (close.find(contentStart)) {
                closeStart = close.start();
                closeEnd = close.end();
            }
            String content = text.substring(contentStart, closeStart >= 0 ? closeStart : text.length());
            String language = open.group(1) == null ? "" : open.group(1).trim();
            if (language.contains(" ")) language = language.substring(0, language.indexOf(' '));
            result.add(new ExtractedBlock(open.start(), language, content, closeEnd >= 0));
            if (closeEnd >= 0) searchFrom = closeEnd;
            else break;
        }
        return result;
    }

    private static String titleFor(String response, int fenceStart, String language, int index) {
        int prefixStart = Math.max(0, fenceStart - 280);
        String[] lines = response.substring(prefixStart, fenceStart).split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                while (line.startsWith("#")) line = line.substring(1).trim();
                line = cleanTitle(line);
                if (!line.isEmpty()) return line;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("artifact:")) {
                line = cleanTitle(line.substring("artifact:".length()));
                if (!line.isEmpty()) return line;
            }
            if (line.startsWith("**") && line.endsWith("**") && line.length() > 4) {
                line = cleanTitle(line.substring(2, line.length() - 2));
                if (!line.isEmpty()) return line;
            }
            break;
        }
        String name = language == null ? "" : language.trim();
        if (name.isEmpty()) name = "text";
        return name.toUpperCase(Locale.ROOT) + " artifact " + index;
    }

    private static String cleanTitle(String title) {
        String clean = title == null ? "" : title.replace("*", "").replace("_", "").trim();
        clean = clean.replaceAll("\\s+", " ");
        if (clean.length() > 64) clean = clean.substring(0, 61) + "…";
        return clean;
    }

    private synchronized void save() {
        try {
            JSONArray array = new JSONArray();
            for (Artifact item : artifacts) {
                JSONObject object = new JSONObject();
                object.put("id", item.id);
                object.put("responseId", item.responseId);
                object.put("chatId", item.chatId);
                object.put("title", item.title);
                object.put("language", item.language);
                object.put("content", item.content);
                object.put("state", item.state.name());
                object.put("createdAt", item.createdAt);
                object.put("updatedAt", item.updatedAt);
                array.put(object);
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
        } catch (Exception ignored) {
            // Workspace persistence must never interrupt chat or inference.
        }
    }

    private synchronized void load() {
        artifacts.clear();
        if (!file.exists()) return;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int count = input.read(data);
            if (count <= 0) return;
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                State state;
                try {
                    state = State.valueOf(object.optString("state", "COMPLETED"));
                } catch (Exception ignored) {
                    state = State.COMPLETED;
                }
                artifacts.add(new Artifact(
                        object.optString("id", UUID.randomUUID().toString()),
                        object.optString("responseId", ""), object.optString("chatId", ""),
                        object.optString("title", "Sync artifact"), object.optString("language", "text"),
                        object.optString("content", ""), state,
                        object.optLong("createdAt", System.currentTimeMillis()),
                        object.optLong("updatedAt", System.currentTimeMillis())));
            }
        } catch (Exception ignored) {
            artifacts.clear();
        }
        trimOldArtifacts();
    }
}
