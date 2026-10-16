package com.sam.syncai;

import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.hardware.camera2.CameraManager;
import android.provider.AlarmClock;
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
            Pattern.compile("(?i)\\b(?:(?:set\\s+)?(?:a\\s+)?timer(?:\\s+(?:for|of))?|(?:start|begin)\\s+(?:a\\s+)?timer(?:\\s+for)?|countdown(?:\\s+for)?)\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)(?:\\s+(?:and|,)\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?))?\\b");
    private static final Pattern TIMER_CLOCK_TIME =
            Pattern.compile("(?i)\\b(?:(?:set\\s+)?(?:a\\s+)?timer(?:\\s+(?:for|to|until))?|countdown(?:\\s+(?:for|to|until))?)\\s+(\\d{1,2})(?::?(\\d{2}))\\s*(a\\.?m\\.?|p\\.?m\\.?)?(?![a-z])");
    private static final Pattern ALARM_LABEL =
            Pattern.compile("(?i)\\b(?:named|called|label(?:ed|led)?(?:\\s+as)?|with\\s+(?:the\\s+)?label(?:\\s+as)?)\\s+(.+?)\\s*$");
    private static final Pattern REMINDER_IN =
            Pattern.compile("(?i)\\bremind\\s+me\\s+in\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)(?:\\s+to\\s+(.+))?");
    private static final Pattern REMINDER_AT =
            Pattern.compile("(?i)\\bremind\\s+me\\s+(tomorrow\\s+)?at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?:\\s+to\\s+(.+))?");
    private static final Pattern VOLUME_PERCENT =
            Pattern.compile("(?i)\\b(?:set|change|turn)\\s+(?:the\\s+)?volume\\s+(?:to\\s+)?(\\d{1,3})\\s*%?\\b");
    private static final Pattern BRIGHTNESS_PERCENT =
            Pattern.compile("(?i)\\b(?:set|change|turn)\\s+(?:the\\s+)?(?:screen\\s+)?brightness\\s+(?:to\\s+)?(\\d{1,3})\\s*%?\\b");
    private static final Pattern ALARM =
            Pattern.compile("(?i)(?:set\\s+(?:an\\s+)?)?(?:alarm|wake me|wake up)(?:\\s+(?:for|at))?\\s+(\\d{1,4})(?::(\\d{2}))?(?!\\d)\\s*(am|pm)?");
    private static final Pattern OPEN_APP =
            Pattern.compile("(?i)^(?:(?:hey|bro|also|okay|alright|please)\\s+)*(?:(?:can|could|would|will)\\s+(?:you|u)\\s+)?(?:please\\s+)?(?:open(?:\\s+up)?|launch|start|run|show(?:\\s+me)?|bring\\s+up|pull\\s+up|switch\\s+to|take\\s+me\\s+to|go\\s+to|go\\s+into)\\s+(.+?)\\s*[.!?,;]*$");
    private static final Pattern CALL =
            Pattern.compile("(?i)^(?:please\\s+)?(?:call|phone|ring)\\s+(.+)$");

    private final Context context;
    private String lastCommand = "";
    private String lastTool = "";
    private String lastApp = "";
    private String lastContact = "";
    private String originalCommand = "";
    private volatile boolean flashlightOn;
    private String flashlightCameraId;

    public ToolEngine(Context context) {
        this.context = context.getApplicationContext();
        observeFlashlightState();
    }

    private void observeFlashlightState() {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return;
            for (String id : manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Boolean flash = c.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && (facing == null
                        || facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)) {
                    flashlightCameraId = id;
                    break;
                }
            }
            if (flashlightCameraId == null) return;
            manager.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String cameraId, boolean enabled) {
                    if (cameraId.equals(flashlightCameraId)) flashlightOn = enabled;
                }
                @Override public void onTorchModeUnavailable(String cameraId) {
                    if (cameraId.equals(flashlightCameraId)) flashlightOn = false;
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception error) {
            SyncEventLogger.record(context, "ToolEngine", "FLASHLIGHT_STATE_OBSERVER_UNAVAILABLE",
                    "WARN", error.getClass().getSimpleName());
        }
    }

    public synchronized Result handle(String raw) {
        if (raw == null) return Result.none();
        originalCommand = raw.trim();
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
            if (!result.handled) {
                String embedded = extractEmbeddedCommand(command);
                if (!embedded.isEmpty()) result = handleSingle(embedded);
            }
            if (result.handled && result.success) remember(command, result.toolName);
            return withDuration(result, started);
        } catch (Exception e) {
            if (e instanceof SecurityException) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                String permission = message.contains("READ_CONTACTS")
                        ? android.Manifest.permission.READ_CONTACTS
                        : message.contains("CALL_PHONE")
                        ? android.Manifest.permission.CALL_PHONE
                        : message.contains("BLUETOOTH_CONNECT")
                        ? android.Manifest.permission.BLUETOOTH_CONNECT
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

    private Result handleCompound(String command) throws Exception {
        String[] parts = command.split("\\s+(?:and then|then|and)\\s+");
        if (parts.length < 2) return Result.none();

        List<Result> results = new ArrayList<>();
        for (String part : parts) {
            String candidate = stripConversationalLead(part.trim());
            if (candidate.isEmpty()) continue;
            Result r = handleSingle(candidate);
            if (r.handled) results.add(r);
        }
        if (results.isEmpty()) return Result.none();
        if (results.size() == 1) return results.get(0);

        StringBuilder response = new StringBuilder();
        StringBuilder tools = new StringBuilder();
        String permission = null;
        boolean ok = true;
        for (Result r : results) {
            if (!r.success) ok = false;
            if (permission == null && r.permission != null) permission = r.permission;
            if (response.length() > 0) response.append("\n");
            response.append(r.response);
            if (tools.length() > 0) tools.append(", ");
            tools.append(r.toolName);
        }
        return new Result(true, ok, response.toString(), tools.toString(), 0, permission);
    }

    private String stripConversationalLead(String command) {
        String candidate = command == null ? "" : command.trim();
        candidate = candidate.replaceFirst(
                "(?i)^(?:(?:hey|bro|also|okay|alright|well|so|by the way|btw|please)\\b[\\s,:-]*)+",
                "");
        candidate = candidate.replaceFirst("(?i)^(?:can|could|would|will)\\s+(?:you|u)\\s+", "");
        candidate = candidate.replaceFirst("(?i)^please\\s+", "");
        return candidate.trim();
    }

    private String extractEmbeddedCommand(String command) {
        if (command == null) return "";
        Matcher request = Pattern.compile(
                "(?i)\\b(?:(?:can|could|would|will)\\s+(?:you|u)|i need you to|please)\\s+(?:please\\s+)?(?=(?:open|launch|start|run|go to|call|phone|ring)\\b)(.+)$")
                .matcher(command);
        if (!request.find()) return "";
        return request.group(1).trim();
    }

    private Result handleSingle(String command) throws Exception {
        Result wakeWordCommand = handleWakeWordCommand(command);
        if (wakeWordCommand.handled) return wakeWordCommand;

        if (command.contains("current time")
                || command.matches(".*\\bwhat(?:'s| is)?(?: the)? time(?: now| right now)?\\b.*")
                || command.matches(".*\\btell me(?: the)? time\\b.*")
                || command.matches(".*\\btime (?:is it|now|right now)\\b.*")
                || command.contains("do you know the time")
                || command.matches(".*\\bwhat time (?:we got|do we have|have we got)\\b.*")
                || command.matches(".*\\btime check\\b.*")) {
            return result("system_time", currentTime());
        }

        if (command.contains("timezone") || command.contains("time zone")) {
            return result("system_time", "Your timezone is " + TimeZone.getDefault().getID() + ".");
        }

        if (command.matches(".*\\b(?:current date|today'?s date|date today|what date is it|what is the date|what's the date)\\b.*")
                || command.matches(".*\\bwhat day is it\\b.*")
                || command.matches(".*\\bwhat day are we on\\b.*")) {
            Date now = new Date();
            DateFormat format = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            return result("system_date", "Today is " + format.format(now) + ".");
        }

        Result mediaProvider = handlePreferredMusicApp(command);
        if (mediaProvider.handled) return mediaProvider;

        Result musicCapability = handleMusicCapabilityCommand(command);
        if (musicCapability.handled) return musicCapability;

        if (isMediaTransportCommand(command)) {
            return dispatchMediaTransport(command);
        }

        // Camera routing is deterministic: inventory/status questions never launch an app,
        // while capture requests open Android's camera in the requested mode.
        if (asksAboutCameraInventory(command)) {
            return describeCameras();
        }

        if (asksToCaptureVideo(command)) {
            return launchSyncCamera("video", requestedCameraFacing(command));
        }

        if (asksToCapturePhoto(command)) {
            return launchSyncCamera("photo", requestedCameraFacing(command));
        }

        if (asksToOpenCamera(command)) {
            return launchSyncCamera("photo", requestedCameraFacing(command));
        }

        // Built-in Sync Recorder commands are deterministic and shared by chat and Voice Mode.
        if (asksToStopSyncRecording(command)) {
            if (!SyncRecorderService.isRecording()) {
                return result("sync_recorder", "Sync Recorder isn't recording right now.");
            }
            context.startService(new Intent(context, SyncRecorderService.class)
                    .setAction(SyncRecorderService.ACTION_STOP));
            return result("sync_recorder", "Stopping Sync Recorder and saving the recording.");
        }
        if (asksToStartSyncRecording(command)) {
            if (SyncRecorderService.isRecording()) {
                return result("sync_recorder", "Sync Recorder is already recording. Say “stop recording” when you're done.");
            }
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                return new Result(true, false,
                        "Sync needs microphone permission before I can start recording. Grant microphone access, then ask me to start again.",
                        "sync_recorder", 0, android.Manifest.permission.RECORD_AUDIO);
            }
            Intent start = new Intent(context, SyncRecorderService.class)
                    .setAction(SyncRecorderService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(start);
            else context.startService(start);
            return result("sync_recorder", "Starting Sync Recorder. The recording notification will appear only while recording is active.");
        }

        if (asksToRecordAudio(command)) {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "sync_media_preferences", Context.MODE_PRIVATE);
            String preferredPackage = prefs.getString("preferred_recorder_package", null);
            if (preferredPackage != null && !preferredPackage.trim().isEmpty()) {
                Intent preferred = context.getPackageManager().getLaunchIntentForPackage(preferredPackage);
                if (preferred != null) {
                    preferred.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(preferred);
                        return result("audio_recorder", "Opened your preferred audio recorder. Start and stop recording in that app.");
                    } catch (Exception ignored) {
                        // Fall through to Android's recording handler; never claim recording started.
                    }
                } else {
                    prefs.edit().remove("preferred_recorder_package")
                            .remove("preferred_recorder_label").apply();
                }
            }
            Intent record = new Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION);
            record.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(record);
                return result("audio_recorder", "Opened the available system audio recorder. Start and stop recording in that app.");
            } catch (ActivityNotFoundException error) {
                return new Result(true, false,
                        "I couldn't find a system audio recorder on this phone. Choose an installed recorder in Sync AI Settings > Default apps, or open one by name.",
                        "audio_recorder", 0);
            }
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
            if (asksForDeviceToggle(command)) return openConnectivityPanel("Wi-Fi", command);
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean connected=false;
            if(cm!=null&&Build.VERSION.SDK_INT>=23){
                NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());
                connected=nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }
            return result("wifi","Wi-Fi is "+(connected?"connected.":"not connected."));
        }

        if (command.contains("network") || command.contains("internet") || command.contains("mobile data")) {
            if (asksForDeviceToggle(command)) return openConnectivityPanel("network connectivity", command);
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean connected=false;
            if(cm!=null&&Build.VERSION.SDK_INT>=23){
                NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());
                connected=nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            return result("network","Internet/network status: "+(connected?"connected.":"offline."));
        }

        if (command.contains("bluetooth")) {
            android.bluetooth.BluetoothAdapter adapter=android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if(adapter==null)return result("bluetooth","Bluetooth is not available on this device.");
            if(Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    !=android.content.pm.PackageManager.PERMISSION_GRANTED)
                return new Result(true,false,"Bluetooth status and control need Android's Nearby devices permission. Enable it in Sync AI's app permissions, then try again.","bluetooth",0,android.Manifest.permission.BLUETOOTH_CONNECT);
            if(asksForDeviceToggle(command)){
                boolean wantsOn=command.contains("enable")||command.contains("activate")
                        ||command.contains("turn on")||command.contains("switch on")||command.matches(".*\\bon\\b.*");
                try{
                    boolean currentlyOn = adapter.isEnabled();
                    if (wantsOn && currentlyOn) return result("bluetooth","Bluetooth is already on.");
                    if (!wantsOn && !currentlyOn) return result("bluetooth","Bluetooth is already off.");
                    if(wantsOn&&!currentlyOn){
                        Intent request=new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(request);
                        return new Result(true,false,"Android's Bluetooth confirmation is open. Approve it to enable Bluetooth.","bluetooth",0);
                    }
                    Intent settings=new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(settings);
                    return new Result(true,false,"Android requires system controls to change Bluetooth. I opened Bluetooth settings so you can finish the change.","bluetooth",0);
                }catch(Exception error){
                    return new Result(true,false,"I couldn't open Android's Bluetooth controls: "+error.getClass().getSimpleName(),"bluetooth",0);
                }
            }
            return result("bluetooth","Bluetooth is "+(adapter.isEnabled()?"on.":"off."));
        }

        if (command.contains("volume")) {
            AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(am==null)return new Result(true,false,"Android's audio service is unavailable.","volume",0);
            int current=am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            Matcher target=VOLUME_PERCENT.matcher(command);
            if(target.find()){
                int percent=Integer.parseInt(target.group(1));
                if(percent>100)return new Result(true,false,"Volume must be between 0% and 100%.","volume",0);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(max*percent/100f),0);
                return result("volume","Media volume set to "+percent+"%.");
            }
            if(command.contains("unmute")){
                am.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,current),0);
                return result("volume","Media audio unmuted.");
            }
            if(command.contains("mute")){
                am.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);
                return result("volume","Media audio muted.");
            }
            if(command.matches(".*\\b(?:volume up|turn it up|turn (?:the )?volume up|increase (?:the )?volume|raise (?:the )?volume|louder)\\b.*")){
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,0);
                return result("volume","Media volume increased.");
            }
            if(command.matches(".*\\b(?:volume down|turn it down|turn (?:the )?volume down|decrease (?:the )?volume|lower (?:the )?volume|quieter)\\b.*")){
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,0);
                return result("volume","Media volume decreased.");
            }
            return result("volume","Media volume is "+am.getStreamVolume(AudioManager.STREAM_MUSIC)+" of "+max+".");
        }

        if (command.contains("brightness")) {
            int brightness=Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,-1);
            Matcher target=BRIGHTNESS_PERCENT.matcher(command);
            boolean explicit=target.find();
            boolean up=command.matches(".*\\b(?:increase|raise|brighten|brightness up)\\b.*");
            boolean down=command.matches(".*\\b(?:decrease|lower|dim|brightness down)\\b.*");
            if(explicit||up||down){
                int percent;
                if(explicit){
                    percent=Integer.parseInt(target.group(1));
                    if(percent>100)return new Result(true,false,"Brightness must be between 0% and 100%.","brightness",0);
                }else{
                    if(brightness<0)return result("brightness","I couldn't read screen brightness.");
                    percent=Math.max(0,Math.min(100,Math.round(brightness*100f/255f)+(up?15:-15)));
                }
                if(!Settings.System.canWrite(context)){
                    try{
                        Intent permission=new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+context.getPackageName()));
                        permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(permission);
                        return new Result(true,false,"Android needs Modify system settings permission before Sync AI can change brightness. I opened that settings page; grant it and repeat the command.","brightness",0);
                    }catch(Exception error){
                        return new Result(true,false,"Brightness control needs Modify system settings permission in Android Settings.","brightness",0);
                    }
                }
                boolean saved=Settings.System.putInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.round(percent*255f/100f));
                if(!saved)return new Result(true,false,"Android rejected the brightness change.","brightness",0);
                Settings.System.putInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                return result("brightness","Screen brightness set to about "+percent+"%.");
            }
            if(brightness<0)return result("brightness","I couldn't read screen brightness.");
            return result("brightness","Screen brightness is about "+Math.round(brightness*100f/255f)+"%.");
        }

        if (command.matches(".*\\b(?:ram|ram usage|ram usage right now|memory usage|how much ram|how much memory is being used)\\b.*")) {
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

        if (asksToOpenProductivity(command)) {
            try {
                Intent productivity = new Intent(context, ProductivityActivity.class);
                productivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(productivity);
                return result("tasks_notes", "Opened Sync AI Tasks & Notes.");
            } catch (Exception error) {
                return new Result(true, false, "I couldn't open Tasks & Notes: "
                        + error.getClass().getSimpleName(), "tasks_notes", 0);
            }
        }

        String taskText = extractItemText(command, true);
        if (taskText != null) {
            boolean saved = saveProductivityItem(taskText, true);
            return new Result(true, saved, saved ? "Added to Tasks & Notes: " + taskText
                    : "I couldn't save that task.", "task", 0);
        }

        String noteText = extractItemText(command, false);
        if (noteText != null) {
            boolean saved = saveProductivityItem(noteText, false);
            return new Result(true, saved, saved ? "Saved your note in Tasks & Notes: " + noteText
                    : "I couldn't save that note.", "note", 0);
        }

        Matcher reminderIn = REMINDER_IN.matcher(command);
        if (reminderIn.find()) {
            double amount = Double.parseDouble(reminderIn.group(1));
            long seconds = durationSeconds(amount, reminderIn.group(2).toLowerCase(Locale.US));
            if (seconds <= 0) throw new IllegalArgumentException("Reminder delay must be positive.");
            String reminderText = reminderIn.group(3) == null ? "Your reminder is due." : reminderIn.group(3).trim();
            ReminderScheduler.schedule(context, seconds * 1000L, "Sync AI reminder", "Reminder: " + reminderText);
            boolean savedAsTask = saveReminderAsTask(reminderText);
            return result("reminder", "Reminder scheduled in " + formatDuration(seconds) + ": " + reminderText
                    + (savedAsTask ? ". I also added it to Tasks & Notes." : ".")
                    + " I'll notify you even if Sync AI is closed.");
        }

        Matcher reminderAt = REMINDER_AT.matcher(command);
        if (reminderAt.find()) {
            boolean tomorrow = reminderAt.group(1) != null;
            int hour = Integer.parseInt(reminderAt.group(2));
            int minute = reminderAt.group(3) == null ? 0 : Integer.parseInt(reminderAt.group(3));
            String ampm = reminderAt.group(4);
            String reminderText = reminderAt.group(5) == null ? "Your reminder is due." : reminderAt.group(5).trim();
            if (ampm != null) {
                if (hour < 1 || hour > 12) throw new IllegalArgumentException("That is not a valid reminder time.");
                if ("pm".equals(ampm) && hour < 12) hour += 12;
                if ("am".equals(ampm) && hour == 12) hour = 0;
            }
            if (hour > 23 || minute > 59) throw new IllegalArgumentException("That is not a valid reminder time.");
            Calendar cal = Calendar.getInstance();
            if (tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY,hour);cal.set(Calendar.MINUTE,minute);
            cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            if(!tomorrow&&cal.getTimeInMillis()<=System.currentTimeMillis())cal.add(Calendar.DAY_OF_YEAR,1);
            long delay=Math.max(1000L,cal.getTimeInMillis()-System.currentTimeMillis());
            ReminderScheduler.schedule(context,delay,"Sync AI reminder","Reminder: "+reminderText);
            boolean savedAsTask = saveReminderAsTask(reminderText);
            return result("reminder","Reminder scheduled for "
                    +DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(cal.getTime())
                    +": "+reminderText+(savedAsTask ? ". I also added it to Tasks & Notes." : "."));
        }

        Matcher timerClockTime = TIMER_CLOCK_TIME.matcher(command);
        if (timerClockTime.find()) {
            return new Result(true, false,
                    "That looks like a clock time, not a timer duration. Say “set an alarm for "
                            + timerClockTime.group(1) + ":" + timerClockTime.group(2)
                            + (timerClockTime.group(3) == null ? "" : " " + timerClockTime.group(3).replace(".", "").toUpperCase(Locale.US))
                            + "” for a specific time, or give me a duration like “10 minutes”.",
                    "timer-clarification", 0);
        }

        Matcher timer = TIMER.matcher(command);
        if (timer.find()) {
            double amount = Double.parseDouble(timer.group(1));
            long seconds = durationSeconds(amount, timer.group(2).toLowerCase(Locale.US));
            if (timer.group(3) != null) {
                seconds += durationSeconds(Double.parseDouble(timer.group(3)),
                        timer.group(4).toLowerCase(Locale.US));
            }
            if (seconds <= 0) throw new IllegalArgumentException("Timer duration must be positive.");
            if (seconds > 86400L) return new Result(true,false,
                    "Android's Clock timer supports up to 24 hours. Use a reminder for a longer delay.","timer",0);
            Intent timerIntent=new Intent(AlarmClock.ACTION_SET_TIMER);
            timerIntent.putExtra(AlarmClock.EXTRA_LENGTH,(int)seconds);
            timerIntent.putExtra(AlarmClock.EXTRA_MESSAGE,"Sync AI timer");
            timerIntent.putExtra(AlarmClock.EXTRA_SKIP_UI,true);
            timerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(timerIntent);
                return result("timer","Sent a timer request to your Clock app for "+formatDuration(seconds)
                        +". Check Clock if your installed app asks for confirmation.");
            } catch(ActivityNotFoundException error) {
                return new Result(true,false,"No installed Clock app accepted Android's timer action. I did not substitute a Sync AI notification.","timer",0);
            }
        }

        String labelSource = normalize(originalCommand).equals(command)
                ? originalCommand : command;
        Matcher labelMatcher = ALARM_LABEL.matcher(labelSource);
        String alarmLabel = labelMatcher.find() ? labelMatcher.group(1).trim() : "Sync AI alarm";
        alarmLabel = alarmLabel.replaceAll("[.!?]+$", "").trim();
        String alarmCommand = ALARM_LABEL.matcher(command).replaceAll("").trim();
        Matcher alarm=ALARM.matcher(alarmCommand);
        if(alarm.find()) {
            String hourDigits = alarm.group(1);
            String minuteDigits = alarm.group(2);
            int hour;
            int minute;
            if (minuteDigits != null) {
                hour = Integer.parseInt(hourDigits);
                minute = Integer.parseInt(minuteDigits);
            } else if (hourDigits.length() == 3 || hourDigits.length() == 4) {
                // Voice often reads 6:30 as "630" and 12:30 as "1230".
                // Split the final two digits as minutes instead of treating 630 as hour 63.
                hour = Integer.parseInt(hourDigits.substring(0, hourDigits.length() - 2));
                minute = Integer.parseInt(hourDigits.substring(hourDigits.length() - 2));
            } else {
                hour = Integer.parseInt(hourDigits);
                minute = 0;
            }
            String ampm=alarm.group(3);
            if(ampm!=null) {
                if(hour<1||hour>12)throw new IllegalArgumentException("That is not a valid alarm time.");
                if("pm".equals(ampm)&&hour<12)hour+=12;
                if("am".equals(ampm)&&hour==12)hour=0;
            }
            if(hour>23||minute>59)throw new IllegalArgumentException("That is not a valid alarm time.");
            Calendar cal=Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY,hour);cal.set(Calendar.MINUTE,minute);
            cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            if(cal.getTimeInMillis()<=System.currentTimeMillis())cal.add(Calendar.DAY_OF_YEAR,1);
            Intent alarmIntent=new Intent(AlarmClock.ACTION_SET_ALARM);
            alarmIntent.putExtra(AlarmClock.EXTRA_HOUR,hour);
            alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES,minute);
            alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE,alarmLabel);
            alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI,true);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String timeText=DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.getTime());
            try {
                context.startActivity(alarmIntent);
                return result("alarm","Sent a Clock app request to set an alarm for "+timeText
                        +" labeled “"+alarmLabel+"”. Check Clock to confirm it appears if your installed app asks for confirmation.");
            } catch(ActivityNotFoundException error) {
                return new Result(true,false,"No installed Clock app accepted Android's alarm action. I did not create a Sync AI notification instead.","alarm",0);
            }
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
        if (open.find()) return launchApp(cleanAppTarget(open.group(1)));

        Matcher call = CALL.matcher(command);
        if (call.find()) return callContact(call.group(1).trim().replaceAll("[.!?,;]+$", ""));

        return Result.none();
    }

    private Result handleWakeWordCommand(String command) throws Exception {
        String c = command.toLowerCase(Locale.US);
        boolean mentionsWakeWord = c.contains("wake word") || c.contains("wake-word")
                || c.contains("hey sync") || c.contains("hey nullverox")
                || c.contains("hey null verox");
        if (!mentionsWakeWord) return Result.none();

        AppPreferences preferences = SyncRuntime.get(context).preferences();
        boolean enabled = SyncAssistantService.isRunning();
        boolean asksStatus = c.contains("status") || c.contains("is it on")
                || c.contains("is it enabled") || c.contains("is wake word on")
                || c.contains("is hey sync on");
        if (asksStatus) {
            return result("wake_word", "Wake-word detection is "
                    + (enabled ? "on and listening locally." : "off."));
        }

        boolean asksToggle = c.contains("toggle") || c.contains("switch");
        boolean asksEnable = c.contains("enable") || c.contains("turn on")
                || c.contains("start listening") || c.contains("activate");
        boolean asksDisable = c.contains("disable") || c.contains("turn off")
                || c.contains("stop listening") || c.contains("deactivate");
        if (!asksToggle && !asksEnable && !asksDisable) return Result.none();
        boolean targetEnabled = asksToggle ? !enabled : asksEnable && !asksDisable;

        if (targetEnabled && enabled) {
            return result("wake_word", "Wake-word detection is already on.");
        }
        if (!targetEnabled && !enabled) {
            preferences.setWakeWordEnabled(false);
            return result("wake_word", "Wake-word detection is already off.");
        }
        if (!targetEnabled) {
            preferences.setWakeWordEnabled(false);
            SyncAssistantService.requestStop(context);
            return result("wake_word", "Wake-word detection is off. The power button still opens voice mode.");
        }

        if (!VoskModelInstaller.isInstalled(context)) {
            return new Result(true, false,
                    "I can't enable wake-word detection yet, bro—the offline Vosk model isn't imported. Open Sync AI → Voice & Wake Word and import the Vosk ZIP first.",
                    "wake_word", 0);
        }
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return new Result(true, false,
                    "Microphone permission is needed for wake-word detection. Enable it in Android Settings, then tell me to enable Hey Sync again.",
                    "wake_word", 0, android.Manifest.permission.RECORD_AUDIO);
        }

        preferences.setWakeWordEnabled(true);
        Intent service = new Intent(context, SyncAssistantService.class);
        if (SyncAssistantService.isVoiceModeUiActive()) {
            service.putExtra(SyncAssistantService.EXTRA_DEFER_UNTIL_VOICE_FINISHED, true);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
            else context.startService(service);
            return result("wake_word", SyncAssistantService.isVoiceModeUiActive()
                    ? "Got you, bro. Wake-word detection will start after this voice session closes."
                    : "Wake-word detection enabled. Say “Hey Sync” or “Hey Nullverox” to activate me.");
        } catch (Exception error) {
            preferences.setWakeWordEnabled(false);
            return new Result(true, false,
                    "I couldn't start wake-word detection: "
                            + (error.getMessage() == null ? error.toString() : error.getMessage()),
                    "wake_word", 0);
        }
    }

    /** Removes conversational wrappers while preserving the requested app name. */
    private static String cleanAppTarget(String raw) {
        String target = raw == null ? "" : raw.trim();
        target = target.replaceAll("[.!?,;]+$", "").trim();
        target = target.replaceFirst("(?i)^(?:my|the|default|installed)\\s+", "");
        target = target.replaceFirst("(?i)\\s+(?:app|application)$", "");
        return target.trim();
    }
    private static String normalizeAppName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]", "");
    }

    private static int appMatchScore(String wanted, String candidate) {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0;
        if (wanted.equals(candidate)) return 1000;
        if (candidate.contains(wanted) && wanted.length() >= 4) {
            return 800 - Math.min(100, candidate.length() - wanted.length());
        }
        if (wanted.contains(candidate) && candidate.length() >= 4) {
            return 760 - Math.min(100, wanted.length() - candidate.length());
        }
        if (wanted.length() < 5 || candidate.length() < 5) return 0;
        int distance = editDistance(wanted, candidate);
        int maxDistance = Math.max(1, Math.min(3, Math.max(wanted.length(), candidate.length()) / 4));
        if (distance > maxDistance) return 0;
        return 600 - distance * 80 - Math.abs(wanted.length() - candidate.length()) * 5;
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1,
                                previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private Result launchApp(String query) {
        PackageManager pm = context.getPackageManager();
        String wanted = normalizeAppName(query);
        if ("fb".equals(wanted)) wanted = "facebook";
        else if ("ig".equals(wanted)) wanted = "instagram";
        else if ("wa".equals(wanted)) wanted = "whatsapp";
        else if ("yt".equals(wanted)) wanted = "youtube";
        else if ("dc".equals(wanted)) wanted = "discord";
        // Speech recognition commonly hears ChatGPT as “chat gbt” or “jack gbt”.
        // Canonicalize these narrow, known variants before fuzzy matching.
        if ("chatgbt".equals(wanted) || "jackgbt".equals(wanted)
                || "jackgpt".equals(wanted) || "chatgptapp".equals(wanted)) {
            wanted = "chatgpt";
        }

        String packageName = null;
        CharSequence label = null;
        int bestScore = 0;
        int secondScore = 0;
        String secondPackageName = null;
        CharSequence secondLabel = null;
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            // Ignore packages that cannot actually be launched; they are not useful
            // candidates for a natural-language "open app" request.
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue;
            CharSequence appLabel = pm.getApplicationLabel(app);
            String name = appLabel == null ? "" : appLabel.toString();
            String normalizedName = normalizeAppName(name);
            int score = appMatchScore(wanted, normalizedName);
            if (score > bestScore) {
                if (packageName != null && !packageName.equals(app.packageName)) {
                    secondScore = bestScore;
                    secondPackageName = packageName;
                    secondLabel = label;
                }
                bestScore = score;
                packageName = app.packageName;
                label = appLabel;
            } else if (score > secondScore && !app.packageName.equals(packageName)) {
                secondScore = score;
                secondPackageName = app.packageName;
                secondLabel = appLabel;
            }
        }

        // Near-equal fuzzy matches should be clarified instead of opening a guess.
        if (packageName != null && secondPackageName != null && bestScore < 1000
                && secondScore >= bestScore - 30) {
            return new Result(true, false,
                    "I found more than one close match: " + label + " and " + secondLabel
                            + ". Which app did you mean?",
                    "app_launch", 0);
        }

        // If the app label was hidden from the launcher listing, try ChatGPT's
        // known Android package as a final fallback. Package visibility is still
        // respected by Android; this does not bypass platform restrictions.
        if (packageName == null && "chatgpt".equals(wanted)) {
            try {
                ApplicationInfo app = pm.getApplicationInfo("com.openai.chatgpt", 0);
                packageName = app.packageName;
                label = pm.getApplicationLabel(app);
            } catch (Exception ignored) {
            }
        }

        if (packageName == null) {
            try {
                packageName = pm.getApplicationInfo(query.trim(), 0).packageName;
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
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty()) {
            return new Result(true, false, "Tell me which contact to call.", "contact_call", 0);
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + wanted + "%"},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cursor == null || !cursor.moveToFirst()) {
                return new Result(true, false,
                        "I couldn't find a contact named \"" + wanted + "\".",
                        "contact_call", 0);
            }

            Map<String, String> matches = new java.util.LinkedHashMap<>();
            do {
                String display = cursor.getString(1);
                String number = cursor.getString(0);
                if (display != null && number != null && !number.trim().isEmpty()
                        && !matches.containsKey(display)) {
                    matches.put(display, number);
                }
            } while (cursor.moveToNext());

            String display = null;
            String number = null;
            for (Map.Entry<String, String> entry : matches.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(wanted)) {
                    display = entry.getKey();
                    number = entry.getValue();
                    break;
                }
            }
            if (display == null && matches.size() == 1) {
                Map.Entry<String, String> only = matches.entrySet().iterator().next();
                display = only.getKey();
                number = only.getValue();
            }
            if (display == null || number == null) {
                StringBuilder names = new StringBuilder();
                int count = 0;
                for (String candidate : matches.keySet()) {
                    if (count++ >= 4) break;
                    if (names.length() > 0) names.append(", ");
                    names.append(candidate);
                }
                String response = matches.isEmpty()
                        ? "I couldn't find a phone number for " + wanted + "."
                        : "I found multiple contacts matching \"" + wanted + "\" (" + names
                            + "). Say the full contact name so I don't call the wrong person.";
                return new Result(true, false, response, "contact_call", 0);
            }

            if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {
                    Intent call = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
                    call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(call);
                    lastContact = display;
                    return result("contact_call", "Calling " + display + ".");
                } catch (SecurityException denied) {
                    return launchCallPermissionBridge(number, display);
                } catch (ActivityNotFoundException unavailable) {
                    return new Result(true, false, "Android couldn't find a phone app to call "
                            + display + ".", "contact_call", 0);
                }
            }
            lastContact = display;
            return launchCallPermissionBridge(number, display);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private Result launchCallPermissionBridge(String number,String display) {
        try {
            Intent permission=new Intent(context,CallPermissionActivity.class);
            permission.putExtra(CallPermissionActivity.EXTRA_NUMBER,number);
            permission.putExtra(CallPermissionActivity.EXTRA_CONTACT,display);
            permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(permission);
            return new Result(true,false,"Android will ask for permission before calling "+display
                    +". If permission is denied, the dialer will open instead.","contact_call",0);
        } catch(Exception error) {
            return new Result(true,false,"I couldn't start the direct-call permission flow: "
                    +error.getClass().getSimpleName(),"contact_call",0);
        }
    }

    private static boolean asksForDeviceToggle(String command) {
        return command.matches(".*\\b(?:turn|switch|enable|disable|activate|deactivate|set)\\b.*\\b(?:wi-?fi|wireless|bluetooth|internet|network|mobile data)\\b.*")
                || command.matches(".*\\b(?:increase|decrease|turn on|turn off)\\b.*\\b(?:wi-?fi|wireless|bluetooth|internet|network|mobile data)\\b.*");
    }

    private Result openConnectivityPanel(String target,String command) {
        try {
            Intent panel=Build.VERSION.SDK_INT>=29?new Intent("android.settings.panel.action.INTERNET_CONNECTIVITY")
                    :new Intent(Settings.ACTION_WIFI_SETTINGS);
            panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(panel);
            return new Result(true,false,"Android doesn't allow Sync AI to silently toggle "+target
                    +". I opened the system connectivity controls so you can finish the change.","connectivity_settings",0);
        }catch(Exception error){
            return new Result(true,false,"I couldn't open system connectivity controls: "
                    +error.getClass().getSimpleName(),"connectivity_settings",0);
        }
    }

    private static long durationSeconds(double amount,String unit) {
        if(amount<=0)return 0L;
        if(unit.startsWith("hour")||unit.startsWith("hr"))return Math.round(amount*3600);
        if(unit.startsWith("min"))return Math.round(amount*60);
        return Math.round(amount);
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
        flashlightCameraId = target;
        cm.setTorchMode(target, on);
        flashlightOn = on;
    }

    /**
     * Opens an app chosen in Sync AI's Settings > Default apps screen.
     * App selection itself is intentionally UI-only; natural language can launch,
     * but cannot silently rewrite a user's defaults.
     */
    private Result handlePreferredMusicApp(String command) {
        String normalized = command == null ? "" : command.trim();
        String key;
        String labelKey;
        String category;
        if (normalized.matches("(?i)^(?:open|launch|start|show)\\s+(?:my\\s+)?(?:default\\s+)?(?:music|media|audio)\\s+(?:app|player)$")
                || normalized.matches("(?i)^(?:open|launch|start)\\s+my\\s+music$")) {
            key = "preferred_music_package";
            labelKey = "preferred_music_label";
            category = "music app";
        } else if (normalized.matches("(?i)^(?:open|launch|start|show)\\s+(?:my\\s+)?(?:default\\s+)?(?:file\\s+manager|files|file\\s+browser)$")) {
            key = "preferred_file_manager_package";
            labelKey = "preferred_file_manager_label";
            category = "file manager";
        } else if (normalized.matches("(?i)^(?:open|launch|start|show)\\s+(?:my\\s+)?(?:default\\s+)?camera\\s+app$")) {
            key = "preferred_camera_package";
            labelKey = "preferred_camera_label";
            category = "camera app";
        } else if (normalized.matches("(?i)^(?:open|launch|start|show)\\s+(?:my\\s+)?(?:default\\s+)?(?:audio\\s+)?recorder\\s+app$")) {
            key = "preferred_recorder_package";
            labelKey = "preferred_recorder_label";
            category = "recorder app";
        } else {
            return Result.none();
        }

        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "sync_media_preferences", Context.MODE_PRIVATE);
        String packageName = prefs.getString(key, null);
        String label = prefs.getString(labelKey, category);
        if (packageName == null || packageName.trim().isEmpty()) {
            return new Result(true, false,
                    "No preferred " + category + " is selected. Choose one in Sync AI Settings > Default apps.",
                    "preferred_app", 0);
        }
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                prefs.edit().remove(key).remove(labelKey).apply();
                return new Result(true, false,
                        "Your saved " + category + " is no longer launchable. Choose an installed app again in Settings > Default apps.",
                        "preferred_app", 0);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            return result("preferred_app", "Opening your preferred " + category + ", " + label + ".");
        } catch (Exception error) {
            return new Result(true, false,
                    "I couldn't open " + label + ": " + error.getClass().getSimpleName() + ".",
                    "preferred_app", 0);
        }
    }

    private Result handleMusicCapabilityCommand(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        boolean shuffle = normalized.matches(".*\\b(?:shuffle|random play|shuffle mode)\\b.*")
                && (normalized.matches(".*\\b(?:music|songs?|tracks?|playback|playlist)\\b.*")
                    || normalized.matches("^(?:turn|switch|enable|disable|set)\\b.*"));
        boolean repeat = normalized.matches(".*\\b(?:repeat|loop)\\b.*")
                && (normalized.matches(".*\\b(?:music|songs?|tracks?|playback|playlist)\\b.*")
                    || normalized.matches("^(?:turn|switch|enable|disable|set)\\b.*"));
        boolean playlists = normalized.matches(".*\\bplaylists?\\b.*")
                && normalized.matches(".*\\b(?:show|open|list|create|make|add|remove|play|manage|my|all)\\b.*");
        if (!shuffle && !repeat && !playlists) return Result.none();

        MusicProviderRegistry registry = new MusicProviderRegistry(context);
        MusicProviderRegistry.Capability capability = playlists
                ? MusicProviderRegistry.Capability.PLAYLISTS
                : shuffle ? MusicProviderRegistry.Capability.SHUFFLE : MusicProviderRegistry.Capability.REPEAT;
        MusicProviderRegistry.Provider provider = registry.selectedProvider();
        if (provider.supports(capability)) {
            // Provider-specific implementations should dispatch here once an adapter is verified.
            return new Result(true, false, registry.unsupportedMessage(capability), "music_control", 0);
        }
        return new Result(true, false, registry.unsupportedMessage(capability), "music_control", 0);
    }

    private static boolean isMediaTransportCommand(String command) {
        return command.matches(".*\\b(?:pause|resume|unpause|stop|next|skip|previous|prev)\\b.*\\b(?:music|song|track|playback)\\b.*")
                || command.matches(".*\\b(?:play|pause|resume|stop)\\b.*\\b(?:the )?music\\b.*")
                || command.matches(".*\\b(?:next song|next track|skip this song|skip this track|previous song|previous track|last song)\\b.*");
    }

    private Result dispatchMediaTransport(String command) {
        String action;
        int keyCode;
        if (command.matches(".*\\b(?:pause|stop)\\b.*")) {
            action = "pause"; keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
        } else if (command.matches(".*\\b(?:resume|unpause|play)\\b.*")) {
            action = "resume"; keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
        } else if (command.matches(".*\\b(?:previous|prev|last)\\b.*")) {
            action = "previous track"; keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        } else {
            action = "next track"; keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
        }
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            return new Result(true, false, "Android's audio service isn't available.", "media_control", 0);
        }
        try {
            long now = android.os.SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now,
                    android.view.KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now,
                    android.view.KeyEvent.ACTION_UP, keyCode, 0));
            return result("media_control", "Sent the system media command to the active player (" + action
                    + "). The player decides whether it supports or accepts the command.");
        } catch (Exception error) {
            return new Result(true, false, "I couldn't send the media command: "
                    + error.getClass().getSimpleName() + ".", "media_control", 0);
        }
    }

    private static boolean asksToOpenProductivity(String command) {
        return command.matches(".*\\\\b(?:open|show|launch|bring up)\\\\s+(?:(?:my|the|sync ai)\\\\s+)?(?:tasks(?:\\\\s*(?:and|&)\\\\s*notes)?|notes|task list|to-do list|todo list)\\\\b.*")
                || command.matches(".*\\\\b(?:show|view|open)\\\\s+(?:my\\\\s+)?(?:tasks|notes)\\\\b.*");
    }

    private static String extractItemText(String command, boolean task) {
        String kind = task ? "(?:task|todo|to-do)" : "(?:note|memo)";
        Matcher matcher = Pattern.compile("(?i)^(?:(?:please|hey|bro)\\\\s+)*(?:add|create|save|make|write)\\\\s+(?:(?:a|the|new)\\\\s+)?"
                + kind + "(?:\\\\s+(?:to|about|saying|that says|called|named))?\\\\s*[:,-]?\\\\s+(.+?)\\\\s*[.!?]*$").matcher(command);
        if (!matcher.matches()) return null;
        String value = matcher.group(1) == null ? "" : matcher.group(1).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean saveProductivityItem(String text, boolean task) {
        if (text == null || text.trim().isEmpty()) return false;
        String key = task ? "tasks_json" : "notes_json";
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "sync_productivity", Context.MODE_PRIVATE);
            org.json.JSONArray items = new org.json.JSONArray(prefs.getString(key, "[]"));
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("id", java.util.UUID.randomUUID().toString());
            item.put("text", text.trim());
            item.put("done", false);
            item.put("created", System.currentTimeMillis());
            item.put("source", "command");
            items.put(item);
            return prefs.edit().putString(key, items.toString()).commit();
        } catch (Exception error) {
            SyncEventLogger.recordException(context, "ToolEngine", "PRODUCTIVITY_ITEM_SAVE_FAILED",
                    error, "kind=" + (task ? "task" : "note"));
            return false;
        }
    }

    /**
     * Keep chat/voice reminders visible in the local Tasks & Notes list.
     * The reminder scheduler remains the source of notification timing.
     */
    private boolean saveReminderAsTask(String text) {
        if (text == null || text.trim().isEmpty() || "Your reminder is due.".equalsIgnoreCase(text.trim())) {
            return false;
        }
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "sync_productivity", Context.MODE_PRIVATE);
            org.json.JSONArray tasks = new org.json.JSONArray(prefs.getString("tasks_json", "[]"));
            org.json.JSONObject task = new org.json.JSONObject();
            task.put("id", java.util.UUID.randomUUID().toString());
            task.put("text", text.trim());
            task.put("done", false);
            task.put("created", System.currentTimeMillis());
            task.put("source", "reminder-command");
            tasks.put(task);
            return prefs.edit().putString("tasks_json", tasks.toString()).commit();
        } catch (Exception error) {
            SyncEventLogger.recordException(context, "ToolEngine", "REMINDER_TASK_SAVE_FAILED",
                    error, "textLength=" + text.length());
            return false;
        }
    }

    private static boolean asksAboutCameraInventory(String command) {
        return command.matches(".*\\b(?:what|which|how many)\\b.*\\b(?:cameras?|lenses?)\\b.*")
                || command.matches(".*\\b(?:front|back|rear|selfie)\\s+(?:camera|lens)\\b.*\\b(?:available|exist|have|is there|do i have)\\b.*")
                || command.matches(".*\\b(?:is there|do i have|are there)\\b.*\\b(?:front|back|rear|selfie)\\s+(?:camera|lens)\\b.*");
    }

    private static boolean asksToCaptureVideo(String command) {
        return command.matches(".*\\b(?:record|shoot|film|capture)\\b.*\\b(?:a\\s+)?(?:video|clip|movie)\\b.*")
                || command.matches(".*\\b(?:video record|start recording video|start video recording)\\b.*");
    }

    private static boolean asksToCapturePhoto(String command) {
        return command.matches(".*\\b(?:take|capture|shoot|snap)\\b.*\\b(?:a\\s+)?(?:photo|picture|pic|selfie)\\b.*")
                || command.matches(".*\\b(?:take|capture|shoot)\\b.*\\b(?:a\\s+)?(?:selfie)\\b.*");
    }

    private static boolean asksToOpenCamera(String command) {
        return command.matches(".*\\b(?:open|launch|start|show|bring up|pull up)\\b.*\\b(?:camera|camera app)\\b.*")
                || command.equals("camera") || command.equals("open my camera")
                || command.equals("open camera app");
    }

    private static boolean asksToStopSyncRecording(String command) {
        return command.matches(".*\\\\b(?:stop|finish|end)\\\\s+(?:(?:the|my|sync)\\\\s+)*(?:audio\\\\s+)?recording\\\\b.*")
                || command.matches(".*\\\\b(?:stop|finish)\\\\s+(?:sync\\\\s+)?recorder\\\\b.*");
    }

    private static boolean asksToStartSyncRecording(String command) {
        if (command.contains("external recorder") || command.contains("recorder app")
                || command.equals("open audio recorder") || command.equals("open voice recorder")) return false;
        return command.matches(".*\\\\b(?:start|begin)\\\\s+(?:(?:a|the|my|sync)\\\\s+)*(?:audio\\\\s+)?recording\\\\b.*")
                || command.matches(".*\\\\b(?:record|recording)\\\\s+(?:my\\\\s+)?(?:audio|voice|a voice note|a voice memo)\\\\b.*")
                || command.matches(".*\\\\bstart\\\\s+(?:sync\\\\s+)?recorder\\\\b.*");
    }

    private static boolean asksToRecordAudio(String command) {
        return command.matches(".*\\b(?:record|start recording|open)\\b.*\\b(?:audio|voice memo|voice note|sound recording)\\b.*")
                || command.equals("open voice recorder") || command.equals("open audio recorder");
    }

    private static Integer requestedCameraFacing(String command) {
        if (command.matches(".*\\b(?:front|selfie|front-facing)\\b.*")) {
            return android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        if (command.matches(".*\\b(?:back|rear|main)\\s+(?:camera|lens)\\b.*")
                || command.matches(".*\\b(?:back|rear)\\b.*\\b(?:camera|lens)\\b.*")) {
            return android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        return null;
    }

    private Result describeCameras() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            return new Result(true, false, "Android's camera service isn't available.", "camera_inventory", 0);
        }
        try {
            int front = 0, back = 0, external = 0;
            for (String id : manager.getCameraIdList()) {
                Integer facing = manager.getCameraCharacteristics(id).get(
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (facing == null) continue;
                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) front++;
                else if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) back++;
                else if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL) external++;
            }
            StringBuilder message = new StringBuilder("Android reports ")
                    .append(back).append(" rear camera").append(back == 1 ? "" : "s")
                    .append(" and ").append(front).append(" front-facing camera")
                    .append(front == 1 ? "" : "s");
            if (external > 0) message.append(", plus ").append(external).append(" external camera")
                    .append(external == 1 ? "" : "s");
            message.append(". These are camera IDs exposed by Android; a device may expose logical cameras rather than every physical lens.");
            return result("camera_inventory", message.toString());
        } catch (Exception error) {
            return new Result(true, false, "I couldn't inspect the available cameras: "
                    + error.getClass().getSimpleName() + ".", "camera_inventory", 0);
        }
    }

    private Result launchSyncCamera(String mode, Integer facing) {
        Intent intent = new Intent(context, CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(CameraActivity.EXTRA_MODE, mode);
        if (facing != null) {
            intent.putExtra(CameraActivity.EXTRA_FACING,
                    facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back");
        }
        try {
            context.startActivity(intent);
            return result("sync_camera", "Opened Sync Camera in " + mode
                    + " mode. Review the preview and tap " + ("video".equals(mode) ? "Record" : "Capture")
                    + " when you're ready.");
        } catch (Exception error) {
            return new Result(true, false, "I couldn't open Sync Camera: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()),
                    "sync_camera", 0);
        }
    }

    private Result launchCameraCapture(String action, Integer facing, String mode) {
        if ("camera".equals(mode)) {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "sync_media_preferences", Context.MODE_PRIVATE);
            String preferredPackage = prefs.getString("preferred_camera_package", null);
            if (preferredPackage != null && !preferredPackage.trim().isEmpty()) {
                Intent preferred = context.getPackageManager().getLaunchIntentForPackage(preferredPackage);
                if (preferred != null) {
                    preferred.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(preferred);
                        return result("camera", "Opened your preferred camera app. Camera-facing selection depends on that app.");
                    } catch (Exception ignored) {
                        // Fall back to Android's camera intent below.
                    }
                } else {
                    prefs.edit().remove("preferred_camera_package")
                            .remove("preferred_camera_label").apply();
                }
            }
        }
        Intent intent = new Intent(action);
        if (facing != null) {
            // These extras are best-effort compatibility hints; camera apps may ignore them.
            intent.putExtra("android.intent.extras.CAMERA_FACING", facing);
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT",
                    facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
            intent.putExtra("android.intent.extras.USE_FRONT_CAMERA",
                    facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                // Never silently downgrade a requested video/photo capture into ordinary photo mode.
                return new Result(true, false,
                        "No compatible system camera handler is available for " + mode + " mode.",
                        "camera", 0);
            }
            context.startActivity(intent);
            String detail = "Opened the system camera";
            if ("photo".equals(mode)) detail += " for photo capture";
            else if ("video".equals(mode)) detail += " for video capture";
            if (facing != null) detail += facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT
                    ? " (front camera requested)" : " (rear camera requested)";
            detail += ". The camera app may ignore camera-facing hints; Sync hasn't verified a photo/video was saved.";
            return result("camera", detail);
        } catch (ActivityNotFoundException error) {
            return new Result(true, false, "I couldn't open a compatible camera app.", "camera", 0);
        } catch (SecurityException error) {
            return new Result(true, false, "Android blocked access to the camera app.", "camera", 0);
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
        if (!command.matches(".*\\d.*")) return false;
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
