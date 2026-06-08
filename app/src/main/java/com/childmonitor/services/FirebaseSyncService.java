package com.childmonitor.services;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class FirebaseSyncService extends Service {

    private static final long HEARTBEAT_INTERVAL = 60 * 1000;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private DatabaseReference deviceRef;
    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();

        deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", generateDeviceId());

        getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .edit().putString("device_id", deviceId).apply();

        deviceRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId);

        handler = new Handler(Looper.getMainLooper());
        registerDevice();
        startHeartbeat();
    }

    private String generateDeviceId() {
        return android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
        );
    }

    private void registerDevice() {
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("device_model", android.os.Build.MODEL);
        deviceInfo.put("android_version", android.os.Build.VERSION.RELEASE);
        deviceInfo.put("app_version", "1.0");
        deviceInfo.put("registered_at", System.currentTimeMillis());
        deviceInfo.put("device_id", deviceId);
        deviceInfo.put("status", "active");

        deviceRef.child("info").setValue(deviceInfo);
    }

    private void startHeartbeat() {
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
                handler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        handler.post(heartbeatRunnable);
    }

    private void sendHeartbeat() {
        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("last_seen", System.currentTimeMillis());
        heartbeat.put("battery", getBatteryLevel());
        heartbeat.put("network", getNetworkType());
        heartbeat.put("online", isOnline());

        deviceRef.child("heartbeat").setValue(heartbeat);
    }

    private int getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) return -1;
        int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
        return (int) ((level / (float) scale) * 100);
    }

    private String getNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null) return "offline";
        return info.getTypeName();
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
        }
        startService(new Intent(this, FirebaseSyncService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
