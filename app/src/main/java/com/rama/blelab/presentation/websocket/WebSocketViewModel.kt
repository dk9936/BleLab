package com.rama.blelab.presentation.websocket

import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

data class WebSocketLog(
    val message: String,
    val type: WebSocketLogType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

enum class WebSocketLogType {
    SENT,
    RECEIVED,
    SYSTEM,
    ERROR
}

enum class WebSocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class WebSocketViewModel : ViewModel() {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(WebSocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<WebSocketLog>>(emptyList())
    val logs: StateFlow<List<WebSocketLog>> = _logs.asStateFlow()

    fun connect(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            addLog("Enter a WebSocket URL first.", WebSocketLogType.ERROR)
            return
        }

        disconnect(addLog = false)
        _connectionState.value = WebSocketConnectionState.CONNECTING
        addLog("Connecting to $trimmedUrl", WebSocketLogType.SYSTEM)

        val request = Request.Builder()
            .url(trimmedUrl)
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = WebSocketConnectionState.CONNECTED
                    addLog("Connected", WebSocketLogType.SYSTEM)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    addLog(text, WebSocketLogType.RECEIVED)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    addLog(bytes.hex(), WebSocketLogType.RECEIVED)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    _connectionState.value = WebSocketConnectionState.DISCONNECTED
                    addLog("Closing: $code $reason", WebSocketLogType.SYSTEM)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = WebSocketConnectionState.DISCONNECTED
                    addLog("Disconnected: $code $reason", WebSocketLogType.SYSTEM)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = WebSocketConnectionState.DISCONNECTED
                    addLog(t.message ?: "WebSocket connection failed", WebSocketLogType.ERROR)
                }
            }
        )
    }

    fun send(message: String) {
        val text = message.trim()
        if (text.isBlank()) return

        if (_connectionState.value != WebSocketConnectionState.CONNECTED) {
            addLog("Connect before sending a message.", WebSocketLogType.ERROR)
            return
        }

        val sent = webSocket?.send(text) == true
        if (sent) {
            addLog(text, WebSocketLogType.SENT)
        } else {
            addLog("Message was not sent.", WebSocketLogType.ERROR)
        }
    }

    fun disconnect(addLog: Boolean = true) {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = WebSocketConnectionState.DISCONNECTED
        if (addLog) {
            addLog("Disconnected", WebSocketLogType.SYSTEM)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        disconnect(addLog = false)
        client.dispatcher.executorService.shutdown()
        super.onCleared()
    }

    private fun addLog(message: String, type: WebSocketLogType) {
        _logs.value = _logs.value + WebSocketLog(message, type)
    }
}
