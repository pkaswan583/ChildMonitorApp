package com.childmonitor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.childmonitor.services.AppUsageService;
import com.childmonitor.services.CallSmsService;
import com.childmonitor.services.DailyReportService;
import com.childmonitor.services.FirebaseSyncService;
import com.childmonitor.services.LocationService;
import com.childmonitor.services.SurroundingSoundService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            startAllServices(context);
        }
    }

    private void startAllServices(Context context) {
        Class<?>[] services = {
            LocationService.class,
            AppUsageService.class,
            CallSmsService.class,
            SurroundingSoundService.class,
            FirebaseSyncService.class,
            DailyReportService.class
        };

        for (Class<?> serviceClass : services) {
            Intent serviceIntent = new Intent(context, serviceClass);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
