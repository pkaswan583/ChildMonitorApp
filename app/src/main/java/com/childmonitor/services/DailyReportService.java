package com.childmonitor.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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

        deviceRef.child("app_usage").child("daily").child(today)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot usageSnapshot) {
                        deviceRef.child("location").child("history")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot locationSnapshot) {
                                        Map<String, Object> report = new HashMap<>();
                                        report.put("date", today);
                                        report.put("generated_at", System.currentTimeMillis());

                                        Map<String, Object> appSummary = new HashMap<>();
                                        if (usageSnapshot.exists()) {
                                            int totalApps = (int) usageSnapshot.getChildrenCount();
                                            appSummary.put("total_apps_used", totalApps);
                                            long totalMinutes = 0;
                                            for (DataSnapshot app : usageSnapshot.getChildren()) {
                                                Long mins = app.child("usage_minutes")
                                                        .getValue(Long.class);
                                                if (mins != null) totalMinutes += mins;
                                            }
                                            appSummary.put("total_screen_time_minutes", totalMinutes);
                                        }
                                        report.put("app_usage", appSummary);
                                        report.put("location_points_logged",
                                                locationSnapshot.getChildrenCount());

                                        deviceRef.child("daily_reports")
                                                .child(today)
                                                .setValue(report);

                                        sendReportNotification(today);
                                    }

                                    @Override
