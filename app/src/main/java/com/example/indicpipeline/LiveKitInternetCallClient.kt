package com.example.indicpipeline

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class LiveKitInternetCallClient(
    appContext: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onIncomingSpeech(fromUser: String, text: String, srcTransCode: String)
        fun onIncomingInfo(message: String)
    }

    private val ctx = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var room: Room? = null

    @Volatile
    private var connected: Boolean = false

    @Volatile
    private var eventsJobStarted: Boolean = false

    fun connect(url: String, token: String) {
        disconnect("reconnect")
        val r = LiveKit.create(ctx)
        room = r
        connected = false
        eventsJobStarted = false

        scope.launch {
            try {
                startEventsCollector(r)
                r.connect(url, token)
                // We use LiveKit mainly for session + data. Keep mic/cam disabled by default.
                r.localParticipant.setMicrophoneEnabled(false)
                r.localParticipant.setCameraEnabled(false)
                connected = true
                listener.onConnected()
            } catch (e: Exception) {
                connected = false
                listener.onDisconnected(e.message)
            }
        }
    }

    fun disconnect(reason: String?) {
        connected = false
        val r = room
        room = null
        if (r != null) {
            scope.launch {
                try {
                    r.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        if (reason != null) {
            listener.onDisconnected(reason)
        }
    }

    fun isConnected(): Boolean = connected && room != null

    fun sendSpeech(selfLabel: String, text: String, srcTransCode: String) {
        val r = room ?: return
        if (!connected) return

        val payload = JSONObject()
            .put("type", "speech")
            .put("from", selfLabel)
            .put("src", srcTransCode)
            .put("text", text)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        scope.launch {
            try {
                r.localParticipant.publishData(
                    data = payload,
                    reliability = DataPublishReliability.RELIABLE,
                    topic = "speech",
                    identities = null
                )
            } catch (e: Exception) {
                listener.onIncomingInfo("publishData failed: ${e.message}")
            }
        }
    }

    private fun startEventsCollector(r: Room) {
        if (eventsJobStarted) return
        eventsJobStarted = true

        scope.launch {
            try {
                r.events.events.collect { event: RoomEvent ->
                    when (event) {
                        is RoomEvent.DataReceived -> handleData(event)
                        is RoomEvent.Disconnected -> {
                            connected = false
                            listener.onDisconnected(event.reason?.toString())
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                listener.onIncomingInfo("event collector stopped: ${e.message}")
            }
        }
    }

    private fun handleData(event: RoomEvent.DataReceived) {
        try {
            val msg = JSONObject(String(event.data, StandardCharsets.UTF_8))
            val type = msg.optString("type", "")
            if (type == "speech") {
                val from = msg.optString("from", event.participant?.identity?.value ?: "peer")
                val text = msg.optString("text", "")
                val src = msg.optString("src", "")
                if (text.isNotBlank() && src.isNotBlank()) {
                    listener.onIncomingSpeech(from, text, src)
                }
            } else {
                listener.onIncomingInfo(msg.toString())
            }
        } catch (e: Exception) {
            listener.onIncomingInfo("data parse failed: ${e.message}")
        }
    }
}

