package com.example.indicpipeline.call.livekit;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Helper to fetch LiveKit token from existing token server.
 * Keep this minimal: fetchToken executes network call and returns token string or null.
 */
public class LiveKitManager {
    private static final String TAG = "LiveKitManager";
    private final OkHttpClient client = new OkHttpClient();

    public interface TokenCallback {
        void onToken(String token);
        void onError(String message);
    }

    /**
     * Synchronous token fetch (caller must run on background thread).
     */
    @Nullable
    public String fetchTokenSync(String tokenServerBaseUrl, String room, String identity) {
        try {
            String qRoom = URLEncoder.encode(room == null ? "" : room, StandardCharsets.UTF_8.name());
            String qId = URLEncoder.encode(identity == null ? "" : identity, StandardCharsets.UTF_8.name());
            String url = tokenServerBaseUrl;
            if (!url.endsWith("/")) url += "/";
            url += "token?room=" + qRoom + "&identity=" + qId;

            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    Log.e(TAG, "token fetch failed: " + resp.code());
                    return null;
                }
                String body = resp.body() == null ? null : resp.body().string();
                if (body == null) return null;
                // Server is expected to return raw token or JSON { token: "..." }
                body = body.trim();
                if (body.startsWith("{")) {
                    // Try to extract token minimally
                    int idx = body.indexOf("\"token\"");
                    if (idx >= 0) {
                        int colon = body.indexOf(':', idx);
                        if (colon >= 0) {
                            String rest = body.substring(colon + 1).trim();
                            rest = rest.replaceAll("^[\"]+|[\"]+$", "");
                            rest = rest.replaceAll("[{}\\s]", "");
                            return rest;
                        }
                    }
                }
                return body;
            }
        } catch (IOException e) {
            Log.e(TAG, "fetchTokenSync error", e);
            return null;
        }
    }
}

