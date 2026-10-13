package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OpenAppTool implements SyncTool {
    private static final String ZARCHIVER_PACKAGE = "ru.zdevs.zarchiver";
    private final Context context;
    private final PackageManager packageManager;
    private final Map<String, String> aliases = new HashMap<>();

    public OpenAppTool(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();

        aliases.put("fb", "facebook");
        aliases.put("fb app", "facebook");
        aliases.put("yt", "youtube");
        aliases.put("ig", "instagram");
        aliases.put("insta", "instagram");
        aliases.put("wa", "whatsapp");
        aliases.put("dc", "discord");
    }

    @Override
    public String getName() {
        return "open_app";
    }

    @Override
    public String getDescription() {
        return "Open an installed app, Android Settings screen, or file manager. " +
                "Accepts natural aliases such as FB/Facebook, YT/YouTube, and Discord. " +
                "Use this for Bluetooth/Wi-Fi settings and all file-related requests; file-related requests MUST use ZArchiver, not the system Files app. " +
                "If the exact app is missing, this tool finds the closest installed launchable app and returns a confirmation request instead of opening it automatically.";
    }

    @Override
    public String getInputSchema() {
        return "{\"app\":\"Discord\"}";
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String requested = arguments == null ? null : arguments.get("app");
        if (requested == null || requested.trim().isEmpty()) {
            throw new IllegalArgumentException("APP_NAME_REQUIRED");
        }

        String confirmedPackage = arguments.get("confirmed_package");
        if (confirmedPackage != null && !confirmedPackage.trim().isEmpty()) {
            String label = arguments.get("confirmed_label");
            return launchPackage(confirmedPackage, label == null ? confirmedPackage : label);
        }

        String cleaned = cleanRequest(requested);
        String settingsResult = tryOpenSettings(cleaned);
        if (settingsResult != null) return settingsResult;

        if (isFileRequest(cleaned)) {
            return openZArchiver();
        }

        List<AppInfo> apps = getLaunchableApps();
        AppInfo exact = findExact(apps, cleaned);
        if (exact != null) {
            return launchPackage(exact.packageName, exact.label);
        }

        AppInfo closest = findClosest(apps, cleaned);
        if (closest == null || closest.score < 0.38) {
            return "ERROR: No installed app closely matched \"" + requested + "\".";
        }

        return "OPEN_APP_CONFIRM|" + escapeField(requested) + "|" +
                escapeField(closest.label) + "|" + escapeField(closest.packageName);
    }

    private String tryOpenSettings(String request) {
        String normalized = normalize(request);

        String action = null;
        String label = null;

        if (containsAny(normalized, "bluetooth", "bt") &&
                containsAny(normalized, "setting", "settings", "options")) {
            action = Settings.ACTION_BLUETOOTH_SETTINGS;
            label = "Bluetooth settings";
        } else if (containsAny(normalized, "wifi", "wi fi", "wireless") &&
                containsAny(normalized, "setting", "settings", "options")) {
            action = Settings.ACTION_WIFI_SETTINGS;
            label = "Wi-Fi settings";
        } else if (normalized.equals("bluetooth")) {
            action = Settings.ACTION_BLUETOOTH_SETTINGS;
            label = "Bluetooth settings";
        } else if (normalized.equals("wifi") || normalized.equals("wi fi")) {
            action = Settings.ACTION_WIFI_SETTINGS;
            label = "Wi-Fi settings";
        } else if (normalized.equals("settings") || normalized.equals("android settings")) {
            action = Settings.ACTION_SETTINGS;
            label = "Android Settings";
        }

        if (action == null) return null;

        Intent intent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(packageManager) == null) {
            return "ERROR: " + label + " is not available on this device.";
        }
        context.startActivity(intent);
        return "Opened " + label + ".";
    }

    private boolean isFileRequest(String request) {
        String n = normalize(request);
        String[] fileTerms = {
                "download", "downloads", "file", "files", "file manager",
                "folder", "storage", "documents", "document", "archive",
                "zip", "rar", "7z", "apk", "obb", "internal storage",
                "sd card", "sdcard", "my files"
        };
        for (String term : fileTerms) {
            if (n.contains(normalize(term))) return true;
        }
        return false;
    }

    private String openZArchiver() {
        Intent intent = packageManager.getLaunchIntentForPackage(ZARCHIVER_PACKAGE);
        if (intent == null) {
            return "ERROR: ZArchiver is not installed. File-related requests are configured to use ZArchiver rather than the system Files app.";
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Opened ZArchiver for file management.";
    }

    private List<AppInfo> getLaunchableApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcher, 0);
        List<AppInfo> result = new ArrayList<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String packageName = info.activityInfo.packageName;
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String label = appInfo == null
                    ? packageName
                    : String.valueOf(appInfo.loadLabel(packageManager));
            if (label.trim().isEmpty()) label = packageName;
            result.add(new AppInfo(label, packageName));
        }

        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase(Locale.US)));
        return result;
    }

    private AppInfo findExact(List<AppInfo> apps, String requested) {
        String normalizedRequest = canonical(normalize(requested));
        for (AppInfo app : apps) {
            String label = canonical(normalize(app.label));
            String packageName = canonical(normalize(app.packageName));
            if (normalizedRequest.equals(label) || normalizedRequest.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    private AppInfo findClosest(List<AppInfo> apps, String requested) {
        String request = canonical(normalize(requested));
        AppInfo best = null;
        double bestScore = 0.0;

        for (AppInfo app : apps) {
            String label = canonical(normalize(app.label));
            String packageName = canonical(normalize(app.packageName));

            double labelScore = similarity(request, label);
            double packageScore = similarity(request, packageName);
            double score = Math.max(labelScore, packageScore);

            if (request.contains(label) && label.length() >= 4) score = Math.max(score, 0.72);
            if (label.contains(request) && request.length() >= 4) score = Math.max(score, 0.70);

            if (score > bestScore) {
                bestScore = score;
                best = new AppInfo(app.label, app.packageName, score);
            }
        }
        return best;
    }

    private double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        Set<String> aTokens = tokens(a);
        Set<String> bTokens = tokens(b);
        int overlap = 0;
        for (String token : aTokens) if (bTokens.contains(token)) overlap++;

        double tokenScore = aTokens.isEmpty() ? 0.0 :
                (double) overlap / Math.max(aTokens.size(), bTokens.size());

        int distance = levenshtein(a, b);
        double editScore = 1.0 - ((double) distance / Math.max(a.length(), b.length()));

        return Math.max(tokenScore, editScore);
    }

    private Set<String> tokens(String value) {
        if (value.isEmpty()) return Collections.emptySet();
        return new HashSet<>(Arrays.asList(value.split(" ")));
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[b.length()];
    }

    private String cleanRequest(String value) {
        String cleaned = normalize(value);
        String[] prefixes = {
                "open ", "launch ", "start ", "run ", "go to ",
                "open the ", "launch the ", "start the "
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : prefixes) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }
        if (cleaned.startsWith("the ")) cleaned = cleaned.substring(4).trim();
        if (cleaned.endsWith(" app")) cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
        if (cleaned.startsWith("the ")) cleaned = cleaned.substring(4).trim();
        return cleaned;
    }

    private String canonical(String value) {
        String normalized = value;
        String alias = aliases.get(normalized);
        return alias == null ? normalized : alias;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.US)
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private String launchPackage(String packageName, String label) {
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return "ERROR: " + label + " is installed but has no launchable activity.";
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Opened " + label + ".";
    }

    private String escapeField(String value) {
        return value.replace("|", " ").replace("\\n", " ").trim();
    }

    private static final class AppInfo {
        final String label;
        final String packageName;
        final double score;

        AppInfo(String label, String packageName) {
            this(label, packageName, 1.0);
        }

        AppInfo(String label, String packageName, double score) {
            this.label = label;
            this.packageName = packageName;
            this.score = score;
        }
    }
}
