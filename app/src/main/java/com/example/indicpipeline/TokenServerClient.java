package com.example.indicpipeline;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TokenServerClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    public String fetchToken(String tokenServerBaseUrl, String room, String identity) throws IOException {
        String base = tokenServerBaseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        final String bodyStr;
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("room", room);
            bodyJson.put("identity", identity);
            bodyStr = bodyJson.toString();
        } catch (JSONException e) {
            throw new IOException("Failed to build JSON body", e);
        }

        RequestBody body = RequestBody.create(bodyStr, JSON);
        Request req = new Request.Builder()
                .url(base + "/token")
                .post(body)
                .build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                throw new IOException("Token server HTTP " + res.code());
            }
            String raw = res.body() != null ? res.body().string() : "";
            final String token;
            try {
                JSONObject out = new JSONObject(raw);
                token = out.optString("token", "");
            } catch (JSONException e) {
                throw new IOException("Invalid JSON response from token server", e);
            }
            if (token == null || token.trim().isEmpty()) {
                throw new IOException("Token missing in response");
            }
            return token.trim();
        }
    }
}

