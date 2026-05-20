package com.example.indicpipeline.call.manager;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.indicpipeline.call.signaling.SignalingRepository;
import com.example.indicpipeline.call.state.CallStateManager;
import com.example.indicpipeline.utils.RingtonePlayer;
import io.livekit.android.room.Room;

/**
 * Centralized Call Manager singleton (PHASE 2).
 *
 * Responsibilities:
 * - Maintain current call state, room, peer info
 * - Hold reference to active LiveKit Room (survives Activity recreation)
 * - Provide connect/disconnect methods (only call disconnect on explicit user action or call end)
 * - Handle socket re-registration
 *
 * CRITICAL: Activities should ONLY observe state via LiveData.
 * Activities should NOT own LiveKit lifecycle.
 */
public class CallManager {
    private static final String TAG = "CallManager";
    private static CallManager instance;

    private Room currentRoom;
    private String currentCallId;
    private String currentRoomId;
    private String currentPeerUid;
    private String currentPeerName;
    private boolean isCaller;
    private Context appContext;

    private final MutableLiveData<CallStateManager.CallState> callState = new MutableLiveData<>(CallStateManager.CallState.IDLE);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final SignalingRepository signaling = SignalingRepository.getInstance();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private java.util.concurrent.ScheduledFuture<?> timeoutFuture;
    private final RingtonePlayer ringtonePlayer = new RingtonePlayer();

    private CallManager() {}

    /**
     * Initialize CallManager with application context. Call once from Application.onCreate().
     */
    public synchronized void init(Context context) {
        if (this.appContext == null && context != null) {
            this.appContext = context.getApplicationContext();
        }
    }

    public static synchronized CallManager getInstance() {
        if (instance == null) instance = new CallManager();
        return instance;
    }

    /**
     * Initialize a new outgoing call.
     */
    public synchronized void initOutgoingCall(String targetUid, String targetName) {
        Log.i(TAG, "[CALL_FLOW] initOutgoingCall: targetUid=" + targetUid + " targetName=" + targetName);
        if (!canStartNewCall()) {
            Log.w(TAG, "[CALL_FLOW] Call already in progress; cannot start new outgoing call");
            return;
        }
        signaling.clearEvents(); // Clear stale events before starting
        currentPeerUid = targetUid;
        currentPeerName = targetName;
        isCaller = true;
        currentCallId = null;
        currentRoomId = null;
        setCallState(CallStateManager.CallState.CALLING);
    }

    /**
     * Initialize a new incoming call.
     */
    public synchronized void initIncomingCall(String callId, String roomId, String fromUid, String fromName) {
        Log.i(TAG, "[CALL_FLOW] initIncomingCall: callId=" + callId + " roomId=" + roomId + " fromUid=" + fromUid);
        // If the same callId is already set, treat this as idempotent and return
        if (currentCallId != null && currentCallId.equals(callId)) {
            Log.i(TAG, "[CALL_FLOW] initIncomingCall: already initialized for callId=" + callId);
            return;
        }

        if (!canStartNewCall()) {
            Log.w(TAG, "[CALL_FLOW] Call already in progress; cannot accept new incoming call");
            return;
        }

        signaling.clearEvents(); // Clear stale events
        currentCallId = callId;
        currentRoomId = roomId;
        currentPeerUid = fromUid;
        currentPeerName = fromName;
        isCaller = false;
        setCallState(CallStateManager.CallState.RINGING);
    }


    /**
     * Set the active Room reference (after LiveKit connect).
     */
    public synchronized void setRoom(Room room) {
        this.currentRoom = room;
    }

    /**
     * Set call IDs after signaling ack.
     */
    public synchronized void setCallIds(String callId, String roomId) {
        this.currentCallId = callId;
        this.currentRoomId = roomId;
    }

