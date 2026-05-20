package com.example.indicpipeline.call.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.indicpipeline.R;
import com.example.indicpipeline.ui.call.CallActivity;
import com.example.indicpipeline.utils.NotificationManager;

/**
 * Foreground service that keeps the call alive and shows a persistent ongoing notification.
 */
public class CallForegroundService extends Service {
    private static final String TAG = "CallForegroundService";
    private static final int ONGOING_ID = 101;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager.createNotificationChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String callId = intent != null ? intent.getStringExtra("callId") : null;
        String roomId = intent != null ? intent.getStringExtra("roomId") : null;

        Intent openCall = new Intent(this, CallActivity.class);
        openCall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (callId != null) openCall.putExtra("callId", callId);
        if (roomId != null) openCall.putExtra("roomId", roomId);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getActivity(this, 0, openCall, pendingFlags);

        Notification notification = new NotificationCompat.Builder(this, "incoming_calls")
                .setContentTitle("Call in progress")
                .setContentText("Tap to return to call")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        try {
            startForeground(ONGOING_ID, notification);
            Log.i(TAG, "Foreground service started for callId=" + callId + " roomId=" + roomId);
            return START_STICKY;
        } catch (SecurityException se) {
            // Starting foreground failed due to missing privileged microphone FGS permission on newer Android.
            Log.e(TAG, "Failed to start foreground service due to security exception: " + se.getMessage(), se);
            // Stop service gracefully to avoid app crash and fallback to non-FGS behavior.
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "CallForegroundService destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}


