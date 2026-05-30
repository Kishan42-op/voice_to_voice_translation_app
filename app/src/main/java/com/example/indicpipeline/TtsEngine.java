package com.example.indicpipeline;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class TtsEngine {
    private static final String TAG = "TTS_DEBUG";
    // Many Indic VITS models use 22050 Hz. If 16000 Hz sounds like 'slow noise', 
    // 22050 Hz is the correct rate.
    private static final int SAMPLE_RATE_HZ = 22050;

    private final OrtEnvironment env;
    private final AudioManager audioManager;
    private OrtSession session;
    private final Map<Character, Long> vocab = new HashMap<>();

    public TtsEngine(Context context, OrtEnvironment sharedEnv, String folderCode) throws Exception {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (sharedEnv == null) {
            this.env = OrtEnvironment.getEnvironment();
        } else {
            this.env = sharedEnv;
        }

        String ttsBase = "tts/" + folderCode;
        Log.i(TAG, "[INIT] Loading TTS from: " + ttsBase + ", folderCode=" + folderCode);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(ttsBase + "/tokens.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    String token = parts[0];
                    long id = Long.parseLong(parts[1]);
                    if (token.equals("|")) {
                        vocab.put(' ', id);
                    } else if (token.length() == 1) {
                        vocab.put(token.charAt(0), id);
                    }
                }
            }
        }
        Log.i(TAG, "[INIT] Loaded vocab entries: " + vocab.size());

        String modelName = "tts_model_" + folderCode + ".onnx";
        File tempModelFile = new File(context.getCacheDir(), modelName);
        if (!tempModelFile.exists()) {
            Log.i(TAG, "[INIT] Extracting TTS model to cache: " + modelName);
            try (InputStream is = context.getAssets().open(ttsBase + "/model.onnx");
                 FileOutputStream fos = new FileOutputStream(tempModelFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
        }

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
        session = env.createSession(tempModelFile.getAbsolutePath(), options);
        Log.i(TAG, "[INIT] TTS Session created for " + folderCode);
    }

    public long speak(String uromanText) throws Exception {
        String cleanText = uromanText.replaceAll("[\\n\\t\\r]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        Log.i(TAG, "[SPEAK] Requested text: [" + cleanText + "]");

        List<Long> ids = new ArrayList<>();
        for (char c : cleanText.toCharArray()) {
            if (vocab.containsKey(c)) {
                ids.add(vocab.get(c));
            } else {
                ids.add(vocab.getOrDefault(' ', 0L));
            }
        }

        List<Long> interspersed = new ArrayList<>();
        interspersed.add(0L);
        for (long id : ids) {
            interspersed.add(id);
            interspersed.add(0L);
        }

        long[] inputArray = new long[interspersed.size()];
        for (int i = 0; i < interspersed.size(); i++) {
            inputArray[i] = interspersed.get(i);
        }

        if (env == null || session == null) return 0;

        long[] shape = new long[]{1, inputArray.length};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputArray), shape);
        OnnxTensor lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{inputArray.length}), new long[]{1});
        OnnxTensor noiseScale = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{0.667f}), new long[]{1});
        OnnxTensor lengthScale = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{1.0f}), new long[]{1});
        OnnxTensor noiseScaleW = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{0.8f}), new long[]{1});

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("x", inputTensor);
        inputs.put("x_length", lengthTensor);
        inputs.put("noise_scale", noiseScale);
        inputs.put("length_scale", lengthScale);
        inputs.put("noise_scale_w", noiseScaleW);

        long startTime = System.currentTimeMillis();
        OrtSession.Result result = session.run(inputs);

        OnnxTensor rawOutput = (OnnxTensor) result.get(0);
        FloatBuffer floatBuffer = rawOutput.getFloatBuffer();
        float[] audioArray = new float[floatBuffer.remaining()];
        floatBuffer.get(audioArray);

        Log.i(TAG, "[SPEAK] Inference complete. audioSamples=" + audioArray.length);

        long generationTime = System.currentTimeMillis() - startTime;

        playAudio(audioArray);

        result.close();
        inputTensor.close();
        lengthTensor.close();
        noiseScale.close();
        lengthScale.close();
        noiseScaleW.close();

        return generationTime;
    }

    private void playAudio(float[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "[TTS] No audio data to play.");
            return;
        }
        
        logAudioState("play-before-start");

        // 1. Peak Normalization: Ensure audio is always loud but never clipping.
        // This fixes 'quiet noise' caused by low amplitude model output.
        float maxAbsRaw = 0f;
        for (float f : audioData) {
            float abs = Math.abs(f);
            if (abs > maxAbsRaw) maxAbsRaw = abs;
        }
        
        float targetPeak = 0.9f;
        float normalizationFactor = (maxAbsRaw > 0) ? (targetPeak / maxAbsRaw) : 1.0f;
        // Limit normalization factor to avoid extreme gain on silence
        if (normalizationFactor > 10.0f) normalizationFactor = 10.0f;

        short[] pcm16 = new short[audioData.length];
        float maxAbsFinal = 0f;

        for (int i = 0; i < audioData.length; i++) {
            float f = audioData[i] * normalizationFactor;
            // Strict clamping
            if (f > 1.0f) f = 1.0f;
            if (f < -1.0f) f = -1.0f;
            pcm16[i] = (short) (f * 32767.0f);
            
            float abs = Math.abs(f);
            if (abs > maxAbsFinal) maxAbsFinal = abs;
        }

        Log.i(TAG, "[TTS] Audio data normalized: rawMax=" + maxAbsRaw + " factor=" + normalizationFactor + " finalMax=" + maxAbsFinal);
        
        if (maxAbsFinal < 0.001f) {
            Log.w(TAG, "[TTS] WARNING: Audio data is effectively SILENT.");
            return;
        }

        int sampleRate = SAMPLE_RATE_HZ;
        int minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        
        try {
            AudioTrack audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(minBufSize, 32000)) 
                    .build();

            try {
                audioTrack.play();
                audioTrack.setVolume(1.0f);
                
                int written = audioTrack.write(pcm16, 0, pcm16.length, AudioTrack.WRITE_BLOCKING);
                Log.i(TAG, "[TTS] Audio written (pcm16): " + written + " / " + pcm16.length);
                
                audioTrack.stop();
                
                long durationMs = (long) ((pcm16.length / (float) SAMPLE_RATE_HZ) * 1000);
                Log.d(TAG, "[TTS] Playback duration estimate: " + durationMs + "ms");
                Thread.sleep(durationMs + 100); 
                
                Log.i(TAG, "[TTS] Playback finished.");
            } finally {
                audioTrack.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "[TTS] Playback failed: " + e.getMessage(), e);
        }
    }

    private void logAudioState(String stage) {
        if (audioManager == null) return;
        try {
            int mode = audioManager.getMode();
            boolean speaker = audioManager.isSpeakerphoneOn();
            int musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int callVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            Log.i(TAG, "[" + stage + "] mode=" + mode + " speaker=" + speaker + " musicVol=" + musicVolume + " callVol=" + callVolume);
        } catch (Exception e) {
            Log.w(TAG, "[" + stage + "] Audio state log failed: " + e.getMessage());
        }
    }

    public void close() throws Exception {
        if (session != null) {
            session.close();
        }
    }

    public static class TtsResult {
        public long timeMs;
        public float[] audioData;
        public TtsResult(long t, float[] a) { timeMs = t; audioData = a; }
    }

    public TtsResult synthesizeToFile(String rawText) throws Exception {
        String cleanText = rawText.replaceAll("[\\n\\t\\r]", " ").replaceAll("\\s+", " ").trim().toLowerCase();
        List<Long> ids = new ArrayList<>();
        for (char c : cleanText.toCharArray()) {
            if (vocab.containsKey(c)) ids.add(vocab.get(c));
            else ids.add(vocab.getOrDefault(' ', 0L));
        }
        List<Long> interspersed = new ArrayList<>();
        interspersed.add(0L);
        for (long id : ids) {
            interspersed.add(id);
            interspersed.add(0L);
        }
        long[] inputArray = new long[interspersed.size()];
        for (int i = 0; i < interspersed.size(); i++) inputArray[i] = interspersed.get(i);
        if (env == null || session == null) return null;
        long[] shape = new long[]{1, inputArray.length};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputArray), shape);
        OnnxTensor lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{inputArray.length}), new long[]{1});
        OnnxTensor noiseScale = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{0.667f}), new long[]{1});
        OnnxTensor lengthScale = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{1.0f}), new long[]{1});
        OnnxTensor noiseScaleW = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{0.8f}), new long[]{1});
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("x", inputTensor);
        inputs.put("x_length", lengthTensor);
        inputs.put("noise_scale", noiseScale);
        inputs.put("length_scale", lengthScale);
        inputs.put("noise_scale_w", noiseScaleW);
        long startTime = System.currentTimeMillis();
        OrtSession.Result result = session.run(inputs);
        long generationTime = System.currentTimeMillis() - startTime;
        OnnxTensor rawOutput = (OnnxTensor) result.get(0);
        FloatBuffer floatBuffer = rawOutput.getFloatBuffer();
        float[] audioArray = new float[floatBuffer.remaining()];
        floatBuffer.get(audioArray);
        result.close();
        inputTensor.close(); lengthTensor.close(); noiseScale.close(); lengthScale.close(); noiseScaleW.close();
        return new TtsResult(generationTime, audioArray);
    }
}
