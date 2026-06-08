package com.childmonitor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CallReceiver extends BroadcastReceiver {

    private static long callStartTime = 0;
    private static String lastNumber = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String deviceId = context.getSharedPreferences("cm_prefs", Context.MODE_PRIVATE)
                .getString("device_id", "unknown");

        DatabaseReference callRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("communications")
                .child("calls");

        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                lastNumber = number != null ? number : "Unknown";
                logCallEvent(callRef, lastNumber, "incoming_ringing");

            } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                callStartTime = System.currentTimeMillis();
                logCallEvent(callRef, lastNumber, "call_started");

            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                if (callStartTime > 0) {
                    int duration = (int) ((System.currentTimeMillis() - callStartTime) / 1000);
                    logCallEndEvent(callRef, lastNumber, duration);
                    callStartTime = 0;
                }
            }
        }

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            lastNumber = number != null ? number : "Unknown";
            logCallEvent(callRef, lastNumber, "outgoing");
        }
    }

    private void logCallEvent(DatabaseReference ref, String number, String type) {
        Map<String, Object> data = new HashMap<>();
        data.put("number", number);
        data.put("type", type);
        data.put("timestamp", System.currentTimeMillis());
        data.put("time_str", new SimpleDateFormat("HH:mm:ss",
                Locale.getDefault()).format(new Date()));

        ref.child(String.valueOf(System.currentTimeMillis())).setValue(data);
    }

    private void logCallEndEvent(DatabaseReference ref, String number, int duration) {
        Map<String, Object> data = new HashMap<>();
        data.put("number", number);
        data.put("type", "call_ended");
        data.put("duration_seconds", duration);
        data.put("timestamp", System.currentTimeMillis());
        data.put("time_str", new SimpleDateFormat("HH:mm:ss",
                Locale.getDefault()).format(new Date()));

        ref.child(String.valueOf(System.currentTimeMillis())).setValue(data);
    }
}
