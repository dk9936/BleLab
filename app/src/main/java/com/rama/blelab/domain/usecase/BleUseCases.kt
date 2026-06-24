package com.rama.blelab.domain.usecase

import com.rama.blelab.domain.repository.BluetoothRepository

data class BleUseCases(
    val startScan: StartScanUseCase,
    val stopScan: StopScanUseCase,
    val connectToDevice: ConnectToDeviceUseCase,
    val disconnect: DisconnectUseCase,
    val sendMessage: SendMessageUseCase
)

class StartScanUseCase(private val repository: BluetoothRepository) {
    operator fun invoke() = repository.startScanning()
}

class StopScanUseCase(private val repository: BluetoothRepository) {
    operator fun invoke() = repository.stopScanning()
}

class ConnectToDeviceUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(address: String) = repository.connect(address)
}

class DisconnectUseCase(private val repository: BluetoothRepository) {
    operator fun invoke() = repository.disconnect()
}

class SendMessageUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(content: String, isHex: Boolean = false) = repository.sendMessage(content, isHex)
}
