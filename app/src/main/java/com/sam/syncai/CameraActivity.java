package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class CameraActivity extends Activity implements TextureView.SurfaceTextureListener {
    public static final String EXTRA_MODE = "sync_camera_mode";
    public static final String EXTRA_FACING = "sync_camera_facing";
    private static final int REQ_CAMERA = 7410, REQ_AUDIO = 7411;
    private TextureView preview;
    private TextView status;
    private Button flip, capture, mode;
    private Camera camera;
    private MediaRecorder recorder;
    private boolean front, videoMode, recording;
    private Uri outputUri;
    private File legacyFile;
    private android.os.ParcelFileDescriptor outputDescriptor;
    private String outputName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String requestedMode = getIntent().getStringExtra(EXTRA_MODE);
        videoMode = "video".equalsIgnoreCase(requestedMode);
        front = "front".equalsIgnoreCase(getIntent().getStringExtra(EXTRA_FACING));
        AppTheme p = AppTheme.from(this);
        AppTheme.applySystemBars(this, p);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(12), 0);
        TextView back = label("‹", 32);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView title = label("SYNC CAMERA", 17);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        flip = new Button(this); flip.setAllCaps(false); flip.setText("Flip");
        flip.setOnClickListener(v -> switchCamera());
        header.addView(flip, new LinearLayout.LayoutParams(dp(76), dp(44)));
        root.addView(header);
        FrameLayout frame = new FrameLayout(this);
        preview = new TextureView(this);
        preview.setSurfaceTextureListener(this);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        status = label("Camera preview", 12);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setBackgroundColor(0x66000000);
        frame.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(12), dp(10), dp(12), dp(16));
        controls.setBackgroundColor(0xFF101014);
        mode = new Button(this); mode.setAllCaps(false); mode.setText(videoMode ? "Video" : "Photo");
        mode.setOnClickListener(v -> {
            if (recording) return;
            videoMode = !videoMode;
            mode.setText(videoMode ? "Video" : "Photo");
            capture.setText(videoMode ? "● Record" : "● Capture");
            status.setText(videoMode ? "Video mode" : "Photo mode");
        });
        controls.addView(mode, new LinearLayout.LayoutParams(0, dp(52), 1));
        capture = new Button(this); capture.setAllCaps(false); capture.setText(videoMode ? "● Record" : "● Capture");
        capture.setOnClickListener(v -> capture());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(58), 2);
        cp.leftMargin = dp(8); controls.addView(capture, cp);
        root.addView(controls);
        setContentView(root);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    private void capture() {
        if (camera == null) { Toast.makeText(this, "Camera is not ready.", Toast.LENGTH_SHORT).show(); return; }
        if (videoMode) { if (recording) stopVideo(); else startVideo(); }
        else takePhoto();
    }
    private void openCamera() {
        if (!preview.isAvailable() || isFinishing() || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        closeCamera();
        int id = findCamera(front ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
        if (id < 0 && front) { front = false; id = findCamera(Camera.CameraInfo.CAMERA_FACING_BACK); }
        if (id < 0) { status.setText("No compatible camera found."); return; }
        try {
            camera = Camera.open(id);
            Camera.CameraInfo info = new Camera.CameraInfo(); Camera.getCameraInfo(id, info);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
            int result = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                    ? (info.orientation + degrees) % 360 : (info.orientation - degrees + 360) % 360;
            camera.setDisplayOrientation(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? (360-result)%360 : result);
            Camera.Parameters params = camera.getParameters();
            List<String> focus = params.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(params);
            camera.setPreviewTexture(preview.getSurfaceTexture());
            camera.startPreview();
            status.setText((front ? "Front" : "Rear") + " camera • " + (videoMode ? "Video" : "Photo"));
        } catch (Exception e) {
            SyncEventLogger.recordException(this, "CameraActivity", "CAMERA_OPEN_FAILED", e, "id="+id);
            status.setText("Couldn't open camera. Close other camera apps and retry."); closeCamera();
        }
    }
    private int findCamera(int facing) {
        for (int i=0;i<Camera.getNumberOfCameras();i++) {
            Camera.CameraInfo info = new Camera.CameraInfo(); Camera.getCameraInfo(i, info);
            if (info.facing == facing) return i;
        }
        return -1;
    }
    private void switchCamera() { if (!recording) { front=!front; openCamera(); } }

    private void takePhoto() {
        try {
            outputName = "Sync_Photo_" + stamp() + ".jpg";
            outputUri = createOutput(false, outputName);
            if (outputUri == null) throw new IllegalStateException("No image output");
            camera.takePicture(null, null, (bytes, cam) -> {
                boolean saved=false;
                try (OutputStream out = Build.VERSION.SDK_INT >= 29
                        ? getContentResolver().openOutputStream(outputUri, "w")
                        : new FileOutputStream(legacyFile)) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    out.write(bytes); out.flush(); saved=true;
                } catch (Exception e) {
                    SyncEventLogger.recordException(this, "CameraActivity", "PHOTO_SAVE_FAILED", e, outputName);
                }
                if (saved) { publishOutput(false); status.setText("Saved " + outputName); Toast.makeText(this,"Photo saved.",Toast.LENGTH_SHORT).show(); }
                else { deleteOutput(); status.setText("Photo wasn't saved."); }
                try { cam.startPreview(); } catch (Exception ignored) {}
            });
        } catch (Exception e) { SyncEventLogger.recordException(this,"CameraActivity","PHOTO_CAPTURE_FAILED",e,""); Toast.makeText(this,"Couldn't capture photo.",Toast.LENGTH_SHORT).show(); }
    }

    private void startVideo() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); return;
        }
        try {
            outputName="Sync_Video_"+stamp()+".mp4"; outputUri=createOutput(true,outputName);
            if (outputUri==null) throw new IllegalStateException("No video output");
            camera.unlock(); recorder=new MediaRecorder(); recorder.setCamera(camera);
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoEncodingBitRate(4_000_000); recorder.setVideoFrameRate(30);
            Camera.Parameters params=camera.getParameters();
            Camera.Size size=chooseVideoSize(params);
            if (size != null) recorder.setVideoSize(size.width,size.height);
            if (Build.VERSION.SDK_INT >= 29) {
                outputDescriptor=getContentResolver().openFileDescriptor(outputUri,"w");
                if (outputDescriptor==null) throw new IllegalStateException("No video descriptor");
                recorder.setOutputFile(outputDescriptor.getFileDescriptor());
            } else recorder.setOutputFile(legacyFile.getAbsolutePath());
            recorder.setOrientationHint(front ? 270 : 90);
            recorder.setPreviewDisplay(new android.view.Surface(preview.getSurfaceTexture()));
            recorder.prepare(); recorder.start(); recording=true;
            capture.setText("■ Stop"); flip.setEnabled(false); mode.setEnabled(false);
            status.setText("VIDEO RECORDING • "+outputName);
            SyncEventLogger.record(this,"CameraActivity","VIDEO_RECORDING_STARTED","INFO",outputName);
        } catch (Exception e) {
            SyncEventLogger.recordException(this,"CameraActivity","VIDEO_START_FAILED",e,outputName);
            cleanupVideo(false); try { camera.lock(); camera.startPreview(); } catch(Exception ignored){}
            status.setText("Couldn't start video. Check permissions and storage.");
        }
    }
    private void stopVideo() {
        boolean saved=false;
        try { if (recorder!=null) { recorder.stop(); saved=true; } }
        catch(Exception e) { SyncEventLogger.recordException(this,"CameraActivity","VIDEO_STOP_FAILED",e,outputName); }
        cleanupVideo(saved); recording=false;
        try { camera.lock(); camera.startPreview(); } catch(Exception ignored){}
        capture.setText("● Record"); flip.setEnabled(true); mode.setEnabled(true);
        if(saved) publishOutput(true); else deleteOutput();
        status.setText(saved ? "Saved "+outputName : "Video wasn't saved.");
        Toast.makeText(this,saved?"Video saved.":"Video wasn't saved.",Toast.LENGTH_SHORT).show();
    }
    private void cleanupVideo(boolean saved) {
        if(recorder!=null){try{recorder.reset();}catch(Exception ignored){} try{recorder.release();}catch(Exception ignored){} recorder=null;}
        if(outputDescriptor!=null){try{outputDescriptor.close();}catch(Exception ignored){} outputDescriptor=null;}
        if(!saved) deleteOutput();
    }
    private Camera.Size chooseVideoSize(Camera.Parameters params) {
        List<Camera.Size> supported = params.getSupportedVideoSizes();
        if (supported == null || supported.isEmpty()) return params.getPreviewSize();
        Camera.Size best = null;
        long targetPixels = 1280L * 720L;
        for (Camera.Size size : supported) {
            long pixels = (long) size.width * size.height;
            if (pixels <= targetPixels && (best == null
                    || pixels > (long) best.width * best.height)) best = size;
        }
        if (best != null) return best;
        // If every advertised size exceeds 720p, prefer the smallest supported mode
        // rather than risking a high-resolution encoder configuration failure.
        for (Camera.Size size : supported) {
            if (best == null || (long) size.width * size.height < (long) best.width * best.height) best = size;
        }
        return best;
    }

    private Uri createOutput(boolean video,String name) {
        if(Build.VERSION.SDK_INT>=29){
            ContentValues v=new ContentValues(); v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            v.put(MediaStore.MediaColumns.MIME_TYPE,video?"video/mp4":"image/jpeg");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,video?Environment.DIRECTORY_MOVIES+"/Sync AI":Environment.DIRECTORY_PICTURES+"/Sync AI");
            v.put(MediaStore.MediaColumns.IS_PENDING,1);
            return getContentResolver().insert(video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
        }
        File base=getExternalFilesDir(video?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_PICTURES);
        File dir=new File(base==null?getFilesDir():base,"Sync AI"); if(!dir.exists()&&!dir.mkdirs()) return null;
        legacyFile=new File(dir,name); return Uri.fromFile(legacyFile);
    }
    private void publishOutput(boolean video) {
        if(Build.VERSION.SDK_INT>=29 && outputUri!=null){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(outputUri,v,null,null);}
        SyncEventLogger.record(this,"CameraActivity",video?"VIDEO_SAVED":"PHOTO_SAVED","INFO",outputName);
    }
    private void deleteOutput() {
        if(Build.VERSION.SDK_INT>=29 && outputUri!=null){try{getContentResolver().delete(outputUri,null,null);}catch(Exception ignored){}}
        else if(legacyFile!=null&&legacyFile.exists()) legacyFile.delete();
    }
    private String stamp(){return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());}
    private void closeCamera(){if(camera!=null){try{camera.stopPreview();}catch(Exception ignored){}try{camera.release();}catch(Exception ignored){}camera=null;}}
    @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture t,int w,int h){openCamera();}
    @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture t,int w,int h){if(!recording)openCamera();}
    @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture t){if(recording)stopVideo();closeCamera();return true;}
    @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture t){}
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){
        super.onRequestPermissionsResult(code,perms,grants);
        if(grants.length==0||grants[0]!=PackageManager.PERMISSION_GRANTED){status.setText("Permission denied.");return;}
        if(code==REQ_CAMERA)openCamera(); else if(code==REQ_AUDIO&&!recording)startVideo();
    }
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);return t;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onResume(){super.onResume();if(preview!=null&&preview.isAvailable()&&!recording)openCamera();}
    @Override protected void onPause(){if(recording)stopVideo();closeCamera();super.onPause();}
    @Override protected void onDestroy(){if(recording)stopVideo();closeCamera();super.onDestroy();}
}