    /**
     * Transition call to connected state.
     * Only call this when the actual LiveKit room connection is established,
     * NOT when UI opens.
     */
    public synchronized void setConnected() {
        Log.i(TAG, "setConnected: callId=" + currentCallId + " roomId=" + currentRoomId);
        setCallState(CallStateManager.CallState.CONNECTED);
    }

    /**
     * Transition to CONNECTING while token is being fetched / LiveKit room is joining.
     */
    public synchronized void setConnecting() {
        Log.i(TAG, "setConnecting: callId=" + currentCallId + " roomId=" + currentRoomId);
        setCallState(CallStateManager.CallState.CONNECTING);
    }

    /**
     * Caller/receiver should enter CONNECTING after acceptance is exchanged.
     */
    public synchronized void handleCallAccepted(String callId, String roomId) {
        Log.i(TAG, "handleCallAccepted: callId=" + callId + " roomId=" + roomId);
        if (callId != null) currentCallId = callId;
        if (roomId != null) currentRoomId = roomId;
        setConnecting();
    }

    /**
     * Remote peer rejected the call; keep the rejection state visible until UI clears it.
     */
    public synchronized void handleRemoteRejected(String callId) {
        Log.i(TAG, "handleRemoteRejected: callId=" + callId);
        if (callId != null && currentCallId != null && !callId.equals(currentCallId)) {
            Log.w(TAG, "Ignoring rejected call for stale callId=" + callId);
            return;
        }
        finishTerminalState(CallStateManager.CallState.REJECTED, false, false);
    }

    /**
     * Remote peer ended the call. If we were connected, show ENDED; if still waiting, show MISSED.
     */
    public synchronized void handleRemoteEnded(String callId) {
        Log.i(TAG, "handleRemoteEnded: callId=" + callId + " currentState=" + getCurrentState());
        if (callId != null && currentCallId != null && !callId.equals(currentCallId)) {
            Log.w(TAG, "Ignoring ended call for stale callId=" + callId);
            return;
        }
        CallStateManager.CallState terminal = getCurrentState() == CallStateManager.CallState.CONNECTED
                || getCurrentState() == CallStateManager.CallState.CONNECTING
                ? CallStateManager.CallState.ENDED
                : CallStateManager.CallState.MISSED;
        finishTerminalState(terminal, false, false);
    }

    /**
     * End the active call.
     * ONLY called when user taps "End Call" button or call times out.
     * NOT called from onDestroy() or onBackPressed().
     */
    public synchronized void endCall() {
        Log.i(TAG, "endCall");
        finishTerminalState(CallStateManager.CallState.ENDED, true, true);
    }

    /**
     * Reject an incoming call (before joining).
     * Does NOT affect any live connection (none exists yet).
     */
    public synchronized void rejectCall(String callId) {
        Log.i(TAG, "rejectCall: callId=" + callId);
        signaling.rejectCall(callId);
        finishTerminalState(CallStateManager.CallState.REJECTED, false, true);
    }

    /**
     * Handle call timeout (auto-cleanup after 30 seconds).
     */
    public synchronized void handleCallTimeout() {
        Log.i(TAG, "handleCallTimeout: callId=" + currentCallId + " state=" + getCurrentState());
        finishTerminalState(CallStateManager.CallState.MISSED, true, true);
    }

    // Getters
    public LiveData<CallStateManager.CallState> getCallState() { return callState; }
    public LiveData<String> getError() { return error; }

    public Room getRoom() { return currentRoom; }
    public String getCallId() { return currentCallId; }
    public String getRoomId() { return currentRoomId; }
    public String getPeerUid() { return currentPeerUid; }
    public String getPeerName() { return currentPeerName; }
    public boolean isCaller() { return isCaller; }
    public CallStateManager.CallState getCurrentState() {
        CallStateManager.CallState state = callState.getValue();
        return state != null ? state : CallStateManager.CallState.IDLE;
    }

    public synchronized void resetToIdleAfterUiDismiss() {
        Log.i(TAG, "resetToIdleAfterUiDismiss");
        resetCallFields();
        setCallState(CallStateManager.CallState.IDLE);
    }

