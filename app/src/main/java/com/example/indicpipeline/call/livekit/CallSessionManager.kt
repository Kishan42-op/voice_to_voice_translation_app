package com.example.indicpipeline.call.livekit

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.livekit.android.LiveKit
import kotlinx.coroutines.*

/**
 * Manages LiveKit room connection with audio capability using Kotlin coroutines.
 */
class CallSessionManager(private val context: Context) {
    companion object {
        private const val TAG = "LIVEKIT"
    }

    private var room: io.livekit.android.room.Room? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var isMuted = false
    private var isSpeakerOn = true

    private val isConnected = MutableLiveData<Boolean>(false)
    private val isRemoteParticipantConnected = MutableLiveData<Boolean>(false)
    private val connectionError = MutableLiveData<String?>(null)
    private val remoteParticipantName = MutableLiveData<String?>(null)

    init {
        Log.i(TAG, "Init CallSessionManager")
    }

    fun connect(liveKitUrl: String, roomName: String, token: String, localParticipantName: String) {
        Log.i(TAG, "connect() url=$liveKitUrl room=$roomName identity=$localParticipantName")

        scope.launch {
            try {
                requestAudioFocus()

                // Create room
                room = LiveKit.create(context)
                Log.i(TAG, "✓ Room created")

                // Connect on default dispatcher (thread pool)
                withContext(Dispatchers.Default) {
                    room?.connect(liveKitUrl, token)
                }

                Log.i(TAG, "✓ Connected to LiveKit room: $roomName")
                isConnected.postValue(true)
                connectionError.postValue(null)

                // Simulate participant connection for testing (will be replaced by real participant events)
                withContext(Dispatchers.Main) {
                    delay(1000)
                    isRemoteParticipantConnected.postValue(true)
                    remoteParticipantName.postValue("Remote Participant")
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ Connection failed: ${e.message}", e)
                connectionError.postValue("Connection failed: ${e.message}")
                isConnected.postValue(false)
            }
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        Log.i(TAG, "toggleMute: ${if (isMuted) "MUTED" else "UNMUTED"}")

        try {
            scope.launch {
                withContext(Dispatchers.Default) {
                    try {
                        room?.localParticipant?.setMicrophoneEnabled(!isMuted)
                        Log.i(TAG, "✓ Microphone ${if (isMuted) "disabled" else "enabled"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting microphone enabled: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ toggleMute failed: ${e.message}")
        }
    }

    fun isMuted(): Boolean = isMuted

    fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        Log.i(TAG, "toggleSpeaker: ${if (isSpeakerOn) "ON (speaker)" else "OFF (earpiece)"}")

        try {
            audioManager.setSpeakerphoneOn(isSpeakerOn)
            Log.i(TAG, "✓ Speakerphone ${if (isSpeakerOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "✗ toggleSpeaker failed: ${e.message}")
        }
    }

    fun isSpeakerOn(): Boolean = isSpeakerOn

    fun disconnect() {
        Log.i(TAG, "Disconnecting...")

        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    try {
                        // Disable audio
                        room?.localParticipant?.setMicrophoneEnabled(false)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error disabling microphone: ${e.message}")
                    }

                    // Disconnect room
                    room?.disconnect()
                }

                room = null

                // Release audio focus
                audioManager.abandonAudioFocus(null)

                isConnected.postValue(false)
                isRemoteParticipantConnected.postValue(false)
                remoteParticipantName.postValue(null)

                Log.i(TAG, "✓ Disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Disconnect error: ${e.message}")
            }
        }
    }

    fun cleanup() {
        Log.i(TAG, "cleanup()")
        disconnect()
        job.cancel()
    }

    fun getIsConnected(): LiveData<Boolean> = isConnected
    fun getIsRemoteParticipantConnected(): LiveData<Boolean> = isRemoteParticipantConnected
    fun getConnectionError(): LiveData<String?> = connectionError
    fun getRemoteParticipantName(): LiveData<String?> = remoteParticipantName

    private fun requestAudioFocus() {
        try {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            Log.i(TAG, "✓ Audio focus requested")
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request error: ${e.message}")
        }
    }
}

