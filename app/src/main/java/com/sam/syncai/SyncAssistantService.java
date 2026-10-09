package com.sam.syncai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in, foreground Vosk wake-word listener. The LLM is not initialized by
 * this service; it hands off to the existing Android assistant voice route.
 */
public final class SyncAssistantService extends Service implements RecognitionListener {
    public static final String ACTION_STOP = "com.sam.syncai.action.STOP_WAKE_WORD";
    public static final String ACTION_VOICE_MODE_STARTED =
            "com.sam.syncai.action.VOICE_MODE_STARTED";
    public static final String ACTION_VOICE_MODE_FINISHED =
            "com.sam.syncai.action.VOICE_MODE_FINISHED";

    private static final String TAG = "SyncAI";
    private static final String CHANNEL_ID = "sync_wake_word";
    private static final int NOTIFICATION_ID = 2840;
    private static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean wakeTriggered = new AtomicBoolean(false);
    private final ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
    private boolean modelLoading;
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private boolean voiceModeActive;
    private boolean waitingForVoiceStart;
    private boolean receiverRegistered;
    private final Runnable launchTimeout = () -> {
        if (running && waitingForVoiceStart && !voiceModeActive) {
            String assistantSettings = readAssistantSettings();
            boolean syncIsDefault = assistantSettings.contains(getPackageName());
            SyncEventLogger.record(this, "SyncAssistantService",
                    "VOICE_MODE_START_TIMEOUT", "WARN",
                    "assistant activity did not signal start; syncIsDefault=" + syncIsDefault
                            + " settings=" + assistantSettings);
            waitingForVoiceStart = false;
            startWakeListening();
            if (!syncIsDefault) {
                main.postDelayed(() -> updateNotification(
                        "Wake word works. Set Sync AI as default assistant in Voice & Wake Word settings."),
                        300L);
            } else {
                main.postDelayed(() -> updateNotification(
                        "Sync AI is default, but voice mode did not open. Export logs for diagnosis."),
                        300L);
            }
        }
    };

    private final BroadcastReceiver voiceReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (ACTION_VOICE_MODE_STARTED.equals(action)) {
                voiceModeActive = true;
                waitingForVoiceStart = false;
                main.removeCallbacks(launchTimeout);
                stopSpeechPipeline();
                SyncEventLogger.record(SyncAssistantService.this, "SyncAssistantService",
                        "VOICE_MODE_STARTED", "INFO", "wake listener stopped; voice mode owns the microphone");
            } else if (ACTION_VOICE_MODE_FINISHED.equals(action)) {
                voiceModeActive = false;
                waitingForVoiceStart = false;
                main.removeCallbacks(launchTimeout);
                SyncEventLogger.record(SyncAssistantService.this, "SyncAssistantService",
                        "VOICE_MODE_FINISHED", "INFO", "resuming wake listener");
                main.postDelayed(SyncAssistantService.this::startWakeListening, 650L);
            }
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public static void requestStop(Context context) {
        running = false;
        context.stopService(new Intent(context, SyncAssistantService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        SyncEventLogger.install(this);
        createNotificationChannel();
        startForegroundCompat(buildNotification("Say “Hey Sync” or “Hey Nullverox” to start voice mode."));
        running = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_VOICE_MODE_STARTED);
        filter.addAction(ACTION_VOICE_MODE_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(voiceReceiver, filter);
        }
        receiverRegistered = true;
        SyncEventLogger.record(this, "SyncAssistantService", "SERVICE_STARTED",
                "INFO", "foreground Vosk wake-word service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            running = false;
            getPreferences().setWakeWordEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            SyncEventLogger.record(this, "SyncAssistantService", "MIC_PERMISSION_MISSING",
                    "ERROR", "wake service cannot run without microphone permission");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!VoskModelInstaller.isInstalled(this)) {
            updateNotification("Vosk model missing — import the model ZIP in Sync AI.");
            SyncEventLogger.record(this, "SyncAssistantService", "VOSK_MODEL_MISSING",
                    "ERROR", "model not installed; service stopped");
            stopSelf();
            return START_NOT_STICKY;
        }
        getPreferences().setWakeWordEnabled(true);
        if (model == null) {
            updateNotification("Loading the offline wake-word model…");
            loadVoskModelAsync();
        } else {
            startWakeListening();
        }
        return START_NOT_STICKY;
    }

    private AppPreferences getPreferences() {
        return SyncRuntime.get(this).preferences();
    }

    private void loadVoskModelAsync() {
        if (modelLoading) return;
        modelLoading = true;
        File modelDir = VoskModelInstaller.modelDirectory(this);
        modelExecutor.execute(() -> {
            Model loaded = null;
            Exception failure = null;
            try {
                loaded = new Model(modelDir.getAbsolutePath());
            } catch (Exception error) {
                failure = error;
            }
            final Model loadedModel = loaded;
            final Exception loadError = failure;
            main.post(() -> {
                modelLoading = false;
                if (!running || !getPreferences().isWakeWordEnabled()) {
                    if (loadedModel != null) {
                        try { loadedModel.close(); } catch (Exception ignored) {}
                    }
                    return;
                }
                if (loadError != null || loadedModel == null) {
                    if (loadError != null) {
                        SyncEventLogger.recordException(this, "SyncAssistantService",
                                "VOSK_MODEL_LOAD_ERROR", loadError, "model initialization failed");
                    }
                    updateNotification("Vosk model could not load — open Sync AI for diagnostics.");
                    stopSelf();
                    return;
                }
                model = loadedModel;
                SyncEventLogger.record(this, "SyncAssistantService", "VOSK_MODEL_READY",
                        "INFO", "modelPath=" + modelDir.getAbsolutePath());
                startWakeListening();
            });
        });
    }

    private void startWakeListening() {
        if (!running || voiceModeActive || waitingForVoiceStart
                || speechService != null || model == null) return;
        if (!getPreferences().isWakeWordEnabled()) return;
        try {
            wakeTriggered.set(false);
            recognizer = new Recognizer(model, 16000.0f, "[\"hey sync\", \"hey nullverox\", \"hey null verox\", \"[unk]\"]");
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
            updateNotification("Listening locally for “Hey Sync” or “Hey Nullverox”. Tap to stop.");
            SyncEventLogger.record(this, "SyncAssistantService", "WAKE_LISTENING",
                    "INFO", "grammar=[hey sync, hey nullverox, hey null verox, [unk]]; local model; no LLM loaded by service");
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "SyncAssistantService",
                    "WAKE_LISTEN_START_ERROR", error, "could not start Vosk");
            stopSpeechPipeline();
            main.postDelayed(this::startWakeListening, 1800L);
        }
    }

