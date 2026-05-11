package com.example.indicpipeline;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ai.onnxruntime.OrtEnvironment;

public class MainActivity extends AppCompatActivity {

    private AudioRecorder recorder;
    private AsrEngine asrEngine;
    private OfflineTranslator translator;
    private TtsEngine ttsEngine;
    private OrtEnvironment sharedEnv;

    private Spinner myLanguageSpinner;
    private Button btnCall, btnEnd, btnBenchmark;
    private TextView tvSystemStatus, tvAsrOutput, tvTransOutput;
    private SwitchCompat switchInternetCall;
    private EditText etRoomId, etUserId;

    // Hardcoded config so users only enter Room + User ID
    private static final String LIVEKIT_URL = "wss://indicpipelineapp-0vui3jrn.livekit.cloud";
    // Vercel serverless base. The app appends "/token".
    private static final String TOKEN_SERVER_BASE_URL = "https://call-server-x3ug.vercel.app/api";

    // Benchmark Progress UI
    private LinearLayout layoutBenchmarkProgress;
    private TextView tvBenchmarkStatus;
    private ProgressBar pbBenchmark;

    // Timing TextViews
    private TextView tvAsrTime, tvTransTime, tvTtsTime, tvTotalTime;

    private LinearLayout layoutLoading;

    private BlockingQueue<short[]> audioQueue;
    private Thread pipelineThread;
    private volatile boolean inCall = false;

    private volatile boolean isSpeaking = false;
    private boolean isInitialBoot = true;

    private short[] accumulatedAudio = new short[0];
    // RMS thresholds depend heavily on device + mic gain. These defaults are tuned to avoid
    // "random words" hallucinations while still triggering on normal speech.
    private static final double SILENCE_RMS = 750.0;
    // Chunk size is 1 second (see AudioRecorder). We use simple RMS-based VAD with hysteresis
    // to avoid ASR hallucinations on background noise.
    private static final double SPEECH_RMS_START = 1200.0;
    private static final double SPEECH_RMS_CONTINUE = 900.0;
    private static final int END_SILENCE_CHUNKS = 1; // 1s of silence ends an utterance
    private static final int MIN_UTTERANCE_SAMPLES = 16000; // 1s @ 16kHz
    private static final long MIN_GAP_BETWEEN_UTTERANCES_MS = 600;

    private List<LangConfig> languages;

    private LiveKitInternetCallClient liveKitClient;
    private final ExecutorService incomingExecutor = Executors.newSingleThreadExecutor();
    private volatile String liveKitSelfUserId = "me";
    private final ExecutorService tokenExecutor = Executors.newSingleThreadExecutor();
    private final TokenServerClient tokenServerClient = new TokenServerClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        myLanguageSpinner = findViewById(R.id.myLanguageSpinner);
        btnCall = findViewById(R.id.btnCall);
        btnEnd = findViewById(R.id.btnEnd);
        btnBenchmark = findViewById(R.id.btnBenchmark);
        tvSystemStatus = findViewById(R.id.tvSystemStatus);
        tvAsrOutput = findViewById(R.id.tvAsrOutput);
        tvTransOutput = findViewById(R.id.tvTransOutput);
        layoutLoading = findViewById(R.id.layoutLoading);
        switchInternetCall = findViewById(R.id.switchInternetCall);
        etRoomId = findViewById(R.id.etRoomId);
        etUserId = findViewById(R.id.etUserId);

        // Setup Progress UI
        layoutBenchmarkProgress = findViewById(R.id.layoutBenchmarkProgress);
        tvBenchmarkStatus = findViewById(R.id.tvBenchmarkStatus);
        pbBenchmark = findViewById(R.id.pbBenchmark);

        tvAsrTime = findViewById(R.id.tvAsrTime);
        tvTransTime = findViewById(R.id.tvTransTime);
        tvTtsTime = findViewById(R.id.tvTtsTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);

        btnCall.setEnabled(false);
        btnEnd.setEnabled(false);
        btnBenchmark.setEnabled(false);

        setupLanguages();
        recorder = new AudioRecorder(this);
        audioQueue = new LinkedBlockingQueue<>();

