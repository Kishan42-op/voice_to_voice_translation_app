package com.example.indicpipeline;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.Observer;

import com.example.indicpipeline.call.CallConfig;
import com.example.indicpipeline.call.manager.CallManager;
import com.example.indicpipeline.call.signaling.CallEvent;
import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.socket.SocketManager;
import com.example.indicpipeline.utils.NotificationManager;

import com.example.indicpipeline.models.GlobalModelManager;

/**
 * Application entry: initialize socket manager and observe incoming calls.
 */
public class App extends Application {
    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Global Model Manager
        GlobalModelManager.getInstance();
        Log.i(TAG, "GlobalModelManager initialized");

        try {
            SocketManager.getInstance().init(CallConfig.SIGNALING_SERVER_URL);
            SocketManager.getInstance().connect();
        } catch (Exception e) { Log.e(TAG, "socket init failed", e); }

        // Initialize CallManager with application context so it can start foreground service and play ringtone
        try {
            CallManager.getInstance().init(getApplicationContext());
        } catch (Exception e) { Log.w(TAG, "CallManager init failed", e); }

        // Observe incoming calls and launch IncomingCallActivity when app is foreground or background.
        SignalingRepository.getInstance().getIncomingCall().observeForever(new Observer<CallEvent>() {
            @Override
            public void onChanged(CallEvent callEvent) {
                if (callEvent == null) return;
                try {
                    Log.i(TAG, "Incoming call received: from=" + callEvent.fromName + " callId=" + callEvent.callId);

                    // Initialize call state centrally BEFORE showing any UI/notification to avoid races
                    CallManager.getInstance().initIncomingCall(callEvent.callId, callEvent.roomId, callEvent.fromUid, callEvent.fromName);
                    Log.i(TAG, "✓ Call state initialized");

                    // Show notification (with ringtone) AFTER central state is initialized
                    NotificationManager.showIncomingCallNotification(
                            getApplicationContext(),
                            callEvent.fromName,
                            callEvent.callId,
                            callEvent.fromUid,
                            callEvent.roomId
                    );
                    Log.i(TAG, "✓ Incoming call notification shown with ringtone");

                    // Then launch incoming UI activity
                    Intent i = new Intent(getApplicationContext(), com.example.indicpipeline.ui.call.IncomingCallActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    i.putExtra("callId", callEvent.callId);
                    i.putExtra("fromUid", callEvent.fromUid);
                    i.putExtra("fromName", callEvent.fromName);
                    i.putExtra("roomId", callEvent.roomId);
                    getApplicationContext().startActivity(i);
                    Log.i(TAG, "✓ IncomingCallActivity launched");
                } catch (Exception e) { Log.e(TAG, "launch incoming failed", e); }
            }
        });
    }
}



