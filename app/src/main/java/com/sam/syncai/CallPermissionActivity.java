package com.sam.syncai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Shared direct-call permission bridge used by chat, Hey Sync and assistant voice mode. */
public final class CallPermissionActivity extends Activity {
    public static final String EXTRA_NUMBER="com.sam.syncai.extra.CALL_NUMBER";
    public static final String EXTRA_CONTACT="com.sam.syncai.extra.CALL_CONTACT";
    private String number;
    private String contact;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        number=getIntent().getStringExtra(EXTRA_NUMBER);
        contact=getIntent().getStringExtra(EXTRA_CONTACT);
        if(number==null||number.trim().isEmpty()){finish();return;}
        SyncEventLogger.record(this,"CallPermissionActivity","DIRECT_CALL_FLOW","INFO",
                "contact="+(contact==null?"":contact)+" permissionGranted="
                +(checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED));
        if(checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) placeDirectCall();
        else requestPermissions(new String[]{Manifest.permission.CALL_PHONE},7301);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode!=7301)return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED) placeDirectCall();
        else {
            SyncEventLogger.record(this,"CallPermissionActivity","CALL_PERMISSION_DENIED","WARN","dialer fallback");
            Toast.makeText(this,"Direct calling permission was denied. The dialer is ready instead.",
                    Toast.LENGTH_LONG).show();
            openDialerFallback();
        }
    }

    private void placeDirectCall() {
        try {
            startActivity(new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(number))));
            SyncEventLogger.record(this,"CallPermissionActivity","ACTION_CALL_DISPATCHED","INFO",
                    "contact="+(contact==null?"":contact));
            finish();
        } catch(SecurityException|android.content.ActivityNotFoundException error) {
            SyncEventLogger.recordException(this,"CallPermissionActivity","ACTION_CALL_FAILED",error,
                    "dialer fallback");
            openDialerFallback();
        }
    }

    private void openDialerFallback() {
        try { startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number)))); }
        catch(Exception error) {
            SyncEventLogger.recordException(this,"CallPermissionActivity","ACTION_DIAL_FAILED",error,"");
            Toast.makeText(this,"Android couldn't open a phone app.",Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
