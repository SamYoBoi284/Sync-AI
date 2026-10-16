package com.sam.syncai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Browse, import, inspect, export, and remove files explicitly kept by Sync AI. */
public final class FileLibraryActivity extends Activity {
    public static final String EXTRA_PREFILL_PROMPT = "com.sam.syncai.extra.FILE_LIBRARY_PROMPT";
    private static final int REQ_IMPORT = 7101;
    private static final int REQ_EXPORT = 7102;
    private int BG, SURFACE, SURFACE_2, TEXT, MUTED, ACCENT;

    private FileLibraryStore store;
    private LinearLayout root;
    private FrameLayout body;
    private TextView status;
    private FileLibraryStore.LibraryFile selected;
    private String pendingExportId;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new FileLibraryStore(getApplicationContext());
        final AppTheme palette = AppTheme.from(this);
        BG = palette.background; SURFACE = palette.surface; SURFACE_2 = palette.surface2;
        TEXT = palette.text; MUTED = palette.muted; ACCENT = palette.primary;
        buildShell();
        AppTheme.applySystemBars(this, palette);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            androidx.core.graphics.Insets safe = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        renderList();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackgroundColor(SURFACE);

        Button back = button("‹", false);
        back.setOnClickListener(v -> {
            if (selected != null) {
                selected = null;
                renderList();
            } else finish();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.VERTICAL);
        headings.setPadding(dp(10), 0, 0, 0);
        headings.addView(text("FILE LIBRARY", 17, TEXT, true));
        headings.addView(text("Files kept privately on this phone", 11, MUTED, false));
        header.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));

        Button importFilesButton = button("+ IMPORT", true);
        importFilesButton.setOnClickListener(v -> openImportPicker());
        header.addView(importFilesButton, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        status = text("", 12, MUTED, false);
        status.setPadding(dp(16), dp(6), dp(16), dp(4));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQ_EXPORT) pendingExportId = null;
            return;
        }
        if (requestCode == REQ_IMPORT) {
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            importUris(uris);
        } else if (requestCode == REQ_EXPORT) {
            Uri uri = data.getData();
            String id = pendingExportId;
            pendingExportId = null;
            if (uri != null && id != null) writeExport(uri, id);
        }
    }

    private void importUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        status.setText("Importing " + uris.size() + (uris.size() == 1 ? " file…" : " files…"));
        io.execute(() -> {
            int imported = 0, duplicates = 0, failed = 0;
            ArrayList<String> errors = new ArrayList<>();
            for (Uri uri : uris) {
                try {
                    int before = store.all().size();
                    FileLibraryStore.LibraryFile item = store.importUri(uri);
                    if (store.all().size() == before) duplicates++;
                    else imported++;
                    SyncEventLogger.record(this, "FileLibraryActivity", "IMPORT_ITEM_RESULT", "INFO",
                            "name=" + item.name);
                } catch (Exception error) {
                    failed++;
                    errors.add(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                    SyncEventLogger.recordException(this, "FileLibraryActivity", "IMPORT_ITEM_FAILED",
                            error, "uri=" + uri);
                }
            }
            final int i = imported, d = duplicates, f = failed;
            final String firstError = errors.isEmpty() ? "" : errors.get(0);
            runOnUiThread(() -> {
                status.setText("");
                renderList();
                String message = "Imported " + i + (i == 1 ? " file" : " files")
                        + (d > 0 ? " · " + d + " already in library" : "")
                        + (f > 0 ? " · " + f + " failed" : "");
                if (!firstError.isEmpty()) message += "\n" + firstError;
                Toast.makeText(this, message, f > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void renderList() {
        selected = null;
        body.removeAllViews();
        List<FileLibraryStore.LibraryFile> items = store.all();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(24));
        page.addView(text("YOUR FILES", 22, TEXT, true));
        TextView description = text(
                "Import files once, then mention their names in chat to let Sync work with readable text. Imported copies stay in Sync AI's private storage.",
                13, MUTED, false);
        description.setPadding(0, dp(6), 0, dp(14));
        page.addView(description);
        page.addView(text(items.size() + (items.size() == 1 ? " FILE" : " FILES"),
                11, ACCENT, true));

        if (items.isEmpty()) {
            LinearLayout card = card();
            TextView title = text("Your library is empty", 17, TEXT, true);
            title.setGravity(Gravity.CENTER);
            card.addView(title);
            TextView hint = text("Tap + IMPORT to select one or more files from your phone.", 13, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.addView(hint);
            page.addView(card);
        } else {
            for (FileLibraryStore.LibraryFile file : items) {
                LinearLayout itemCard = card();
                itemCard.setClickable(true);
                itemCard.setFocusable(true);
                itemCard.setOnClickListener(v -> showFile(file.id));
                itemCard.addView(text(file.name, 15, TEXT, true));
                String meta = android.text.format.Formatter.formatShortFileSize(this, file.sizeBytes)
                        + " · " + (file.isTextReadable() ? "Text-readable" : (file.mimeType == null ? "File" : file.mimeType));
                itemCard.addView(text(meta, 11, MUTED, false));
                itemCard.addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                        Locale.getDefault()).format(new Date(file.updatedAt)), 10, MUTED, false));
                TextView action = text("OPEN  ›", 11, ACCENT, true);
                action.setGravity(Gravity.END);
                itemCard.addView(action);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dp(10);
                page.addView(itemCard, lp);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showFile(String id) {
        FileLibraryStore.LibraryFile file = store.get(id);
        if (file == null) { renderList(); return; }
        selected = file;
        body.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(24));
        page.addView(text(file.name, 22, TEXT, true));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.topMargin = dp(7);
        metaLp.bottomMargin = dp(14);
        page.addView(text(android.text.format.Formatter.formatShortFileSize(this, file.sizeBytes)
                + " · " + file.mimeType + "\nSHA-256: " + file.sha256, 11, MUTED, false), metaLp);

        LinearLayout row = new LinearLayout(this);
        Button ask = button("ASK SYNC", true);
        ask.setOnClickListener(v -> askSync(file));
        row.addView(ask, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button export = button("EXPORT", false);
        export.setOnClickListener(v -> beginExport(file));
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        exportLp.leftMargin = dp(6);
        row.addView(export, exportLp);
        Button delete = button("DELETE", false);
        delete.setOnClickListener(v -> confirmDelete(file));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        deleteLp.leftMargin = dp(6);
        row.addView(delete, deleteLp);
        page.addView(row);

        LinearLayout previewCard = card();
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, -2);
        previewLp.topMargin = dp(14);
        page.addView(previewCard, previewLp);
        previewCard.addView(text("CONTENT PREVIEW", 11, ACCENT, true));
        if (file.isTextReadable()) {
            try {
                FileLibraryStore.TextContent content = store.readText(file.id, 12000);
                TextView preview = text(content.text.isEmpty() ? "(Empty file)" : content.text,
                        12, TEXT, false);
                preview.setTypeface(Typeface.MONOSPACE);
                preview.setTextIsSelectable(true);
                preview.setPadding(0, dp(10), 0, dp(6));
                previewCard.addView(preview);
                if (content.truncated) {
                    previewCard.addView(text("Preview shortened for display. Mention the filename in chat to provide Sync with a bounded excerpt.", 11, MUTED, false));
                }
            } catch (Exception error) {
                previewCard.addView(text("Could not read this text file: " + error.getMessage(), 12, MUTED, false));
            }
        } else {
            previewCard.addView(text(
                    "This file is stored and can be exported, but its format is not currently text-extractable by Sync's local language model. Images, PDFs, and office documents need format-specific extraction before Sync can analyze their contents.",
                    13, MUTED, false));
        }

        Button copyName = button("COPY FILE NAME", false);
        copyName.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("File name", file.name));
            Toast.makeText(this, "Copied file name. Mention it in chat to use the file.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-1, dp(44));
        copyLp.topMargin = dp(12);
        page.addView(copyName, copyLp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void askSync(FileLibraryStore.LibraryFile file) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_PREFILL_PROMPT, "Read and summarize the contents of the imported File Library file \"" + file.name + "\". If I have not asked for another action, ask me what I'd like to do with it next.");
        startActivity(intent);
        finish();
    }

    private void beginExport(FileLibraryStore.LibraryFile file) {
        pendingExportId = file.id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(file.mimeType == null ? "*/*" : file.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, file.name);
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void writeExport(Uri uri, String id) {
        status.setText("Exporting file…");
        io.execute(() -> {
            try {
                FileLibraryStore.LibraryFile item = store.get(id);
                if (item == null) throw new IllegalStateException("That file was removed.");
                try (InputStream in = new FileInputStream(store.exportFile(item));
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Could not open the selected destination.");
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    out.flush();
                }
                runOnUiThread(() -> Toast.makeText(this, "Exported " + item.name, Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + error.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                runOnUiThread(() -> status.setText(""));
            }
        });
    }

    private void confirmDelete(FileLibraryStore.LibraryFile file) {
        new AlertDialog.Builder(this)
                .setTitle("Remove file?")
                .setMessage("Remove \"" + file.name + "\" from Sync AI's private File Library? This cannot be undone.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REMOVE", (dialog, which) -> {
                    store.delete(file.id);
                    selected = null;
                    renderList();
                    Toast.makeText(this, "Removed " + file.name, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(SURFACE);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), AppTheme.mix(TEXT, SURFACE, 0.85f));
        card.setBackground(background);
        return card;
    }

    private Button button(String label, boolean prominent) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTextColor(prominent ? AppTheme.onAccent(ACCENT) : TEXT);
        button.setMinHeight(dp(40));
        button.setPadding(dp(9), 0, dp(9), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(prominent ? ACCENT : SURFACE_2);
        bg.setCornerRadius(dp(12));
        button.setBackground(bg);
        button.setOnClickListener(v -> { });
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
