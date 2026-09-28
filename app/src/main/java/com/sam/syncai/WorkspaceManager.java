package com.sam.syncai;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class WorkspaceManager {
    private final File root;

    public WorkspaceManager(Context context) {
        root = new File(context.getFilesDir(), "workspace");
        if (!root.exists()) root.mkdirs();
    }

    public String read(String name) throws Exception {
        return new String(Files.readAllBytes(resolve(name).toPath()), StandardCharsets.UTF_8);
    }

    public void write(String name, String content) throws Exception {
        if (content == null) throw new IllegalArgumentException("File content cannot be null.");
        File file = resolve(name);
        if (content.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw new IllegalArgumentException("Workspace files are limited to 1 MB.");
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private File resolve(String name) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("A file name is required.");
        String clean = name.replace('\\', '/');
        if (clean.contains("..") || clean.startsWith("/") || clean.contains(":")) {
            throw new SecurityException("Invalid workspace path.");
        }
        File file = new File(root, clean);
        File canonicalRoot = root.getCanonicalFile();
        File canonical = file.getCanonicalFile();
        if (!canonical.getPath().startsWith(canonicalRoot.getPath() + File.separator)) {
            throw new SecurityException("Workspace path escapes the app workspace.");
        }
        File parent = canonical.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create workspace directory.");
        }
        return canonical;
    }
}