    // Private helper
    private void setCallState(CallStateManager.CallState state) {
        Log.i(TAG, "setCallState: " + state + " callId=" + currentCallId + " roomId=" + currentRoomId);
        callState.postValue(state);
        CallStateManager.getInstance().setState(state);

        // Side-effects for lifecycle: play/stop tones and schedule timeouts
        switch (state) {
            case RINGING:
                // Incoming call: play ringtone and schedule timeout (no FGS)
                try { ringtonePlayer.playRingtone(appContext); } catch (Exception e) { Log.w(TAG, "ringtone play failed", e); }
                scheduleTimeoutIfNeeded();
                break;
            case CALLING:
                // Outgoing caller should not hear endless ringback; only wait and schedule timeout.
                scheduleTimeoutIfNeeded();
                break;
            case CONNECTED:
                // Stop any tones
                try { ringtonePlayer.stop(); } catch (Exception ignored) {}
                // Cancel timeout
                if (timeoutFuture != null) { timeoutFuture.cancel(false); timeoutFuture = null; }
                break;
            case CONNECTING:
                // Stop any tones while joining; keep service and allow room connection
                try { ringtonePlayer.stop(); } catch (Exception ignored) {}
                if (timeoutFuture != null) { timeoutFuture.cancel(false); timeoutFuture = null; }
                break;
            case REJECTED:
            case ENDED:
            case MISSED:
                // Stop tones
                try { ringtonePlayer.stop(); } catch (Exception ignored) {}
                if (timeoutFuture != null) { timeoutFuture.cancel(false); timeoutFuture = null; }
                break;
            default:
                break;
        }
    }

    /**
     * Reset to IDLE state (e.g., after call has ended and UI is dismissed).
     */
    public synchronized void resetToIdle() {
        Log.i(TAG, "resetToIdle");
        currentCallId = null;
        currentRoomId = null;
        currentPeerUid = null;
        currentPeerName = null;
        isCaller = false;
        setCallState(CallStateManager.CallState.IDLE);
    }

    private void startForegroundService() {
        Log.i(TAG, "Foreground service disabled; skipping startForegroundService()");
    }

    private void stopForegroundService() {
        Log.i(TAG, "Foreground service disabled; skipping stopForegroundService()");
    }

    private void scheduleTimeoutIfNeeded() {
        // Cancel previous
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        // Schedule 30s timeout for RINGING or CALLING
        timeoutFuture = scheduler.schedule(() -> {
            Log.i(TAG, "Call timeout triggered");
            handleCallTimeout();
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
    }

    private synchronized void finishTerminalState(CallStateManager.CallState terminalState, boolean emitCallEnded, boolean resetFields) {
        Log.i(TAG, "finishTerminalState: state=" + terminalState + " emitCallEnded=" + emitCallEnded + " resetFields=" + resetFields);

        if (emitCallEnded && currentCallId != null && !currentCallId.isEmpty()) {
            signaling.endCall(currentCallId);
        }

        stopActiveMediaAndTimers();

        if (resetFields) {
            resetCallFields();
        }

        setCallState(terminalState);
    }

    private void stopActiveMediaAndTimers() {
        if (currentRoom != null) {
            try {
                currentRoom.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting room", e);
            }
            currentRoom = null;
        }

        try {
            ringtonePlayer.stop();
        } catch (Exception ignored) {}

        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    private void resetCallFields() {
        currentCallId = null;
        currentRoomId = null;
        currentPeerUid = null;
        currentPeerName = null;
        isCaller = false;
    }

    private boolean canStartNewCall() {
        CallStateManager.CallState state = callState.getValue();
        return state == null
                || state == CallStateManager.CallState.IDLE
                || state == CallStateManager.CallState.ENDED
                || state == CallStateManager.CallState.REJECTED
                || state == CallStateManager.CallState.MISSED;
    }
}