        liveKitClient = new LiveKitInternetCallClient(getApplicationContext(), new LiveKitInternetCallClient.Listener() {
            @Override
            public void onConnected() {
                // LiveKit/WebRTC may default to speakerphone; force earpiece to reduce feedback.
                applyInCallAudioRoute(false);
                runOnUiThread(() -> tvSystemStatus.setText("Internet Call Connected. Listening..."));
            }

            @Override
            public void onDisconnected(String reason) {
                // Ensure we always restore audio routing even if disconnect happens remotely/unexpectedly.
                resetAudioRoute();
                runOnUiThread(() -> tvSystemStatus.setText("Internet Call Disconnected: " + (reason == null ? "" : reason)));
            }

            @Override
            public void onIncomingSpeech(String fromUser, String text, String srcTransCode) {
                incomingExecutor.execute(() -> handleIncomingSpeech(fromUser, text, srcTransCode));
            }

            @Override
            public void onIncomingInfo(String message) {
                runOnUiThread(() -> Log.i("INTERNET_CALL", message));
            }
        });

        try {
            sharedEnv = OrtEnvironment.getEnvironment();
        } catch (Exception e) { e.printStackTrace(); }

        bootAllModels();

        myLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialBoot) return;
                swapTtsModel(languages.get(position).ttsFolder);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnCall.setOnClickListener(v -> startCall());
        btnEnd.setOnClickListener(v -> endCall());

        // NEW: Benchmark Button Click
        btnBenchmark.setOnClickListener(v -> startBenchmark());
    }

    private void setupLanguages() {
        languages = new ArrayList<>();
        languages.add(new LangConfig("Hindi", "hi", "hin_Deva", "hin"));
        languages.add(new LangConfig("Gujarati", "gu", "guj_Gujr", "guj"));
        languages.add(new LangConfig("Marathi", "mr", "mar_Deva", "mar"));
        languages.add(new LangConfig("Bengali", "bn", "ben_Beng", "ben"));
        languages.add(new LangConfig("Tamil", "ta", "tam_Taml", "tam"));
        languages.add(new LangConfig("Telugu", "te", "tel_Telu", "tel"));
        languages.add(new LangConfig("Kannada", "kn", "kan_Knda", "kan"));
        languages.add(new LangConfig("Malayalam", "ml", "mal_Mlym", "mal"));
        languages.add(new LangConfig("Odia", "or", "ory_Orya", "ory"));
        languages.add(new LangConfig("Punjabi", "pa", "pan_Guru", "pan"));
        languages.add(new LangConfig("Assamese", "as", "asm_Beng", "asm"));

        ArrayAdapter<LangConfig> adapter = new ArrayAdapter<>(this, R.layout.spinner_language_item, languages);
        adapter.setDropDownViewResource(R.layout.spinner_language_dropdown_item);
        myLanguageSpinner.setAdapter(adapter);
        myLanguageSpinner.setSelection(0);
    }

    private void bootAllModels() {
        btnCall.setEnabled(false);
        layoutLoading.setVisibility(View.VISIBLE);
        tvSystemStatus.setText("Booting Pipeline...");

        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                runOnUiThread(() -> tvSystemStatus.setText("Loading ASR Engine..."));
                asrEngine = new AsrEngine(this, sharedEnv);

                runOnUiThread(() -> tvSystemStatus.setText("Loading Translation Engine..."));
                translator = new OfflineTranslator(this, sharedEnv);

                runOnUiThread(() -> tvSystemStatus.setText("Loading TTS Voice..."));
                LangConfig myLang = (LangConfig) myLanguageSpinner.getSelectedItem();
                ttsEngine = new TtsEngine(this, null, myLang.ttsFolder);

                long t1 = System.currentTimeMillis();
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    tvSystemStatus.setText("Ready! (Loaded in " + (t1 - t0) / 1000 + "s)");
                    btnCall.setEnabled(true);
                    btnBenchmark.setEnabled(true); // Enable Benchmark Button
                    isInitialBoot = false;
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> tvSystemStatus.setText("Error loading models!"));
            }
        }).start();
    }

    private void swapTtsModel(String folderName) {
        layoutLoading.setVisibility(View.VISIBLE);
        btnCall.setEnabled(false);
        tvSystemStatus.setText("Swapping TTS Voice...");

        new Thread(() -> {
            try {
                if (ttsEngine != null) ttsEngine.close();
                ttsEngine = new TtsEngine(this, null, folderName);
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    tvSystemStatus.setText("System Ready.");
                    btnCall.setEnabled(true);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // --- NORMAL MICROPHONE CALL LOGIC ---
    private void startCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }

        if (switchInternetCall != null && switchInternetCall.isChecked()) {
            startInternetCall();
            return;
        }

        inCall = true;
        isSpeaking = false;
        btnCall.setEnabled(false);
        btnEnd.setEnabled(true);
        btnBenchmark.setEnabled(false); // Disable benchmark during a live call
        audioQueue.clear();
        accumulatedAudio = new short[0];

        tvAsrTime.setText("ASR Time: -- ms");
        tvTransTime.setText("Translation Time: -- ms");
        tvTtsTime.setText("TTS Processing Time: -- ms");
        tvTotalTime.setText("Total AI Processing: -- ms");
        tvSystemStatus.setText("Listening...");

        LangConfig myLang = (LangConfig) myLanguageSpinner.getSelectedItem();

        recorder.setChunkListener(chunk -> {
            if (!isSpeaking) {
                audioQueue.offer(chunk);
            }
        });

        recorder.start();

        pipelineThread = new Thread(() -> runPipelineLoop(myLang));
        pipelineThread.start();
    }

    private void runPipelineLoop(LangConfig myLang) {
        boolean inUtterance = false;
        double maxRms = 0.0;
        int silenceChunks = 0;
        long lastAcceptedAtMs = 0;
        String lastAcceptedText = "";

        while (inCall) {
            try {
                short[] chunk = audioQueue.take();
                List<short[]> pending = new ArrayList<>();
                pending.add(chunk);
                audioQueue.drainTo(pending);
                short[] combinedNew = combineChunks(pending);

                double rms = calculateRMS(combinedNew);

                if (!inUtterance) {
                    if (rms >= SPEECH_RMS_START) {
                        inUtterance = true;
                        silenceChunks = 0;
                        maxRms = rms;
                        accumulatedAudio = combinedNew;
                    } else {
                        // Ignore background noise
                        continue;
                    }
                } else {
                    if (rms < SILENCE_RMS) {
                        silenceChunks++;
                        if (silenceChunks >= END_SILENCE_CHUNKS) {
                            short[] audioToProcess = accumulatedAudio;
                            accumulatedAudio = new short[0];
                            inUtterance = false;
                            silenceChunks = 0;

                            if (audioToProcess.length < MIN_UTTERANCE_SAMPLES || maxRms < SPEECH_RMS_START) {
                                continue;
                            }
                            long now = System.currentTimeMillis();
                            if (now - lastAcceptedAtMs < MIN_GAP_BETWEEN_UTTERANCES_MS) {
                                continue;
                            }

                            runOnUiThread(() -> tvSystemStatus.setText("Thinking..."));

                            long asrStart = System.currentTimeMillis();
                            AsrEngine.Result asrRes = asrEngine.transcribe(audioToProcess, myLang.asrCode);
                            long asrTime = System.currentTimeMillis() - asrStart;

                            String heard = asrRes.text == null ? "" : asrRes.text.trim();
                            if (isLikelyFalsePositiveTranscript(heard) || isNearDuplicate(heard, lastAcceptedText)) {
                                runOnUiThread(() -> {
                                    tvAsrTime.setText("ASR Time: " + asrTime + " ms");
                                    tvSystemStatus.setText("Listening...");
                                });
                                continue;
                            }

                            lastAcceptedAtMs = now;
                            lastAcceptedText = heard;
                            runOnUiThread(() -> tvAsrOutput.setText("Heard: " + heard));

                            long transStart = System.currentTimeMillis();
                            // Single-language UI: translate into your own language (often same as spoken).
                            String translatedStr = translator.translate(heard, myLang.transCode, myLang.transCode);
                            long transTime = System.currentTimeMillis() - transStart;

                            runOnUiThread(() -> tvTransOutput.setText("Translated: " + translatedStr));

                            runOnUiThread(() -> tvSystemStatus.setText("Speaking..."));
                            isSpeaking = true;
                            audioQueue.clear();

                            long ttsTime = 0;
                            try {
                                ttsTime = ttsEngine.speak(translatedStr);
                            } catch (Exception ex) {
                                Log.e("PIPELINE_ERROR", "TTS failed: " + ex.getMessage());
                            } finally {
                                isSpeaking = false;
                            }

                            long finalTtsTime = ttsTime;
                            long totalTime = asrTime + transTime + ttsTime;

                            runOnUiThread(() -> {
                                tvAsrTime.setText("ASR Time: " + asrTime + " ms");
                                tvTransTime.setText("Translation Time: " + transTime + " ms");
                                tvTtsTime.setText("TTS Processing Time: " + finalTtsTime + " ms");
                                tvTotalTime.setText("Total AI Processing: " + totalTime + " ms");
                                tvSystemStatus.setText("Listening...");
                            });
                        }
                    } else {
                        // Continue utterance (speech or near-speech). Use hysteresis to reduce noise triggers.
                        if (rms >= SPEECH_RMS_CONTINUE || rms >= SPEECH_RMS_START) {
                            maxRms = Math.max(maxRms, rms);
                        }
                        silenceChunks = 0;

                        short[] newBuffer = new short[accumulatedAudio.length + combinedNew.length];
                        System.arraycopy(accumulatedAudio, 0, newBuffer, 0, accumulatedAudio.length);
                        System.arraycopy(combinedNew, 0, newBuffer, accumulatedAudio.length, combinedNew.length);
                        accumulatedAudio = newBuffer;

                        if (accumulatedAudio.length > 16000 * 8) {
                            accumulatedAudio = new short[0];
                            inUtterance = false;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // --- INTERNET CALL MODE: send ASR text over WebSocket, translate+TTS on receiver ---
    private void startInternetCall() {
        String roomId = etRoomId.getText() == null ? "" : etRoomId.getText().toString().trim();
        String userId = etUserId.getText() == null ? "" : etUserId.getText().toString().trim();

        if (roomId.isEmpty() || userId.isEmpty()) {
            Toast.makeText(this, "Please fill Room ID and Your User ID.", Toast.LENGTH_LONG).show();
            return;
        }

        inCall = true;
        isSpeaking = false;
        applyInCallAudioRoute(false);
        btnCall.setEnabled(false);
        btnEnd.setEnabled(true);
        btnBenchmark.setEnabled(false);
        audioQueue.clear();
        accumulatedAudio = new short[0];

        tvAsrTime.setText("ASR Time: -- ms");
        tvTransTime.setText("Translation Time: -- ms");
        tvTtsTime.setText("TTS Processing Time: -- ms");
        tvTotalTime.setText("Total AI Processing: -- ms");
        tvSystemStatus.setText("Getting token...");

        LangConfig myLang = (LangConfig) myLanguageSpinner.getSelectedItem();
        liveKitSelfUserId = userId;

        // 1) Fetch token from your PC token server
        tokenExecutor.execute(() -> {
            try {
                String token = tokenServerClient.fetchToken(TOKEN_SERVER_BASE_URL, roomId, userId);
                runOnUiThread(() -> tvSystemStatus.setText("Connecting..."));

                // 2) Connect to LiveKit Cloud using the token
                liveKitClient.connect(LIVEKIT_URL, token);

                // 3) Start microphone pipeline (ASR -> send text)
                recorder.setChunkListener(chunk -> {
                    if (!isSpeaking) {
                        audioQueue.offer(chunk);
                    }
                });
                recorder.start();

                pipelineThread = new Thread(() -> runInternetPipelineLoop(myLang));
                pipelineThread.start();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    String msg = e.getMessage();
                    if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
                    tvSystemStatus.setText("Token fetch failed: " + msg);
                    btnCall.setEnabled(true);
                    btnEnd.setEnabled(false);
                    btnBenchmark.setEnabled(true);
                });
                inCall = false;
            }
        });
    }

    private void runInternetPipelineLoop(LangConfig myLang) {
        boolean inUtterance = false;
        double maxRms = 0.0;
        int silenceChunks = 0;
        long lastAcceptedAtMs = 0;
        String lastAcceptedText = "";

        while (inCall) {
            try {
                short[] chunk = audioQueue.take();
                List<short[]> pending = new ArrayList<>();
                pending.add(chunk);
                audioQueue.drainTo(pending);
                short[] combinedNew = combineChunks(pending);

                double rms = calculateRMS(combinedNew);
                if (!inUtterance) {
                    if (rms >= SPEECH_RMS_START) {
                        inUtterance = true;
                        silenceChunks = 0;
                        maxRms = rms;
                        accumulatedAudio = combinedNew;
                    } else {
                        continue;
                    }
                } else {
                    if (rms < SILENCE_RMS) {
                        silenceChunks++;
                        if (silenceChunks >= END_SILENCE_CHUNKS) {
                            short[] audioToProcess = accumulatedAudio;
                            accumulatedAudio = new short[0];
                            inUtterance = false;
                            silenceChunks = 0;

                            if (audioToProcess.length < MIN_UTTERANCE_SAMPLES || maxRms < SPEECH_RMS_START) {
                                continue;
                            }
                            long now = System.currentTimeMillis();
                            if (now - lastAcceptedAtMs < MIN_GAP_BETWEEN_UTTERANCES_MS) {
                                continue;
                            }

                            runOnUiThread(() -> tvSystemStatus.setText("Thinking..."));

                            long asrStart = System.currentTimeMillis();
                            AsrEngine.Result asrRes = asrEngine.transcribe(audioToProcess, myLang.asrCode);
                            long asrTime = System.currentTimeMillis() - asrStart;

                            String finalText = asrRes.text == null ? "" : asrRes.text.trim();
                            if (isLikelyFalsePositiveTranscript(finalText) || isNearDuplicate(finalText, lastAcceptedText)) {
                                runOnUiThread(() -> {
                                    tvAsrTime.setText("ASR Time: " + asrTime + " ms");
                                    tvSystemStatus.setText("Listening...");
                                });
                                continue;
                            }

                            lastAcceptedAtMs = now;
                            lastAcceptedText = finalText;
                            runOnUiThread(() -> {
                                tvAsrOutput.setText("You said: " + finalText);
                                tvTransOutput.setText("Sent to peer (they will translate offline).");
                                tvAsrTime.setText("ASR Time: " + asrTime + " ms");
                                tvTransTime.setText("Translation Time: -- ms");
                                tvTtsTime.setText("TTS Processing Time: -- ms");
                                tvTotalTime.setText("Total AI Processing: " + asrTime + " ms");
                                tvSystemStatus.setText("Listening...");
                            });

                            // Send transcript; receiver translates to their chosen target language
                            liveKitClient.sendSpeech(liveKitSelfUserId, finalText, myLang.transCode);
                        }
                    } else {
                        if (rms >= SPEECH_RMS_CONTINUE || rms >= SPEECH_RMS_START) {
                            maxRms = Math.max(maxRms, rms);
                        }
                        silenceChunks = 0;

                        short[] newBuffer = new short[accumulatedAudio.length + combinedNew.length];
                        System.arraycopy(accumulatedAudio, 0, newBuffer, 0, accumulatedAudio.length);
                        System.arraycopy(combinedNew, 0, newBuffer, accumulatedAudio.length, combinedNew.length);
                        accumulatedAudio = newBuffer;

                        if (accumulatedAudio.length > 16000 * 8) {
                            accumulatedAudio = new short[0];
                            inUtterance = false;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingSpeech(String fromUser, String text, String srcTransCode) {
        try {
            LangConfig myLang = (LangConfig) myLanguageSpinner.getSelectedItem();

            runOnUiThread(() -> {
                tvSystemStatus.setText("Incoming... Translating");
                tvAsrOutput.setText(fromUser + " said: " + text);
            });

            long transStart = System.currentTimeMillis();
            // Always translate incoming speech into *my* selected language.
            String translatedStr = translator.translate(text, srcTransCode, myLang.transCode);
            long transTime = System.currentTimeMillis() - transStart;

            runOnUiThread(() -> {
                tvTransOutput.setText("You hear (" + myLang.name + "): " + translatedStr);
                tvTransTime.setText("Translation Time: " + transTime + " ms");
                tvSystemStatus.setText("Speaking...");
            });

            isSpeaking = true;
            audioQueue.clear();

            long ttsTime = 0;
            try {
                // WebRTC/device changes can flip speakerphone back on between calls.
                // Re-apply earpiece routing right before we play TTS.
                applyInCallAudioRoute(false);
                ttsTime = ttsEngine.speak(translatedStr);
            } finally {
                isSpeaking = false;
            }

            long finalTtsTime = ttsTime;
            runOnUiThread(() -> {
                tvTtsTime.setText("TTS Processing Time: " + finalTtsTime + " ms");
                tvSystemStatus.setText("Listening...");
            });
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> tvSystemStatus.setText("Incoming processing failed."));
        }
    }

    private void endCall() {
        inCall = false;
        isSpeaking = false;
        recorder.stop();
        if (pipelineThread != null) pipelineThread.interrupt();
        if (liveKitClient != null) liveKitClient.disconnect("call ended");
        resetAudioRoute();
        btnCall.setEnabled(true);
        btnEnd.setEnabled(false);
        btnBenchmark.setEnabled(true);
        tvSystemStatus.setText("Call Ended.");
    }

    // --- NEW: BENCHMARK AUTOMATION LOGIC ---
    private void startBenchmark() {
        btnCall.setEnabled(false);
        btnEnd.setEnabled(false);
        btnBenchmark.setEnabled(false);

        layoutBenchmarkProgress.setVisibility(View.VISIBLE);
        pbBenchmark.setProgress(0);
        tvBenchmarkStatus.setText("Scanning dataset folders...");

        BatchEvaluator.runBatchBenchmark(this, asrEngine, translator, languages, new BatchEvaluator.BenchmarkCallback() {
            @Override
            public void onProgress(int currentFile, int totalFiles, String statusText) {
                runOnUiThread(() -> {
                    pbBenchmark.setMax(totalFiles);
                    pbBenchmark.setProgress(currentFile);
                    tvBenchmarkStatus.setText(statusText + "\n(" + currentFile + " of " + totalFiles + ")");
                });
            }

            @Override
            public void onComplete(String finalMessage) {
                runOnUiThread(() -> {
                    tvBenchmarkStatus.setText(finalMessage);
                    Toast.makeText(MainActivity.this, finalMessage, Toast.LENGTH_LONG).show();

                    // Re-enable buttons when finished
                    btnCall.setEnabled(true);
                    btnBenchmark.setEnabled(true);
                });
            }

            @Override
            public void onError(String errorMsg) {
                runOnUiThread(() -> {
                    tvBenchmarkStatus.setText("Error: " + errorMsg);
                    Toast.makeText(MainActivity.this, "Benchmark Error!", Toast.LENGTH_SHORT).show();

                    btnCall.setEnabled(true);
                    btnBenchmark.setEnabled(true);
                });
            }
        });
    }

    // --- UTILS ---
    private double calculateRMS(short[] chunk) {
        if (chunk.length == 0) return 0;
        double sum = 0;
        for (short s : chunk) sum += s * s;
        return Math.sqrt(sum / chunk.length);
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

    private void applyInCallAudioRoute(boolean speakerphone) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            am.setSpeakerphoneOn(speakerphone);
        } catch (Exception ignored) {}
    }

    private void resetAudioRoute() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            am.setSpeakerphoneOn(false);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {}
    }

    private boolean isLikelyFalsePositiveTranscript(String text) {
        if (text == null) return true;
        String t = text.trim();
        if (t.isEmpty()) return true;
        if (t.length() <= 1) return true;

        boolean hasLetter = false;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i))) { hasLetter = true; break; }
        }
        if (!hasLetter) return true;

        // Very short single-token outputs are often noise hallucinations for this pipeline.
        String[] parts = t.split("\\s+");
        if (parts.length <= 1 && t.length() < 4) return true;
        return false;
    }

    private boolean isNearDuplicate(String text, String lastText) {
        if (text == null || lastText == null) return false;
        String a = text.trim();
        String b = lastText.trim();
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equalsIgnoreCase(b);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCall();
            } else {
                tvSystemStatus.setText("Status: Mic Permission Denied!");
            }
        }
    }
}