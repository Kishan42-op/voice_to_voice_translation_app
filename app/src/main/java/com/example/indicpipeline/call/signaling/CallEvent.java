package com.example.indicpipeline.call.signaling;

/**
 * Simple call event model used by signaling repository and UI.
 */
public class CallEvent {
    public String callId;
    public String fromUid;
    public String fromName;
    public String toUid;
    public String roomId; // filled when accepted

    public CallEvent() {}
}

