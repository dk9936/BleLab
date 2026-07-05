package com.rama.blelab.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ScannerDeviceProfile(
    val address: String,
    val favorite: Boolean = false,
    val note: String = "",
    val tag: String = "",
    val alertEnabled: Boolean = false,
    val autoConnect: Boolean = false
)

data class DeviceRssiPoint(
    val timestamp: Long,
    val rssi: Int
)

data class ScanSessionEntry(
    val timestamp: Long,
    val name: String?,
    val address: String,
    val rssi: Int
)

enum class RadarSignalFilter {
    ALL,
    FAVORITES,
    STRONG,
    MOVING,
    ALERTS
}

enum class ScanListFilter {
    ALL,
    FAVORITES,
    STRONG,
    ALERTS,
    AUTO_CONNECT
}
