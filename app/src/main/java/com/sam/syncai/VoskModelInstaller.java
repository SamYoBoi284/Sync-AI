package com.sam.syncai;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the optional offline Vosk model into app-private storage. */
public final class VoskModelInstaller {
    private static final String WAKE_MODEL_DIR = "vosk-model";
    private static final String VOICE_MODEL_DIR = "vosk-voice-model";
    private static final long MAX_UNPACKED_BYTES = 3L * 1024L * 1024L * 1024L;

    private VoskModelInstaller() {}

    /** Existing install path retained for Hey Sync wake-word compatibility. */
    public static File modelDirectory(Context context) {
        return wakeWordModelDirectory(context);
    }

    public static File wakeWordModelDirectory(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), WAKE_MODEL_DIR);
    }

    public static File voiceModelDirectory(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), VOICE_MODEL_DIR);
    }

    public static boolean isInstalled(Context context) {
        return isValidModel(wakeWordModelDirectory(context));
    }

    public static boolean isWakeWordInstalled(Context context) {
        return isInstalled(context);
    }

    public static boolean isVoiceModelInstalled(Context context) {
        return isValidModel(voiceModelDirectory(context));
    }

    /** Backward-compatible entry point: legacy imports belong to the wake-word model. */
    public static void importZip(Context context, Uri uri) throws Exception {
        importWakeWordZip(context, uri);
    }

    public static void importWakeWordZip(Context context, Uri uri) throws Exception {
        importZipInto(context, uri, WAKE_MODEL_DIR, wakeWordModelDirectory(context));
    }

    public static void importVoiceZip(Context context, Uri uri) throws Exception {
        importZipInto(context, uri, VOICE_MODEL_DIR, voiceModelDirectory(context));
    }

    private static void importZipInto(Context context, Uri uri, String directoryName,
                                      File target) throws Exception {
        Context app = context.getApplicationContext();
        File files = app.getFilesDir();
        File temp = new File(files, directoryName + ".importing");
        File backup = new File(files, directoryName + ".backup");
        deleteRecursively(temp);
        if (!target.exists() && backup.exists()) backup.renameTo(target);
        deleteRecursively(backup);
        if (!temp.mkdirs() && !temp.isDirectory()) {
            throw new IllegalStateException("Could not create the temporary Vosk model folder.");
        }

        long unpacked = 0L;
        InputStream raw = app.getContentResolver().openInputStream(uri);
        if (raw == null) throw new IllegalStateException("Could not open the selected ZIP file.");
        try (InputStream input = raw;
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                int firstSlash = name.indexOf('/');
                if (firstSlash >= 0) name = name.substring(firstSlash + 1);
                if (name.isEmpty()) {
                    zip.closeEntry();
                    continue;
                }
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                    throw new IllegalArgumentException("The ZIP contains an unsafe path.");
                }
                File out = new File(temp, name);
                String rootPath = temp.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(rootPath)) {
                    throw new IllegalArgumentException("The ZIP contains an unsafe path.");
                }
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) {
                        throw new IllegalStateException("Could not create a model directory.");
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IllegalStateException("Could not create a model directory.");
                    }
                    try (BufferedOutputStream output =
                                 new BufferedOutputStream(new FileOutputStream(out))) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            unpacked += count;
                            if (unpacked > MAX_UNPACKED_BYTES) {
                                throw new IllegalArgumentException("The selected ZIP expands beyond the 3 GiB Vosk model limit.");
                            }
                            output.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
            }

            if (!isValidModel(temp)) {
                throw new IllegalArgumentException(
                        "This ZIP does not contain the expected Vosk English model files.");
            }

            if (target.exists() && !target.renameTo(backup)) {
                throw new IllegalStateException("Could not preserve the existing Vosk model.");
            }
            if (!temp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target);
                throw new IllegalStateException("Could not install the Vosk model.");
            }
            deleteRecursively(backup);
        } catch (Exception error) {
            deleteRecursively(temp);
            if (!target.exists() && backup.exists()) backup.renameTo(target);
            throw error;
        }
    }

    private static boolean isValidModel(File root) {
        return nonEmpty(new File(root, "am/final.mdl"))
                && nonEmpty(new File(root, "conf/model.conf"))
                && nonEmpty(new File(root, "graph/HCLr.fst"))
                && nonEmpty(new File(root, "graph/Gr.fst"));
    }

    private static boolean nonEmpty(File file) {
        return file.isFile() && file.length() > 0L;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
