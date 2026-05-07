package com.example.indicpipeline;

import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class InternetCallClient {

    public interface Listener {
        void onConnected();
        void onDisconnected(@Nullable String reason);
        void onIncomingSpeech(String fromUser, String text, String srcTransCode);
        void onIncomingInfo(String message);
    }

    private final OkHttpClient httpClient;
    private final Listener listener;

    private volatile WebSocket ws;
    private volatile boolean joined = false;
    private String selfUser;
    private String roomId;

    public InternetCallClient(Listener listener) {
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .build();
    }

    public synchronized void connect(String wsUrl, String roomId, String selfUser) {
        disconnect("reconnect");
        this.roomId = roomId;
        this.selfUser = selfUser;
        this.joined = false;

        Request req = new Request.Builder().url(wsUrl).build();
        ws = httpClient.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    JSONObject join = new JSONObject();
                    join.put("type", "join");
                    join.put("room", InternetCallClient.this.roomId);
                    join.put("user", InternetCallClient.this.selfUser);
                    webSocket.send(join.toString());
                    joined = true;
                } catch (JSONException ignored) {}
                listener.onConnected();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    String type = msg.optString("type", "");
                    if ("speech".equals(type)) {
                        String from = msg.optString("from", "peer");
                        if (InternetCallClient.this.selfUser != null && InternetCallClient.this.selfUser.equals(from)) {
                            return;
                        }
                        String body = msg.optString("text", "");
                        String src = msg.optString("src", "");
                        if (!body.trim().isEmpty() && !src.trim().isEmpty()) {
                            listener.onIncomingSpeech(from, body, src);
                        }
                    } else if ("info".equals(type)) {
                        listener.onIncomingInfo(msg.optString("message", text));
                    } else {
                        listener.onIncomingInfo(text);
                    }
                } catch (Exception e) {
                    listener.onIncomingInfo(text);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                joined = false;
                listener.onDisconnected(reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                joined = false;
                listener.onDisconnected(t != null ? t.getMessage() : "ws failure");
            }
        });
    }

    public synchronized void disconnect(@Nullable String reason) {
        WebSocket cur = ws;
        ws = null;
        joined = false;
        if (cur != null) {
            cur.close(1000, reason == null ? "disconnect" : reason);
        }
    }

    public boolean isConnected() {
        return ws != null && joined;
    }

    public void sendSpeech(String text, String srcTransCode) {
        WebSocket cur = ws;
        if (cur == null || !joined) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "speech");
            msg.put("room", roomId);
            msg.put("from", selfUser);
            msg.put("src", srcTransCode);
            msg.put("text", text);
            cur.send(msg.toString());
        } catch (JSONException ignored) {}
    }
}

