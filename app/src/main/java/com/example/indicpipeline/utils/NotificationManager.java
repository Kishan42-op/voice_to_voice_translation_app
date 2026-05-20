package com.example.indicpipeline.utils;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Utility class for managing incoming call notifications.
 */
public class NotificationManager {
    private static final String CHANNEL_ID = "incoming_calls";
    private static final int NOTIFICATION_ID = 100;
    private static final String TAG = "NotificationMgr";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Incoming Calls";
            String description = "Notifications for incoming calls";
            int importance = android.app.NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // Set sound for incoming calls
            Uri soundUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
            channel.setSound(soundUri, audioAttributes);

            // Enable vibration
            channel.enableVibration(true);
            channel.setShowBadge(true);

            android.app.NotificationManager notificationManager =
                    context.getSystemService(android.app.NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static void showIncomingCallNotification(Context context, String fromName, String callId, String fromUid, String roomId) {
        // On Android 13+ ensure we have POST_NOTIFICATIONS permission before showing notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Do not attempt to show notification if permission is missing
                android.util.Log.w(TAG, "Missing POST_NOTIFICATIONS permission; skipping incoming call notification");
                return;
            }
        }

        createNotificationChannel(context);

        // Prepare full-screen intent to bring IncomingCallActivity to foreground
        Intent intent = new Intent(context, com.example.indicpipeline.ui.call.IncomingCallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("callId", callId);
        intent.putExtra("fromName", fromName);
        intent.putExtra("fromUid", fromUid);
        intent.putExtra("roomId", roomId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Incoming Call")
                .setContentText("Call from " + (fromName == null || fromName.isEmpty() ? "Unknown" : fromName))
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVibrate(new long[]{0, 500, 250, 500})
                .setContentIntent(fullScreenPendingIntent);

        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    public static void cancelIncomingCallNotification(Context context) {
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }
}

