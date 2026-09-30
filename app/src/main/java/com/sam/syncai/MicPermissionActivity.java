package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Window;

public final class MicPermissionActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        SyncEventLogger.install(this);
        super.onCreate(state);
        SyncEventLogger.record(this, "MicPermissionActivity", "onCreate", "INFO",
                "savedState=" + (state != null));
        Window window = getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7001);
        } else {
            finish();
        }
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        finish();
    }
}
