package com.example.indicpipeline.call.state;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Centralized call state manager. Exposes LiveData for UI/ViewModels to observe.
 */
public class CallStateManager {
    public enum CallState {
        IDLE,
        CALLING,
        RINGING,
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

    public void setState(CallState s) { state.postValue(s); }
}

