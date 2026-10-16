package com.sam.syncai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Lightweight local-only notes and task list. Timed reminders remain handled by ToolEngine. */
public final class ProductivityActivity extends Activity {
    private static final String PREFS = "sync_productivity";
    private static final String NOTES = "notes_json";
    private static final String TASKS = "tasks_json";
    private AppTheme palette;
    private LinearLayout notesList, tasksList;
    private EditText noteInput, taskInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = AppTheme.from(this);
        AppTheme.applySystemBars(this, palette);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundColor(palette.background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, palette.text, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(46)));
        TextView title = text("TASKS & NOTES", 19, palette.text, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        titleLp.leftMargin = dp(8);
        header.addView(title, titleLp);
        Button calendar = new Button(this);
        calendar.setAllCaps(false);
        calendar.setText("Calendar");
        calendar.setOnClickListener(v -> openCalendar());
        header.addView(calendar, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        body.addView(sectionTitle("TASKS"));
        taskInput = new EditText(this);
        taskInput.setSingleLine(true);
        taskInput.setHint("Add a task…");
        taskInput.setTextColor(palette.text);
        taskInput.setHintTextColor(palette.muted);
        taskInput.setBackground(round(palette.surface, dp(14)));
        taskInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        body.addView(taskInput, new LinearLayout.LayoutParams(-1, dp(50)));
        Button addTask = new Button(this);
        addTask.setAllCaps(false);
        addTask.setText("Add task");
        addTask.setOnClickListener(v -> addItem(true));
        body.addView(addTask, new LinearLayout.LayoutParams(-1, dp(44)));
        tasksList = new LinearLayout(this);
        tasksList.setOrientation(LinearLayout.VERTICAL);
        body.addView(tasksList);

        body.addView(sectionTitle("NOTES"));
        noteInput = new EditText(this);
        noteInput.setHint("Write a note…");
        noteInput.setTextColor(palette.text);
        noteInput.setHintTextColor(palette.muted);
        noteInput.setMinLines(2);
        noteInput.setMaxLines(5);
        noteInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        noteInput.setBackground(round(palette.surface, dp(14)));
        noteInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.addView(noteInput, new LinearLayout.LayoutParams(-1, dp(100)));
        Button addNote = new Button(this);
        addNote.setAllCaps(false);
        addNote.setText("Save note");
        addNote.setOnClickListener(v -> addItem(false));
        body.addView(addNote, new LinearLayout.LayoutParams(-1, dp(44)));
        notesList = new LinearLayout(this);
        notesList.setOrientation(LinearLayout.VERTICAL);
        body.addView(notesList);

        TextView reminderHint = text("Timed reminders still work through chat, for example: “Remind me in 30 minutes to take a break.”", 12, palette.muted, false);
        reminderHint.setPadding(dp(2), dp(18), dp(2), dp(12));
        body.addView(reminderHint);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        renderItems();
    }

    private void addItem(boolean task) {
        EditText input = task ? taskInput : noteInput;
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, task ? "Enter a task first." : "Write a note first.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray array = readArray(task ? TASKS : NOTES);
            JSONObject item = new JSONObject();
            item.put("id", UUID.randomUUID().toString());
            item.put("text", value);
            item.put("done", false);
            item.put("created", System.currentTimeMillis());
            array.put(item);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(task ? TASKS : NOTES, array.toString()).apply();
            input.setText("");
            renderItems();
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "ProductivityActivity", "SAVE_ITEM_FAILED", error, task ? "task" : "note");
            Toast.makeText(this, "Couldn't save this item.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderItems() {
        renderList(true, tasksList);
        renderList(false, notesList);
    }

    private void renderList(boolean task, LinearLayout target) {
        target.removeAllViews();
        JSONArray array = readArray(task ? TASKS : NOTES);
        if (array.length() == 0) {
            TextView empty = text(task ? "No tasks yet." : "No notes yet.", 12, palette.muted, false);
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            target.addView(empty);
            return;
        }
        for (int i = array.length() - 1; i >= 0; i--) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String value = item.optString("text", "");
            boolean done = item.optBoolean("done", false);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(6), dp(6), dp(6));
            row.setBackground(round(palette.surface, dp(14)));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(6);
            if (task) {
                CheckBox check = new CheckBox(this);
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(palette.primary));
                check.setChecked(done);
                check.setOnCheckedChangeListener((button, checked) -> setDone(id, checked));
                row.addView(check);
            }
            TextView text = text(value, 13, palette.text, !done);
            if (done) text.setAlpha(0.55f);
            text.setPadding(dp(5), dp(6), dp(5), dp(6));
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            Button delete = new Button(this);
            delete.setAllCaps(false);
            delete.setText("×");
            delete.setOnClickListener(v -> deleteItem(task, id));
            row.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(42)));
            target.addView(row, rowLp);
        }
    }

    private void setDone(String id, boolean done) {
        updateItem(TASKS, id, done, false);
    }
    private void deleteItem(boolean task, String id) {
        try {
            String key = task ? TASKS : NOTES;
            JSONArray old = readArray(key), next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id"))) next.put(item);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, next.toString()).apply();
            renderItems();
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "ProductivityActivity", "DELETE_ITEM_FAILED", error, id);
        }
    }
    private void updateItem(String key, String id, boolean done, boolean unused) {
        try {
            JSONArray old = readArray(key);
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && id.equals(item.optString("id"))) item.put("done", done);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, old.toString()).apply();
            renderItems();
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "ProductivityActivity", "UPDATE_ITEM_FAILED", error, id);
        }
    }
    private JSONArray readArray(String key) {
        try { return new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    private void openCalendar() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR);
            startActivity(intent);
        } catch (Exception error) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI);
                startActivity(view);
            } catch (Exception ignored) {
                Toast.makeText(this, "No calendar app is available.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private TextView sectionTitle(String value) {
        TextView t = text(value, 12, palette.secondary, true);
        t.setPadding(dp(2), dp(18), dp(2), dp(8));
        return t;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
