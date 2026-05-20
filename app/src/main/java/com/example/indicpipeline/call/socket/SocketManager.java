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
        if (serverUrl != null && serverUrl.equals(url) && socket != null) return;
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
            Log.i(TAG, "[SOCKET] Initializing socket to: " + url);
            socket = IO.socket(serverUrl, opts);
            attachCoreListeners();
            reattachListeners();
            // Add an AuthStateListener so we register when the user signs in later
            try {
                if (authStateListener != null) {
                    FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
                    authStateListener = null;
                }
                authStateListener = firebaseAuth -> {
                    if (firebaseAuth.getCurrentUser() != null) {
                        try { registerCurrentUser(); } catch (Exception e) { Log.e(TAG, "[SOCKET] auth register failed", e); }
                    }
                };
                FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
            } catch (Exception e) { Log.w(TAG, "[SOCKET] could not attach auth listener", e); }
        } catch (URISyntaxException e) {
            Log.e(TAG, "[SOCKET] ✗ Invalid socket URL: " + url, e);
        }
    }

    private void reattachListeners() {
        if (socket == null) return;
        synchronized (listeners) {
            for (Map.Entry<String, Emitter.Listener> entry : listeners.entrySet()) {
                Log.i(TAG, "[SOCKET] Re-attaching listener for event: " + entry.getKey());
                socket.off(entry.getKey());
                socket.on(entry.getKey(), entry.getValue());
            }
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
                Log.i(TAG, "[SOCKET] ✓ Socket connected | socketId: " + socket.id());
                try {
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        JSONObject payload = new JSONObject();
                        payload.put("uid", uid);
                        socket.emit("register", payload);
                        Log.i(TAG, "[SOCKET] ✓ Emitted register event for uid: " + uid);
                    }
                } catch (Exception e) { Log.e(TAG, "[SOCKET] register emit failed", e); }
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "[SOCKET] ✗ Socket disconnected");
            }
        });

        socket.on("reconnect", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "[SOCKET] ✓ Socket reconnected");
                try { registerCurrentUser(); } catch (Exception e) { Log.e(TAG, "[SOCKET] auth register failed on reconnect", e); }
            }
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String error = args != null && args.length > 0 ? args[0].toString() : "unknown";
                Log.e(TAG, "[SOCKET] ✗ Connect error to: " + serverUrl);
                Log.e(TAG, "[SOCKET] ✗ Error details: " + error);
            }
        });

        socket.on("register-ack", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "[SOCKET] ✓ Register acknowledged by server");
            }
        });
    }

    /**
     * Emit a register event for the current Firebase user (if any). Safe to call multiple times.
     */
    public synchronized void registerCurrentUser() {
        try {
            if (socket == null) {
                Log.w(TAG, "[SOCKET] registerCurrentUser: socket not initialized");
                return;
            }
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.w(TAG, "[SOCKET] registerCurrentUser: no authenticated user");
                return;
            }
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            JSONObject payload = new JSONObject();
            payload.put("uid", uid);
            socket.emit("register", payload);
            Log.i(TAG, "[SOCKET] ✓ Emitted register event (manual) for uid: " + uid);
        } catch (Exception e) {
            Log.e(TAG, "[SOCKET] registerCurrentUser error", e);
        }
    }

    public synchronized void connect() {
        if (socket == null) return;
        if (!socket.connected()) socket.connect();
    }

    public synchronized void disconnect() {
        if (socket != null && socket.connected()) {
            try { socket.disconnect(); } catch (Exception ignored) {}
        }
    }

    public synchronized void emit(String event, JSONObject payload) {
        if (socket == null) {
            Log.w(TAG, "[SOCKET] Cannot emit " + event + ", socket is null");
            return;
        }
        Log.i(TAG, "[SOCKET] Emitting event: " + event);
        socket.emit(event, payload);
    }

    public synchronized void on(String event, Emitter.Listener listener) {
        if (socket == null) {
            Log.w(TAG, "[SOCKET] Cannot register listener for " + event + ", socket is null. Storing for later.");
            listeners.put(event, listener);
            return;
        }
        // Ensure only one listener per event type exists
        socket.off(event);
        listeners.put(event, listener);
        socket.on(event, listener);
        Log.i(TAG, "[SOCKET] Registered listener for event: " + event);
    }

    public synchronized void off(String event) {
        listeners.remove(event);
        if (socket == null) return;
        socket.off(event);
        Log.i(TAG, "[SOCKET] Unregistered listener for event: " + event);
    }

}



