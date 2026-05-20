package com.example.indicpipeline.ui.call;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.livekit.LiveKitManager;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.example.indicpipeline.utils.NotificationManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Incoming call screen: fullscreen accept/reject UI.
 */
public class IncomingCallActivity extends AppCompatActivity {
    private TextView tvName;
    private Button btnAccept, btnReject;
    private String callId, fromUid, fromName, roomId;
    private SignalingRepository signaling = SignalingRepository.getInstance();
    private LiveKitManager liveKitManager = new LiveKitManager();

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

        btnAccept.setOnClickListener(v -> {
            // Cancel the notification once user interacts with the call
            NotificationManager.cancelIncomingCallNotification(IncomingCallActivity.this);

            signaling.acceptCall(callId);
            CallStateManager.getInstance().setState(CallStateManager.CallState.CONNECTED);

            new Thread(() -> {
                String identity = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
                String token = liveKitManager.fetchTokenSync(CallConfig.TOKEN_SERVER_BASE_URL, roomId, identity);
                if (token == null || token.isEmpty()) {
                    runOnUiThread(() -> tvName.setText("Failed to join call"));
                    return;
                }

                Intent i = new Intent(IncomingCallActivity.this, CallActivity.class);
                i.putExtra("callId", callId);
                i.putExtra("roomId", roomId);
                i.putExtra("livekitToken", token);
                i.putExtra("isCaller", false); // receiving side
                runOnUiThread(() -> {
                    startActivity(i);
                    finish();
                });
            }).start();
        });

        btnReject.setOnClickListener(v -> {
            // Cancel the notification when rejecting the call
            NotificationManager.cancelIncomingCallNotification(IncomingCallActivity.this);

            signaling.rejectCall(callId);
            CallStateManager.getInstance().setState(CallStateManager.CallState.REJECTED);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel notification when activity is destroyed
        NotificationManager.cancelIncomingCallNotification(this);
    }
}


