package com.example.indicpipeline.call;

/**
 * Central call configuration so signaling/token server URLs are changed in one place.
 * Replace the defaults with your PC/server IP for local testing.
 *
 * Can be overridden at runtime via SettingsActivity (saved to SharedPreferences).
 */
public final class CallConfig {
    private CallConfig() {}

    // Default Socket.IO signaling server URL (can be overridden in SettingsActivity)
    public static final String SIGNALING_SERVER_URL = "http://164.52.199.72:3000/";

    // Default LiveKit token server base URL (matches MainActivity working setup)
    public static final String TOKEN_SERVER_BASE_URL = "https://call-server-x3ug.vercel.app/api";

    // LiveKit server URL - update this to your actual LiveKit server
    // Examples:
    // - Local: ws://192.168.1.2:7880
    // - Cloud: wss://your-workspace.livekit.cloud
    public static final String LIVEKIT_URL = "wss://indicpipelineapp-0vui3jrn.livekit.cloud";

    // For debug: set to true to see detailed logs
    public static final boolean DEBUG_LOGS = true;
}





