package com.example.indicpipeline.ui.call;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.indicpipeline.R;
import com.example.indicpipeline.call.livekit.LiveKitManager;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;

/**
 * Active call screen. Minimal controls: mute, speaker, end call.
 */
public class CallActivity extends AppCompatActivity {
    private TextView tvRoom;
    private Button btnMute, btnSpeaker, btnEnd;
    private String roomId, token;
    private LiveKitManager liveKitManager = new LiveKitManager();
    private SignalingRepository signaling = SignalingRepository.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        tvRoom = findViewById(R.id.tvCallRoom);
        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnEnd = findViewById(R.id.btnEndCall);

        roomId = getIntent().getStringExtra("roomId");
        token = getIntent().getStringExtra("livekitToken");

        tvRoom.setText("Room: " + (roomId == null ? "-" : roomId));

        btnMute.setOnClickListener(v -> {
            btnMute.setSelected(!btnMute.isSelected());
        });

        btnSpeaker.setOnClickListener(v -> {
            btnSpeaker.setSelected(!btnSpeaker.isSelected());
        });

        btnEnd.setOnClickListener(v -> {
            CallStateManager.getInstance().setState(CallStateManager.CallState.ENDED);
            signaling.endCall("");
            finish();
        });

        // TODO: connect to LiveKit with token and join room. This is left minimal for initial integration.
    }
}
