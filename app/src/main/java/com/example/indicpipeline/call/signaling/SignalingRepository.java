package com.example.indicpipeline.call.signaling;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.indicpipeline.call.socket.SocketManager;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Repository responsible for emitting and receiving signaling events via SocketManager.
 * Exposes LiveData for incoming call and call-accepted events.
 */
public class SignalingRepository {
    private static final String TAG = "SignalingRepo";
    private static SignalingRepository instance;
    private final SocketManager socket = SocketManager.getInstance();

    private final MutableLiveData<CallEvent> incomingCall = new MutableLiveData<>();
    private final MutableLiveData<CallEvent> callInitiated = new MutableLiveData<>();
    private final MutableLiveData<CallEvent> callAccepted = new MutableLiveData<>();

    private SignalingRepository() {
        // listen for incoming-call
        socket.on("incoming-call", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    CallEvent ev = new CallEvent();
                    ev.callId = obj.optString("callId");
                    ev.fromUid = obj.optString("from");
                    ev.fromName = obj.optString("fromName");
                    ev.toUid = obj.optString("to");
                    ev.roomId = obj.optString("room");
                    Log.i(TAG, "✓ Received incoming-call from " + ev.fromName + " (uid: " + ev.fromUid + ")");
                    incomingCall.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "✗ incoming-call parse error", e); }
            }
        });

        socket.on("call-accepted", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    CallEvent ev = new CallEvent();
                    ev.callId = obj.optString("callId");
                    ev.fromUid = obj.optString("from");
                    ev.toUid = obj.optString("to");
                    ev.roomId = obj.optString("room");
                    Log.i(TAG, "✓ Received call-accepted | room: " + ev.roomId);
                    callAccepted.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "✗ call-accepted parse error", e); }
            }
        });

        socket.on("call-initiated", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    CallEvent ev = new CallEvent();
                    ev.callId = obj.optString("callId");
                    ev.roomId = obj.optString("room");
                    Log.i(TAG, "✓ Call initiated with callId: " + ev.callId + " room: " + ev.roomId);
                    callInitiated.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "✗ call-initiated error", e); }
            }
        });

        socket.on("call-rejected", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    Log.i(TAG, "✗ Call rejected for callId: " + obj.optString("callId"));
                } catch (Exception e) { Log.e(TAG, "✗ call-rejected error", e); }
            }
        });

        socket.on("call-error", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String reason = obj.optString("reason", "unknown");
                    Log.e(TAG, "✗ Call error: " + reason);
                } catch (Exception e) { Log.e(TAG, "✗ call-error parse error", e); }
            }
        });
    }

    public static synchronized SignalingRepository getInstance() {
        if (instance == null) instance = new SignalingRepository();
        return instance;
    }

    public LiveData<CallEvent> getIncomingCall() { return incomingCall; }
    public LiveData<CallEvent> getCallInitiated() { return callInitiated; }
    public LiveData<CallEvent> getCallAccepted() { return callAccepted; }

    public void callUser(String targetUid, String displayName) {
        try {
            JSONObject p = new JSONObject();
            String from = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
            p.put("to", targetUid);
            p.put("from", from);
            p.put("fromName", displayName == null ? "" : displayName);
            socket.emit("call-user", p);
            Log.i(TAG, "emitted call-user -> " + targetUid);
        } catch (JSONException e) { Log.e(TAG, "callUser json error", e); }
    }

    public void acceptCall(String callId) {
        try {
            JSONObject p = new JSONObject();
            p.put("callId", callId);
            String from = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
            p.put("from", from);
            socket.emit("call-accepted", p);
            Log.i(TAG, "emitted call-accepted for " + callId);
        } catch (JSONException e) { Log.e(TAG, "acceptCall json error", e); }
    }

    public void rejectCall(String callId) {
        try {
            JSONObject p = new JSONObject();
            p.put("callId", callId);
            p.put("from", FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid());
            socket.emit("call-rejected", p);
            Log.i(TAG, "emitted call-rejected for " + callId);
        } catch (JSONException e) { Log.e(TAG, "rejectCall json error", e); }
    }

    public void endCall(String callId) {
        try {
            JSONObject p = new JSONObject();
            p.put("callId", callId);
            p.put("from", FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid());
            socket.emit("call-ended", p);
            Log.i(TAG, "emitted call-ended for " + callId);
        } catch (JSONException e) { Log.e(TAG, "endCall json error", e); }
    }
}