    private void inspectHypothesis(String hypothesis) {
        if (hypothesis == null || !running || voiceModeActive || waitingForVoiceStart) return;
        try {
            JSONObject result = new JSONObject(hypothesis);
            String heard = result.optString("text", result.optString("partial", ""))
                    .trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
            // Match only a complete wake phrase. The [unk] grammar alternative lets
            // ordinary speech remain unrecognized instead of forcing every utterance
            // into one of the wake-word phrases.
            boolean isWakePhrase = "hey sync".equals(heard)
                    || "hey nullverox".equals(heard)
                    || "hey null verox".equals(heard);
            if (isWakePhrase && wakeTriggered.compareAndSet(false, true)) {
                triggerAssistant(heard);
            }
        } catch (Exception ignored) {
            // Vosk can emit an empty hypothesis while the recognizer is warming up.
        }
    }

    private void triggerAssistant(String phrase) {
        SyncEventLogger.record(this, "SyncAssistantService", "WAKE_WORD_DETECTED",
                "INFO", "phrase=" + phrase + "; suspending Vosk before assistant handoff");
        String assistantSettings = readAssistantSettings();
        boolean syncIsDefault = assistantSettings.contains(getPackageName());
        SyncEventLogger.record(this, "SyncAssistantService", "ASSISTANT_CONFIGURATION",
                syncIsDefault ? "INFO" : "WARN",
                "syncIsDefault=" + syncIsDefault + " settings=" + assistantSettings);
        updateNotification(syncIsDefault
                ? "Hey Sync detected — opening voice mode."
                : "Hey Sync detected. Sync AI must be the default assistant to open voice mode.");
        stopSpeechPipeline();
        vibrate();
        waitingForVoiceStart = true;
        main.postDelayed(launchTimeout, 9000L);
        SyncEventLogger.record(this, "SyncAssistantService", "MIC_HANDOFF_RELEASE_WAIT",
                "INFO", "Vosk stopped; waiting briefly before requesting assistant session");
        main.postDelayed(this::dispatchAssistantHandoff, 250L);
    }

