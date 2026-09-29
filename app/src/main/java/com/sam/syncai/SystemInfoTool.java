package com.sam.syncai;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.os.Process;
import android.os.StatFs;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.media.AudioManager;
import android.provider.Settings;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

import java.util.Locale;
import java.util.Map;

public final class SystemInfoTool implements SyncTool {
    private final Context context;

    public SystemInfoTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "system_info"; }

    @Override public String getDescription() {
        return "Read local phone status such as battery, charging, Wi-Fi, Bluetooth, volume, brightness, RAM, storage, device info, network state, or flashlight state.";
    }

    @Override public String getInputSchema() {
        return "{\"query\":\"battery|charging|wifi|bluetooth|volume|brightness|ram|storage|device|network|flashlight|current_app|alarms|timers|all\"}";
    }

    @Override public String execute(Map<String, String> arguments) {
        String query = arguments == null ? "all" : arguments.get("query");
        if (query == null || query.trim().isEmpty()) query = "all";
        query = query.toLowerCase(Locale.US).trim();

        if ("all".equals(query)) {
            return battery() + "\n" + network() + "\n" + wifi() + "\n" +
                    bluetooth() + "\n" + volume() + "\n" + brightness() + "\n" +
                    ram() + "\n" + storage() + "\n" + device() + "\n" + flashlight();
        }

        switch (query) {
            case "battery":
            case "charging":
                return battery();
            case "wifi":
                return wifi();
            case "bluetooth":
                return bluetooth();
            case "volume":
                return volume();
            case "brightness":
                return brightness();
            case "ram":
                return ram();
            case "storage":
                return storage();
            case "device":
                return device();
            case "network":
                return network();
            case "flashlight":
                return flashlight();
            case "current_app":
                return currentApp();
            case "alarms":
                return scheduled("alarm");
            case "timers":
                return scheduled("timer");
            default:
                return "Unknown system-info query: " + query;
        }
    }

    private String battery() {
        Intent intent = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return "Battery information unavailable.";
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = scale <= 0 ? -1 : Math.round(level * 100f / scale);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        return "Battery: " + (percent >= 0 ? percent + "%" : "unknown") +
                " • " + (charging ? "charging" : "not charging") + ".";
    }

    private String wifi() {
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            return manager == null ? "Wi-Fi information unavailable." :
                    "Wi-Fi: " + (manager.isWifiEnabled() ? "on" : "off") + ".";
        } catch (SecurityException e) {
            return "Wi-Fi state is restricted by Android permissions.";
        }
    }

    private String bluetooth() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return "Bluetooth: unsupported.";
            return "Bluetooth: " + (adapter.isEnabled() ? "on" : "off") + ".";
        } catch (Throwable e) {
            return "Bluetooth state unavailable on this Android build.";
        }
    }

    private String volume() {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return "Volume information unavailable.";
        int media = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return "Media volume: " + media + "/" + max + ".";
    }

    private String brightness() {
        try {
            int value = Settings.System.getInt(
                    context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return "Screen brightness: " + Math.round(value / 255f * 100f) + "%.";
        } catch (Exception e) {
            return "Screen brightness unavailable.";
        }
    }

    private String ram() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return "RAM information unavailable.";
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        double avail = info.availMem / 1024.0 / 1024.0 / 1024.0;
        double total;
        if (Build.VERSION.SDK_INT >= 16) total = info.totalMem / 1024.0 / 1024.0 / 1024.0;
        else total = 0;
        return String.format(Locale.US, "RAM: %.2f GiB available%s.",
                avail, total > 0 ? String.format(Locale.US, " of %.2f GiB", total) : "");
    }

    private String storage() {
        StatFs stats = new StatFs(context.getFilesDir().getAbsolutePath());
        long total = stats.getTotalBytes();
        long free = stats.getAvailableBytes();
        double totalGiB = total / 1024.0 / 1024.0 / 1024.0;
        double freeGiB = free / 1024.0 / 1024.0 / 1024.0;
        return String.format(Locale.US, "Storage: %.2f GiB free of %.2f GiB.",
                freeGiB, totalGiB);
    }

    private String device() {
        return "Device: " + Build.MANUFACTURER + " " + Build.MODEL +
                " • Android " + Build.VERSION.RELEASE +
                " • API " + Build.VERSION.SDK_INT + ".";
    }

    private String network() {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return "Network information unavailable.";
        Network network = manager.getActiveNetwork();
        if (network == null) return "Network: offline.";
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null) return "Network: connected, type unknown.";

        String type;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "Wi-Fi";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "mobile data";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "Ethernet";
        else type = "other";

        return "Network: connected via " + type +
                (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        ? " (validated)." : ".");
    }

    private String scheduled(String type) {
        return new ScheduledActionStore(context).describe(type);
    }

    private String currentApp() {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return "Current app: unavailable.";

            int mode = ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName());
            if (mode != AppOpsManager.MODE_ALLOWED) {
                return "Current app: unavailable. Android Usage Access permission is not enabled.";
            }

            UsageStatsManager usage = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usage == null) return "Current app: unavailable.";

            long now = System.currentTimeMillis();
            UsageEvents events = usage.queryEvents(now - 120_000L, now);
            UsageEvents.Event event = new UsageEvents.Event();
            String latestPackage = null;
            long latestTime = 0L;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                        event.getTimeStamp() > latestTime) {
                    latestTime = event.getTimeStamp();
                    latestPackage = event.getPackageName();
                }
            }

            if (latestPackage == null) return "Current app: unavailable.";
            android.content.pm.ApplicationInfo app =
                    context.getPackageManager().getApplicationInfo(latestPackage, 0);
            String label = String.valueOf(app.loadLabel(context.getPackageManager()));
            return "Current app: " + label + " (" + latestPackage + ").";
        } catch (Throwable e) {
            return "Current app: unavailable on this Android build.";
        }
    }

    private String flashlight() {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return "Flashlight state unavailable.";
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                if (!Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) continue;
                return "Flashlight: " + (manager.getTorchMode(id) == android.hardware.camera2.CameraManager.TORCH_MODE_ON
                        ? "on" : "off") + ".";
            }
            return "Flashlight: unavailable.";
        } catch (Throwable e) {
            return "Flashlight state unavailable.";
        }
    }
}
