package com.example.indicpipeline;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AudioRecorder {

    // Callback interface for streaming chunks
    public interface ChunkListener {
        void onChunkAvailable(short[] chunk);
    }

    private final Context ctx;
    private AudioRecord record;
    private Thread thread;
    private volatile boolean running = false;
    private ChunkListener chunkListener;

    private static final int SR = 16000;
    // 100ms chunks for better VAD responsiveness
    private static final int CHUNK_SIZE_SAMPLES = 1600; 

    public AudioRecorder(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void setChunkListener(ChunkListener listener) {
        this.chunkListener = listener;
    }

    public void start() {
        Log.i("AudioRecorder", "Starting AudioRecorder (100ms chunks)");
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("RECORD_AUDIO permission not granted");
        }

        int minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int internalBuffSize = Math.max(minBuf, CHUNK_SIZE_SAMPLES * 10);

        record = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SR,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                internalBuffSize
        );

        record.startRecording();
        running = true;

        thread = new Thread(() -> {
            short[] readBuffer = new short[CHUNK_SIZE_SAMPLES];

            while (running) {
                // Read exactly chunk size (blocking call)
                int result = readFully(record, readBuffer, CHUNK_SIZE_SAMPLES);

                if (result == CHUNK_SIZE_SAMPLES && running) {
                    // Debug: Check if we are getting actual audio
                    double rms = 0;
                    for (short s : readBuffer) rms += s * s;
                    rms = Math.sqrt(rms / CHUNK_SIZE_SAMPLES);
                    if (rms > 0) {
                        Log.v("AudioRecorder", "Chunk read success. RMS: " + (int)rms);
                    } else {
                        Log.w("AudioRecorder", "Chunk read SILENCE (RMS=0). Check mic access.");
                    }

                    // Send a copy to the listener so we don't overwrite it while it's being processed
                    short[] chunkCopy = new short[CHUNK_SIZE_SAMPLES];
                    System.arraycopy(readBuffer, 0, chunkCopy, 0, CHUNK_SIZE_SAMPLES);

                    if (chunkListener != null) {
                        chunkListener.onChunkAvailable(chunkCopy);
                    }
                }
            }
        });
        thread.start();
    }

    // Helper to ensure we get a full chunk
    private int readFully(AudioRecord record, short[] buffer, int length) {
        int totalRead = 0;
        while (totalRead < length && running) {
            int read = record.read(buffer, totalRead, length - totalRead);
            if (read < 0) break; // Error
            totalRead += read;
        }
        return totalRead;
    }

    public void stop() {
        running = false;
        if (record != null) {
            try {
                record.stop();
                record.release();
            } catch (Exception ignored) {}
            record = null;
        }
        if (thread != null) {
            try { thread.join(); } catch (Exception ignored) {}
            thread = null;
        }
    }
}


