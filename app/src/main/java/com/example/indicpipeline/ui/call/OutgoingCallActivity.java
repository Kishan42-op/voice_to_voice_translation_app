 package com.example.indicpipeline.ui.call;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.livekit.LiveKitManager;
import com.example.indicpipeline.call.signaling.CallEvent;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Outgoing call screen: shows calling UI and waits for accept/reject.
 */
public class OutgoingCallActivity extends AppCompatActivity {
    private TextView tvName, tvStatus;
    private Button btnCancel;
    private String targetUid, targetName;
    private String currentCallId;
    private String currentRoomId;
    private SignalingRepository signaling = SignalingRepository.getInstance();
    private LiveKitManager liveKitManager = new LiveKitManager();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing_call);

        tvName = findViewById(R.id.tvOutgoingName);
        tvStatus = findViewById(R.id.tvOutgoingStatus);
        btnCancel = findViewById(R.id.btnCancelOutgoing);

        targetUid = getIntent().getStringExtra("targetUid");
        targetName = getIntent().getStringExtra("targetName");

        tvName.setText(targetName == null ? "Calling..." : targetName);
        tvStatus.setText("Calling...");

        btnCancel.setOnClickListener(v -> {
            // Emit a simple end or cancel - backend should cleanup
            if (currentCallId != null && !currentCallId.isEmpty()) {
                signaling.endCall(currentCallId);
            }
            CallStateManager.getInstance().setState(CallStateManager.CallState.ENDED);
            finish();
        });

        // Start call
        signaling.callUser(targetUid, targetName);
        CallStateManager.getInstance().setState(CallStateManager.CallState.CALLING);

        signaling.getCallInitiated().observe(this, new Observer<CallEvent>() {
            @Override
            public void onChanged(CallEvent callEvent) {
                if (callEvent == null) return;
                currentCallId = callEvent.callId;
                currentRoomId = callEvent.roomId;
                tvStatus.setText("Calling...");
            }
        });

        // Observe for call accepted by peer
        signaling.getCallAccepted().observe(this, new Observer<CallEvent>() {
            @Override
            public void onChanged(CallEvent callEvent) {
                if (callEvent == null) return;
                if (currentCallId != null && callEvent.callId != null && !currentCallId.equals(callEvent.callId)) return;
                if (callEvent.roomId != null && !callEvent.roomId.isEmpty()) {
                    currentRoomId = callEvent.roomId;
                    tvStatus.setText("Accepted. Joining...");
                    new Thread(() -> {
                        String identity = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
                        String token = liveKitManager.fetchTokenSync(CallConfig.TOKEN_SERVER_BASE_URL, currentRoomId, identity);
                        if (token == null || token.isEmpty()) {
                            runOnUiThread(() -> tvStatus.setText("Failed to fetch call token"));
                            return;
                        }
                        Intent i = new Intent(OutgoingCallActivity.this, CallActivity.class);
                        i.putExtra("callId", currentCallId);
                        i.putExtra("roomId", currentRoomId);
                        i.putExtra("livekitToken", token);
                        i.putExtra("isCaller", true);
                        startActivity(i);
                        finish();
                    }).start();
                }
            }
        });
    }
}


