package com.rama.blelab.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.blelab.domain.repository.BluetoothRepository
import com.rama.blelab.domain.usecase.BleUseCases
import kotlinx.coroutines.flow.*

class ScannerViewModel(
    private val repository: BluetoothRepository,
    private val useCases: BleUseCases
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

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

    fun onSearchTextChange(text: String) {
        _searchText.value = text
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
}
