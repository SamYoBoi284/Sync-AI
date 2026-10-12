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
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WorkspaceActivity extends Activity {
    private static final int BG = Color.rgb(7, 8, 14);
    private static final int SURFACE = Color.rgb(17, 19, 30);
    private static final int SURFACE_2 = Color.rgb(25, 28, 43);
    private static final int TEXT = Color.rgb(239, 240, 250);
    private static final int MUTED = Color.rgb(151, 156, 180);
    private static final int ACCENT = Color.rgb(154, 96, 255);

    private WorkspaceStore store;
    private LinearLayout root;
    private FrameLayout body;
    private WorkspaceStore.Artifact selected;
    private String pendingExport;
    private String pendingMime = "text/plain";
    private WebView activeWebView;
    private TextView selectedMetaView;
    private TextView activeSourceView;
    private boolean showingPreview = true;
    private long lastPreviewRefreshAt;
    private int lastPreviewContentLength;
    private String selectedSignature = "";
    private ScrollView listScroll;
    private String lastListSignature = "";
    private boolean pollScheduled;
    private final Handler workspaceHandler = new Handler(Looper.getMainLooper());
    private final Runnable workspacePoller = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            if (store != null) {
                store.reload();
                if (selected == null) {
                    List<WorkspaceStore.Artifact> latest = store.all();
                    String currentSignature = signature(latest);
                    if (!currentSignature.equals(lastListSignature)) {
                        int oldScrollY = listScroll == null ? 0 : listScroll.getScrollY();
                        renderList();
                        if (listScroll != null) {
                            ScrollView updatedScroll = listScroll;
                            updatedScroll.post(() -> updatedScroll.scrollTo(0, oldScrollY));
                        }
                    }
                } else {
                    WorkspaceStore.Artifact latest = store.get(selected.id);
                    if (latest == null) {
                        selected = null;
                        renderList();
                    } else if (!artifactSignature(latest).equals(selectedSignature)) {
                        WorkspaceStore.Artifact previous = selected;
                        boolean stateChanged = previous.state != latest.state;
                        selected = latest;
                        selectedSignature = artifactSignature(latest);
                        if (selectedMetaView != null) {
                            selectedMetaView.setText(latest.language.toUpperCase(Locale.ROOT)
                                    + "  •  " + stateLabel(latest.state));
                            selectedMetaView.setTextColor(stateColor(latest.state));
                        }
                        if (activeSourceView != null) activeSourceView.setText(latest.content);
                        long now = System.currentTimeMillis();
                        boolean refreshPreview = activeWebView != null
                                && (stateChanged || latest.state != WorkspaceStore.State.IN_PROGRESS
                                || latest.content.length() - lastPreviewContentLength >= 160
                                || now - lastPreviewRefreshAt >= 3000L);
                        if (refreshPreview) {
                            activeWebView.loadDataWithBaseURL(
                                    "about:blank", latest.content, "text/html", "UTF-8", null);
                            lastPreviewRefreshAt = now;
                            lastPreviewContentLength = latest.content.length();
                        }
                    }
                }
            }
            workspaceHandler.postDelayed(this, 900L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new WorkspaceStore(getApplicationContext());
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        buildShell();
        renderList();
    }

    @Override protected void onResume() {
        super.onResume();
        if (body != null && selected == null) renderList();
        if (!pollScheduled) {
            pollScheduled = true;
            workspaceHandler.postDelayed(workspacePoller, 900L);
        }
    }

    @Override protected void onPause() {
        workspaceHandler.removeCallbacks(workspacePoller);
        pollScheduled = false;
        super.onPause();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackgroundColor(SURFACE);

        Button back = button("‹", true);
        back.setOnClickListener(v -> {
            if (selected != null) {
                selected = null;
                renderList();
            } else {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.VERTICAL);
        headings.setPadding(dp(10), 0, 0, 0);
        TextView title = text("SYNC CANVAS", 17, TEXT, true);
        TextView subtitle = text("Artifacts created by Sync", 12, MUTED, false);
        headings.addView(title);
        headings.addView(subtitle);
        header.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = button("↻", true);
        refresh.setContentDescription("Refresh workspace");
        refresh.setOnClickListener(v -> {
            if (selected == null) renderList();
            else showArtifact(selected.id);
        });
        header.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));

        body = new FrameLayout(this);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(left, top, right, bottom);
            return insets;
        });
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        root.requestApplyInsets();
    }

    private void renderList() {
        selected = null;
        showingPreview = true;
        store.reload();
        if (activeWebView != null) {
            activeWebView.destroy();
            activeWebView = null;
        }
        body.removeAllViews();

        List<WorkspaceStore.Artifact> items = store.all();
        lastListSignature = signature(items);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(18), dp(16), dp(24));

        TextView intro = text("Made by Sync", 23, TEXT, true);
        page.addView(intro);
        TextView description = text(
                "Code, documents, and runnable HTML previews Sync has produced appear here. This is a viewing workspace, not an editor.",
                13, MUTED, false);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.topMargin = dp(6);
        descLp.bottomMargin = dp(18);
        page.addView(description, descLp);

        if (items.isEmpty()) {
            LinearLayout empty = card();
            TextView mark = text("✧", 38, ACCENT, true);
            mark.setGravity(Gravity.CENTER);
            empty.addView(mark, new LinearLayout.LayoutParams(-1, dp(56)));
            TextView emptyTitle = text("Your Canvas is ready", 17, TEXT, true);
            emptyTitle.setGravity(Gravity.CENTER);
            empty.addView(emptyTitle);
            TextView emptyBody = text(
                    "When Sync generates a fenced code block or document artifact in chat, it will show up here. Ask Sync to create a webpage, code file, or other deliverable.",
                    13, MUTED, false);
            emptyBody.setGravity(Gravity.CENTER);
            emptyBody.setPadding(dp(10), dp(8), dp(10), dp(12));
            empty.addView(emptyBody);
            page.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView count = text(items.size() + (items.size() == 1 ? " ARTIFACT" : " ARTIFACTS"),
                    11, MUTED, true);
            count.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-1, -2);
            countLp.bottomMargin = dp(10);
            page.addView(count, countLp);

            for (WorkspaceStore.Artifact artifact : items) {
                LinearLayout itemCard = card();
                itemCard.setClickable(true);
                itemCard.setFocusable(true);
                itemCard.setOnClickListener(v -> showArtifact(artifact.id));

                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView glyph = text(glyphFor(artifact.language), 23, ACCENT, true);
                glyph.setGravity(Gravity.CENTER);
                row.addView(glyph, new LinearLayout.LayoutParams(dp(42), dp(48)));

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setPadding(dp(8), 0, 0, 0);
                info.addView(text(artifact.title, 15, TEXT, true));
                String meta = artifact.language.toUpperCase(Locale.ROOT)
                        + "  •  " + stateLabel(artifact.state);
                info.addView(text(meta, 11, stateColor(artifact.state), true));
                info.addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT,
                        DateFormat.SHORT).format(new Date(artifact.updatedAt)), 11, MUTED, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text("›", 27, MUTED, false));
                itemCard.addView(row);
                TextView snippet = text(snippet(artifact.content), 12, MUTED, false);
                snippet.setMaxLines(2);
                LinearLayout.LayoutParams snippetLp = new LinearLayout.LayoutParams(-1, -2);
                snippetLp.topMargin = dp(8);
                itemCard.addView(snippet, snippetLp);

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
                itemLp.bottomMargin = dp(10);
                page.addView(itemCard, itemLp);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        listScroll = scroll;
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showArtifact(String id) {
        WorkspaceStore.Artifact artifact = store.get(id);
        if (artifact == null) {
            selected = null;
            renderList();
            return;
        }
        selected = artifact;
        selectedSignature = artifactSignature(artifact);
        selectedMetaView = null;
        activeSourceView = null;
        lastPreviewRefreshAt = System.currentTimeMillis();
        lastPreviewContentLength = artifact.content == null ? 0 : artifact.content.length();
        if (activeWebView != null) {
            activeWebView.destroy();
            activeWebView = null;
        }
        body.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(20));

        page.addView(text(artifact.title, 22, TEXT, true));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.topMargin = dp(5);
        metaLp.bottomMargin = dp(14);
        selectedMetaView = text(artifact.language.toUpperCase(Locale.ROOT) + "  •  "
                + stateLabel(artifact.state), 12, stateColor(artifact.state), true);
        page.addView(selectedMetaView, metaLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = button("COPY", false);
        copy.setOnClickListener(v -> copyArtifact(selected == null ? artifact : selected));
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button save = button("SAVE", false);
        save.setOnClickListener(v -> beginExport(selected == null ? artifact : selected));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        saveLp.leftMargin = dp(7);
        actions.addView(save, saveLp);
        Button delete = button("DELETE", false);
        delete.setOnClickListener(v -> confirmDelete(artifact));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        deleteLp.leftMargin = dp(7);
        actions.addView(delete, deleteLp);
        page.addView(actions);

        boolean html = isHtml(artifact);
        if (html) {
            LinearLayout viewToggle = new LinearLayout(this);
            Button preview = button("PREVIEW", showingPreview, false);
            Button source = button("SOURCE", !showingPreview, false);
            preview.setOnClickListener(v -> {
                showingPreview = true;
                showArtifact(artifact.id);
            });
            source.setOnClickListener(v -> {
                showingPreview = false;
                showArtifact(artifact.id);
            });
            viewToggle.addView(preview, new LinearLayout.LayoutParams(0, dp(38), 1));
            LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(0, dp(38), 1);
            sourceLp.leftMargin = dp(7);
            viewToggle.addView(source, sourceLp);
            LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(-1, -2);
            toggleLp.topMargin = dp(10);
            page.addView(viewToggle, toggleLp);
        }

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(round(SURFACE, dp(14)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(420));
        previewLp.topMargin = dp(12);
        page.addView(previewFrame, previewLp);

        if (html && showingPreview) {
            WebView webView = new WebView(this);
            activeWebView = webView;
            webView.setBackgroundColor(SURFACE);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setAllowFileAccess(false);
            webView.getSettings().setAllowContentAccess(false);
            webView.getSettings().setDomStorageEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return true;
                }

                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String scheme = request.getUrl().getScheme();
                    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                        return new WebResourceResponse("text/plain", "UTF-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });
            webView.loadDataWithBaseURL("about:blank", artifact.content, "text/html", "UTF-8", null);
            previewFrame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ScrollView codeScroll = new ScrollView(this);
            TextView code = text(artifact.content, 12, TEXT, false);
            activeSourceView = code;
            code.setTypeface(Typeface.MONOSPACE);
            code.setTextIsSelectable(true);
            code.setGravity(Gravity.TOP | Gravity.START);
            code.setPadding(dp(13), dp(13), dp(13), dp(13));
            codeScroll.addView(code);
            previewFrame.addView(codeScroll, new FrameLayout.LayoutParams(-1, -1));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private static String artifactSignature(WorkspaceStore.Artifact artifact) {
        if (artifact == null) return "";
        return artifact.id + "|" + artifact.updatedAt + "|" + artifact.state
                + "|" + (artifact.content == null ? 0 : artifact.content.length());
    }

    private void copyArtifact(WorkspaceStore.Artifact artifact) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(
                ClipData.newPlainText(artifact.title, artifact.content));
        Toast.makeText(this, "Copied " + artifact.title, Toast.LENGTH_SHORT).show();
    }

    private void beginExport(WorkspaceStore.Artifact artifact) {
        pendingExport = artifact.content;
        pendingMime = isHtml(artifact) ? "text/html" : "text/plain";
        String extension = isHtml(artifact) ? ".html"
                : ("json".equalsIgnoreCase(artifact.language) ? ".json" : ".txt");
        String name = artifact.title.replaceAll("[^A-Za-z0-9._-]+", "-");
        if (name.isEmpty()) name = "sync-artifact";
        if (!name.contains(".")) name += extension;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(pendingMime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, 6201);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 6201) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingExport = null;
            return;
        }
        Uri uri = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Couldn't open the destination.");
            output.write((pendingExport == null ? "" : pendingExport).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Artifact saved", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Save failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingExport = null;
        }
    }

    private void confirmDelete(WorkspaceStore.Artifact artifact) {
        new AlertDialog.Builder(this)
                .setTitle("Remove artifact?")
                .setMessage("This only removes the saved Workspace copy. Your chat history stays unchanged.")
                .setNegativeButton("CANCEL", (dialog, which) -> { })
                .setPositiveButton("REMOVE", (dialog, which) -> {
                    store.delete(artifact.id);
                    selected = null;
                    renderList();
                })
                .show();
    }

    private static String signature(List<WorkspaceStore.Artifact> items) {
        StringBuilder value = new StringBuilder();
        for (WorkspaceStore.Artifact item : items) {
            value.append(item.id).append(':').append(item.state.name()).append(':')
                    .append(item.updatedAt).append(':').append(item.content.hashCode()).append('|');
        }
        return value.toString();
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(13), dp(14), dp(13));
        view.setBackground(round(SURFACE, dp(15)));
        return view;
    }

    private Button button(String label, boolean quiet) {
        return button(label, false, quiet);
    }

    private Button button(String label, boolean selected, boolean quiet) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(selected ? ACCENT : (quiet ? MUTED : TEXT));
        button.setBackground(round(selected ? Color.rgb(44, 31, 70) : SURFACE_2, dp(12)));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isHtml(WorkspaceStore.Artifact artifact) {
        String language = artifact.language == null ? "" : artifact.language.toLowerCase(Locale.ROOT);
        return "html".equals(language) || "htm".equals(language) || "html5".equals(language);
    }

    private static String glyphFor(String language) {
        if (language == null) return "◇";
        switch (language.toLowerCase(Locale.ROOT)) {
            case "html": case "htm": case "html5": return "◉";
            case "java": case "kotlin": case "python": case "javascript": case "js":
            case "typescript": case "ts": case "cpp": case "c": case "rust": return "{ }";
            case "json": case "xml": case "yaml": case "yml": return "▤";
            case "md": case "markdown": case "text": return "≡";
            default: return "◇";
        }
    }

    private static String stateLabel(WorkspaceStore.State state) {
        if (state == WorkspaceStore.State.IN_PROGRESS) return "IN PROGRESS";
        if (state == WorkspaceStore.State.FAILED) return "INCOMPLETE";
        return "COMPLETED";
    }

    private static int stateColor(WorkspaceStore.State state) {
        return state == WorkspaceStore.State.COMPLETED ? ACCENT
                : (state == WorkspaceStore.State.FAILED ? Color.rgb(255, 130, 145)
                : Color.rgb(255, 200, 105));
    }

    private static String snippet(String content) {
        if (content == null) return "";
        String clean = content.trim().replaceAll("\\s+", " ");
        return clean.length() <= 180 ? clean : clean.substring(0, 177) + "…";
    }
}
