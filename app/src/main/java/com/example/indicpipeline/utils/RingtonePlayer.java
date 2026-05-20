package com.example.indicpipeline.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simple ringtone/ringback player utility.
 */
public class RingtonePlayer {
    private static final String TAG = "RingtonePlayer";
    private Ringtone ringtone;
    private ToneGenerator ringbackTone;
    private final ScheduledExecutorService ringbackScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> ringbackFuture;

    public void playRingtone(Context context) {
        try {
            releaseRingbackTone();
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(context, alert);
            if (ringtone != null && !ringtone.isPlaying()) {
                ringtone.play();
                Log.i(TAG, "Playing ringtone");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to play ringtone", e);
        }
    }

    public void playRingback(Context context) {
        try {
            stop();
            ringbackTone = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 85);
            ringbackFuture = ringbackScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (ringbackTone != null) {
                        // A short repeating network-style ringback tone, not the device ringtone.
                        ringbackTone.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Ringback tone play failed", e);
                }
            }, 0, 3000, TimeUnit.MILLISECONDS);
            Log.i(TAG, "Playing ringback tone");
        } catch (Exception e) {
            Log.w(TAG, "Failed to play ringback", e);
            releaseRingbackTone();
        }
    }

    public void stop() {
        try {
            if (ringtone != null && ringtone.isPlaying()) {
                ringtone.stop();
            }
        } catch (Exception ignored) {}
        releaseRingbackTone();
    }

    private void releaseRingbackTone() {
        try {
            if (ringbackFuture != null) {
                ringbackFuture.cancel(false);
                ringbackFuture = null;
            }
        } catch (Exception ignored) {}
        try {
            if (ringbackTone != null) {
                ringbackTone.release();
            }
        } catch (Exception ignored) {}
        ringbackTone = null;
    }
}



