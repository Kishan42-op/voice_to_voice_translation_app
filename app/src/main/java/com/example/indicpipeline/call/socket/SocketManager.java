package com.example.indicpipeline.call.socket;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth.AuthStateListener;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Singleton socket manager for app-wide websocket connection (signaling only).
 * Responsibilities:
 *  - maintain persistent connection
 *  - register authenticated user with backend (emit "register")
 *  - provide subscribe/emit helpers
 */
public class SocketManager {
    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private String serverUrl;
    private final Map<String, Emitter.Listener> listeners = Collections.synchronizedMap(new HashMap<>());
    private AuthStateListener authStateListener;

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    public synchronized void init(String url) {
        if (url == null) throw new IllegalArgumentException("url required");
        if (serverUrl != null && serverUrl.equals(url)) return;
        serverUrl = url;
        if (socket != null) {
            try { socket.disconnect(); } catch (Exception ignored) {}
            socket = null;
        }
        try {
            IO.Options opts = new IO.Options();
            opts.reconnection = true;
            opts.reconnectionAttempts = Integer.MAX_VALUE;
            opts.forceNew = true;
            // Use only WebSocket transport (no polling which can fail silently)
            opts.transports = new String[]{"websocket"};
            Log.i(TAG, "Initializing socket to: " + url);
            socket = IO.socket(serverUrl, opts);
            attachCoreListeners();
            // Add an AuthStateListener so we register when the user signs in later
            try {
                if (authStateListener != null) {
                    FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
                    authStateListener = null;
                }
                authStateListener = firebaseAuth -> {
                    if (firebaseAuth.getCurrentUser() != null) {
                        // If socket is connected, emit register immediately; otherwise it will register on connect
                        try { registerCurrentUser(); } catch (Exception e) { Log.e(TAG, "auth register failed", e); }
                    }
                };
                FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
            } catch (Exception e) { Log.w(TAG, "could not attach auth listener", e); }
        } catch (URISyntaxException e) {
            Log.e(TAG, "✗ Invalid socket URL: " + url, e);
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    private void attachCoreListeners() {
        if (socket == null) return;
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "✓ Socket connected | socketId: " + socket.id());
                // register authenticated user
                try {
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        JSONObject payload = new JSONObject();
                        payload.put("uid", uid);
                        socket.emit("register", payload);
                        Log.i(TAG, "✓ Emitted register event for uid: " + uid);
                    }
                } catch (Exception e) { Log.e(TAG, "register emit failed", e); }
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "✗ Socket disconnected");
            }
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String error = args != null && args.length > 0 ? args[0].toString() : "unknown";
                Log.e(TAG, "✗ Connect error to: " + serverUrl);
                Log.e(TAG, "✗ Error details: " + error);
                if (args != null && args.length > 0 && args[0] instanceof Exception) {
                    ((Exception) args[0]).printStackTrace();
                }
            }
        });

        socket.on("register-ack", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "✓ Register acknowledged by server");
            }
        });
    }

    /**
     * Emit a register event for the current Firebase user (if any). Safe to call multiple times.
     */
    public synchronized void registerCurrentUser() {
        try {
            if (socket == null) throw new IllegalStateException("SocketManager not initialized");
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.w(TAG, "registerCurrentUser: no authenticated user");
                return;
            }
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            JSONObject payload = new JSONObject();
            payload.put("uid", uid);
            socket.emit("register", payload);
            Log.i(TAG, "✓ Emitted register event (manual) for uid: " + uid);
        } catch (Exception e) {
            Log.e(TAG, "registerCurrentUser error", e);
        }
    }

    public synchronized void connect() {
        if (socket == null) throw new IllegalStateException("SocketManager not initialized");
        if (!socket.connected()) socket.connect();
    }

    public synchronized void disconnect() {
        if (socket != null && socket.connected()) {
            try { socket.disconnect(); } catch (Exception ignored) {}
        }
    }

    public synchronized void emit(String event, JSONObject payload) {
        if (socket == null) throw new IllegalStateException("SocketManager not initialized");
        socket.emit(event, payload);
    }

    public synchronized void on(String event, Emitter.Listener listener) {
        if (socket == null) throw new IllegalStateException("SocketManager not initialized");
        listeners.put(event, listener);
        socket.on(event, listener);
    }

    public synchronized void off(String event) {
        if (socket == null) return;
        Emitter.Listener l = listeners.remove(event);
        if (l != null) socket.off(event, l);
    }
}



