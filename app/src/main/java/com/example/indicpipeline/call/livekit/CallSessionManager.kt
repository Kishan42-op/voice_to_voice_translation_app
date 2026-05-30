package com.example.indicpipeline.call.livekit

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * CLEAN LiveKit session manager for audio calls.
 *
 * Sends translated audio only via data channel.
 * Does NOT publish microphone to LiveKit (translation pipeline handles audio).
 * Receives remote audio via data channel only.
 */
class CallSessionManager(private val context: Context) {
    companion object {
        private const val TAG = "LIVEKIT"
    }

    private var room: Room? = null
    private var eventJob: Job? = null
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isMuted = false
    private var isSpeakerOn = true

    private val isConnected = MutableLiveData(false)
    private val isRemoteParticipantConnected = MutableLiveData(false)
    private val connectionError = MutableLiveData<String?>(null)
    private val remoteParticipantName = MutableLiveData<String?>(null)

    // Translation pipeline LiveData
    private val remotePreferredLanguage = MutableLiveData<String?>(null)
    private val incomingTranscription = MutableLiveData<String?>(null)
    private val incomingTranslation = MutableLiveData<String?>(null)

    fun getIsConnected(): LiveData<Boolean> = isConnected
    fun getIsRemoteParticipantConnected(): LiveData<Boolean> = isRemoteParticipantConnected
    fun getConnectionError(): LiveData<String?> = connectionError
    fun getRemoteParticipantName(): LiveData<String?> = remoteParticipantName

    fun getRemotePreferredLanguage(): LiveData<String?> = remotePreferredLanguage
    fun getIncomingTranscription(): LiveData<String?> = incomingTranscription
    fun getIncomingTranslation(): LiveData<String?> = incomingTranslation

    fun sendPreferredLanguage(langCode: String) {
        publishData(JSONObject().put("type", "preferred_lang").put("code", langCode))
    }

    fun sendTranscription(text: String) {
        publishData(JSONObject().put("type", "speech").put("text", text))
    }

    fun sendTranslation(text: String) {
        publishData(JSONObject().put("type", "translation").put("text", text))
    }

    // Audio is synthesized locally on receiver; no audio streaming through network

