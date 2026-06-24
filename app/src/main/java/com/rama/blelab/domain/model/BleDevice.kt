package com.rama.blelab.domain.model

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnected: Boolean = false
)
