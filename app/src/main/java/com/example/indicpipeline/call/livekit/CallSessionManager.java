package com.example.indicpipeline.call.livekit;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.livekit.android.LiveKit;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.RemoteParticipant;
import io.livekit.android.room.track.Track;
import kotlin.Unit;
import kotlinx.coroutines.GlobalScope;

/**
 * Manages a LiveKit room session with proper audio connection, mute/speaker, and cleanup.
 */
public class CallSessionManager {
    private static final String TAG = "LIVEKIT";
    private final Context context;
    private Room room;
    private AudioManager audioManager;
    private boolean isMuted = false;
    private boolean isSpeakerOn = true;

    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isRemoteParticipantConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> connectionError = new MutableLiveData<>(null);
    private final MutableLiveData<String> remoteParticipantName = new MutableLiveData<>(null);

    public CallSessionManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void connect(String liveKitUrl, String roomName, String token, String localParticipantName) {
        Log.i(TAG, "Connecting to: " + roomName);

        try {
            if (room != null) {
                disconnect();
            }

            room = LiveKit.create(context);
            attachRoomListeners();
            requestAudioFocus();

            Log.i(TAG, "Connecting to url=" + liveKitUrl + " room=" + roomName);

            GlobalScope.INSTANCE.launch(throwable -> {
                Log.e(TAG, "Coroutine error", (Throwable) throwable);
                return null;
            }, context1 -> {
                try {
                    if (room != null) {
                        room.connect(
                                liveKitUrl,
                                token,
                                new Room.ConnectOptions().name(localParticipantName)
                        );
                        Log.i(TAG, "✓ Connected to: " + roomName);
                        isConnected.postValue(true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Connection failed: " + e.getMessage());
                    connectionError.postValue("Connection failed: " + e.getMessage());
                    isConnected.postValue(false);
                }
                return Unit.INSTANCE;
            });
        } catch (Exception e) {
            Log.e(TAG, "Setup failed: " + e.getMessage());
            connectionError.postValue("Setup failed: " + e.getMessage());
        }
    }

    public void toggleMute() {
        isMuted = !isMuted;
        Log.i(TAG, "Mute: " + (isMuted ? "ON" : "OFF"));

        try {
            if (room != null && room.getLocalParticipant() != null) {
                room.getLocalParticipant().setAudioEnabled(!isMuted);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling mute: " + e.getMessage());
        }
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        Log.i(TAG, "Speaker: " + (isSpeakerOn ? "ON" : "OFF"));

        try {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        } catch (Exception e) {
            Log.e(TAG, "Error toggling speaker: " + e.getMessage());
        }
    }

    public boolean isSpeakerOn() {
        return isSpeakerOn;
    }

    private void attachRoomListeners() {
        if (room == null) return;

        room.addOnRoomStateChangedListener((room1, isConnected1) -> {
            Log.i(TAG, "State: " + (isConnected1 ? "CONNECTED" : "DISCONNECTED"));
            this.isConnected.postValue(isConnected1);
        });

        room.addOnParticipantListener(new Room.OnParticipantListener() {
            @Override
            public void onParticipantConnected(Room room, RemoteParticipant participant) {
                Log.i(TAG, "Remote joined: " + participant.getName());
                remoteParticipantName.postValue(participant.getName());
                isRemoteParticipantConnected.postValue(true);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant participant) {
                Log.i(TAG, "Remote left: " + participant.getName());
                isRemoteParticipantConnected.postValue(false);
            }
        });

        room.addOnRoomListener(new Room.OnRoomListener() {
            @Override
            public void onRoomMetadataChanged(Room room, String metadata) {}

            @Override
            public void onParticipantMetadataChanged(Room room, RemoteParticipant participant, String metadata) {}

            @Override
            public void onConnectionQualityChanged(Room room, io.livekit.android.room.participant.Participant participant, int quality) {
                Log.i(TAG, "Quality for " + participant.getName() + ": " + quality);
            }

            @Override
            public void onRecordingStatusChanged(Room room, boolean recording) {}
        });
    }

    private void requestAudioFocus() {
        try {
            if (audioManager != null) {
                int result = audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN
                );
                Log.i(TAG, "Audio focus: " + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "OK" : "FAILED"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio focus error: " + e.getMessage());
        }
    }

    public void disconnect() {
        Log.i(TAG, "Disconnecting...");

        try {
            if (audioManager != null) {
                audioManager.abandonAudioFocus(null);
            }

            if (room != null) {
                room.disconnect();
            }

            isConnected.postValue(false);
            isRemoteParticipantConnected.postValue(false);
            isMuted = false;

            Log.i(TAG, "Disconnected");
        } catch (Exception e) {
            Log.e(TAG, "Disconnect error: " + e.getMessage());
        }
    }

    public LiveData<Boolean> getIsConnected() {
        return isConnected;
    }

    public LiveData<Boolean> getIsRemoteParticipantConnected() {
        return isRemoteParticipantConnected;
    }

    public LiveData<String> getConnectionError() {
        return connectionError;
    }

    public LiveData<String> getRemoteParticipantName() {
        return remoteParticipantName;
    }

    public void cleanup() {
        Log.i(TAG, "Cleanup");
        disconnect();
        room = null;
    }
}

