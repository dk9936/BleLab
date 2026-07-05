package com.rama.blelab.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.blelab.data.repository.ScannerProfileDataStore
import com.rama.blelab.domain.model.BleDevice
import com.rama.blelab.domain.model.DeviceRssiPoint
import com.rama.blelab.domain.model.MovementState
import com.rama.blelab.domain.model.RadarSignalFilter
import com.rama.blelab.domain.model.ScanListFilter
import com.rama.blelab.domain.model.ScanSessionEntry
import com.rama.blelab.domain.model.ScannerDeviceProfile
import com.rama.blelab.domain.repository.BluetoothRepository
import com.rama.blelab.domain.repository.GattDetailsState
import com.rama.blelab.domain.usecase.BleUseCases
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val repository: BluetoothRepository,
    private val useCases: BleUseCases,
    private val scannerProfileDataStore: ScannerProfileDataStore
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BleDevice?>(null)
    val selectedDevice = combine(repository.scannedDevices, _selectedDevice) { devices, selectedDevice ->
        val selectedAddress = selectedDevice?.address
        devices.firstOrNull { it.address == selectedAddress } ?: selectedDevice
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _profiles = MutableStateFlow<Map<String, ScannerDeviceProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, ScannerDeviceProfile>> = _profiles.asStateFlow()

    private val _rssiHistory = MutableStateFlow<Map<String, List<DeviceRssiPoint>>>(emptyMap())
    val rssiHistory: StateFlow<Map<String, List<DeviceRssiPoint>>> = _rssiHistory.asStateFlow()

    private val _isRecordingSession = MutableStateFlow(false)
    val isRecordingSession: StateFlow<Boolean> = _isRecordingSession.asStateFlow()

    private val _sessionEntries = MutableStateFlow<List<ScanSessionEntry>>(emptyList())
    val sessionEntries: StateFlow<List<ScanSessionEntry>> = _sessionEntries.asStateFlow()

    private val _radarFilter = MutableStateFlow(RadarSignalFilter.ALL)
    val radarFilter: StateFlow<RadarSignalFilter> = _radarFilter.asStateFlow()

    private val _scanListFilter = MutableStateFlow(ScanListFilter.ALL)
    val scanListFilter: StateFlow<ScanListFilter> = _scanListFilter.asStateFlow()

    private val _alertMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val alertMessage: SharedFlow<String> = _alertMessage.asSharedFlow()

    private val alertedAddresses = mutableSetOf<String>()
    private val autoConnectAttempts = mutableSetOf<String>()

    val devices = combine(repository.scannedDevices, _searchText, _profiles, _scanListFilter) { devices, text, profiles, filter ->
        devices
            .filter { device ->
                text.isBlank() ||
                    device.name?.contains(text, ignoreCase = true) == true ||
                    device.address.contains(text, ignoreCase = true) ||
                    profiles[device.address]?.tag?.contains(text, ignoreCase = true) == true ||
                    profiles[device.address]?.note?.contains(text, ignoreCase = true) == true
            }
            .filter { device -> device.matchesFilter(filter, profiles[device.address]) }
            .sortedWith(
                compareByDescending<BleDevice> { profiles[it.address]?.favorite == true }
                    .thenByDescending { it.rssi }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = repository.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gattDetailsState = repository.gattDetailsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GattDetailsState.Idle)

    init {
        scannerProfileDataStore.profiles
            .onEach { _profiles.value = it }
            .launchIn(viewModelScope)

        repository.scannedDevices
            .onEach { scannedDevices ->
                updateRssiHistory(scannedDevices)
                recordSessionEntries(scannedDevices)
                triggerProfileActions(scannedDevices)
            }
            .launchIn(viewModelScope)
    }

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

    fun toggleFavorite(address: String) {
        updateProfile(address) { it.copy(favorite = !it.favorite) }
    }

    fun toggleAlert(address: String) {
        updateProfile(address) { it.copy(alertEnabled = !it.alertEnabled) }
    }

    fun toggleAutoConnect(address: String) {
        updateProfile(address) { it.copy(autoConnect = !it.autoConnect) }
        autoConnectAttempts.remove(address)
    }

    fun updateDeviceNotes(address: String, note: String, tag: String) {
        updateProfile(address) { it.copy(note = note.trim(), tag = tag.trim()) }
    }

    fun toggleSessionRecording() {
        _isRecordingSession.value = !_isRecordingSession.value
    }

    fun clearSession() {
        _sessionEntries.value = emptyList()
    }

    fun setRadarFilter(filter: RadarSignalFilter) {
        _radarFilter.value = filter
    }

    fun setScanListFilter(filter: ScanListFilter) {
        _scanListFilter.value = filter
    }

    fun filteredRadarDevices(
        devices: List<BleDevice>,
        profiles: Map<String, ScannerDeviceProfile>
    ): List<BleDevice> {
        return when (_radarFilter.value) {
            RadarSignalFilter.ALL -> devices
            RadarSignalFilter.FAVORITES -> devices.filter { profiles[it.address]?.favorite == true }
            RadarSignalFilter.STRONG -> devices.filter { it.rssi >= -65 }
            RadarSignalFilter.MOVING -> devices.filter { it.movementState == MovementState.MOVING }
            RadarSignalFilter.ALERTS -> devices.filter { profiles[it.address]?.alertEnabled == true }
        }
    }

    fun buildScanExport(): String {
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val currentDevices = devices.value
        val profileMap = profiles.value
        val sessionRows = sessionEntries.value

        return buildString {
            appendLine("BLE Lab Scan Export")
            appendLine("Generated,$generatedAt")
            appendLine()
            appendLine("Current Devices")
            appendLine("Name,Address,RSSI,Favorite,Tag,Note,Alert,AutoConnect")
            currentDevices.forEach { device ->
                val profile = profileMap[device.address]
                appendCsvRow(
                    device.name ?: "Unknown Device",
                    device.address,
                    "${device.rssi}",
                    "${profile?.favorite == true}",
                    profile?.tag.orEmpty(),
                    profile?.note.orEmpty(),
                    "${profile?.alertEnabled == true}",
                    "${profile?.autoConnect == true}"
                )
            }
            appendLine()
            appendLine("Session Entries")
            appendLine("Time,Name,Address,RSSI")
            sessionRows.forEach { entry ->
                appendCsvRow(
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp)),
                    entry.name ?: "Unknown Device",
                    entry.address,
                    "${entry.rssi}"
                )
            }
        }
    }

    private fun updateProfile(
        address: String,
        transform: (ScannerDeviceProfile) -> ScannerDeviceProfile
    ) {
        val existing = _profiles.value[address] ?: ScannerDeviceProfile(address = address)
        val updatedProfiles = _profiles.value + (address to transform(existing))
        _profiles.value = updatedProfiles
        viewModelScope.launch {
            scannerProfileDataStore.saveProfiles(updatedProfiles)
        }
    }

    private fun updateRssiHistory(scannedDevices: List<BleDevice>) {
        if (scannedDevices.isEmpty()) return
        val now = System.currentTimeMillis()
        _rssiHistory.update { current ->
            current.toMutableMap().also { mutable ->
                scannedDevices.forEach { device ->
                    mutable[device.address] = (mutable[device.address].orEmpty() + DeviceRssiPoint(now, device.rssi))
                        .takeLast(48)
                }
            }
        }
    }

    private fun recordSessionEntries(scannedDevices: List<BleDevice>) {
        if (!_isRecordingSession.value || scannedDevices.isEmpty()) return
        val now = System.currentTimeMillis()
        val entries = scannedDevices.map { device ->
            ScanSessionEntry(
                timestamp = now,
                name = device.name,
                address = device.address,
                rssi = device.rssi
            )
        }
        _sessionEntries.update { (it + entries).takeLast(500) }
    }

    private fun triggerProfileActions(scannedDevices: List<BleDevice>) {
        val profileMap = _profiles.value
        scannedDevices.forEach { device ->
            val profile = profileMap[device.address] ?: return@forEach
            if (profile.alertEnabled && alertedAddresses.add(device.address)) {
                _alertMessage.tryEmit("${device.name ?: device.address} is nearby")
            }
            if (profile.autoConnect && autoConnectAttempts.add(device.address)) {
                connect(device.address)
            }
        }
    }

    private fun BleDevice.matchesFilter(
        filter: ScanListFilter,
        profile: ScannerDeviceProfile?
    ): Boolean {
        return when (filter) {
            ScanListFilter.ALL -> true
            ScanListFilter.FAVORITES -> profile?.favorite == true
            ScanListFilter.STRONG -> rssi >= -65
            ScanListFilter.ALERTS -> profile?.alertEnabled == true
            ScanListFilter.AUTO_CONNECT -> profile?.autoConnect == true
        }
    }

    private fun StringBuilder.appendCsvRow(vararg values: String) {
        appendLine(values.joinToString(",") { value ->
            "\"${value.replace("\"", "\"\"")}\""
        })
    }
}
