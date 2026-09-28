package com.sam.syncai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ModelManager {
    private final Context context;
    private final File modelsDir;
    private final File registryFile;
    private final List<ModelInfo> models = new ArrayList<>();
    private String loadedModelId;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.modelsDir = new File(this.context.getFilesDir(), "models");
        this.registryFile = new File(this.context.getFilesDir(), "models.json");
        if (!modelsDir.exists()) modelsDir.mkdirs();
        loadRegistry();
    }

    public synchronized List<ModelInfo> getModels() {
        return new ArrayList<>(models);
    }

    public synchronized ModelInfo getLoadedModel() {
        if (loadedModelId == null) return null;
        for (ModelInfo m : models) {
            if (m.id.equals(loadedModelId)) return m;
        }
        loadedModelId = null;
        return null;
    }

    public synchronized void markLoaded(String id) {
        loadedModelId = id;
    }

    public synchronized void clearLoaded() {
        loadedModelId = null;
    }

    public String importModel(Uri uri) throws Exception {
        String originalName = queryDisplayName(uri);
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = "imported-model";
        }

        String extension = extensionOf(originalName);
        String id = UUID.randomUUID().toString();
        File destination = new File(modelsDir, id + (extension.isEmpty() ? "" : "." + extension));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0;

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destination)) {
            if (in == null) throw new IllegalStateException("Could not open selected model.");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                size += read;
            }
        } catch (Exception e) {
            // Never leave a partial model behind.
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw e;
        }

        String hash = hex(digest.digest());

        synchronized (this) {
            for (ModelInfo existing : models) {
                if (existing.sha256.equalsIgnoreCase(hash)) {
                    //noinspection ResultOfMethodCallIgnored
                    destination.delete();
                    return existing.id;
                }
            }

            ModelInfo info = new ModelInfo(
                    id,
                    originalName,
                    destination.getAbsolutePath(),
                    formatFor(extension),
                    size,
                    hash,
                    System.currentTimeMillis()
            );
            models.add(info);
            saveRegistry();
            return info.id;
        }
    }

    public synchronized void removeModel(String id) {
        for (int i = 0; i < models.size(); i++) {
            ModelInfo m = models.get(i);
            if (!m.id.equals(id)) continue;
            //noinspection ResultOfMethodCallIgnored
            m.file().delete();
            models.remove(i);
            if (id.equals(loadedModelId)) loadedModelId = null;
            saveRegistry();
            return;
        }
    }

    public synchronized long totalStorageBytes() {
        long total = 0;
        for (ModelInfo m : models) total += Math.max(0, m.sizeBytes);
        return total;
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor == null) return null;
        try {
            if (cursor.moveToFirst()) return cursor.getString(0);
            return null;
        } finally {
            cursor.close();
        }
    }

    private void loadRegistry() {
        synchronized (this) {
            models.clear();
            if (!registryFile.exists()) return;
            try (FileInputStream in = new FileInputStream(registryFile)) {
                byte[] data = new byte[(int) registryFile.length()];
                int read = in.read(data);
                if (read <= 0) return;
                JSONArray array = new JSONArray(new String(data, "UTF-8"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    File file = new File(o.optString("path"));
                    if (!file.exists()) continue;
                    models.add(new ModelInfo(
                            o.getString("id"),
                            o.getString("name"),
                            o.getString("path"),
                            o.optString("format", "unknown"),
                            o.optLong("sizeBytes", file.length()),
                            o.optString("sha256", ""),
                            o.optLong("importedAt", 0)
                    ));
                }
            } catch (Exception ignored) {
                models.clear();
            }
        }
    }

    private void saveRegistry() {
        try {
            JSONArray array = new JSONArray();
            for (ModelInfo m : models) {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("name", m.name);
                o.put("path", m.path);
                o.put("format", m.format);
                o.put("sizeBytes", m.sizeBytes);
                o.put("sha256", m.sha256);
                o.put("importedAt", m.importedAt);
                array.put(o);
            }
            try (FileOutputStream out = new FileOutputStream(registryFile)) {
                out.write(array.toString(2).getBytes("UTF-8"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not save model registry.", e);
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }

    private static String formatFor(String extension) {
        if ("gguf".equals(extension)) return "gguf";
        if ("ggml".equals(extension)) return "ggml";
        if ("bin".equals(extension)) return "bin";
        if ("safetensors".equals(extension)) return "safetensors";
        if ("onnx".equals(extension)) return "onnx";
        if ("tflite".equals(extension)) return "tflite";
        return extension.isEmpty() ? "unknown" : extension;
    }

    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) b.append(String.format("%02x", value));
        return b.toString();
    }
}
