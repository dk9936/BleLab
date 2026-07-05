package com.rama.blelab.domain.model

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnected: Boolean = false,
    val distanceMeters: Double? = null,
    val movementState: MovementState = MovementState.UNKNOWN,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

enum class MovementState {
    UNKNOWN,
    MOVING,
    STOPPED
}
