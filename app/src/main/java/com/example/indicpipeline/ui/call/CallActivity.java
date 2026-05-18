package com.example.indicpipeline.ui.call;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.livekit.CallSessionManager;
import com.example.indicpipeline.call.livekit.LiveKitManager;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Active call screen: displays call UI and manages audio connection via CallSessionManager.
 *
 * Responsibilities:
 * - Display call status and remote participant info
 * - Manage CallSessionManager lifecycle (create, connect, disconnect)
 * - Wire up mute/speaker button clicks
 * - Handle permissions
 * - Observe call state changes from CallSessionManager
 * - Clean up on end call or disconnect
 */
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 123;

    // UI elements
    private TextView tvRoom, tvStatus, tvRemoteParticipant;
    private Button btnMute, btnSpeaker, btnEnd;

    // Intent data
    private String roomId, token;
    private boolean isCaller;

    // LiveKit session
    private CallSessionManager sessionManager;
    private SignalingRepository signaling = SignalingRepository.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Get intent data
        roomId = getIntent().getStringExtra("roomId");
        token = getIntent().getStringExtra("livekitToken");
        isCaller = getIntent().getBooleanExtra("isCaller", false);

        Log.i(TAG, "Creating CallActivity: roomId=" + roomId + " isCaller=" + isCaller);

        // Initialize UI
        tvRoom = findViewById(R.id.tvCallRoom);
        tvStatus = findViewById(R.id.tvCallStatus);
        tvRemoteParticipant = findViewById(R.id.tvRemoteParticipantName);
        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnEnd = findViewById(R.id.btnEndCall);

        tvRoom.setText("Room: " + (roomId == null ? "-" : roomId));
        tvStatus.setText("Connecting...");

        // Set button initial states (not selected)
        btnMute.setSelected(false); // initially unmuted
        btnSpeaker.setSelected(true); // initially speaker on

        // Mute button: toggle mute state
        btnMute.setOnClickListener(v -> {
            if (sessionManager != null) {
                sessionManager.toggleMute();
                btnMute.setSelected(sessionManager.isMuted());
                Log.i(TAG, "Mute toggled: " + sessionManager.isMuted());
            }
        });

        // Speaker button: toggle speaker state
        btnSpeaker.setOnClickListener(v -> {
            if (sessionManager != null) {
                sessionManager.toggleSpeaker();
                btnSpeaker.setSelected(sessionManager.isSpeakerOn());
                Log.i(TAG, "Speaker toggled: " + sessionManager.isSpeakerOn());
            }
        });

        // End call button: disconnect and finish
        btnEnd.setOnClickListener(v -> {
            Log.i(TAG, "End call button clicked");
            endCall();
        });

        // Check and request RECORD_AUDIO permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Requesting RECORD_AUDIO permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        RECORD_AUDIO_PERMISSION_CODE);
            } else {
                // Permission already granted, start the call
                startCallSession();
            }
        } else {
            // API < 23, assume permission granted
            startCallSession();
        }
    }

    /**
     * Initialize and connect CallSessionManager to LiveKit.
     */
    private void startCallSession() {
        Log.i(TAG, "Starting call session");

        if (roomId == null || token == null) {
            Log.e(TAG, "✗ Missing roomId or token");
            tvStatus.setText("Error: missing call parameters");
            return;
        }

        // Create session manager
        sessionManager = new CallSessionManager(this);

        // Observe connection state
        sessionManager.getIsConnected().observe(this, isConnected -> {
            Log.i(TAG, "Room connection state: " + isConnected);
            tvStatus.setText(isConnected ? "Connected" : "Disconnected");
        });

        // Observe remote participant connection
        sessionManager.getIsRemoteParticipantConnected().observe(this, isConnected -> {
            Log.i(TAG, "Remote participant connection state: " + isConnected);
        });

        // Observe remote participant name
        sessionManager.getRemoteParticipantName().observe(this, name -> {
            tvRemoteParticipant.setText(name == null ? "Waiting..." : name);
        });

        // Observe connection errors
        sessionManager.getConnectionError().observe(this, error -> {
            if (error != null) {
                Log.e(TAG, "Connection error: " + error);
                tvStatus.setText("Error: " + error);
            }
        });

        // Connect to LiveKit room (on background thread)
        new Thread(() -> {
            String userIdentity = FirebaseAuth.getInstance().getCurrentUser() == null ?
                    "unknown" : FirebaseAuth.getInstance().getCurrentUser().getUid();

            // LiveKit Cloud URL (adjust if using self-hosted)
            String liveKitUrl = "wss://voicetovoicetranslationapp.livekit.cloud";

            Log.i(TAG, "Connecting to LiveKit: url=" + liveKitUrl + " room=" + roomId + " identity=" + userIdentity);
            sessionManager.connect(liveKitUrl, roomId, token, userIdentity);
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✓ RECORD_AUDIO permission granted");
                startCallSession();
            } else {
                Log.w(TAG, "✗ RECORD_AUDIO permission denied");
                tvStatus.setText("Microphone permission required");
            }
        }
    }

    /**
     * End the call and cleanup resources.
     */
    private void endCall() {
        Log.i(TAG, "Ending call");

        // Tell signaling backend to end the call
        signaling.endCall("");

        // Disconnect LiveKit session
        if (sessionManager != null) {
            sessionManager.disconnect();
            sessionManager.cleanup();
        }

        // Update call state
        CallStateManager.getInstance().setState(CallStateManager.CallState.ENDED);

        // Close activity
        finish();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "CallActivity onDestroy");

        // Cleanup session
        if (sessionManager != null) {
            sessionManager.cleanup();
        }

        super.onDestroy();
    }
}
