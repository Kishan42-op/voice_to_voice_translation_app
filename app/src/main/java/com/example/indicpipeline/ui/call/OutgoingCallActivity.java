package com.example.indicpipeline.ui.call;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.livekit.LiveKitManager;
import com.example.indicpipeline.call.manager.CallManager;
import com.example.indicpipeline.call.signaling.CallEvent;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Outgoing call screen.
 *
 * State flow:
 * CALLING -> CONNECTING -> CONNECTED
 * CALLING -> REJECTED / MISSED / ENDED
 */
public class OutgoingCallActivity extends AppCompatActivity {
    private static final String TAG = "OutgoingCallActivity";

    private TextView tvName, tvStatus;
    private Button btnCancel;

    private String targetUid, targetName;
    private String currentCallId;
    private String currentRoomId;

    private final SignalingRepository signaling = SignalingRepository.getInstance();
    private final LiveKitManager liveKitManager = new LiveKitManager();
    private final CallManager callManager = CallManager.getInstance();

    private final Handler handler = new Handler(Looper.getMainLooper());

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

        CallStateManager.getInstance().getState().observe(this, this::renderState);

        btnCancel.setOnClickListener(v -> {
            // Explicit hangup only.
            callManager.endCall();
            finish();
        });

        callManager.initOutgoingCall(targetUid, targetName);
        signaling.callUser(targetUid, targetName);

        signaling.getCallInitiated().observe(this, callEvent -> {
            if (callEvent == null) return;
            currentCallId = callEvent.callId;
            currentRoomId = callEvent.roomId;
            android.util.Log.i(TAG, "[CALL_FLOW] Outgoing call delivered: callId=" + currentCallId + " roomId=" + currentRoomId);
        });

        signaling.getCallAccepted().observe(this, callEvent -> {
            if (callEvent == null) return;
            if (currentCallId != null && callEvent.callId != null && !currentCallId.equals(callEvent.callId)) {
                android.util.Log.w(TAG, "[CALL_FLOW] Ignoring call-accepted for mismatching callId: " + callEvent.callId);
                return;
            }

            if (currentCallId == null) {
                currentCallId = callEvent.callId;
            }
            currentRoomId = callEvent.roomId != null && !callEvent.roomId.isEmpty() ? callEvent.roomId : currentRoomId;
            android.util.Log.i(TAG, "[CALL_FLOW] Outgoing call accepted: callId=" + callEvent.callId + " roomId=" + currentRoomId);
            callManager.handleCallAccepted(callEvent.callId, currentRoomId);

            new Thread(() -> {
                String identity = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
                android.util.Log.i(TAG, "[LIVEKIT] Fetching token for room: " + currentRoomId);
                String token = liveKitManager.fetchTokenSync(CallConfig.TOKEN_SERVER_BASE_URL, currentRoomId, identity);
                if (token == null || token.isEmpty()) {
                    runOnUiThread(() -> tvStatus.setText("Unable to join call"));
                    android.util.Log.e(TAG, "[LIVEKIT] Failed to fetch token after acceptance");
                    callManager.endCall();
                    return;
                }

                android.util.Log.i(TAG, "[CALL_FLOW] Token fetched, starting CallActivity");
                Intent i = new Intent(OutgoingCallActivity.this, CallActivity.class);
                i.putExtra("callId", currentCallId);
                i.putExtra("roomId", currentRoomId);
                i.putExtra("livekitToken", token);
                i.putExtra("isCaller", true);
                runOnUiThread(() -> {
                    startActivity(i);
                    finish();
                });
            }).start();
        });

        signaling.getCallRejected().observe(this, callEvent -> {
            if (callEvent == null) return;
            if (currentCallId != null && callEvent.callId != null && !currentCallId.equals(callEvent.callId)) return;

            android.util.Log.i(TAG, "[CALL_FLOW] Outgoing call rejected by remote: callId=" + callEvent.callId);
            callManager.handleRemoteRejected(callEvent.callId);
            scheduleClose();
        });

        signaling.getCallEnded().observe(this, callEvent -> {
            if (callEvent == null) return;
            if (currentCallId != null && callEvent.callId != null && !currentCallId.equals(callEvent.callId)) return;

            android.util.Log.i(TAG, "[CALL_FLOW] Outgoing call ended by remote: callId=" + callEvent.callId);
            callManager.handleRemoteEnded(callEvent.callId);
            scheduleClose();
        });

        signaling.getCallError().observe(this, reason -> {
            if (reason == null) return;
            android.util.Log.e(TAG, "[CALL_FLOW] Outgoing call error received: " + reason);
            if ("target-offline".equals(reason)) {
                tvStatus.setText("Recipient is offline");
            } else {
                tvStatus.setText("Call failed: " + reason);
            }
            scheduleClose();
        });


        renderState(callManager.getCurrentState());
    }

    private void renderState(CallStateManager.CallState state) {
        if (state == null) state = CallStateManager.CallState.IDLE;
        android.util.Log.i(TAG, "Render outgoing state: " + state);

        switch (state) {
            case CALLING:
                tvStatus.setText("Calling...");
                btnCancel.setText("Cancel");
                break;
            case CONNECTING:
                tvStatus.setText("Connecting...");
                btnCancel.setText("Cancel");
                break;
            case CONNECTED:
                tvStatus.setText("Connected");
                btnCancel.setText("End");
                break;
            case REJECTED:
                tvStatus.setText("Call declined");
                btnCancel.setText("Close");
                break;
            case MISSED:
                tvStatus.setText("Missed call");
                btnCancel.setText("Close");
                break;
            case ENDED:
                tvStatus.setText("Call ended");
                btnCancel.setText("Close");
                break;
            case RINGING:
                tvStatus.setText("Calling...");
                btnCancel.setText("Cancel");
                break;
            case IDLE:
            default:
                tvStatus.setText("Calling...");
                btnCancel.setText("Cancel");
                break;
        }
    }

    private void scheduleClose() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            callManager.resetToIdleAfterUiDismiss();
            finish();
        }, 1200L);
    }
}
