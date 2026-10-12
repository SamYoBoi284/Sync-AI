package com.sam.syncai;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** A private, persistent library of files the user explicitly imports into Sync AI. */
public final class FileLibraryStore {
    public static final long MAX_IMPORT_BYTES = 32L * 1024L * 1024L;

    public static final class LibraryFile {
        public final String id;
        public final String name;
        public final String mimeType;
        public final long sizeBytes;
        public final String sha256;
        public final long createdAt;
        public final long updatedAt;

        LibraryFile(String id, String name, String mimeType, long sizeBytes, String sha256,
                    long createdAt, long updatedAt) {
            this.id = id;
            this.name = name;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public boolean isTextReadable() {
            return isTextName(name) || (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/"));
        }
    }

    public static final class TextContent {
        public final String text;
        public final boolean truncated;
        public final boolean readable;

        TextContent(String text, boolean truncated, boolean readable) {
            this.text = text;
            this.truncated = truncated;
            this.readable = readable;
        }
    }

    private final Context context;
    private final File directory;
    private final File contentDirectory;
    private final File indexFile;
    private final List<LibraryFile> files = new ArrayList<>();

    public FileLibraryStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "file-library");
        contentDirectory = new File(directory, "items");
        if (!contentDirectory.exists() && !contentDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create Sync AI's File Library.");
        }
        indexFile = new File(directory, "library.json");
        load();
    }

