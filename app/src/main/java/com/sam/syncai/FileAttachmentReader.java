package com.sam.syncai;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FileAttachmentReader {
    private static final int MAX_BYTES = 512 * 1024;

    private FileAttachmentReader() {}

    public static FileAttachment read(Context context, Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String name = "attachment";
        String mime = resolver.getType(uri);
        if (mime == null) mime = "application/octet-stream";

        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
            } finally {
                cursor.close();
            }
        }

        boolean text = isText(name, mime);
        if (!text) return new FileAttachment(name, mime, "", false);

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Could not open " + name);
            byte[] bytes = readLimited(in);
            String content = new String(bytes, StandardCharsets.UTF_8);
            return new FileAttachment(name, mime, content, true);
        }
    }

    private static boolean isText(String name, String mime) {
        String lower = name.toLowerCase(Locale.US);
        return mime.startsWith("text/")
                || mime.contains("json")
                || mime.contains("xml")
                || mime.contains("javascript")
                || mime.contains("yaml")
                || mime.contains("csv")
                || lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".markdown")
                || lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".json")
                || lower.endsWith(".xml")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".py")
                || lower.endsWith(".c")
                || lower.endsWith(".cpp")
                || lower.endsWith(".h")
                || lower.endsWith(".hpp")
                || lower.endsWith(".csv")
                || lower.endsWith(".log")
                || lower.endsWith(".properties")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml");
    }

    private static byte[] readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IllegalArgumentException("Text attachment is too large. Maximum is 512 KB.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
