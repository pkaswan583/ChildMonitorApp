package com.childmonitor.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DailyReportService extends Service {

    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");
        scheduleDailyReport();
    }

    private void scheduleDailyReport() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailyReportService.class);
        intent.setAction("GENERATE_REPORT");
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (alarmManager != null) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "GENERATE_REPORT".equals(intent.getAction())) {
            generateDailyReport();
        }
        return START_STICKY;
    }

    private void generateDailyReport() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
        DatabaseReference deviceRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId);
        Map<String, Object> report = new HashMap<>();
        report.put("date", today);
        report.put("generated_at", System.currentTimeMillis());
        report.put("status", "generated");
        deviceRef.child("daily_reports").child(today).setValue(report);
        sendReportNotification(today);
    }

    private void sendReportNotification(String date) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "daily_report");
        notification.put("date", date);
        notification.put("timestamp", System.currentTimeMillis());
        FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(deviceId)
                .push()
                .setValue(notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
