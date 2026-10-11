package com.sam.syncai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Local reminder store plus AlarmManager delivery and reboot recovery. */
public final class ReminderScheduler {
    private static final String PREFS="sync-reminders";
    private static final String KEY_ITEMS="scheduled_items";
    private ReminderScheduler(){}

    public static synchronized int schedule(Context context,long delayMs,String title,String text) {
        int id=(int)(System.currentTimeMillis()&0x7fffffff);
        if(id==0)id=1;
        long when=System.currentTimeMillis()+Math.max(1000L,delayMs);
        try {
            JSONArray all=load(context);
            JSONObject item=new JSONObject();
            item.put("id",id);item.put("when",when);
            item.put("title",title==null?"Sync AI reminder":title);
            item.put("text",text==null?"Your reminder is due.":text);
            all.put(item);save(context,all);arm(context,item,when);
            SyncEventLogger.record(context,"ReminderScheduler","REMINDER_SCHEDULED","INFO",
                    "id="+id+" when="+when);
            return id;
        } catch(Exception error) {
            throw new IllegalStateException("Could not save this reminder.",error);
        }
    }

    public static synchronized void consume(Context context,int id) {
        try {
            JSONArray source=load(context),remaining=new JSONArray();
            for(int i=0;i<source.length();i++){
                JSONObject item=source.optJSONObject(i);
                if(item!=null&&item.optInt("id",-1)!=id)remaining.put(item);
            }
            save(context,remaining);
        } catch(Exception error) {
            SyncEventLogger.recordException(context,"ReminderScheduler","REMINDER_REMOVE_FAILED",error,"id="+id);
        }
    }

    public static synchronized void reschedulePending(Context context) {
        try {
            JSONArray all=load(context);
            long now=System.currentTimeMillis();
            for(int i=0;i<all.length();i++){
                JSONObject item=all.optJSONObject(i);if(item==null)continue;
                long when=item.optLong("when",now+1000L);
                if(when<=now)when=now+1000L;
                arm(context,item,when);
            }
            SyncEventLogger.record(context,"ReminderScheduler","REMINDERS_REARMED","INFO","count="+all.length());
        } catch(Exception error) {
            SyncEventLogger.recordException(context,"ReminderScheduler","REMINDERS_REARM_FAILED",error,"");
        }
    }

    private static void arm(Context context,JSONObject item,long when) {
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarms==null)throw new IllegalStateException("Android's alarm service is unavailable.");
        int id=item.optInt("id",1);
        Intent intent=new Intent(context,SyncNotificationReceiver.class);
        intent.putExtra("title",item.optString("title","Sync AI reminder"));
        intent.putExtra("text",item.optString("text","Your reminder is due."));
        intent.putExtra("id",id);
        intent.putExtra("persistent_reminder",true);
        PendingIntent pending=PendingIntent.getBroadcast(context,id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(Build.VERSION.SDK_INT>=31&&!alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending);
        }
    }

    private static JSONArray load(Context context) {
        SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try{return new JSONArray(prefs.getString(KEY_ITEMS,"[]"));}
        catch(Exception ignored){return new JSONArray();}
    }

    private static void save(Context context,JSONArray items) {
        context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putString(KEY_ITEMS,items.toString()).apply();
    }
}
