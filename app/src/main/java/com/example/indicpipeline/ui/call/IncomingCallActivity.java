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
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.example.indicpipeline.utils.NotificationManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Incoming call screen.
 */
public class IncomingCallActivity extends AppCompatActivity {
    private TextView tvName;
    private Button btnAccept, btnReject;
    private String callId, fromUid, fromName, roomId;
    private final SignalingRepository signaling = SignalingRepository.getInstance();
    private final LiveKitManager liveKitManager = new LiveKitManager();
    private final CallManager callManager = CallManager.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean terminalDismissScheduled = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        tvName = findViewById(R.id.tvIncomingName);
        btnAccept = findViewById(R.id.btnAcceptCall);
        btnReject = findViewById(R.id.btnRejectCall);

        callId = getIntent().getStringExtra("callId");
        fromUid = getIntent().getStringExtra("fromUid");
        fromName = getIntent().getStringExtra("fromName");
        roomId = getIntent().getStringExtra("roomId");

        tvName.setText(fromName == null ? "Incoming Call" : fromName);

        CallStateManager.getInstance().getState().observe(this, state -> {
            if (state == null) return;
            android.util.Log.i("IncomingCallActivity", "[STATE] Render state: " + state);
            switch (state) {
                case RINGING:
                    terminalDismissScheduled = false;
                    tvName.setText(fromName == null ? "Incoming Call" : fromName);
                    break;
                case CONNECTING:
                    terminalDismissScheduled = false;
                    tvName.setText("Connecting...");
                    break;
                case CONNECTED:
                    terminalDismissScheduled = false;
                    tvName.setText("Connected");
                    break;
                case REJECTED:
                    tvName.setText("Call declined");
                    scheduleClose();
                    break;
                case MISSED:
                    tvName.setText("Missed call");
                    scheduleClose();
                    break;
                case ENDED:
                    tvName.setText("Call ended");
                    scheduleClose();
                    break;
                default:
                    break;
            }
        });

        if (callManager.getCallId() == null || !callManager.getCallId().equals(callId)) {
            android.util.Log.i("IncomingCallActivity", "[CALL_FLOW] Initializing CallManager for incoming call: " + callId);
            callManager.initIncomingCall(callId, roomId, fromUid, fromName);
        } else {
            android.util.Log.i("IncomingCallActivity", "[CALL_FLOW] CallManager already initialized for callId=" + callId);
        }

        btnAccept.setOnClickListener(v -> {
            android.util.Log.i("IncomingCallActivity", "[CALL_FLOW] Accept button clicked");
            NotificationManager.cancelIncomingCallNotification(IncomingCallActivity.this);
            callManager.setConnecting();
            signaling.acceptCall(callId);

            new Thread(() -> {
                String identity = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
                android.util.Log.i("IncomingCallActivity", "[LIVEKIT] Fetching token for room: " + roomId);
                String token = liveKitManager.fetchTokenSync(CallConfig.TOKEN_SERVER_BASE_URL, roomId, identity);
                if (token == null || token.isEmpty()) {
                    runOnUiThread(() -> {
                        tvName.setText("Failed to join call");
                        android.util.Log.e("IncomingCallActivity", "[LIVEKIT] Token fetch failed");
                    });
                    return;
                }

                android.util.Log.i("IncomingCallActivity", "[CALL_FLOW] Token fetched, starting CallActivity");
                Intent i = new Intent(IncomingCallActivity.this, CallActivity.class);
                i.putExtra("callId", callId);
                i.putExtra("roomId", roomId);
                i.putExtra("livekitToken", token);
                i.putExtra("isCaller", false);
                runOnUiThread(() -> {
                    startActivity(i);
                    finish();
                });
            }).start();
        });

        btnReject.setOnClickListener(v -> {
            android.util.Log.i("IncomingCallActivity", "[CALL_FLOW] Reject button clicked");
            NotificationManager.cancelIncomingCallNotification(IncomingCallActivity.this);
            callManager.rejectCall(callId);
            finish();
        });

    }

    private void scheduleClose() {
        if (terminalDismissScheduled) return;
        terminalDismissScheduled = true;
        handler.postDelayed(() -> {
            callManager.resetToIdleAfterUiDismiss();
            finish();
        }, 1200L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NotificationManager.cancelIncomingCallNotification(this);
    }
}
