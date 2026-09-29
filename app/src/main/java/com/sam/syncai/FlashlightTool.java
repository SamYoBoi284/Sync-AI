package com.sam.syncai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

import java.util.Map;

public final class FlashlightTool implements SyncTool {
    private final Context context;

    public FlashlightTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String getName() { return "flashlight"; }

    @Override public String getDescription() {
        return "Turn the phone flashlight on or off.";
    }

    @Override public String getInputSchema() {
        return "{\"enabled\":true}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        if (context.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("CAMERA_PERMISSION_REQUIRED");
        }

        String raw = arguments.get("enabled");
        if (raw == null) throw new IllegalArgumentException("Missing enabled.");
        boolean enabled = Boolean.parseBoolean(raw);

        CameraManager cameraManager =
                (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) throw new IllegalStateException("Camera service unavailable.");

        String selectedCamera = null;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(hasFlash) &&
                    (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                selectedCamera = id;
                break;
            }
        }
        if (selectedCamera == null) {
            throw new IllegalStateException("No camera with a usable flash was found.");
        }

        cameraManager.setTorchMode(selectedCamera, enabled);
        context.getSharedPreferences("sync_flashlight_state", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).apply();
        return enabled ? "Flashlight turned on." : "Flashlight turned off.";
    }
}
