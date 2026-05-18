package com.example.indicpipeline.call.livekit;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Helper to fetch LiveKit token from existing token server.
 * Keep this minimal: fetchToken executes network call and returns token string or null.
 */
public class LiveKitManager {
    private static final String TAG = "LiveKitManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
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
            String base = tokenServerBaseUrl == null ? "" : tokenServerBaseUrl.trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String tokenEndpoint = base + "/token";

            Log.i(TAG, "Fetching token from: " + tokenEndpoint + " room=" + room + " identity=" + identity);

            // Prefer POST JSON because current server implementation is JSON-first.
            try {
                JSONObject reqJson = new JSONObject();
                reqJson.put("room", room == null ? "" : room);
                reqJson.put("identity", identity == null ? "" : identity);

                RequestBody reqBody = RequestBody.create(reqJson.toString(), JSON);
                Request req = new Request.Builder().url(tokenEndpoint).post(reqBody).build();

                try (Response resp = client.newCall(req).execute()) {
                    if (resp.isSuccessful()) {
                        String body = resp.body() == null ? null : resp.body().string();
                        String token = parseTokenBody(body);
                        if (token != null && !token.isEmpty()) {
                            Log.i(TAG, "Token fetch success via POST");
                            return token;
                        }
                        Log.e(TAG, "POST succeeded but token missing");
                    } else {
                        Log.e(TAG, "POST token fetch failed: HTTP " + resp.code());
                    }
                }
            } catch (JSONException postJsonError) {
                Log.e(TAG, "Failed to build POST JSON", postJsonError);
            }

            // Fallback: GET query variant for compatibility
            String getUrl = tokenEndpoint + "?room=" + qRoom + "&identity=" + qId;
            Log.i(TAG, "Trying GET fallback: " + getUrl);
            Request getReq = new Request.Builder().url(getUrl).get().build();
            try (Response resp = client.newCall(getReq).execute()) {
                if (!resp.isSuccessful()) {
                    Log.e(TAG, "GET token fetch failed: HTTP " + resp.code());
                    return null;
                }
                String body = resp.body() == null ? null : resp.body().string();
                String token = parseTokenBody(body);
                if (token == null || token.isEmpty()) {
                    Log.e(TAG, "GET succeeded but token missing");
                    return null;
                }
                Log.i(TAG, "Token fetch success via GET");
                return token;
            }
        } catch (IOException e) {
            Log.e(TAG, "fetchTokenSync error", e);
            return null;
        }
    }

    @Nullable
    private String parseTokenBody(@Nullable String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return null;

        // JSON response: { "token": "..." }
        if (trimmed.startsWith("{")) {
            try {
                JSONObject json = new JSONObject(trimmed);
                String token = json.optString("token", "");
                return token == null ? null : token.trim();
            } catch (JSONException e) {
                Log.e(TAG, "Invalid token JSON body", e);
                return null;
            }
        }

        // Raw token response
        return trimmed;
    }
}

