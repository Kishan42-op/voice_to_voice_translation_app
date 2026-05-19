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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Real LiveKit session manager for audio calls.
 *
 * Responsibilities:
 * - connect/disconnect room
 * - publish local mic
 * - observe participant/track events
 * - expose state to UI
 * - handle mute/speaker and cleanup
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

    fun connect(liveKitUrl: String, roomName: String, token: String, localParticipantName: String) {
        Log.i(TAG, "connect() liveKitUrl=$liveKitUrl room=$roomName identity=$localParticipantName")
        Log.i(TAG, "Token length: ${token.length} chars")

        if (liveKitUrl.isBlank() || roomName.isBlank() || token.isBlank()) {
            val error = "Missing parameters: url=${liveKitUrl.isBlank()} room=${roomName.isBlank()} token=${token.isBlank()}"
            Log.e(TAG, error)
            connectionError.postValue(error)
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

                Log.i(TAG, "✓ Room created, connecting to $liveKitUrl")
                room?.connect(liveKitUrl, token, ConnectOptions())

                Log.i(TAG, "✓ Connected to LiveKit room: $roomName")
                isConnected.postValue(true)
                connectionError.postValue(null)

                // Make sure microphone is actually published.
                room?.localParticipant?.setMicrophoneEnabled(true)
                Log.i(TAG, "✓ Microphone enabled")
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "✗ Connection failed: $msg", e)
                connectionError.postValue("Connection failed: $msg")
                isConnected.postValue(false)
                logCauseChain(e)
            }
        }
    }

    private fun attachRoomEventCollector(room: Room) {
        eventJob?.cancel()
        eventJob = scope.launch {
            room.events.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        Log.i(TAG, "✓ Room event: Connected")
                    }

                    is RoomEvent.Reconnecting -> {
                        Log.w(TAG, "↻ Room event: Reconnecting")
                    }

                    is RoomEvent.Reconnected -> {
                        Log.i(TAG, "✓ Room event: Reconnected")
                        isConnected.postValue(true)
                    }

                    is RoomEvent.Disconnected -> {
                        Log.i(TAG, "✗ Room event: Disconnected")
                        isConnected.postValue(false)
                        isRemoteParticipantConnected.postValue(false)
                        remoteParticipantName.postValue(null)
                    }

                    is RoomEvent.FailedToConnect -> {
                        val msg = event.error.message ?: event.error.javaClass.simpleName
                        Log.e(TAG, "✗ Room event: FailedToConnect - $msg", event.error)
                        connectionError.postValue("Connection failed: $msg")
                        isConnected.postValue(false)
                    }

                    is RoomEvent.ParticipantConnected -> {
                        val p = event.participant
                        Log.i(TAG, "✓ Participant connected: ${p.name} (${p.identity})")
                        remoteParticipantName.postValue(
                            p.name?.toString()?.takeIf { it.isNotBlank() } ?: p.identity.toString()
                        )
                        isRemoteParticipantConnected.postValue(true)
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        val p = event.participant
                        Log.i(TAG, "✗ Participant disconnected: ${p.name} (${p.identity})")
                        isRemoteParticipantConnected.postValue(false)
                        remoteParticipantName.postValue(null)
                    }

                    is RoomEvent.TrackSubscribed -> {
                        val participant = event.participant
                        val track = event.track
                        Log.i(TAG, "✓ Track subscribed: kind=${track.kind} participant=${participant.name}")
                        if (track.kind == Track.Kind.AUDIO) {
                            Log.i(TAG, "✓ Remote audio track subscribed from ${participant.name}")
                            if (track is RemoteAudioTrack) {
                                track.setVolume(1.0)
                            }
                            remoteParticipantName.postValue(
                                participant.name?.toString()?.takeIf { it.isNotBlank() } ?: participant.identity.toString()
                            )
                            isRemoteParticipantConnected.postValue(true)
                        }
                    }

                    is RoomEvent.TrackUnsubscribed -> {
                        Log.i(TAG, "✗ Track unsubscribed from ${event.participant.name}")
                    }

                    is RoomEvent.TrackSubscriptionFailed -> {
                        val msg = event.exception.message ?: event.exception.javaClass.simpleName
                        Log.e(TAG, "✗ Track subscription failed: $msg", event.exception)
                        connectionError.postValue("Track subscription failed: $msg")
                    }

                    is RoomEvent.TrackPublished -> {
                        Log.i(TAG, "✓ Track published: ${event.participant.name}")
                    }

                    is RoomEvent.TrackUnpublished -> {
                        Log.i(TAG, "✗ Track unpublished: ${event.participant.name}")
                    }

                    else -> Unit
                }
            }
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        Log.i(TAG, "toggleMute: ${if (isMuted) "MUTED" else "UNMUTED"}")

        scope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(!isMuted)
                Log.i(TAG, "✓ Microphone ${if (isMuted) "disabled" else "enabled"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting microphone enabled: ${e.message}", e)
            }
        }
    }

    fun isMuted(): Boolean = isMuted

    fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        Log.i(TAG, "toggleSpeaker: ${if (isSpeakerOn) "ON (speaker)" else "OFF (earpiece)"}")
        applyAudioRoute(isSpeakerOn)
    }

    fun isSpeakerOn(): Boolean = isSpeakerOn

    fun disconnect() {
        Log.i(TAG, "Disconnecting...")
        scope.launch {
            disconnectInternal(keepJob = true)
        }
    }

    fun cleanup() {
        Log.i(TAG, "cleanup()")
        scope.launch {
            disconnectInternal(keepJob = false)
        }
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

    private fun applyAudioRoute(speakerOn: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
            Log.i(TAG, "✓ Audio route updated: speaker=$speakerOn")
        } catch (e: Exception) {
            Log.w(TAG, "Audio route update failed: ${e.message}")
        }
    }

    private suspend fun disconnectInternal(keepJob: Boolean) {
        try {
            eventJob?.cancel()
            eventJob = null

            room?.let {
                try {
                    it.localParticipant.setMicrophoneEnabled(false)
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling microphone: ${e.message}")
                }
                it.disconnect()
                it.release()
            }
            room = null

            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL

            isConnected.postValue(false)
            isRemoteParticipantConnected.postValue(false)
            remoteParticipantName.postValue(null)

            Log.i(TAG, "✓ Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Disconnect error: ${e.message}", e)
        } finally {
            if (!keepJob) {
                job.cancel()
            }
        }
    }

    private fun logCauseChain(e: Throwable) {
        var cause = e.cause
        var depth = 1
        while (cause != null && depth < 5) {
            Log.e(TAG, "  ✗ Caused by (level $depth): ${cause.message}")
            cause = cause.cause
            depth++
        }
    }
}






