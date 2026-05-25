package com.example.indicpipeline.ui.call;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.widget.LinearLayout;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.indicpipeline.R;
import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.language.LanguageCatalog;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.livekit.CallSessionManager;
import com.example.indicpipeline.call.manager.CallManager;
import com.example.indicpipeline.call.manager.CallPipelineManager;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.example.indicpipeline.models.User;
import com.example.indicpipeline.auth.repository.UserRepository;
import com.example.indicpipeline.auth.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

/**
 * Active call screen.
 *
 * Responsibilities:
 * - Render UI strictly from CallStateManager
 * - Connect LiveKit when permissions are ready
 * - Stop all audio/timers when call ends
 * - Back press only backgrounds the app; explicit End button ends the call
 * - Orchestrates Translation Pipeline
 */
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 123;

    private TextView tvRoom, tvStatus, tvDuration, tvRemoteParticipant, tvRemoteParticipantLanguage;
    private TextView tvLocalSpokenText, tvLocalTranslatedText, tvRemoteTranslatedText;
    private LinearLayout layoutLocalSpeech, layoutRemoteSpeech;
    private Button btnMute, btnSpeaker, btnEnd;

    private String callId, roomId, token;
    private CallSessionManager sessionManager;
    private CallPipelineManager pipelineManager;
    private final CallManager callManager = CallManager.getInstance();
    private final SignalingRepository signaling = SignalingRepository.getInstance();
    private final UserRepository userRepository = new UserRepository();

    private User localUser;
    private LangConfig localLang;
    private LangConfig remoteLang;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long connectedAtMs = 0L;
    private boolean terminalDismissScheduled = false;
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (connectedAtMs <= 0L || tvDuration == null) return;
            long elapsed = SystemClock.elapsedRealtime() - connectedAtMs;
            tvDuration.setText(formatDuration(elapsed));
            timerHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        
        Log.i(TAG, "====================================================");
        Log.i(TAG, "[VERSION_TAG] CALL_ACTIVITY_V3_HANDSHAKE_TRACE");
        Log.i(TAG, "====================================================");

        callId = getIntent().getStringExtra("callId");
        roomId = getIntent().getStringExtra("roomId");
        token = getIntent().getStringExtra("livekitToken");

        Log.i(TAG, "Creating CallActivity: callId=" + callId + " roomId=" + roomId);

        tvRoom = findViewById(R.id.tvCallRoom);
        tvStatus = findViewById(R.id.tvCallStatus);
        tvDuration = findViewById(R.id.tvCallDuration);
        tvRemoteParticipant = findViewById(R.id.tvRemoteParticipantName);
        tvRemoteParticipantLanguage = findViewById(R.id.tvRemoteParticipantLanguage);
        
        tvLocalSpokenText = findViewById(R.id.tvLocalSpokenText);
        tvLocalTranslatedText = findViewById(R.id.tvLocalTranslatedText);
        tvRemoteTranslatedText = findViewById(R.id.tvRemoteTranslatedText);
        layoutLocalSpeech = findViewById(R.id.layoutLocalSpeech);
        layoutRemoteSpeech = findViewById(R.id.layoutRemoteSpeech);

        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnEnd = findViewById(R.id.btnEndCall);

        tvRoom.setText("Room: " + (roomId == null ? "-" : roomId));
        tvDuration.setText("00:00");

        CallStateManager.getInstance().getState().observe(this, this::renderState);
        signaling.getCallEnded().observe(this, callEvent -> {
            if (callEvent == null || callEvent.callId == null) return;
            if (callId != null && !callId.equals(callEvent.callId)) return;
            Log.i(TAG, "[CALL_FLOW] Received remote call-ended for callId=" + callEvent.callId);
            callManager.handleRemoteEnded(callEvent.callId);
        });

        btnMute.setSelected(false);
        btnSpeaker.setSelected(true);

        btnMute.setOnClickListener(v -> {
            if (sessionManager != null) {
                Log.i(TAG, "[CALL_FLOW] Mute button clicked");
                sessionManager.toggleMute();
                updateMuteButton();
            }
        });

        btnSpeaker.setOnClickListener(v -> {
            if (sessionManager != null) {
                Log.i(TAG, "[CALL_FLOW] Speaker button clicked");
                sessionManager.toggleSpeaker();
                updateSpeakerButton();
            }
        });

        btnEnd.setOnClickListener(v -> {
            Log.i(TAG, "[CALL_FLOW] End call button clicked");
            callManager.endCall();
            finish();
        });


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Requesting RECORD_AUDIO permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        RECORD_AUDIO_PERMISSION_CODE);
            } else {
                startCallSession();
            }
        } else {
            startCallSession();
        }

        renderState(callManager.getCurrentState());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.i(TAG, "Back pressed - moving app to background WITHOUT ending call.");
                moveTaskToBack(true);
            }
        });
    }

    private void startCallSession() {
        Log.i(TAG, "Starting call session");

        if (roomId == null || token == null) {
            Log.e(TAG, "Missing roomId or token");
            tvStatus.setText("Error: missing call parameters");
            return;
        }

        sessionManager = new CallSessionManager(this);

        sessionManager.getIsConnected().observe(this, isConnected -> {
            Log.i(TAG, "LiveKit connected=" + isConnected);
            if (Boolean.TRUE.equals(isConnected)) {
                callManager.setConnected();
                updateMuteButton();
                updateSpeakerButton();
                
                // Once connected, exchange preferred language
                exchangePreferredLanguage();
            }
        });

        sessionManager.getIsRemoteParticipantConnected().observe(this, isConnected -> {
            Log.i(TAG, "Remote participant connected=" + isConnected);
            if (Boolean.TRUE.equals(isConnected)) {
                // Re-send our language in case the remote side just joined
                exchangePreferredLanguage();
            }
        });

        sessionManager.getRemoteParticipantName().observe(this, name ->
                Log.i(TAG, "Remote participant name=" + name));

        sessionManager.getConnectionError().observe(this, error -> {
            if (error != null) {
                Log.e(TAG, "Connection error: " + error);
                tvStatus.setText("Connection error");
            }
        });

        // Observe Pipeline LiveData
        sessionManager.getRemotePreferredLanguage().observe(this, langCode -> {
            Log.i(TAG, "[PIPELINE] Remote preferred language: " + langCode);
            remoteLang = LanguageCatalog.findByCode(langCode);
            if (remoteLang != null) {
                runOnUiThread(() -> tvRemoteParticipantLanguage.setText("Language: " + remoteLang.name));
                initializePipeline();
            }
        });

        sessionManager.getIncomingTranscription().observe(this, text -> {
            if (text == null) return;
            Log.i(TAG, "[PIPELINE] Incoming transcription: " + text);
        });

        sessionManager.getIncomingTranslation().observe(this, text -> {
            if (text == null) return;
            Log.i(TAG, "[PIPELINE] Incoming translation: " + text);
            runOnUiThread(() -> {
                layoutRemoteSpeech.setVisibility(View.VISIBLE);
                tvRemoteTranslatedText.setText(text);
            });
        });

        // Use direct listener instead of LiveData to prevent chunk loss
        sessionManager.setAudioDataListener(this::playReceivedAudio);

        new Thread(() -> {
            String userIdentity = FirebaseAuth.getInstance().getCurrentUser() == null
                    ? "unknown"
                    : FirebaseAuth.getInstance().getCurrentUser().getUid();
            String liveKitUrl = CallConfig.LIVEKIT_URL;

            // Fetch local user profile to get preferred language
            userRepository.getUserDocument(userIdentity, new AuthRepository.AuthResultCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    localUser = user;
                    if (user.getPreferredLanguage() != null) {
                        localLang = LanguageCatalog.findByName(user.getPreferredLanguage().getName());
                    }
                    Log.i(TAG, "Connecting to LiveKit: url=" + liveKitUrl + " room=" + roomId + " identity=" + userIdentity);
                    sessionManager.connect(liveKitUrl, roomId, token, userIdentity);
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Failed to load local user profile: " + message);
                    sessionManager.connect(liveKitUrl, roomId, token, userIdentity);
                }
            });
        }).start();
    }

    private void exchangePreferredLanguage() {
        if (localLang != null && sessionManager != null) {
            Log.i(TAG, "[PIPELINE] Sending preferred language code: " + localLang.asrCode + " (" + localLang.name + ")");
            sessionManager.sendPreferredLanguage(localLang.asrCode);
        } else {
            Log.w(TAG, "[PIPELINE] Skipping language exchange: localLang=" + (localLang == null ? "NULL" : localLang.name) + " sessionManager=" + (sessionManager == null ? "NULL" : "READY"));
        }
    }

    private void initializePipeline() {
        if (localLang == null || remoteLang == null) {
            Log.w(TAG, "[PIPELINE] Cannot initialize: local=" + (localLang == null ? "NULL" : localLang.name) + " remote=" + (remoteLang == null ? "NULL" : remoteLang.name));
            return;
        }
        if (pipelineManager != null) {
            Log.i(TAG, "[PIPELINE] Pipeline already initialized, skipping duplicate init");
            return;
        }

        Log.i(TAG, "[PIPELINE] Starting deterministic initialization: " + localLang.name + " (Source) -> " + remoteLang.name + " (Target)");
        pipelineManager = new CallPipelineManager(this);
        pipelineManager.setListener(new CallPipelineManager.PipelineListener() {
            @Override
            public void onLoadingProgress(String message) {
                runOnUiThread(() -> tvStatus.setText(message));
            }

            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    String status = "Translation Active (" + localLang.name + " → " + remoteLang.name + ")";
                    Log.i(TAG, "[PIPELINE] " + status);
                    tvStatus.setText(status);
                    pipelineManager.start();
                    
                    // Mute the raw LiveKit microphone so peers only hear the translated output
                    if (sessionManager != null && !sessionManager.isMuted()) {
                        Log.i(TAG, "[PIPELINE] Muting raw microphone for translated call");
                        sessionManager.toggleMute();
                        updateMuteButton();
                    }
                });
            }

            @Override
            public void onLocalTranscription(String text) {
                runOnUiThread(() -> {
                    layoutLocalSpeech.setVisibility(View.VISIBLE);
                    tvLocalSpokenText.setText(text);
                });
                sessionManager.sendTranscription(text);
            }

            @Override
            public void onLocalTranslation(String text) {
                runOnUiThread(() -> {
                    tvLocalTranslatedText.setText(text);
                });
                sessionManager.sendTranslation(text);
            }

            @Override
            public void onTranslatedAudioReady(byte[] audioBytes) {
                Log.i(TAG, "[PIPELINE] Pushing translated audio to WebRTC track (" + audioBytes.length + " bytes)");
                sessionManager.pushAudio(audioBytes);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "[PIPELINE] Error: " + message);
                runOnUiThread(() -> tvStatus.setText("Pipeline Error"));
            }
        });

        pipelineManager.initialize(localLang, remoteLang);
    }

    private AudioTrack persistentAudioTrack;
    private final java.util.concurrent.BlockingQueue<byte[]> playbackQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    private Thread playbackThread;
    private volatile boolean isPlaybackRunning = false;

    private void initPlayback() {
        if (isPlaybackRunning) return;
        isPlaybackRunning = true;
        playbackThread = new Thread(() -> {
            try {
                int sampleRate = 16000;
                int minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
                Log.i(TAG, "[AUDIO] Initializing persistent AudioTrack. minBufferSize: " + minBufSize);
                
                persistentAudioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(Math.max(minBufSize, 16000 * 4)) // 1s buffer
                        .build();

                persistentAudioTrack.play();
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                boolean isSpeaker = am != null && am.isSpeakerphoneOn();
                Log.i(TAG, "[AUDIO] playback started (STATE: " + persistentAudioTrack.getState() + ") Speakerphone: " + isSpeaker);

                while (isPlaybackRunning) {
                    byte[] data = playbackQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (data == null) continue;

                    Log.d(TAG, "[AUDIO] Processing chunk from queue: " + data.length + " bytes");
                    
                    float[] floatArray = new float[data.length / 4];
                    java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floatArray);
                    
                    int written = persistentAudioTrack.write(floatArray, 0, floatArray.length, AudioTrack.WRITE_BLOCKING);
                    if (written < 0) {
                        Log.e(TAG, "[AUDIO] AudioTrack write error: " + written);
                    } else if (written < floatArray.length) {
                        Log.w(TAG, "[AUDIO] Partial write: " + written + "/" + floatArray.length);
                    } else {
                        Log.i(TAG, "[AUDIO] AudioTrack write success: " + written + " floats");
                        Log.i(TAG, "[AUDIO] playback active");
                    }
                }
            } catch (InterruptedException e) {
                Log.i(TAG, "[AUDIO] Playback thread interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "[AUDIO] Playback thread CRITICAL ERROR", e);
            } finally {
                if (persistentAudioTrack != null) {
                    try {
                        persistentAudioTrack.stop();
                        persistentAudioTrack.release();
                    } catch (Exception ignored) {}
                    persistentAudioTrack = null;
                }
                Log.i(TAG, "[AUDIO] Playback thread finished");
            }
        });
        playbackThread.start();
    }

    private void stopPlayback() {
        Log.i(TAG, "[AUDIO] Stopping playback...");
        isPlaybackRunning = false;
        if (playbackThread != null) playbackThread.interrupt();
        playbackQueue.clear();
    }

    private void playReceivedAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "[PIPELINE] Received empty/null audio data chunk");
            return;
        }
        
        if (!isPlaybackRunning) {
            Log.i(TAG, "[PIPELINE] Playback not running, initializing...");
            initPlayback();
        }
        
        Log.i(TAG, "[PIPELINE] peer audio chunk received: " + audioData.length + " bytes");
        Log.i(TAG, "[AUDIO] enqueue playback buffer");
        boolean added = playbackQueue.offer(audioData);
        if (!added) {
            Log.e(TAG, "[PIPELINE] Playback queue FULL, dropping chunk!");
        } else {
            Log.d(TAG, "[PIPELINE] Chunk added to playback queue. Size: " + playbackQueue.size());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "RECORD_AUDIO permission granted");
                startCallSession();
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied");
                tvStatus.setText("Microphone permission required");
            }
        }
    }

    private void renderState(CallStateManager.CallState state) {
        if (state == null) state = CallStateManager.CallState.IDLE;
        Log.i(TAG, "Render state: " + state);

        switch (state) {
            case CALLING:
                terminalDismissScheduled = false;
                tvStatus.setText("Calling...");
                tvRemoteParticipant.setText("");
                tvDuration.setText("00:00");
                stopTimer();
                break;
            case RINGING:
                terminalDismissScheduled = false;
                tvStatus.setText("Incoming Call");
                tvRemoteParticipant.setText("");
                tvDuration.setText("00:00");
                stopTimer();
                break;
            case CONNECTING:
                terminalDismissScheduled = false;
                tvStatus.setText("Connecting...");
                tvRemoteParticipant.setText("");
                tvDuration.setText("00:00");
                stopTimer();
                break;
            case CONNECTED:
                terminalDismissScheduled = false;
                tvStatus.setText("Connected");
                tvRemoteParticipant.setText(callManager.getPeerName() == null ? "" : callManager.getPeerName());
                startTimer();
                break;
            case REJECTED:
                tvStatus.setText("Call declined");
                tvRemoteParticipant.setText("");
                tvDuration.setText("00:00");
                stopTimer();
                scheduleTerminalDismiss();
                break;
            case MISSED:
                tvStatus.setText("Missed call");
                tvRemoteParticipant.setText("");
                tvDuration.setText("00:00");
                stopTimer();
                scheduleTerminalDismiss();
                break;
            case ENDED:
                tvStatus.setText("Call ended");
                tvRemoteParticipant.setText("");
                stopTimer();
                scheduleTerminalDismiss();
                break;
            case IDLE:
            default:
                tvStatus.setText("Calling...");
                tvRemoteParticipant.setText("");
                tvDuration.setText("00:00");
                stopTimer();
                break;
        }
    }

    private void updateMuteButton() {
        boolean muted = sessionManager != null && sessionManager.isMuted();
        btnMute.setSelected(muted);
        btnMute.setText(muted ? "Unmute" : "Mute");
        btnMute.setAlpha(muted ? 0.6f : 1.0f);
        Log.i(TAG, "Mute: " + (muted ? "ON" : "OFF"));
    }

    private void updateSpeakerButton() {
        boolean speakerOn = sessionManager != null && sessionManager.isSpeakerOn();
        btnSpeaker.setSelected(speakerOn);
        btnSpeaker.setText(speakerOn ? "Speaker" : "Earpiece");
        btnSpeaker.setAlpha(speakerOn ? 1.0f : 0.6f);
        Log.i(TAG, "Speaker: " + (speakerOn ? "ON" : "OFF"));
    }

    private void startTimer() {
        if (connectedAtMs > 0L) return;
        connectedAtMs = SystemClock.elapsedRealtime();
        timerHandler.removeCallbacks(timerTick);
        timerHandler.post(timerTick);
    }

    private void stopTimer() {
        connectedAtMs = 0L;
        timerHandler.removeCallbacks(timerTick);
    }

    private void scheduleTerminalDismiss() {
        if (terminalDismissScheduled) return;
        terminalDismissScheduled = true;
        timerHandler.postDelayed(() -> {
            callManager.resetToIdleAfterUiDismiss();
            finish();
        }, 1200L);
    }

    private String formatDuration(long elapsedMs) {
        long totalSeconds = elapsedMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "CallActivity onDestroy");
        stopPlayback();
        if (pipelineManager != null) {
            pipelineManager.stop();
        }
        if (sessionManager != null) {
            sessionManager.cleanup();
        }
        stopTimer();
        super.onDestroy();
    }
}
