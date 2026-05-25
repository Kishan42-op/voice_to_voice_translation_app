package com.example.indicpipeline.call.manager;

import android.content.Context;
import android.util.Log;

import com.example.indicpipeline.AsrEngine;
import com.example.indicpipeline.AudioRecorder;
import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.OfflineTranslator;
import com.example.indicpipeline.TtsEngine;
import com.example.indicpipeline.language.LanguageCatalog;
import com.example.indicpipeline.models.GlobalModelManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ai.onnxruntime.OrtEnvironment;

import com.example.indicpipeline.IndicToUroman;

/**
 * Manages the AI translation pipeline during a call.
 * Orchestrates AudioRecorder -> AsrEngine -> Translator -> TtsEngine.
 */
public class CallPipelineManager {
    private static final String TAG = "PIPELINE";

    private final Context context;
    private AudioRecorder recorder;
    private AsrEngine asrEngine;
    private OfflineTranslator translator;
    private TtsEngine ttsEngine;
    private OrtEnvironment sharedEnv;
    private final GlobalModelManager modelManager = GlobalModelManager.getInstance();

    private BlockingQueue<short[]> audioQueue;
    private Thread pipelineThread;
    private volatile boolean isRunning = false;
    private volatile boolean isSpeaking = false;

    private short[] accumulatedAudio = new short[0];

    // Ultra-sensitive RMS thresholds for debugging
    private static final double SILENCE_RMS = 250.0;
    private static final double SPEECH_RMS_START = 500.0;
    private static final double SPEECH_RMS_CONTINUE = 350.0;
    private static final int END_SILENCE_CHUNKS = 2; // 200ms
    private static final int MIN_UTTERANCE_SAMPLES = 4800; // 300ms
    private static final long MIN_GAP_BETWEEN_UTTERANCES_MS = 300;

    private double sessionMaxRms = 0.0;
    private long chunksReceived = 0;

    private LangConfig localLang;
    private LangConfig remoteLang;

    public interface PipelineListener {
        void onLoadingProgress(String message);
        void onReady();
        void onLocalTranscription(String text);
        void onLocalTranslation(String text);
        void onTranslatedAudioReady(byte[] audioBytes);
        void onError(String message);
    }

    private PipelineListener listener;

    public CallPipelineManager(Context context) {
        this.context = context;
        this.audioQueue = new LinkedBlockingQueue<>();
        try {
            this.sharedEnv = OrtEnvironment.getEnvironment();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get OrtEnvironment", e);
        }
    }

    public void setListener(PipelineListener listener) {
        this.listener = listener;
    }

