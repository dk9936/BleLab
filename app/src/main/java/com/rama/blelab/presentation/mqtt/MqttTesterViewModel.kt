package com.rama.blelab.presentation.mqtt

import androidx.lifecycle.ViewModel
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MqttLog(
    val message: String,
    val type: MqttLogType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

enum class MqttLogType {
    SENT,
    RECEIVED,
    SYSTEM,
    ERROR
}

enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class MqttTesterViewModel : ViewModel() {

    private val viewModelJob = SupervisorJob()
    private val scope = CoroutineScope(viewModelJob + Dispatchers.IO)
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var readJob: Job? = null
    private var pingJob: Job? = null
    private var packetId = 1

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<MqttLog>>(emptyList())
    val logs: StateFlow<List<MqttLog>> = _logs.asStateFlow()

    private val _subscribedTopics = MutableStateFlow<List<String>>(emptyList())
    val subscribedTopics: StateFlow<List<String>> = _subscribedTopics.asStateFlow()

    fun connect(
        host: String,
        port: String,
        clientId: String,
        username: String,
        password: String
    ) {
        val cleanHost = host.trim()
        val cleanPort = port.trim().toIntOrNull()
        val cleanClientId = clientId.trim().ifBlank {
            "BleLab-${System.currentTimeMillis().toString().takeLast(6)}"
        }

        if (cleanHost.isBlank() || cleanPort == null) {
            addLog("Enter a valid broker host and port.", MqttLogType.ERROR)
            return
        }

        closeSocket()
        _subscribedTopics.value = emptyList()
        _connectionState.value = MqttConnectionState.CONNECTING
        addLog("Connecting to $cleanHost:$cleanPort", MqttLogType.SYSTEM)

        scope.launch {
            try {
                val newSocket = withContext(Dispatchers.IO) { Socket(cleanHost, cleanPort) }
                newSocket.tcpNoDelay = true
                val input = DataInputStream(newSocket.getInputStream())
                val newOutput = DataOutputStream(newSocket.getOutputStream())

                socket = newSocket
                output = newOutput
                sendPacket(newOutput, buildConnectPacket(cleanClientId, username.trim(), password))

                val packet = readPacket(input)
                val returnCode = packet.payload.getOrNull(1)?.toInt() ?: -1
                if (packet.type != MQTT_CONNACK || returnCode != 0) {
                    throw IllegalStateException("Broker rejected connection, code $returnCode")
                }

                _connectionState.value = MqttConnectionState.CONNECTED
                addLog("Connected as $cleanClientId", MqttLogType.SYSTEM)
                startReader(input)
                startPingLoop()
            } catch (error: Exception) {
                closeSocket()
                _connectionState.value = MqttConnectionState.DISCONNECTED
                addLog(error.message ?: "MQTT connection failed", MqttLogType.ERROR)
            }
        }
    }

    fun subscribe(topic: String) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank()) {
            addLog("Enter a topic to subscribe.", MqttLogType.ERROR)
            return
        }

        val writer = output
        if (_connectionState.value != MqttConnectionState.CONNECTED || writer == null) {
            addLog("Connect before subscribing.", MqttLogType.ERROR)
            return
        }

        scope.launch {
            try {
                sendPacket(writer, buildSubscribePacket(cleanTopic, nextPacketId()))
                _subscribedTopics.value = (_subscribedTopics.value + cleanTopic).distinct()
                addLog("Subscribed to $cleanTopic", MqttLogType.SYSTEM)
            } catch (error: Exception) {
                handleConnectionError(error)
            }
        }
    }

    fun publish(topic: String, payload: String) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank()) {
            addLog("Enter a topic before publishing.", MqttLogType.ERROR)
            return
        }

        val writer = output
        if (_connectionState.value != MqttConnectionState.CONNECTED || writer == null) {
            addLog("Connect before publishing.", MqttLogType.ERROR)
            return
        }

        scope.launch {
            try {
                sendPacket(writer, buildPublishPacket(cleanTopic, payload))
                addLog("$cleanTopic\n$payload", MqttLogType.SENT)
            } catch (error: Exception) {
                handleConnectionError(error)
            }
        }
    }

    fun disconnect(addLog: Boolean = true) {
        scope.launch {
            try {
                output?.let { sendPacket(it, byteArrayOf(0xE0.toByte(), 0x00)) }
            } catch (_: Exception) {
            } finally {
                closeSocket()
                _connectionState.value = MqttConnectionState.DISCONNECTED
                if (addLog) {
                    addLog("Disconnected", MqttLogType.SYSTEM)
                }
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        closeSocket()
        viewModelJob.cancel()
        super.onCleared()
    }

    private fun startReader(input: DataInputStream) {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                while (_connectionState.value == MqttConnectionState.CONNECTED) {
                    val packet = readPacket(input)
                    when (packet.type) {
                        MQTT_PUBLISH -> handlePublish(packet.payload)
                        MQTT_SUBACK -> addLog("Subscription acknowledged", MqttLogType.SYSTEM)
                        MQTT_PINGRESP -> addLog("Ping response", MqttLogType.SYSTEM)
                    }
                }
            } catch (error: Exception) {
                if (_connectionState.value == MqttConnectionState.CONNECTED) {
                    handleConnectionError(error)
                }
            }
        }
    }

    private fun readPacket(input: DataInputStream): MqttPacket {
        val firstByte = input.readUnsignedByte()
        val remainingLength = readRemainingLength(input)
        val payload = ByteArray(remainingLength)
        input.readFully(payload)
        return MqttPacket(type = firstByte shr 4, payload = payload)
    }

    private fun handlePublish(packetPayload: ByteArray) {
        if (packetPayload.size < 2) return
        val topicLength = ((packetPayload[0].toInt() and 0xFF) shl 8) or (packetPayload[1].toInt() and 0xFF)
        if (packetPayload.size < 2 + topicLength) return

        val topic = packetPayload.copyOfRange(2, 2 + topicLength).toString(Charsets.UTF_8)
        val body = packetPayload.copyOfRange(2 + topicLength, packetPayload.size).toString(Charsets.UTF_8)
        addLog("$topic\n$body", MqttLogType.RECEIVED)
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (_connectionState.value == MqttConnectionState.CONNECTED) {
                delay(30_000)
                output?.let { sendPacket(it, byteArrayOf(0xC0.toByte(), 0x00)) }
            }
        }
    }

    private fun handleConnectionError(error: Exception) {
        closeSocket()
        _connectionState.value = MqttConnectionState.DISCONNECTED
        addLog(error.message ?: "MQTT connection lost", MqttLogType.ERROR)
    }

    private fun closeSocket() {
        readJob?.cancel()
        pingJob?.cancel()
        readJob = null
        pingJob = null
        output = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun sendPacket(writer: DataOutputStream, packet: ByteArray) {
        synchronized(writer) {
            writer.write(packet)
            writer.flush()
        }
    }

    private fun nextPacketId(): Int {
        val id = packetId
        packetId = if (packetId == 65_535) 1 else packetId + 1
        return id
    }

    private fun addLog(message: String, type: MqttLogType) {
        _logs.value = (_logs.value + MqttLog(message, type)).takeLast(200)
    }

    private data class MqttPacket(
        val type: Int,
        val payload: ByteArray
    )

    private companion object {
        const val MQTT_CONNACK = 2
        const val MQTT_PUBLISH = 3
        const val MQTT_SUBACK = 9
        const val MQTT_PINGRESP = 13
    }
}

