package com.rama.blelab.domain.repository

import com.rama.blelab.domain.model.BleDevice
import com.rama.blelab.domain.model.ParsingFormula
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

interface BluetoothRepository {
    val scannedDevices: StateFlow<List<BleDevice>>
    val isScanning: StateFlow<Boolean>
    val connectionState: StateFlow<ConnectionState>
    val gattDetailsState: StateFlow<GattDetailsState>
    val messages: Flow<BleMessage>

    fun startScanning()
    fun stopScanning()
    fun discoverGattDetails(address: String)
    fun clearGattDetails()
    fun connect(address: String)
    fun disconnect()
    fun sendMessage(content: String, isHex: Boolean = false)
}

sealed class GattDetailsState {
    object Idle : GattDetailsState()
    object Loading : GattDetailsState()
    data class Success(val details: BleGattDetails) : GattDetailsState()
    data class Error(val message: String) : GattDetailsState()
}

data class BleGattDetails(
    val deviceName: String?,
    val address: String,
    val services: List<GattServiceInfo>
)

data class GattServiceInfo(
    val uuid: String,
    val type: String,
    val characteristics: List<GattCharacteristicInfo>
)

data class GattCharacteristicInfo(
    val uuid: String,
    val properties: List<String>,
    val descriptors: List<String>
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: BleDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class BleMessage(
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType,
    val format: MessageFormat = MessageFormat.TEXT,
    val rawData: ByteArray? = null,
    val parsedContent: String? = null,
    val parserName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleMessage

        if (timestamp != other.timestamp) return false
        if (content != other.content) return false
        if (type != other.type) return false
        if (format != other.format) return false
        if (!rawData.contentEquals(other.rawData)) return false
        if (parsedContent != other.parsedContent) return false
        if (parserName != other.parserName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        result = 31 * result + (parsedContent?.hashCode() ?: 0)
        result = 31 * result + (parserName?.hashCode() ?: 0)
        return result
    }
}

enum class MessageType { RX, TX }
enum class MessageFormat { TEXT, HEX }

@Serializable
data class Macro(
    val name: String,
    val command: String,
    val formulas: List<ParsingFormula> = emptyList()
)