    private void dispatchAssistantHandoff() {
        if (!running || !waitingForVoiceStart || voiceModeActive) return;
        try {
            // Wake-word activation already knows this is Sync AI. Do not send the
            // implicit ACTION_ASSIST intent here: Android/Samsung can present an
            // app chooser instead of opening the selected assistant session.
            // Launch the same translucent voice Activity directly. The hardware
            // assistant-button path remains owned by VoiceInteractionSession.
            Intent voice = new Intent(this, VoiceModeActivity.class);
            voice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            SyncEventLogger.record(this, "SyncAssistantService",
                    "WAKE_OVERLAY_LAUNCH_ATTEMPT", "INFO",
                    "explicit VoiceModeActivity launch after Vosk shutdown");
            startActivity(voice);
            SyncEventLogger.record(this, "SyncAssistantService", "WAKE_OVERLAY_LAUNCH_DISPATCHED",
                    "INFO", "explicit VoiceModeActivity launch dispatched");
        } catch (Exception error) {
            waitingForVoiceStart = false;
            main.removeCallbacks(launchTimeout);
            SyncEventLogger.recordException(this, "SyncAssistantService",
                    "WAKE_OVERLAY_LAUNCH_ERROR", error,
                    "explicit VoiceModeActivity launch failed");
            updateNotification("Couldn't open voice mode — export logs for diagnosis.");
            main.postDelayed(this::startWakeListening, 1000L);
        }
    }

    private String readAssistantSettings() {
        String voiceInteraction = Settings.Secure.getString(
                getContentResolver(), "voice_interaction_service");
        String assistant = Settings.Secure.getString(
                getContentResolver(), "assistant");
        return "voiceInteractionService=" + String.valueOf(voiceInteraction)
                + "; assistant=" + String.valueOf(assistant);
    }

    private void stopSpeechPipeline() {
        SpeechService oldService = speechService;
        speechService = null;
        if (oldService != null) {
            try { oldService.stop(); } catch (Exception ignored) {}
            try { oldService.shutdown(); } catch (Exception ignored) {}
        }
        Recognizer oldRecognizer = recognizer;
        recognizer = null;
        if (oldRecognizer != null) {
            try { oldRecognizer.close(); } catch (Exception ignored) {}
        }
    }

    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (manager != null) manager.getDefaultVibrator().vibrate(
                        VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            45L, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "SyncAssistantService",
                    "WAKE_HAPTIC_ERROR", error, "optional wake haptic failed");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Sync AI wake word", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification while Hey Sync listening is enabled.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 2841, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, SyncAssistantService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 2842, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Sync AI • Hey Sync")
                .setContentText(text)
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "Stop listening", stopPending).build());
        return builder.build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override public void onPartialResult(String hypothesis) {
        inspectHypothesis(hypothesis);
    }

    @Override public void onResult(String hypothesis) {
        inspectHypothesis(hypothesis);
    }

    @Override public void onFinalResult(String hypothesis) {
        inspectHypothesis(hypothesis);
        if (running && !voiceModeActive && !waitingForVoiceStart && speechService != null) {
            main.postDelayed(this::restartRecognition, 250L);
        }
    }

    private void restartRecognition() {
        stopSpeechPipeline();
        startWakeListening();
    }

    @Override public void onError(Exception error) {
        SyncEventLogger.recordException(this, "SyncAssistantService",
                "VOSK_RECOGNITION_ERROR", error, "wake listener error");
        stopSpeechPipeline();
        if (running && !voiceModeActive && !waitingForVoiceStart
                && getPreferences().isWakeWordEnabled()) {
            main.postDelayed(this::startWakeListening, 900L);
        }
    }

    @Override public void onTimeout() {
        if (running && !voiceModeActive && !waitingForVoiceStart) {
            restartRecognition();
        }
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        SyncEventLogger.record(this, "SyncAssistantService", "SERVICE_STOPPED",
                "INFO", "foreground wake service destroyed");
        running = false;
        main.removeCallbacks(launchTimeout);
        modelExecutor.shutdownNow();
        stopSpeechPipeline();
        if (model != null) {
            try { model.close(); } catch (Exception ignored) {}
            model = null;
        }
        if (receiverRegistered) {
            try { unregisterReceiver(voiceReceiver); } catch (Exception ignored) {}
        }
        getPreferences().setWakeWordEnabled(false);
        stopForeground(true);
        super.onDestroy();
    }
}
