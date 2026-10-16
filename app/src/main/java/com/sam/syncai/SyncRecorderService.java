package com.sam.syncai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class SyncRecorderService extends Service {
    public static final String ACTION_START = "com.sam.syncai.recorder.START";
    public static final String ACTION_STOP = "com.sam.syncai.recorder.STOP";
    public static final String ACTION_STATE = "com.sam.syncai.recorder.STATE";
    public static final String EXTRA_RECORDING = "recording";
    public static final String EXTRA_MESSAGE = "message";
    private static final String CHANNEL_ID = "sync_recorder_recording";
    private static final int NOTIFICATION_ID = 2850;
    private static volatile boolean recording;
    private MediaRecorder recorder;
    private File outputFile;

    public static boolean isRecording() { return recording; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRecording("Recording saved.");
            stopSelf();
        } else if (ACTION_START.equals(action) && !recording) {
            startRecording();
        }
        return START_NOT_STICKY;
    }

    private void startRecording() {
        File dir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) == null
                ? getFilesDir() : getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                "Sync AI Recordings");
        if (!dir.exists() && !dir.mkdirs()) {
            broadcastState(false, "Couldn't create the recordings folder.");
            stopSelf();
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputFile = new File(dir, "Sync_Recording_" + stamp + ".m4a");
        try {
            // The notification exists only during an explicit recording session.
            startForegroundCompat(buildNotification());
            if (Build.VERSION.SDK_INT >= 31) recorder = new MediaRecorder(this);
            else recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            broadcastState(true, "Recording in progress.");
            SyncEventLogger.record(this, "SyncRecorderService", "RECORDING_STARTED", "INFO",
                    "file=" + outputFile.getName());
        } catch (Exception error) {
            SyncEventLogger.recordException(this, "SyncRecorderService", "RECORDING_START_FAILED",
                    error, outputFile == null ? "" : outputFile.getName());
            releaseRecorder();
            if (outputFile != null && outputFile.exists()) outputFile.delete();
            recording = false;
            stopForeground(true);
            broadcastState(false, "Couldn't start recording. Check microphone permission.");
            stopSelf();
        }
    }

    private void stopRecording(String message) {
        boolean hadRecording = recording;
        recording = false;
        boolean saved = false;
        if (recorder != null) {
            try { recorder.stop(); saved = outputFile != null && outputFile.exists() && outputFile.length() > 0; }
            catch (RuntimeException error) {
                SyncEventLogger.recordException(this, "SyncRecorderService", "RECORDING_STOP_FAILED",
                        error, outputFile == null ? "" : outputFile.getName());
            }
            releaseRecorder();
        }
        if (!saved && outputFile != null && outputFile.exists()) outputFile.delete();
        stopForeground(true);
        broadcastState(false, saved ? message : (hadRecording ? "Recording could not be saved." : "Ready to record."));
        SyncEventLogger.record(this, "SyncRecorderService", saved ? "RECORDING_SAVED" : "RECORDING_STOPPED",
                saved ? "INFO" : "WARN", saved ? "file=" + outputFile.getName() + " bytes=" + outputFile.length() : "no valid output");
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    private void broadcastState(boolean active, String message) {
        Intent state = new Intent(ACTION_STATE);
        state.setPackage(getPackageName());
        state.putExtra(EXTRA_RECORDING, active);
        state.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(state);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Recording in progress", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown only while Sync AI is recording audio.");
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, RecorderActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 2850, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, SyncRecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 2851, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Sync AI is recording")
                .setContentText("Tap Stop to finish and save the audio.")
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,
                        "Stop recording", stopPending).build())
                .build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (recording || recorder != null) stopRecording("Recording saved.");
        super.onDestroy();
    }
}
