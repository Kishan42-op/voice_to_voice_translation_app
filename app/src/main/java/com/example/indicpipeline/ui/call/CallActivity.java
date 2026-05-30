package com.example.indicpipeline.ui.call;

import android.content.pm.PackageManager;
// ...existing imports...
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.indicpipeline.R;
import com.example.indicpipeline.LangConfig;
import com.example.indicpipeline.TtsEngine;
import com.example.indicpipeline.IndicToUroman;
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
    // Local TTS speaking flag (mirrors MainActivity behavior)
    private volatile boolean isSpeaking = false;
    private TtsEngine receiverTtsEngine;
    private String receiverTtsFolder;
    private final Object receiverTtsLock = new Object();

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
        // btnTtsTest removed in rollback - no debug button

        tvRoom.setText("Room: " + (roomId == null ? "-" : roomId));
        tvDuration.setText("00:00");

        CallStateManager.getInstance().getState().observe(this, this::renderState);
        signaling.getCallEnded().observe(this, callEvent -> {
            if (callEvent == null || callEvent.callId == null) return;
            if (callId != null && !callId.equals(callEvent.callId)) return;
            Log.i(TAG, "[CALL_FLOW] Received remote call-ended for callId=" + callEvent.callId);
            callManager.handleRemoteEnded(callEvent.callId);
        });

        // Initial button state (will be updated when connected)
        btnMute.setSelected(false);
        btnMute.setText("Mute");
        btnSpeaker.setSelected(true);

        btnMute.setOnClickListener(v -> {
            if (sessionManager != null) {
                Log.i(TAG, "[CALL_FLOW] Mute button clicked");
                sessionManager.toggleMute();
                boolean isMuted = sessionManager.isMuted();
                if (pipelineManager != null) {
                    pipelineManager.setMuted(isMuted);
                }
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

        // Debug TTS test removed


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
                
                // PRE-LOAD the TTS Engine for my own language to avoid delays on first translation
                new Thread(() -> {
                    try {
                        Log.i(TAG, "[PIPELINE] Pre-loading TTS Engine...");
                        ensureReceiverTtsEngine(localLang != null ? localLang : remoteLang);
                    } catch (Exception e) {
                        Log.e(TAG, "[PIPELINE] Failed to pre-load TTS: " + e.getMessage());
                    }
                }).start();
            }
        });

        sessionManager.getIncomingTranscription().observe(this, text -> {
            if (text == null) return;
            Log.i(TAG, "[PIPELINE] Incoming transcription: " + text);
            runOnUiThread(() -> {
                layoutRemoteSpeech.setVisibility(View.VISIBLE);
                tvRemoteTranslatedText.setText((tvRemoteParticipant.getText() == null ? "Peer" : tvRemoteParticipant.getText().toString()) + " said: " + text);
            });
        });

        sessionManager.getIncomingTranslation().observe(this, text -> {
            if (text == null) return;
            Log.i(TAG, "[PIPELINE] Incoming translation: " + text);
            runOnUiThread(() -> {
                layoutRemoteSpeech.setVisibility(View.VISIBLE);
                tvRemoteTranslatedText.setText(text);
            });
            // Receiver synthesizes the translated text locally using TTS
            new Thread(() -> handleRemoteTranslation(text)).start();
        });

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
        // Sync the initial mute state
        if (sessionManager != null) {
            pipelineManager.setMuted(sessionManager.isMuted());
        }
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
                // Send the translated text to remote peer
                sessionManager.sendTranslation(text);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "[PIPELINE] Error: " + message);
                runOnUiThread(() -> tvStatus.setText("Pipeline Error"));
            }
        });

        pipelineManager.initialize(localLang, remoteLang);
    }

    // --- Receiver: handle incoming translation and speak locally ---
    private void handleRemoteTranslation(String translatedText) {
        Log.i(TAG, "[TRACE] handleRemoteTranslation ENTER with text: " + translatedText);
        try {
            if (translatedText == null || translatedText.trim().isEmpty()) {
                Log.w(TAG, "[TRACE] [LOCAL_TTS] Received empty translation; skipping.");
                return;
            }

            synchronized (receiverTtsLock) {
                if (isSpeaking) {
                    Log.w(TAG, "[TRACE] [LOCAL_TTS] Already speaking; dropping overlapping translation.");
                    return;
                }
                isSpeaking = true;
                Log.d(TAG, "[TRACE] isSpeaking set to TRUE");
            }

            // Choose the receiver's own language for local TTS.
            LangConfig myLang = localLang;
            if (myLang == null) {
                if (remoteLang != null) {
                    myLang = remoteLang;
                    Log.w(TAG, "[TRACE] [LOCAL_TTS] localLang unavailable, falling back to remoteLang: " + myLang.name);
                } else {
                    myLang = LanguageCatalog.getSupportedLanguages().get(0);
                    Log.w(TAG, "[TRACE] [LOCAL_TTS] localLang and remoteLang unavailable, falling back to default: " + myLang.name);
                }
            }

            Log.i(TAG, "[TRACE] [LOCAL_TTS] Incoming translation: " + translatedText);
            runOnUiThread(() -> {
                layoutRemoteSpeech.setVisibility(View.VISIBLE);
                tvRemoteTranslatedText.setText(translatedText);
                tvStatus.setText("Incoming... Speaking...");
            });

            // Pass the raw translated text (Native Script) directly to TTS.
            // Transliteration was found to cause 'noise' because the model vocab expects native characters.

            if (pipelineManager != null) {
                Log.d(TAG, "[TRACE] Notifying pipeline manager to pause (isSpeaking=true)");
                pipelineManager.setSpeaking(true);
                pipelineManager.clearAudioQueue();
            }

            long ttsTime = 0;
            try {
                Log.d(TAG, "[TRACE] Ensuring TTS Engine is ready...");
                ensureReceiverTtsEngine(myLang);
                
                Log.i(TAG, "[TRACE] [LOCAL_TTS] Synthesizing in voice: " + myLang.name);
                ttsTime = receiverTtsEngine.speak(translatedText);
                Log.i(TAG, "[TRACE] [LOCAL_TTS] speak() COMPLETED, took: " + ttsTime + " ms");
            } catch (Exception e) {
                Log.e(TAG, "[TRACE] [LOCAL_TTS] Synthesis error: " + e.getMessage(), e);
            } finally {
                synchronized (receiverTtsLock) {
                    isSpeaking = false;
                    Log.d(TAG, "[TRACE] isSpeaking set to FALSE");
                }
                if (pipelineManager != null) {
                    Log.d(TAG, "[TRACE] Notifying pipeline manager to resume (isSpeaking=false)");
                    pipelineManager.setSpeaking(false);
                }
            }

            runOnUiThread(() -> {
                tvStatus.setText("Listening...");
            });
            Log.i(TAG, "[TRACE] handleRemoteTranslation EXIT");
        } catch (Exception e) {
            Log.e(TAG, "[TRACE] [LOCAL_TTS] handleRemoteTranslation failed: " + e.getMessage(), e);
            runOnUiThread(() -> tvStatus.setText("Incoming processing failed."));
        }
    }

    private synchronized void ensureReceiverTtsEngine(LangConfig myLang) throws Exception {
        if (myLang == null) {
            throw new IllegalStateException("Receiver language unavailable");
        }
        String folder = myLang.ttsFolder;
        if (receiverTtsEngine != null && folder != null && folder.equals(receiverTtsFolder)) {
            return;
        }
        if (receiverTtsEngine != null) {
            try {
                receiverTtsEngine.close();
            } catch (Exception e) {
                Log.w(TAG, "[LOCAL_TTS] Failed to close old receiver TTS: " + e.getMessage());
            }
        }
        receiverTtsEngine = new TtsEngine(this, null, folder);
        receiverTtsFolder = folder;
    }

    // The previous receiver audio-focus / routing hacks were reverted.
    // Receiver now follows the MainActivity pattern: update UI, apply in-call route, and call TtsEngine.speak()

    // Removed: persistentAudioTrack, playbackQueue, playbackThread, isPlaybackRunning
    // Removed: initPlayback(), stopPlayback(), playReceivedAudio()
    // These were part of the incorrect audio streaming architecture
    // Correct architecture: receiver receives TEXT and does local TTS

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
        Log.d(TAG, "[MUTE_UI] Updating button: muted=" + muted);
        btnMute.setSelected(muted);
        btnMute.setText(muted ? "Unmute" : "Mute");
        btnMute.setAlpha(muted ? 0.6f : 1.0f);
        Log.i(TAG, "[MUTE_UI] Button updated: " + (muted ? "MUTED (Unmute button)" : "ACTIVE (Mute button)"));
    }

    private void updateSpeakerButton() {
        boolean speakerOn = sessionManager != null && sessionManager.isSpeakerOn();
        Log.d(TAG, "[SPEAKER_UI] Updating button: speaker=" + speakerOn);
        btnSpeaker.setSelected(speakerOn);
        btnSpeaker.setText(speakerOn ? "Speaker" : "Earpiece");
        btnSpeaker.setAlpha(speakerOn ? 1.0f : 0.6f);
        Log.i(TAG, "[SPEAKER_UI] Button updated: " + (speakerOn ? "SPEAKER ON" : "EARPIECE ON"));
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

    private void applyInCallAudioRoute(boolean speakerphone) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            
            if (speakerphone) {
                // If speaker is requested, use MODE_NORMAL and USAGE_MEDIA (now in TtsEngine)
                // this is the most reliable way to force audio to the loud speaker.
                am.setMode(android.media.AudioManager.MODE_NORMAL);
                am.setSpeakerphoneOn(true);
            } else {
                // If earpiece is requested, we must use MODE_IN_COMMUNICATION
                am.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                am.setSpeakerphoneOn(false);
            }
            Log.i(TAG, "[AUDIO_ROUTE] Applied: Speakerphone=" + speakerphone + " Mode=" + am.getMode());
        } catch (Exception e) {
            Log.w(TAG, "[AUDIO_ROUTE] Failed to apply in-call route: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "CallActivity onDestroy");
        if (receiverTtsEngine != null) {
            try {
                receiverTtsEngine.close();
            } catch (Exception e) {
                Log.w(TAG, "[LOCAL_TTS] Failed to close receiver TTS: " + e.getMessage());
            }
        }
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