    private fun publishData(json: JSONObject) {
        val r = room ?: return
        if (isConnected.value != true) {
            Log.w(TAG, "[DATA] Cannot publish: Not connected")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val data = json.toString().toByteArray(StandardCharsets.UTF_8)
                r.localParticipant.publishData(data, DataPublishReliability.RELIABLE)
                Log.d(TAG, "[DATA] Published: type=${json.optString("type")} size=${data.size}")
            } catch (e: Exception) {
                Log.e(TAG, "[DATA] Publish failed: ${e.message}")
            }
        }
    }

    private fun handleData(data: ByteArray) {
        try {
            val json = JSONObject(String(data, StandardCharsets.UTF_8))
            val type = json.optString("type")
            Log.i(TAG, "[DATA_RX] Received: type=$type")
            when (type) {
                "preferred_lang" -> {
                    val code = json.optString("code")
                    Log.i(TAG, "[LANG] Peer language: $code")
                    remotePreferredLanguage.postValue(code)
                }
                "speech" -> {
                    val text = json.optString("text")
                    Log.i(TAG, "[TEXT_RX] Transcription: $text")
                    incomingTranscription.postValue(text)
                }
                "translation" -> {
                    val text = json.optString("text")
                    Log.i(TAG, "[TEXT_RX] Translation: $text")
                    incomingTranslation.postValue(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[DATA] Parse failed: ${e.message}")
        }
    }

    fun connect(liveKitUrl: String, roomName: String, token: String, localParticipantName: String) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "[CONNECT] User: $localParticipantName, Room: $roomName")
        Log.i(TAG, "========================================")

        if (liveKitUrl.isBlank() || roomName.isBlank() || token.isBlank()) {
            connectionError.postValue("Missing parameters")
            isConnected.postValue(false)
            return
        }

        scope.launch {
            try {
                disconnectInternal(keepJob = true)
                requestAudioFocus()

                room = LiveKit.create(context, RoomOptions(), LiveKitOverrides())
                attachRoomEventCollector(room!!)
                applyAudioRoute(true)

                // Disable audio track subscription globally to ensure we only receive data
                val connectOptions = ConnectOptions(
                    audio = false, // Do not publish audio
                    video = false  // Do not publish video
                )

                room?.connect(liveKitUrl, token, connectOptions)
                Log.i(TAG, "[CONNECT] ✓ Connected to LiveKit: $roomName")
                isConnected.postValue(true)
                connectionError.postValue(null)

                // IMPORTANT: Hard-disable the microphone from being published to LiveKit.
                // In this architecture, we NEVER want LiveKit to capture or send raw audio.
                try {
                    room?.localParticipant?.setMicrophoneEnabled(false)
                    isMuted = true
                    Log.i(TAG, "[CONNECT] ✓ LiveKit Microphone HARD-DISABLED (using data-channel pipeline)")
                } catch (e: Exception) {
                    Log.w(TAG, "[CONNECT] Could not disable mic: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[CONNECT] ✗ Failed: ${e.message}", e)
                connectionError.postValue("Connection failed: ${e.message}")
                isConnected.postValue(false)
            }
        }
    }

    private fun attachRoomEventCollector(room: Room) {
        eventJob?.cancel()
        eventJob = scope.launch {
            room.events.events.collect { event ->
                Log.d(TAG, "[EVENT] Received: ${event::class.simpleName}")
                when (event) {
                    is RoomEvent.Connected -> {
                        Log.i(TAG, "[EVENT] RoomConnected")
                        isConnected.postValue(true)
                    }
                    is RoomEvent.Disconnected -> {
                        Log.i(TAG, "[EVENT] RoomDisconnected")
                        isConnected.postValue(false)
                        isRemoteParticipantConnected.postValue(false)
                        remoteParticipantName.postValue(null)
                    }
                    is RoomEvent.DataReceived -> handleData(event.data)
                    is RoomEvent.ParticipantConnected -> {
                        val p = event.participant
                        Log.i(TAG, "[EVENT] ParticipantConnected: ${p.identity?.value}")
                        isRemoteParticipantConnected.postValue(true)
                        remoteParticipantName.postValue(p.name?.takeIf { it.isNotBlank() } ?: p.identity?.value)
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val p = event.participant
                        if (event.track.kind == Track.Kind.AUDIO) {
                            Log.w(TAG, "[EVENT] AudioTrackSubscribed from: ${p.identity?.value} - IGNORING (Translation Pipeline only)")
                            // We do not subscribe to audio tracks in this mode
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun toggleMute() {
        val currentMuted = isMuted
        val newMuted = !currentMuted
        isMuted = newMuted
        Log.i(TAG, "[MUTE] Toggle requested: $currentMuted → $newMuted")

        // Note: We no longer enable the LiveKit microphone track here.
        // The Mute state is now purely for the UI and Pipeline management.
        Log.i(TAG, "[MUTE] ✓ State updated: muted=$newMuted (LiveKit Mic remains DISABLED)")
    }

    fun isMuted(): Boolean = isMuted

    fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        Log.i(TAG, "[SPEAKER] Toggle requested: $isSpeakerOn")
        applyAudioRoute(isSpeakerOn)
        Log.i(TAG, "[SPEAKER] ✓ Routing updated: speakerphone=$isSpeakerOn")
    }

    fun isSpeakerOn(): Boolean = isSpeakerOn

    fun cleanup() {
        Log.i(TAG, "[CLEANUP] Starting cleanup")
        scope.launch {
            disconnectInternal(keepJob = false)
        }
    }

    private fun requestAudioFocus() {
        try {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            Log.i(TAG, "[AUDIO_FOCUS] Requested: $res")
        } catch (e: Exception) {
            Log.w(TAG, "[AUDIO_FOCUS] Error: ${e.message}")
        }
    }

    private fun applyAudioRoute(speakerOn: Boolean) {
        try {
            if (speakerOn) {
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            } else {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            
            @Suppress("DEPRECATION")
            val actualSpeaker = audioManager.isSpeakerphoneOn
            val actualMode = audioManager.mode
            Log.i(TAG, "[AUDIO_ROUTE] Applied: speaker=$speakerOn mode=$actualMode")
            Log.d(TAG, "[AUDIO_ROUTE] Verified: actualSpeaker=$actualSpeaker actualMode=$actualMode")
        } catch (e: Exception) {
            Log.w(TAG, "[AUDIO_ROUTE] Error: ${e.message}")
        }
    }

    private suspend fun disconnectInternal(keepJob: Boolean) {
        try {
            eventJob?.cancel()
            eventJob = null
            room?.let {
                it.disconnect()
                it.release()
            }
            room = null
            isConnected.postValue(false)
            isRemoteParticipantConnected.postValue(false)
            remoteParticipantName.postValue(null)
            Log.i(TAG, "[CLEANUP] ✓ Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "[CLEANUP] Error: ${e.message}")
        } finally {
            if (!keepJob) {
                job.cancel()
            }
        }
    }
}