    public synchronized List<LibraryFile> all() {
        ArrayList<LibraryFile> result = new ArrayList<>(files);
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    public synchronized LibraryFile get(String id) {
        if (id == null) return null;
        for (LibraryFile file : files) if (id.equals(file.id)) return file;
        return null;
    }

    public synchronized LibraryFile findByName(String name) {
        if (name == null) return null;
        for (LibraryFile file : files) {
            if (file.name.equalsIgnoreCase(name.trim())) return file;
        }
        return null;
    }

    public synchronized List<LibraryFile> findMentioned(String query, int maximum) {
        ArrayList<LibraryFile> matches = new ArrayList<>();
        if (query == null || query.trim().isEmpty() || maximum <= 0) return matches;
        String lower = query.toLowerCase(Locale.ROOT);
        for (LibraryFile file : files) {
            String name = file.name.toLowerCase(Locale.ROOT);
            String stem = stem(name);
            boolean exactName = lower.contains(name);
            boolean exactStem = stem.length() >= 4 && Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])" + Pattern.quote(stem) + "(?![\\p{L}\\p{N}_])")
                    .matcher(lower).find();
            if (exactName || exactStem) matches.add(file);
        }
        Collections.sort(matches, (a, b) -> {
            int byNameLength = Integer.compare(b.name.length(), a.name.length());
            return byNameLength != 0 ? byNameLength : Long.compare(b.updatedAt, a.updatedAt);
        });
        if (matches.size() > maximum) return new ArrayList<>(matches.subList(0, maximum));
        return matches;
    }

    /** Copies the selected URI into app-private storage; no ongoing URI permission is needed. */
    public synchronized LibraryFile importUri(Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("No file was selected.");
        ContentResolver resolver = context.getContentResolver();
        String name = displayName(resolver, uri);
        String mime = resolver.getType(uri);
        if (mime == null || mime.trim().isEmpty()) mime = mimeFromName(name);

        String id = UUID.randomUUID().toString();
        File temporary = new File(contentDirectory, id + ".part");
        File destination = new File(contentDirectory, id + ".bin");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0L;
        try (InputStream in = resolver.openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temporary)) {
            if (in == null) throw new IllegalStateException("Android could not open the selected file.");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                size += count;
                if (size > MAX_IMPORT_BYTES) {
                    throw new IllegalArgumentException("Files larger than 32 MiB are not supported yet.");
                }
                digest.update(buffer, 0, count);
                out.write(buffer, 0, count);
            }
            out.getFD().sync();
        } catch (Exception error) {
            temporary.delete();
            throw error;
        }

        String hash = toHex(digest.digest());
        for (LibraryFile old : files) {
            if (old.sha256.equalsIgnoreCase(hash)) {
                temporary.delete();
                return old;
            }
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("Could not save the imported file in Sync AI.");
        }
        long now = System.currentTimeMillis();
        LibraryFile item = new LibraryFile(id, safeName(name), mime, size, hash, now, now);
        files.add(item);
        try {
            saveIndex();
        } catch (Exception error) {
            files.remove(item);
            destination.delete();
            throw error;
        }
        SyncEventLogger.record(context, "FileLibraryStore", "FILE_IMPORTED", "INFO",
                "name=" + item.name + " bytes=" + item.sizeBytes + " sha256=" + item.sha256);
        return item;
    }

    public synchronized void delete(String id) {
        LibraryFile item = get(id);
        if (item == null) return;
        files.remove(item);
        new File(contentDirectory, item.id + ".bin").delete();
        saveIndex();
        SyncEventLogger.record(context, "FileLibraryStore", "FILE_REMOVED", "INFO", "name=" + item.name);
    }

    public synchronized TextContent readText(String id, int maxChars) throws Exception {
        LibraryFile item = get(id);
        if (item == null) throw new IllegalArgumentException("That file is no longer in the library.");
        if (!item.isTextReadable()) return new TextContent("", false, false);
        int limit = Math.max(1, maxChars);
        File source = new File(contentDirectory, item.id + ".bin");
        StringBuilder result = new StringBuilder(Math.min(limit, 4096));
        boolean truncated = false;
        try (Reader reader = new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                int available = limit - result.length();
                if (count > available) {
                    if (available > 0) result.append(buffer, 0, available);
                    truncated = true;
                    break;
                }
                result.append(buffer, 0, count);
                if (result.length() >= limit) {
                    int next = reader.read();
                    if (next != -1) truncated = true;
                    break;
                }
            }
        }
        return new TextContent(result.toString(), truncated, true);
    }

    /** Writes generated text to the library. A non-null replaceId explicitly updates that item. */
    public synchronized LibraryFile saveGeneratedText(String requestedName, String text, String replaceId)
            throws Exception {
        String name = safeName(requestedName);
        if (name.equals("imported-file")) name = "sync-edited-file.txt";
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);

        if (replaceId != null) {
            LibraryFile old = get(replaceId);
            if (old == null) throw new IllegalArgumentException("The original file is no longer in the library.");
            if (!old.isTextReadable()) throw new IllegalArgumentException("Only text-readable files can be replaced by generated text.");
            File temp = new File(contentDirectory, old.id + ".part");
            File destination = new File(contentDirectory, old.id + ".bin");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.getFD().sync();
            }
            try {
                moveReplacing(temp, destination);
            } catch (Exception error) {
                temp.delete();
                throw new IllegalStateException("Could not replace the file.", error);
            }
            long now = System.currentTimeMillis();
            LibraryFile updated = new LibraryFile(old.id, old.name, old.mimeType, bytes.length,
                    sha256(bytes), old.createdAt, now);
            replaceMetadata(old, updated);
            saveIndex();
            SyncEventLogger.record(context, "FileLibraryStore", "FILE_UPDATED_FROM_SYNC", "INFO",
                    "name=" + updated.name + " bytes=" + updated.sizeBytes);
            return updated;
        }

        String base = name;
        int dot = name.lastIndexOf('.');
        String prefix = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : ".txt";
        int copyNumber = 1;
        while (findByName(name) != null) {
            name = prefix + " (Sync copy " + copyNumber++ + ")" + suffix;
        }
        String id = UUID.randomUUID().toString();
        File destination = new File(contentDirectory, id + ".bin");
        try (FileOutputStream out = new FileOutputStream(destination)) {
            out.write(bytes);
            out.getFD().sync();
        }
        long now = System.currentTimeMillis();
        LibraryFile item = new LibraryFile(id, name, mimeFromName(name), bytes.length,
                sha256(bytes), now, now);
        files.add(item);
        try {
            saveIndex();
        } catch (RuntimeException error) {
            files.remove(item);
            destination.delete();
            throw error;
        }
        SyncEventLogger.record(context, "FileLibraryStore", "FILE_SAVED_FROM_SYNC", "INFO",
                "name=" + item.name + " bytes=" + item.sizeBytes);
        return item;
    }

    public File exportFile(LibraryFile item) {
        return new File(contentDirectory, item.id + ".bin");
    }

    public static boolean isTextName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        String[] extensions = {".txt", ".md", ".markdown", ".csv", ".tsv", ".json", ".xml",
                ".html", ".htm", ".css", ".js", ".mjs", ".cjs", ".java", ".kt", ".kts",
                ".py", ".pyw", ".c", ".h", ".cpp", ".hpp", ".sh", ".bat", ".ps1", ".yaml",
                ".yml", ".toml", ".ini", ".cfg", ".conf", ".properties", ".sql", ".log",
                ".gradle", ".groovy", ".dart", ".go", ".rs", ".swift", ".php", ".rb",
                ".gitignore", ".makefile"};
        for (String extension : extensions) if (lower.endsWith(extension)) return true;
        return false;
    }

    private synchronized void replaceMetadata(LibraryFile old, LibraryFile updated) {
        for (int i = 0; i < files.size(); i++) {
            if (old.id.equals(files.get(i).id)) {
                files.set(i, updated);
                return;
            }
        }
    }

    private String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) name = cursor.getString(column);
            }
        } catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) name = uri.getLastPathSegment();
        return safeName(name);
    }

    private static String safeName(String raw) {
        String value = raw == null ? "" : raw.trim().replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) value = "imported-file";
        if (value.length() > 160) value = value.substring(0, 160);
        return value;
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String mimeFromName(String name) {
        if (name == null) return "application/octet-stream";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "text/javascript";
        return "application/octet-stream";
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private synchronized void saveIndex() {
        try {
            JSONArray array = new JSONArray();
            for (LibraryFile item : files) {
                JSONObject object = new JSONObject();
                object.put("id", item.id);
                object.put("name", item.name);
                object.put("mimeType", item.mimeType);
                object.put("sizeBytes", item.sizeBytes);
                object.put("sha256", item.sha256);
                object.put("createdAt", item.createdAt);
                object.put("updatedAt", item.updatedAt);
                array.put(object);
            }
            File temp = new File(directory, "library.json.part");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            moveReplacing(temp, indexFile);
        } catch (Exception error) {
            throw new IllegalStateException("Could not save the File Library index.", error);
        }
    }

    private static void moveReplacing(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private synchronized void load() {
        files.clear();
        if (!indexFile.exists()) return;
        try (FileInputStream in = new FileInputStream(indexFile)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray array = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String id = object.optString("id", "");
                if (id.isEmpty() || !new File(contentDirectory, id + ".bin").isFile()) continue;
                files.add(new LibraryFile(id, object.optString("name", "imported-file"),
                        object.optString("mimeType", "application/octet-stream"),
                        object.optLong("sizeBytes", 0L), object.optString("sha256", ""),
                        object.optLong("createdAt", System.currentTimeMillis()),
                        object.optLong("updatedAt", System.currentTimeMillis())));
            }
        } catch (Exception error) {
            SyncEventLogger.recordException(context, "FileLibraryStore", "LIBRARY_INDEX_LOAD_FAILED",
                    error, "loading local library metadata");
            files.clear();
        }
    }
}
