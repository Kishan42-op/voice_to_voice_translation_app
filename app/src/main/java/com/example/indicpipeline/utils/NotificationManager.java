package com.example.indicpipeline.utils;

import android.app.NotificationChannel;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;

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
            // Enable sound for incoming calls
            channel.enableVibration(true);
            channel.setShowBadge(true);

            android.app.NotificationManager notificationManager =
                    context.getSystemService(android.app.NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static void showIncomingCallNotification(Context context, String fromName, String callId) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Incoming Call")
                .setContentText("Call from " + (fromName == null || fromName.isEmpty() ? "Unknown" : fromName))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(null, false)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVibrate(new long[]{0, 500, 250, 500})
                .setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI);

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

