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
            Pattern.compile("(?i)\\b(?:(?:set\\s+)?(?:a\\s+)?timer(?:\\s+(?:for|to|until))?|countdown(?:\\s+(?:for|to|until))?)\\s+(\\d{1,2})(?::(\\d{2}))\\s*(a\\.?m\\.?|p\\.?m\\.?)?\\b");
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
            Pattern.compile("(?i)(?:set\\s+(?:an\\s+)?)?(?:alarm|wake me|wake up)(?:\\s+(?:for|at))?\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");
    private static final Pattern OPEN_APP =
            Pattern.compile("(?i)^(?:please\\s+)?(?:open|launch|start|run|go to)\\s+(.+)$");
    private static final Pattern CALL =
            Pattern.compile("(?i)^(?:please\\s+)?(?:call|phone|ring)\\s+(.+)$");

    private final Context context;
    private String lastCommand = "";
    private String lastTool = "";
    private String lastApp = "";
    private String lastContact = "";
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

        Matcher reminderIn = REMINDER_IN.matcher(command);
        if (reminderIn.find()) {
            double amount = Double.parseDouble(reminderIn.group(1));
            long seconds = durationSeconds(amount, reminderIn.group(2).toLowerCase(Locale.US));
            if (seconds <= 0) throw new IllegalArgumentException("Reminder delay must be positive.");
            String reminderText = reminderIn.group(3) == null ? "Your reminder is due." : reminderIn.group(3).trim();
            ReminderScheduler.schedule(context, seconds * 1000L, "Sync AI reminder", "Reminder: " + reminderText);
            return result("reminder", "Reminder scheduled in " + formatDuration(seconds) + ": " + reminderText
                    + ". I'll notify you even if Sync AI is closed.");
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
            return result("reminder","Reminder scheduled for "
                    +DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(cal.getTime())
                    +": "+reminderText+".");
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

        Matcher labelMatcher = ALARM_LABEL.matcher(command);
        String alarmLabel = labelMatcher.find() ? labelMatcher.group(1).trim() : "Sync AI alarm";
        String alarmCommand = labelMatcher.reset().replaceAll("").trim();
        Matcher alarm=ALARM.matcher(alarmCommand);
        if(alarm.find()) {
            int hour=Integer.parseInt(alarm.group(1));
            int minute=alarm.group(2)==null?0:Integer.parseInt(alarm.group(2));
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
        if (open.find()) return launchApp(open.group(1).trim());

        Matcher call = CALL.matcher(command);
        if (call.find()) return callContact(call.group(1).trim());

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
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            CharSequence appLabel = pm.getApplicationLabel(app);
            String name = appLabel == null ? "" : appLabel.toString();
            String normalizedName = normalizeAppName(name);
            int score = appMatchScore(wanted, normalizedName);
            if (score > bestScore) {
                bestScore = score;
                packageName = app.packageName;
                label = appLabel;
            }
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
