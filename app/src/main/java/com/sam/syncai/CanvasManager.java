package com.sam.syncai;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CanvasManager {
    private static final String FILE_NAME = "canvas.md";
    private static final int MAX_BYTES = 1024 * 1024;

    private CanvasManager() {}

    private static File file(Context context) {
        File root = new File(context.getFilesDir(), "workspace");
        if (!root.exists()) root.mkdirs();
        return new File(root, FILE_NAME);
    }

    public static String read(Context context) throws Exception {
        File file = file(context);
        if (!file.exists()) return "";
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    public static void write(Context context, String title, String body) throws Exception {
        String safeTitle = title == null || title.trim().isEmpty() ? "Untitled Canvas" : title.trim();
        String safeBody = body == null ? "" : body;
        String content = safeTitle + "\n\n" + safeBody;
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Canvas is limited to 1 MB.");
        }
        File file = file(context);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
