package com.example.indicpipeline.call.state;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Centralized call state manager. Exposes LiveData for UI/ViewModels to observe.
 */
public class CallStateManager {
    private static final String TAG = "CallStateManager";

    public enum CallState {
        IDLE,
        CALLING,
        RINGING,
        CONNECTING,
        CONNECTED,
        REJECTED,
        ENDED,
        MISSED
    }

    private static CallStateManager instance;
    private final MutableLiveData<CallState> state = new MutableLiveData<>(CallState.IDLE);

    private CallStateManager() {}

    public static synchronized CallStateManager getInstance() {
        if (instance == null) instance = new CallStateManager();
        return instance;
    }

    public LiveData<CallState> getState() { return state; }

    public void setState(CallState s) {
        CallState current = state.getValue();
        if (current == s) {
            Log.i(TAG, "State unchanged: " + s);
            return;
        }
        Log.i(TAG, "Transition: " + (current == null ? "null" : current.name()) + " -> " + s);
        state.postValue(s);
    }
}