    public void initialize(LangConfig localLang, LangConfig remoteLang) {
        this.localLang = localLang;
        this.remoteLang = remoteLang;

        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                Log.i(TAG, "[PIPELINE] Warming engines for: " + localLang.name + " -> " + remoteLang.name);
                
                if (listener != null) listener.onLoadingProgress("Warming ASR (" + localLang.name + ")...");
                asrEngine = modelManager.getAsrEngine(context, localLang.asrCode);

                if (listener != null) listener.onLoadingProgress("Warming Translator...");
                translator = modelManager.getTranslator(context);
                
                if (listener != null) listener.onLoadingProgress("Warming TTS (" + remoteLang.name + ")...");
                ttsEngine = modelManager.getTtsEngine(context, remoteLang.ttsFolder);

                recorder = new AudioRecorder(context);
                
                long latency = System.currentTimeMillis() - t0;
                Log.i(TAG, "[PIPELINE] Warm-up complete in " + latency + "ms");
                
                if (listener != null) listener.onReady();
            } catch (Exception e) {
                Log.e(TAG, "[PIPELINE] Initialization failed", e);
                if (listener != null) listener.onError("AI Initialization Failed: " + e.getMessage());
            }
        }).start();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        isSpeaking = false;
        audioQueue.clear();
        accumulatedAudio = new short[0];
        chunksReceived = 0;
        sessionMaxRms = 0;

        recorder.setChunkListener(chunk -> {
            if (!isSpeaking) {
                if (chunk.length > 0) {
                    audioQueue.offer(chunk);
                }
            }
        });

        recorder.start();
        pipelineThread = new Thread(this::runPipelineLoop);
        pipelineThread.start();
        Log.i(TAG, "[PIPELINE] Voice Pipeline Loop started.");
    }

    public void stop() {
        isRunning = false;
        if (recorder != null) recorder.stop();
        if (pipelineThread != null) pipelineThread.interrupt();
        Log.i(TAG, "[PIPELINE] Stopped.");
    }

    private void runPipelineLoop() {
        boolean inUtterance = false;
        double maxRmsInUtterance = 0.0;
        int silenceChunks = 0;
        long lastAcceptedAtMs = 0;
        String lastAcceptedText = "";

        Log.i(TAG, "[PIPELINE] Entering processing loop. Sensitivity: " + SPEECH_RMS_START);

        while (isRunning) {
            try {
                short[] chunk = audioQueue.take();
                chunksReceived++;
                
                double rms = calculateRMS(chunk);
                sessionMaxRms = Math.max(sessionMaxRms, rms);

                // Periodic status log
                if (chunksReceived % 50 == 0) {
                    Log.d(TAG, "[PIPELINE] Mic Active. Chunks: " + chunksReceived + ". Current RMS: " + (int)rms + ". Session Max: " + (int)sessionMaxRms);
                }

                if (!inUtterance) {
                    if (rms >= SPEECH_RMS_START) {
                        Log.i(TAG, "[PIPELINE] SPEECH DETECTED! RMS: " + (int)rms);
                        inUtterance = true;
                        silenceChunks = 0;
                        maxRmsInUtterance = rms;
                        accumulatedAudio = chunk;
                    }
                } else {
                    if (rms < SILENCE_RMS) {
                        silenceChunks++;
                        if (silenceChunks >= END_SILENCE_CHUNKS) {
                            Log.i(TAG, "[PIPELINE] SILENCE DETECTED. Finalizing utterance. Length: " + accumulatedAudio.length);
                            short[] audioToProcess = accumulatedAudio;
                            accumulatedAudio = new short[0];
                            inUtterance = false;
                            silenceChunks = 0;

                            if (audioToProcess.length < MIN_UTTERANCE_SAMPLES || maxRmsInUtterance < SPEECH_RMS_START) {
                                Log.d(TAG, "[PIPELINE] Utterance discarded: too short or quiet.");
                                continue;
                            }
                            
                            long now = System.currentTimeMillis();
                            if (now - lastAcceptedAtMs < MIN_GAP_BETWEEN_UTTERANCES_MS) {
                                continue;
                            }

                            processUtterance(audioToProcess, now, lastAcceptedText);
                        }
                    } else {
                        if (rms >= SPEECH_RMS_CONTINUE || rms >= SPEECH_RMS_START) {
                            maxRmsInUtterance = Math.max(maxRmsInUtterance, rms);
                        }
                        silenceChunks = 0;
                        accumulatedAudio = appendAudio(accumulatedAudio, chunk);
                        
                        if (accumulatedAudio.length > 16000 * 10) { // Limit to 10s
                            Log.w(TAG, "[PIPELINE] Utterance timeout (10s). Processing.");
                            inUtterance = false;
                            processUtterance(accumulatedAudio, System.currentTimeMillis(), lastAcceptedText);
                            accumulatedAudio = new short[0];
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "[PIPELINE] Loop Error", e);
            }
        }
    }

    private void processUtterance(short[] audio, long now, String lastText) {
        try {
            long tStart = System.currentTimeMillis();
            Log.i(TAG, "[PIPELINE] --- START PROCESSING ---");

            // 1. ASR
            long t0 = System.currentTimeMillis();
            AsrEngine.Result asrRes = asrEngine.transcribe(audio, localLang.asrCode);
            long asrTime = System.currentTimeMillis() - t0;
            String heard = asrRes.text == null ? "" : asrRes.text.trim();

            if (heard.isEmpty() || isLikelyFalsePositive(heard)) {
                Log.d(TAG, "[ASR] recognized nothing or false positive: '" + heard + "'");
                return;
            }

            Log.i(TAG, "[ASR] recognized: " + heard + " (took " + asrTime + "ms)");
            if (listener != null) listener.onLocalTranscription(heard);

            // 2. TRANSLATE
            long t1 = System.currentTimeMillis();
            Log.i(TAG, "[TRANSLATE] Input: " + heard);
            String translated = translator.translate(heard, localLang.transCode, remoteLang.transCode);
            long transTime = System.currentTimeMillis() - t1;
            
            if (translated == null || translated.trim().isEmpty()) {
                Log.e(TAG, "[TRANSLATE] FAILED: Empty result");
                return;
            }
            Log.i(TAG, "[TRANSLATE] Output (" + remoteLang.name + "): " + translated + " (took " + transTime + "ms)");
            if (listener != null) listener.onLocalTranslation(translated);

            // 3. TRANSLITERATE (FOR TTS)
            // Most TTS models expect romanized input (uroman)
            Log.d(TAG, "[TTS] Transliterating to uroman...");
            String uroman = IndicToUroman.transliterate(translated, remoteLang.transCode);
            Log.i(TAG, "[TTS] Uroman text: " + uroman);

            // 4. TTS
            isSpeaking = true;
            try {
                long t2 = System.currentTimeMillis();
                Log.i(TAG, "[TTS] synthesis started for: " + translated);
                TtsEngine.TtsResult result = ttsEngine.synthesizeToFile(uroman);
                long ttsTime = System.currentTimeMillis() - t2;
                
                if (result != null && result.audioData != null && result.audioData.length > 0) {
                    byte[] audioBytes = floatToByte(result.audioData);
                    Log.i(TAG, "[TTS] pcm bytes generated: " + audioBytes.length + " (took " + ttsTime + "ms)");
                    
                    // 5. SEND
                    if (listener != null) {
                        Log.i(TAG, "[PIPELINE] sending translated audio chunk (" + audioBytes.length + " bytes)");
                        listener.onTranslatedAudioReady(audioBytes);
                    }
                } else {
                    Log.e(TAG, "[TTS] FAILED: No audio generated for text: " + uroman);
                }
                
                long totalTime = System.currentTimeMillis() - tStart;
                Log.i(TAG, "[PIPELINE] End-to-end latency: " + totalTime + "ms");
            } finally {
                isSpeaking = false;
            }

        } catch (Exception e) {
            Log.e(TAG, "[PIPELINE] Utterance Error", e);
        }
    }

    private byte[] floatToByte(float[] floatArray) {
        byte[] byteArray = new byte[floatArray.length * 4];
        java.nio.ByteBuffer.wrap(byteArray).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(floatArray);
        return byteArray;
    }

    private boolean isLikelyFalsePositive(String text) {
        return text.length() < 2 || text.equalsIgnoreCase("you") || text.equalsIgnoreCase("thank you");
    }

    private double calculateRMS(short[] buffer) {
        double sum = 0;
        for (short s : buffer) sum += s * s;
        return Math.sqrt(sum / buffer.length);
    }

    private short[] combineChunks(List<short[]> chunks) {
        int total = 0;
        for (short[] c : chunks) total += c.length;
        short[] res = new short[total];
        int pos = 0;
        for (short[] c : chunks) {
            System.arraycopy(c, 0, res, pos, c.length);
            pos += c.length;
        }
        return res;
    }

    private short[] appendAudio(short[] base, short[] extra) {
        short[] res = new short[base.length + extra.length];
        System.arraycopy(base, 0, res, 0, base.length);
        System.arraycopy(extra, 0, res, base.length, extra.length);
        return res;
    }
}
