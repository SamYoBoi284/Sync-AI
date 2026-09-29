package com.sam.syncai;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CallContactTool implements SyncTool {
    private final Context context;

    public CallContactTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "call_contact"; }

    @Override public String getDescription() {
        return "Find a phone contact by name and place a direct call. Accepts nicknames and partial names; exact or single close matches are used.";
    }

    @Override public String getInputSchema() {
        return "{\"contact\":\"Mama\"}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("READ_CONTACTS_PERMISSION_REQUIRED");
        }
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("CALL_PHONE_PERMISSION_REQUIRED");
        }

        String requested = arguments == null ? null : arguments.get("contact");
        if (requested == null || requested.trim().isEmpty()) {
            throw new IllegalArgumentException("CONTACT_NAME_REQUIRED");
        }

        List<ContactMatch> matches = findMatches(requested.trim());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No contact with a phone number matched \"" + requested + "\".");
        }

        ContactMatch selected = selectBest(matches, requested);
        if (selected == null) {
            throw new IllegalArgumentException("Multiple contacts matched \"" + requested +
                    "\". Please say the full name.");
        }

        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + Uri.encode(selected.number)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            throw new IllegalStateException("No phone app can place calls on this device.");
        }

        context.startActivity(intent);
        return "Calling " + selected.name + ".";
    }

    private List<ContactMatch> findMatches(String requested) {
        List<ContactMatch> result = new ArrayList<>();
        Uri uri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(requested));
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        android.database.Cursor cursor = context.getContentResolver().query(
                uri, projection, null, null, null);
        if (cursor == null) return result;

        try {
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                String number = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                if (name == null || number == null || number.trim().isEmpty()) continue;
                result.add(new ContactMatch(name, number));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    private ContactMatch selectBest(List<ContactMatch> matches, String requested) {
        String target = normalize(requested);
        ContactMatch exact = null;
        List<ContactMatch> unique = new ArrayList<>();

        for (ContactMatch match : matches) {
            boolean duplicate = false;
            for (ContactMatch existing : unique) {
                if (normalize(existing.name).equals(normalize(match.name)) &&
                        existing.number.equals(match.number)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            unique.add(match);

            String normalized = normalize(match.name);
            if (normalized.equals(target)) exact = match;
        }

        if (exact != null) return exact;
        if (unique.size() == 1) return unique.get(0);

        ContactMatch contains = null;
        for (ContactMatch match : unique) {
            String normalized = normalize(match.name);
            if (normalized.contains(target) || target.contains(normalized)) {
                if (contains != null) return null;
                contains = match;
            }
        }
        return contains;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final class ContactMatch {
        final String name;
        final String number;
        ContactMatch(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }
}
