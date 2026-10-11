package com.sam.syncai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms user-requested reminders after Android completes a device boot. */
public final class ReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        SyncEventLogger.install(context);
        SyncEventLogger.record(context,"ReminderBootReceiver","BOOT_COMPLETED","INFO","");
        ReminderScheduler.reschedulePending(context);
    }
}
