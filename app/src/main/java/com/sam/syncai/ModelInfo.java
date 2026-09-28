package com.sam.syncai;

import java.io.File;
import java.util.Locale;

public final class ModelInfo {
    public final String id;
    public final String name;
    public final String path;
    public final String format;
    public final long sizeBytes;
    public final String sha256;
    public final long importedAt;

    public ModelInfo(String id, String name, String path, String format,
                     long sizeBytes, String sha256, long importedAt) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.format = format;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.importedAt = importedAt;
    }

    public File file() {
        return new File(path);
    }

    public String sizeLabel() {
        double size = sizeBytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024.0;
            i++;
        }
        return String.format(Locale.US, "%.1f %s", size, units[i]);
    }

    public String summary() {
        return name + "\n" + format.toUpperCase(Locale.US) + " • " + sizeLabel();
    }
}
