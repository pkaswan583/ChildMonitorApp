package com.childmonitor.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class WebBlockerService extends AccessibilityService {

    private List<String> blockedDomains = new ArrayList<>();
    private DatabaseReference blockedRef;

    private static final String[] BROWSER_PACKAGES = {
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.UCMobile.intl"
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        info.packageNames = BROWSER_PACKAGES;
        setServiceInfo(info);

        loadBlockedDomains();
    }

    private void loadBlockedDomains() {
        String deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");

        blockedRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("blocked_sites");

        blockedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                blockedDomains.clear();
                for (DataSnapshot site : snapshot.getChildren()) {
                    String domain = site.getValue(String.class);
                    if (domain != null) {
                        blockedDomains.add(domain.toLowerCase());
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        blockedDomains.add("adult");
        blockedDomains.add("porn");
        blockedDomains.add("xxx");
        blockedDomains.add("18+");
        blockedDomains.add("gambling");
        blockedDomains.add("bet");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = String.valueOf(event.getPackageName());
        boolean isBrowser = false;
        for (String browser : BROWSER_PACKAGES) {
            if (browser.equals(packageName)) {
                isBrowser = true;
                break;
            }
        }
        if (!isBrowser) return;

        String url = extractUrl(event);
        if (url != null && !url.isEmpty()) {
            checkAndBlockUrl(url);
        }
    }

    private String extractUrl(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        List<AccessibilityNodeInfo> urlBars = root.findAccessibilityNodeInfosByViewId(
                "com.android.chrome:id/url_bar");

        if (urlBars == null || urlBars.isEmpty()) {
            urlBars = root.findAccessibilityNodeInfosByViewId(
                    "com.android.chrome:id/search_box_text");
        }

        if (urlBars != null && !urlBars.isEmpty()) {
            AccessibilityNodeInfo urlBar = urlBars.get(0);
            if (urlBar != null && urlBar.getText() != null) {
                return urlBar.getText().toString().toLowerCase();
            }
        }
        return null;
    }

    private void checkAndBlockUrl(String url) {
        for (String blocked : blockedDomains) {
            if (url.contains(blocked)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                performGlobalAction(GLOBAL_ACTION_HOME);
                logBlockedAttempt(url);
                return;
            }
        }
    }

    private void logBlockedAttempt(String url) {
        String deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");

        FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("blocked_attempts")
                .child(String.valueOf(System.currentTimeMillis()))
                .setValue(url);
    }

    @Override
    public void onInterrupt() {}
}
