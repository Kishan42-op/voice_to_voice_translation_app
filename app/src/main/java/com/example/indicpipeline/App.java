package com.example.indicpipeline;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.Observer;

import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.signaling.CallEvent;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.socket.SocketManager;

/**
 * Application entry: initialize socket manager and observe incoming calls.
 */
public class App extends Application {
    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            SocketManager.getInstance().init(CallConfig.SIGNALING_SERVER_URL);
            SocketManager.getInstance().connect();
        } catch (Exception e) { Log.e(TAG, "socket init failed", e); }

        // Observe incoming calls and launch IncomingCallActivity when app is foreground or background.
        SignalingRepository.getInstance().getIncomingCall().observeForever(new Observer<CallEvent>() {
            @Override
            public void onChanged(CallEvent callEvent) {
                if (callEvent == null) return;
                try {
                    Intent i = new Intent(getApplicationContext(), com.example.indicpipeline.ui.call.IncomingCallActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    i.putExtra("callId", callEvent.callId);
                    i.putExtra("fromUid", callEvent.fromUid);
                    i.putExtra("fromName", callEvent.fromName);
                    i.putExtra("roomId", callEvent.roomId);
                    getApplicationContext().startActivity(i);
                } catch (Exception e) { Log.e(TAG, "launch incoming failed", e); }
            }
        });
    }
}



