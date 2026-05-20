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
    private final MutableLiveData<CallEvent> callRejected = new MutableLiveData<>();
    private final MutableLiveData<CallEvent> callEnded = new MutableLiveData<>();
    private final MutableLiveData<String> callError = new MutableLiveData<>();

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
                    Log.i(TAG, "[CALL_FLOW] ✓ Received incoming-call from " + ev.fromName + " (uid: " + ev.fromUid + ")");
                    incomingCall.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "[CALL_FLOW] ✗ incoming-call parse error", e); }
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
                    Log.i(TAG, "[CALL_FLOW] ✓ Received call-accepted | room: " + ev.roomId);
                    callAccepted.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "[CALL_FLOW] ✗ call-accepted parse error", e); }
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
                    Log.i(TAG, "[CALL_FLOW] ✓ Call initiated with callId: " + ev.callId + " room: " + ev.roomId);
                    callInitiated.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "[CALL_FLOW] ✗ call-initiated error", e); }
            }
        });

        socket.on("call-rejected", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    CallEvent ev = new CallEvent();
                    ev.callId = obj.optString("callId");
                    ev.fromUid = obj.optString("from");
                    ev.toUid = obj.optString("to");
                    ev.roomId = obj.optString("room");
                    Log.i(TAG, "[CALL_FLOW] ✗ Call rejected for callId: " + ev.callId);
                    callRejected.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "[CALL_FLOW] ✗ call-rejected error", e); }
            }
        });

        socket.on("call-ended", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    CallEvent ev = new CallEvent();
                    ev.callId = obj.optString("callId");
                    Log.i(TAG, "[CALL_FLOW] ✗ Call ended for callId: " + ev.callId);
                    callEnded.postValue(ev);
                } catch (Exception e) { Log.e(TAG, "[CALL_FLOW] ✗ call-ended error", e); }
            }
        });

        socket.on("call-error", new io.socket.emitter.Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String reason = obj.optString("reason", "unknown");
                    Log.e(TAG, "[CALL_FLOW] ✗ Call error: " + reason);
                    callError.postValue(reason);
                } catch (Exception e) { Log.e(TAG, "[CALL_FLOW] ✗ call-error parse error", e); }
            }
        });
    }

    public static synchronized SignalingRepository getInstance() {
        if (instance == null) instance = new SignalingRepository();
        return instance;
    }

    /**
     * Clear all current call event LiveData to prevent stale events from affecting new calls.
     */
    public void clearEvents() {
        Log.i(TAG, "[CALL_FLOW] Clearing all signaling events");
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            incomingCall.setValue(null);
            callInitiated.setValue(null);
            callAccepted.setValue(null);
            callRejected.setValue(null);
            callEnded.setValue(null);
            callError.setValue(null);
        } else {
            incomingCall.postValue(null);
            callInitiated.postValue(null);
            callAccepted.postValue(null);
            callRejected.postValue(null);
            callEnded.postValue(null);
            callError.postValue(null);
        }
    }


    public LiveData<CallEvent> getIncomingCall() { return incomingCall; }
    public LiveData<CallEvent> getCallInitiated() { return callInitiated; }
    public LiveData<CallEvent> getCallAccepted() { return callAccepted; }
    public LiveData<CallEvent> getCallRejected() { return callRejected; }
    public LiveData<CallEvent> getCallEnded() { return callEnded; }
    public LiveData<String> getCallError() { return callError; }

    public void callUser(String targetUid, String displayName) {
        try {
            JSONObject p = new JSONObject();
            String from = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
            p.put("to", targetUid);
            p.put("from", from);
            p.put("fromName", displayName == null ? "" : displayName);
            Log.i(TAG, "[CALL_FLOW] Emitting call-user -> " + targetUid);
            socket.emit("call-user", p);
        } catch (JSONException e) { Log.e(TAG, "[CALL_FLOW] callUser json error", e); }
    }

    public void acceptCall(String callId) {
        try {
            JSONObject p = new JSONObject();
            p.put("callId", callId);
            String from = FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
            p.put("from", from);
            Log.i(TAG, "[CALL_FLOW] Emitting call-accepted for " + callId);
            socket.emit("call-accepted", p);
        } catch (JSONException e) { Log.e(TAG, "[CALL_FLOW] acceptCall json error", e); }
    }

    public void rejectCall(String callId) {
        try {
            JSONObject p = new JSONObject();
            p.put("callId", callId);
            p.put("from", FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid());
            Log.i(TAG, "[CALL_FLOW] Emitting call-rejected for " + callId);
            socket.emit("call-rejected", p);
        } catch (JSONException e) { Log.e(TAG, "[CALL_FLOW] rejectCall json error", e); }
    }

    public void endCall(String callId) {
        try {
            JSONObject p = new JSONObject();
            p.put("callId", callId);
            p.put("from", FirebaseAuth.getInstance().getCurrentUser() == null ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid());
            Log.i(TAG, "[CALL_FLOW] Emitting call-ended for " + callId);
            socket.emit("call-ended", p);
        } catch (JSONException e) { Log.e(TAG, "[CALL_FLOW] endCall json error", e); }
    }

}




