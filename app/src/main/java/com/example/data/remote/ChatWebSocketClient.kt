package com.example.data.remote

import com.example.domain.ChatMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class ChatWebSocketClient(
    private val okHttpClient: OkHttpClient = FastApiClientFactory.createOkHttpClient()
) {
    private var activeSocket: WebSocket? = null
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 32)
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    fun connect(baseHttpUrl: String, conversationId: String, bearerToken: String) {
        disconnect()
        val wsBase = baseHttpUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val url = "$wsBase/ws/chat/$conversationId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .build()

        activeSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    val msg = ChatMessage(
                        id = json.optString("id", System.currentTimeMillis().toString()),
                        conversationId = conversationId,
                        senderId = json.optString("senderId", ""),
                        senderName = json.optString("senderName", "Partner"),
                        text = json.optString("text", ""),
                        timestampLabel = json.optString("timestampLabel", "Just now"),
                        delivered = true
                    )
                    _incomingMessages.tryEmit(msg)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Connection failure is handled cleanly by repository offline/fallback state.
            }
        })
    }

    fun sendMessage(text: String): Boolean {
        val socket = activeSocket ?: return false
        val payload = JSONObject().apply {
            put("text", text)
        }.toString()
        return socket.send(payload)
    }

    fun disconnect() {
        activeSocket?.close(1000, "Client closed conversation")
        activeSocket = null
    }
}
