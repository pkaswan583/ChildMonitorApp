package com.childmonitor.services;

import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppUsageService extends Service {

    private static final long CHECK_INTERVAL = 5 * 60 * 1000;
    private Handler handler;
    private Runnable usageChecker;
    private DatabaseReference usageRef;
    private String currentForegroundApp = "";

    @Override
    public void onCreate() {
        super.onCreate();

        String deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");

        usageRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("app_usage");

        handler = new Handler(Looper.getMainLooper());
        startTracking();
    }

    private void startTracking() {
        usageChecker = new Runnable() {
            @Override
            public void run() {
                checkCurrentApp();
                trackDailyUsage();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(usageChecker);
    }

    private void checkCurrentApp() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();

        List<UsageStats> appList = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);

        if (appList != null && !appList.isEmpty()) {
            UsageStats recentApp = null;
            for (UsageStats usageStats : appList) {
                if (recentApp == null ||
                    usageStats.getLastTimeUsed() > recentApp.getLastTimeUsed()) {
                    recentApp = usageStats;
                }
            }
            if (recentApp != null) {
                String appPackage = recentApp.getPackageName();
                if (!appPackage.equals(currentForegroundApp)) {
                    currentForegroundApp = appPackage;
                    logAppOpen(appPackage);
                }
            }
        }
    }

    private void logAppOpen(String packageName) {
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("android.") ||
            packageName.equals("com.childmonitor")) {
            return;
        }

        String appName = getAppName(packageName);
        String timeKey = String.valueOf(System.currentTimeMillis());
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Map<String, Object> data = new HashMap<>();
        data.put("package", packageName);
        data.put("app_name", appName);
        data.put("opened_at", timeStr);
        data.put("date", dateStr);
        data.put("timestamp", System.currentTimeMillis());

        usageRef.child("opens").child(dateStr).child(timeKey).setValue(data);
        usageRef.child("current_app").setValue(data);
    }

    private void trackDailyUsage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (24 * 60 * 60 * 1000);

        List<UsageStats> stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (stats == null || stats.isEmpty()) return;

        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Map<String, Object> dailyData = new HashMap<>();

        for (UsageStats app : stats) {
            if (app.getTotalTimeInForeground() > 60000) {
                String appName = getAppName(app.getPackageName());
                long minutes = app.getTotalTimeInForeground() / 60000;

                Map<String, Object> appData = new HashMap<>();
                appData.put("app_name", appName);
                appData.put("package", app.getPackageName());
                appData.put("usage_minutes", minutes);
                appData.put("last_used", app.getLastTimeUsed());

                dailyData.put(app.getPackageName().replace(".", "_"), appData);
            }
        }

        usageRef.child("daily").child(dateStr).setValue(dailyData);
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(packageName, 0))
                    .toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && usageChecker != null) {
            handler.removeCallbacks(usageChecker);
        }
        startService(new Intent(this, AppUsageService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
