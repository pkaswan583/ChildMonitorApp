package com.childmonitor.services;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SurroundingSoundService extends Service {

    private static final int RECORDING_DURATION = 60 * 1000;
    private MediaRecorder recorder;
    private Handler handler;
    private boolean isRecording = false;
    private String deviceId;
    private DatabaseReference commandRef;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        deviceId = getSharedPreferences("cm_prefs", MODE_PRIVATE)
                .getString("device_id", "unknown");

        listenForRecordCommand();
    }

    private void listenForRecordCommand() {
        commandRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("commands")
                .child("record_sound");

        commandRef.addValueEventListener(
            new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(
                        com.google.firebase.database.DataSnapshot snapshot) {
                    Boolean shouldRecord = snapshot.getValue(Boolean.class);
                    if (shouldRecord != null && shouldRecord && !isRecording) {
                        startRecording();
                    } else if (shouldRecord != null && !shouldRecord && isRecording) {
                        stopRecording();
                    }
                }

                @Override
                public void onCancelled(
                        com.google.firebase.database.DatabaseError error) {}
            });
    }

    private void startRecording() {
        try {
            isRecording = true;

            String fileName = "sound_" + System.currentTimeMillis() + ".3gp";
            File outputFile = new File(getCacheDir(), fileName);

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            updateRecordingStatus(true);

            handler.postDelayed(() -> {
                stopRecordingAndUpload(outputFile);
            }, RECORDING_DURATION);

        } catch (Exception e) {
            isRecording = false;
        }
    }

    private void stopRecording() {
        if (recorder != null && isRecording) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
            } catch (Exception e) {
                // ignore
            }
            isRecording = false;
            updateRecordingStatus(false);
        }
    }

    private void stopRecordingAndUpload(File audioFile) {
        if (!isRecording) return;

        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
            isRecording = false;
            uploadAudioToFirebase(audioFile);
            commandRef.setValue(false);

        } catch (Exception e) {
            isRecording = false;
        }
    }

    private void uploadAudioToFirebase(File audioFile) {
        if (!audioFile.exists()) return;

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("devices")
                .child(deviceId)
                .child("recordings")
                .child(audioFile.getName());

        storageRef.putFile(android.net.Uri.fromFile(audioFile))
            .addOnSuccessListener(taskSnapshot -> {
                storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm",
                            Locale.getDefault()).format(new Date());

                    Map<String, Object> recordingData = new HashMap<>();
                    recordingData.put("url", uri.toString());
                    recordingData.put("timestamp", System.currentTimeMillis());
                    recordingData.put("date", dateStr);
                    recordingData.put("duration_sec", RECORDING_DURATION / 1000);

                    FirebaseDatabase.getInstance()
                            .getReference("devices")
                            .child(deviceId)
                            .child("recordings")
                            .child(String.valueOf(System.currentTimeMillis()))
                            .setValue(recordingData);

                    audioFile.delete();
                });
            })
            .addOnFailureListener(e -> audioFile.delete());
    }

    private void updateRecordingStatus(boolean recording) {
        FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId)
                .child("status")
                .child("is_recording")
                .setValue(recording);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    pub
