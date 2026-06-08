package com.childmonitor.utils;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseHelper {

    private static boolean initialized = false;

    public static void initialize(Context context) {
        if (!initialized) {
            FirebaseApp.initializeApp(context);
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            initialized = true;
        }
    }

    public static String getDeviceRef(Context context) {
        return context.getSharedPreferences("cm_prefs", Context.MODE_PRIVATE)
                .getString("device_id", "unknown");
    }
}
