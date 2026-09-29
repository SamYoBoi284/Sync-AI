package com.sam.syncai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolEngine {
    public static final class Result {
        public final boolean handled;
        public final boolean success;
        public final String response;
        public final String toolName;
        public final long durationMs;
        public final String permission;

        Result(boolean handled, boolean success, String response, String toolName, long durationMs) {
            this(handled, success, response, toolName, durationMs, null);
        }

        Result(boolean handled, boolean success, String response, String toolName,
               long durationMs, String permission) {
            this.handled = handled;
            this.success = success;
            this.response = response;
            this.toolName = toolName;
            this.durationMs = durationMs;
            this.permission = permission;
        }

        static Result none() {
            return new Result(false, false, "", "", 0, null);
        }
    }

    private static final Pattern TIMER =
            Pattern.compile("(?i)(?:timer(?:\\s+(?:for|of))?|remind me in)\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)");
    private static final Pattern ALARM =
            Pattern.compile("(?i)(?:alarm|wake me|wake up)\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");
    private static final Pattern OPEN_APP =
            Pattern.compile("(?i)^(?:please\\s+)?(?:open|launch|start|run|go to)\\s+(.+)$");
    private static final Pattern CALL =
            Pattern.compile("(?i)^(?:please\\s+)?(?:call|phone|ring)\\s+(.+)$");

    private final Context context;
    private String lastCommand = "";
    private String lastTool = "";
    private String lastApp = "";
    private String lastContact = "";
    private boolean flashlightOn;

    public ToolEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized Result handle(String raw) {
        if (raw == null) return Result.none();
        String command = normalize(raw);
        if (command.isEmpty()) return Result.none();

        long started = System.currentTimeMillis();
        try {
            Result compound = handleCompound(command);
            if (compound.handled) {
                if (compound.success) remember(command, compound.toolName);
                return withDuration(compound, started);
            }

            Result result = handleSingle(command);
            if (result.handled && result.success) remember(command, result.toolName);
            return withDuration(result, started);
        } catch (Exception e) {
            if (e instanceof SecurityException) {
                String permission = e.getMessage() != null && e.getMessage().contains("READ_CONTACTS")
                        ? android.Manifest.permission.READ_CONTACTS
                        : android.Manifest.permission.CAMERA;
                return new Result(true, false,
                        "Android permission is required for that action. Allow it and try again.",
                        "permission", System.currentTimeMillis() - started, permission);
            }
            return new Result(true, false,
                    "I couldn't complete that tool action: " +
                            (e.getMessage() == null ? e.toString() : e.getMessage()),
                    "tool-error", System.currentTimeMillis() - started, null);
        }
    }

    private Result withDuration(Result result, long started) {
        return new Result(result.handled, result.success, result.response, result.toolName,
                Math.max(result.durationMs, System.currentTimeMillis() - started), result.permission);
    }

    private Result handleCompound(String command) {
        String[] parts = command.split("\\s+(?:and then|then|and)\\s+");
        if (parts.length < 2) return Result.none();

        List<Result> results = new ArrayList<>();
        boolean allHandled = true;
        for (String part : parts) {
            Result r = handleSingle(part.trim());
            if (!r.handled) {
                allHandled = false;
                break;
            }
            results.add(r);
        }
        if (!allHandled || results.size() < 2) return Result.none();

        StringBuilder response = new StringBuilder();
        StringBuilder tools = new StringBuilder();
        boolean ok = true;
        for (Result r : results) {
            if (!r.success) ok = false;
            if (response.length() > 0) response.append("\n");
            response.append(r.response);
            if (tools.length() > 0) tools.append(", ");
            tools.append(r.toolName);
        }
        return new Result(true, ok, response.toString(), tools.toString(), 0);
    }

    private Result handleSingle(String command) throws Exception {
        if (command.matches(".*\\b(?:what(?:'s| is)?|tell me|give me)\\b.*\\btime\\b.*")
                || command.matches(".*\\btime\\s+is\\s+it\\b.*")) {
            return result("system_time", currentTime());
        }

        if (command.contains("timezone") || command.contains("time zone")) {
            return result("system_time", "Your timezone is " + TimeZone.getDefault().getID() + ".");
        }

        if (command.contains("date") || command.matches(".*\\bwhat day is it\\b.*")
                || command.contains("day today") || command.contains("today")) {
            Date now = new Date();
            DateFormat format = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            return result("system_date", "Today is " + format.format(now) + ".");
        }

        if (command.contains("flashlight") || command.contains("torch")) {
            if (command.contains("turn on") || command.endsWith(" on") || command.contains("enable")) {
                setFlashlight(true);
                return result("flashlight", "Flashlight is on.");
            }
            if (command.contains("turn off") || command.endsWith(" off") || command.contains("disable")) {
                setFlashlight(false);
                return result("flashlight", "Flashlight is off.");
            }
            if (command.contains("toggle") || command.equals("flashlight") || command.equals("torch")) {
                setFlashlight(!flashlightOn);
                return result("flashlight", "Flashlight is " + (flashlightOn ? "on." : "off."));
            }
            return result("flashlight", "Flashlight is currently " + (flashlightOn ? "on." : "off.") + " (last Sync control state).");
        }

        if (command.contains("battery") || command.contains("charge")) {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int percent = bm == null ? -1 :
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            Intent status = context.registerReceiver(null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            boolean charging = false;
            if (status != null) {
                int plugged = status.getIntExtra("plugged", 0);
                charging = plugged != 0;
            }
            return result("battery", percent >= 0
                    ? "Battery is at " + percent + "% and is " + (charging ? "charging." : "not charging.")
                    : "I couldn't read the battery percentage.");
        }

        if (command.contains("wifi") || command.contains("wi-fi")) {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean connected = false;
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                connected = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }
            return result("wifi", "Wi-Fi is " + (connected ? "connected." : "not connected."));
        }

        if (command.contains("network") || command.contains("internet")) {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean connected = false;
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                connected = nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            return result("network", "Internet/network status: " + (connected ? "connected." : "offline."));
        }

        if (command.contains("bluetooth")) {
            android.bluetooth.BluetoothAdapter adapter =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return result("bluetooth", "Bluetooth is not available on this device.");
            return result("bluetooth", "Bluetooth is " +
                    (adapter.isEnabled() ? "on." : "off."));
        }

        if (command.contains("volume")) {
            android.media.AudioManager am = (android.media.AudioManager)
                    context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return result("volume", "I couldn't read the volume.");
            int current = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            return result("volume", "Media volume is " + current + " of " + max + ".");
        }

        if (command.contains("brightness")) {
            int brightness = Settings.System.getInt(
                    context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
            if (brightness < 0) return result("brightness", "I couldn't read screen brightness.");
            return result("brightness", "Screen brightness is about " +
                    Math.round(brightness * 100f / 255f) + "%.");
        }

        if (command.contains("ram") || command.contains("memory")) {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            if (am == null) return result("ram", "I couldn't read RAM information.");
            am.getMemoryInfo(info);
            long used = info.totalMem - info.availMem;
            return result("ram", "RAM: " + formatBytes(used) + " used of " +
                    formatBytes(info.totalMem) + ".");
        }

        if (command.contains("storage") || command.contains("disk space")) {
            StatFs stat = new StatFs(context.getFilesDir().getAbsolutePath());
            long free = stat.getAvailableBytes();
            long total = stat.getTotalBytes();
            return result("storage", "Storage: " + formatBytes(free) + " free of " +
                    formatBytes(total) + ".");
        }

        if (command.contains("device info") || command.contains("phone info")
                || command.contains("what phone") || command.contains("what device")) {
            return result("device_info", Build.MANUFACTURER + " " + Build.MODEL +
                    " • Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        }

        Matcher timer = TIMER.matcher(command);
        if (timer.find()) {
            double amount = Double.parseDouble(timer.group(1));
            String unit = timer.group(2);
            long seconds = unit.startsWith("hour") || unit.startsWith("hr")
                    ? Math.round(amount * 3600)
                    : unit.startsWith("min")
                    ? Math.round(amount * 60)
                    : Math.round(amount);
            if (seconds <= 0) throw new IllegalArgumentException("Timer duration must be positive.");
            scheduleNotification(seconds * 1000L, "Sync//AI timer",
                    "Timer finished: " + formatDuration(seconds) + ".");
            return result("timer", "Done bro, timer set for " + formatDuration(seconds) + ".");
        }

        Matcher alarm = ALARM.matcher(command);
        if (alarm.find()) {
            int hour = Integer.parseInt(alarm.group(1));
            int minute = alarm.group(2) == null ? 0 : Integer.parseInt(alarm.group(2));
            String ampm = alarm.group(3);
            if (ampm != null) {
                if ("pm".equals(ampm) && hour < 12) hour += 12;
                if ("am".equals(ampm) && hour == 12) hour = 0;
            }
            if (hour > 23 || minute > 59) throw new IllegalArgumentException("That is not a valid alarm time.");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            scheduleAt(cal.getTimeInMillis(), "Sync//AI alarm",
                    "Alarm for " + DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.getTime()) + ".");
            return result("alarm", "Alarm set for " +
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.getTime()) + ".");
        }

        if (looksLikeCalculator(command)) {
            String expression = extractExpression(command);
            double value = MathParser.evaluate(expression);
            return result("calculator", expression + " = " + MathParser.format(value));
        }

        if (command.equals("turn it off") || command.equals("switch it off")) {
            if ("flashlight".equals(lastTool)) {
                setFlashlight(false);
                return result("flashlight", "Flashlight is off.");
            }
        }

        if (command.equals("turn it on") || command.equals("switch it on")) {
            if ("flashlight".equals(lastTool)) {
                setFlashlight(true);
                return result("flashlight", "Flashlight is on.");
            }
        }

        if (command.equals("open it") && !TextUtils.isEmpty(lastApp)) {
            return launchApp(lastApp);
        }

        if ((command.equals("call them") || command.equals("call again")) &&
                !TextUtils.isEmpty(lastContact)) {
            return callContact(lastContact);
        }

        if (command.equals("do that again") || command.equals("repeat that") || command.equals("repeat")) {
            if (!TextUtils.isEmpty(lastCommand)) {
                return handleSingle(lastCommand);
            }
        }

        Matcher open = OPEN_APP.matcher(command);
        if (open.find()) return launchApp(open.group(1).trim());

        Matcher call = CALL.matcher(command);
        if (call.find()) return callContact(call.group(1).trim());

        return Result.none();
    }

    private Result launchApp(String query) {
        PackageManager pm = context.getPackageManager();
        String wanted = query.toLowerCase(Locale.US).trim();
        String packageName = null;
        CharSequence label = null;

        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            CharSequence appLabel = pm.getApplicationLabel(app);
            String name = appLabel == null ? "" : appLabel.toString();
            if (name.equalsIgnoreCase(wanted) || name.toLowerCase(Locale.US).contains(wanted)) {
                packageName = app.packageName;
                label = appLabel;
                break;
            }
        }

        if (packageName == null) {
            try {
                packageName = context.getPackageManager()
                        .getApplicationInfo(wanted, 0).packageName;
            } catch (Exception ignored) {
            }
        }
        if (packageName == null) {
            return new Result(true, false,
                    "I couldn't find an app matching \"" + query + "\".",
                    "app_launch", 0);
        }

        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return new Result(true, false,
                    "I found " + (label == null ? query : label) + ", but Android didn't expose a launch action.",
                    "app_launch", 0);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        lastApp = packageName;
        return result("app_launch", "Opening " + (label == null ? query : label) + ".");
    }

    private Result callContact(String name) {
        String wanted = name.trim();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + wanted + "%"},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );
            if (cursor == null || !cursor.moveToFirst()) {
                return new Result(true, false,
                        "I couldn't find a contact named \"" + wanted + "\".",
                        "contact_call", 0);
            }
            String number = cursor.getString(0);
            String display = cursor.getString(1);
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            lastContact = display;
            return result("contact_call", "Opening the dialer for " + display + ".");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void setFlashlight(boolean on) throws Exception {
        android.hardware.camera2.CameraManager cm =
                (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) throw new IllegalStateException("Camera flashlight service is unavailable.");

        String target = null;
        for (String id : cm.getCameraIdList()) {
            android.hardware.camera2.CameraCharacteristics c = cm.getCameraCharacteristics(id);
            Boolean flash = c.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(flash) &&
                    (facing == null || facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)) {
                target = id;
                break;
            }
        }
        if (target == null) throw new IllegalStateException("No controllable flashlight was found.");
        cm.setTorchMode(target, on);
        flashlightOn = on;
    }

    private void scheduleNotification(long delayMs, String title, String text) {
        scheduleAt(System.currentTimeMillis() + Math.max(1000, delayMs), title, text);
    }

    private void scheduleAt(long when, String title, String text) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) throw new IllegalStateException("Alarm service is unavailable.");

        String id = UUID.randomUUID().toString();
        Intent intent = new Intent(context, SyncNotificationReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("text", text);
        intent.putExtra("id", id.hashCode());
        PendingIntent pending = PendingIntent.getBroadcast(
                context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    private static String normalize(String input) {
        return input.toLowerCase(Locale.US)
                .replace('’', (char)39)
                .replaceAll("[!?;]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String currentTime() {
        DateFormat format = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return "It's " + format.format(new Date()) + " in " + TimeZone.getDefault().getID() + ".";
    }

    private static boolean looksLikeCalculator(String command) {
        if (command.matches(".*\\d\\s*[+\\-*/%]\\s*\\d.*")) return true;
        return command.startsWith("calculate ")
                || command.startsWith("what is ")
                || command.startsWith("what's ")
                || command.startsWith("how much is ")
                || command.startsWith("work out ");
    }

    private static String extractExpression(String command) {
        String value = command
                .replaceFirst("(?i)^calculate\\s+", "")
                .replaceFirst("(?i)^what(?:'s| is)\\s+", "")
                .replaceFirst("(?i)^how much is\\s+", "")
                .replaceFirst("(?i)^work out\\s+", "")
                .replaceAll("(?i)\\bplus\\b", "+")
                .replaceAll("(?i)\\bminus\\b", "-")
                .replaceAll("(?i)\\btimes\\b|\\bmultiplied by\\b", "*")
                .replaceAll("(?i)\\bdivided by\\b|\\bdivide by\\b", "/")
                .replaceAll("(?i)\\bmodulo?\\b|\\bpercent(?:age)?\\b", "%")
                .replace("=", "")
                .replace("?", "")
                .trim();
        return value;
    }

    public synchronized void clearContext() {
        lastCommand = "";
        lastTool = "";
        lastApp = "";
        lastContact = "";
    }

    private void remember(String command, String tool) {
        lastCommand = command;
        lastTool = tool;
    }

    private static Result result(String tool, String response) {
        return new Result(true, true, response, tool, 0);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " second" + (seconds == 1 ? "" : "s");
        if (seconds % 3600 == 0) return (seconds / 3600) + " hour" + (seconds == 3600 ? "" : "s");
        if (seconds % 60 == 0) return (seconds / 60) + " minute" + (seconds == 60 ? "" : "s");
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
