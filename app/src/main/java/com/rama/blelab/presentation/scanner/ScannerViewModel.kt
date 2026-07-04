package com.rama.blelab.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.blelab.domain.model.BleDevice
import com.rama.blelab.domain.repository.BluetoothRepository
import com.rama.blelab.domain.repository.GattDetailsState
import com.rama.blelab.domain.usecase.BleUseCases
import kotlinx.coroutines.flow.*

class ScannerViewModel(
    private val repository: BluetoothRepository,
    private val useCases: BleUseCases
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BleDevice?>(null)
    val selectedDevice = combine(repository.scannedDevices, _selectedDevice) { devices, selectedDevice ->
        val selectedAddress = selectedDevice?.address
        devices.firstOrNull { it.address == selectedAddress } ?: selectedDevice
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val devices = combine(repository.scannedDevices, _searchText) { devices, text ->
        if (text.isBlank()) {
            devices
        } else {
            devices.filter {
                it.name?.contains(text, ignoreCase = true) == true ||
                        it.address.contains(text, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = repository.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gattDetailsState = repository.gattDetailsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GattDetailsState.Idle)

    fun onSearchTextChange(text: String) {
        _searchText.value = text
    }

    fun selectDevice(device: BleDevice) {
        _selectedDevice.value = device
        useCases.clearGattDetails()
    }

    fun startScan() {
        useCases.startScan()
    }

    fun stopScan() {
        useCases.stopScan()
    }

    fun connect(address: String) {
        useCases.connectToDevice(address)
    }

    fun discoverGattDetails(address: String) {
        useCases.discoverGattDetails(address)
    }

    fun clearGattDetails() {
        useCases.clearGattDetails()
    }
}
