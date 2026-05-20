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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.livekit.CallSessionManager;
import com.example.indicpipeline.call.manager.CallManager;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
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
 */
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 123;

    private TextView tvRoom, tvStatus, tvDuration, tvRemoteParticipant;
    private Button btnMute, btnSpeaker, btnEnd;

    private String callId, roomId, token;
    private CallSessionManager sessionManager;
    private final CallManager callManager = CallManager.getInstance();
    private final SignalingRepository signaling = SignalingRepository.getInstance();

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

        callId = getIntent().getStringExtra("callId");
        roomId = getIntent().getStringExtra("roomId");
        token = getIntent().getStringExtra("livekitToken");

        Log.i(TAG, "Creating CallActivity: callId=" + callId + " roomId=" + roomId);

        tvRoom = findViewById(R.id.tvCallRoom);
        tvStatus = findViewById(R.id.tvCallStatus);
        tvDuration = findViewById(R.id.tvCallDuration);
        tvRemoteParticipant = findViewById(R.id.tvRemoteParticipantName);
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
            }
        });

        sessionManager.getIsRemoteParticipantConnected().observe(this, isConnected ->
                Log.i(TAG, "Remote participant connected=" + isConnected));

        sessionManager.getRemoteParticipantName().observe(this, name ->
                Log.i(TAG, "Remote participant name=" + name));

        sessionManager.getConnectionError().observe(this, error -> {
            if (error != null) {
                Log.e(TAG, "Connection error: " + error);
                tvStatus.setText("Connection error");
            }
        });

        new Thread(() -> {
            String userIdentity = FirebaseAuth.getInstance().getCurrentUser() == null
                    ? "unknown"
                    : FirebaseAuth.getInstance().getCurrentUser().getUid();
            String liveKitUrl = CallConfig.LIVEKIT_URL;

            Log.i(TAG, "Connecting to LiveKit: url=" + liveKitUrl + " room=" + roomId + " identity=" + userIdentity);
            Log.i(TAG, "Token length: " + (token == null ? 0 : token.length()) + " chars");

            sessionManager.connect(liveKitUrl, roomId, token, userIdentity);
        }).start();
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
        Log.i(TAG, "CallActivity onDestroy - leaving call state untouched");
        if (sessionManager != null) {
            sessionManager.cleanup();
        }
        stopTimer();
        super.onDestroy();
    }
}
