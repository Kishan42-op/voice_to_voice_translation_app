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
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * Real LiveKit session manager for audio calls.
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

    // New LiveData for translation pipeline
    private val remotePreferredLanguage = MutableLiveData<String?>(null)
    private val incomingTranscription = MutableLiveData<String?>(null)
    private val incomingTranslation = MutableLiveData<String?>(null)

    interface AudioDataListener {
        fun onAudioDataReceived(data: ByteArray)
    }
    private var audioDataListener: AudioDataListener? = null

    fun setAudioDataListener(listener: AudioDataListener?) {
        this.audioDataListener = listener
    }

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

    fun pushAudio(audio: ByteArray) {
        // Fallback to Data Channel for stability in prototype
        sendAudio(audio)
    }

    fun sendAudio(audio: ByteArray) {
        val chunkSize = 16384 // 16KB chunks to stay safe with Data Channel limits
        val totalSize = audio.size
        var offset = 0
        var chunkIndex = 0
        val totalChunks = (totalSize + chunkSize - 1) / chunkSize

        Log.i(TAG, "[PIPELINE] sending translated audio chunk: Total size ${audio.size} bytes. Splitting into $totalChunks chunks.")

        while (offset < totalSize) {
            val length = Math.min(chunkSize, totalSize - offset)
            val chunk = ByteArray(length)
            System.arraycopy(audio, offset, chunk, 0, length)
            
            val base64Audio = Base64.encodeToString(chunk, Base64.NO_WRAP)
            Log.d(TAG, "[PIPELINE] Sending audio chunk ${chunkIndex + 1}/$totalChunks (${chunk.size} bytes)")
            
            publishData(JSONObject()
                .put("type", "audio")
                .put("data", base64Audio)
                .put("index", chunkIndex)
                .put("total", totalChunks)
            )
            
            offset += length
            chunkIndex++
        }
        Log.i(TAG, "[PIPELINE] All audio chunks sent.")
    }

    private fun publishData(json: JSONObject) {
        val r = room ?: return
        if (isConnected.value != true) {
            Log.w(TAG, "[PIPELINE] Cannot publish data: Not connected")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val data = json.toString().toByteArray(StandardCharsets.UTF_8)
                r.localParticipant.publishData(data, DataPublishReliability.RELIABLE)
                Log.d(TAG, "[PIPELINE] Data published to room: ${json.optString("type")} (${data.size} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "[PIPELINE] Failed to publish data: ${e.message}")
            }
        }
    }

    private fun handleData(data: ByteArray) {
        try {
            val json = JSONObject(String(data, StandardCharsets.UTF_8))
            val type = json.optString("type")
            Log.i(TAG, "[PIPELINE] Received data channel message: $type")
            when (type) {
                "preferred_lang" -> {
                    val code = json.optString("code")
                    Log.i(TAG, "[PIPELINE] Peer preferred language: $code")
                    remotePreferredLanguage.postValue(code)
                }
                "speech" -> {
                    val text = json.optString("text")
                    Log.i(TAG, "[PIPELINE] Peer transcription: $text")
                    incomingTranscription.postValue(text)
                }
                "translation" -> {
                    val text = json.optString("text")
                    Log.i(TAG, "[PIPELINE] Peer translation: $text")
                    incomingTranslation.postValue(text)
                }
                "audio" -> {
                    val base64Data = json.optString("data")
                    val index = json.optInt("index", 0)
                    val total = json.optInt("total", 1)
                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    Log.i(TAG, "[PIPELINE] peer audio chunk received: ${audioBytes.size} bytes (Chunk ${index + 1}/$total)")
                    
                    // Directly invoke listener on background thread to avoid LiveData/MainThread bottleneck
                    audioDataListener?.onAudioDataReceived(audioBytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PIPELINE] Failed to parse data: ${e.message}")
        }
    }

    fun connect(liveKitUrl: String, roomName: String, token: String, localParticipantName: String) {
        Log.i(TAG, "====================================================")
        Log.i(TAG, "[VERSION_TAG] CALL_SESSION_MANAGER_V3_CONNECT")
        Log.i(TAG, "====================================================")
        
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

                room?.connect(liveKitUrl, token, ConnectOptions())
                Log.i(TAG, "✓ Connected to LiveKit room: $roomName")
                isConnected.postValue(true)
                connectionError.postValue(null)

                // For Translation Pipeline, we MUTE the standard microphone by default
                // to avoid feedback and raw audio leakage.
                room?.localParticipant?.setMicrophoneEnabled(false)
                isMuted = true
                Log.i(TAG, "✓ Standard mic muted (using Pipeline).")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Connection failed: ${e.message}", e)
                connectionError.postValue("Connection failed: ${e.message}")
                isConnected.postValue(false)
            }
        }
    }

    private fun attachRoomEventCollector(room: Room) {
        eventJob?.cancel()
        eventJob = scope.launch {
            room.events.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        isConnected.postValue(true)
                    }
                    is RoomEvent.Disconnected -> {
                        isConnected.postValue(false)
                        isRemoteParticipantConnected.postValue(false)
                        remoteParticipantName.postValue(null)
                    }
                    is RoomEvent.DataReceived -> handleData(event.data)
                    is RoomEvent.ParticipantConnected -> {
                        val p = event.participant
                        isRemoteParticipantConnected.postValue(true)
                        remoteParticipantName.postValue(p.name?.takeIf { it.isNotBlank() } ?: p.identity?.value)
                        Log.i(TAG, "Participant connected: ${p.identity?.value}")
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val p = event.participant
                        if (event.track.kind == Track.Kind.AUDIO) {
                            isRemoteParticipantConnected.postValue(true)
                            remoteParticipantName.postValue(p.name?.takeIf { it.isNotBlank() } ?: p.identity?.value)
                            Log.i(TAG, "Audio track subscribed from: ${p.identity?.value}")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        scope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(!isMuted)
                Log.i(TAG, "Mute toggled: $isMuted")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling mute", e)
            }
        }
    }

    fun isMuted(): Boolean = isMuted

    fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        applyAudioRoute(isSpeakerOn)
        Log.i(TAG, "Speaker toggled: $isSpeakerOn")
    }

    fun isSpeakerOn(): Boolean = isSpeakerOn

    fun cleanup() {
        Log.i(TAG, "Cleanup session")
        scope.launch {
            disconnectInternal(keepJob = false)
        }
    }

    private fun requestAudioFocus() {
        try {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request error")
        }
    }

    private fun applyAudioRoute(speakerOn: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
        } catch (e: Exception) {
            Log.w(TAG, "Audio route update error")
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
            Log.i(TAG, "✓ Disconnected internal")
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        } finally {
            if (!keepJob) {
                job.cancel()
            }
        }
    }
}