private fun buildConnectPacket(
    clientId: String,
    username: String,
    password: String
): ByteArray {
    val variableHeader = ByteArrayOutputStream().apply {
        writeUtf8("MQTT")
        write(4)
        var flags = 0x02
        if (username.isNotBlank()) flags = flags or 0x80
        if (password.isNotBlank()) flags = flags or 0x40
        write(flags)
        writeShort(60)
    }

    val payload = ByteArrayOutputStream().apply {
        writeUtf8(clientId)
        if (username.isNotBlank()) writeUtf8(username)
        if (password.isNotBlank()) writeUtf8(password)
    }

    return buildPacket(0x10, variableHeader.toByteArray() + payload.toByteArray())
}

private fun buildSubscribePacket(topic: String, packetId: Int): ByteArray {
    val body = ByteArrayOutputStream().apply {
        writeShort(packetId)
        writeUtf8(topic)
        write(0)
    }
    return buildPacket(0x82, body.toByteArray())
}

private fun buildPublishPacket(topic: String, payload: String): ByteArray {
    val body = ByteArrayOutputStream().apply {
        writeUtf8(topic)
        write(payload.toByteArray(Charsets.UTF_8))
    }
    return buildPacket(0x30, body.toByteArray())
}

private fun buildPacket(header: Int, body: ByteArray): ByteArray {
    return byteArrayOf(header.toByte()) + encodeRemainingLength(body.size) + body
}

private fun encodeRemainingLength(length: Int): ByteArray {
    var value = length
    val encoded = mutableListOf<Byte>()
    do {
        var byte = value % 128
        value /= 128
        if (value > 0) byte = byte or 128
        encoded.add(byte.toByte())
    } while (value > 0)
    return encoded.toByteArray()
}

private fun readRemainingLength(input: DataInputStream): Int {
    var multiplier = 1
    var value = 0
    var encodedByte: Int
    do {
        encodedByte = input.readUnsignedByte()
        value += (encodedByte and 127) * multiplier
        multiplier *= 128
        if (multiplier > 128 * 128 * 128) {
            throw IllegalArgumentException("Malformed remaining length")
        }
    } while ((encodedByte and 128) != 0)
    return value
}

private fun ByteArrayOutputStream.writeUtf8(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeShort(bytes.size)
    write(bytes)
}

private fun ByteArrayOutputStream.writeShort(value: Int) {
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}
