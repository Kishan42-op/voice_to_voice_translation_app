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
    public static final String SIGNALING_SERVER_URL = "https://voicetovoicetranslationapp-production.up.railway.app/";

    // Default LiveKit token server base URL (can be overridden in SettingsActivity)
    public static final String TOKEN_SERVER_BASE_URL = "https://voicetovoicetranslationapp-production.up.railway.app/";
}


