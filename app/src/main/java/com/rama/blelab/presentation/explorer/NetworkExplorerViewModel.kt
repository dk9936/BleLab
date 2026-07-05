package com.rama.blelab.presentation.explorer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkExplorerState(
    val isScanning: Boolean = false,
    val items: List<NearbyNetworkItem> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdatedMillis: Long? = null
)

data class NearbyNetworkItem(
    val id: String,
    val name: String,
    val address: String,
    val type: NearbyNetworkType,
    val signalDbm: Int?,
    val detail: String,
    val isConnectedOrBonded: Boolean = false,
    val lastSeenMillis: Long = System.currentTimeMillis()
)

enum class NearbyNetworkType {
    WIFI,
    BLE,
    BLUETOOTH
}

@SuppressLint("MissingPermission")
class NetworkExplorerViewModel(
    private val context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val rxBleClient by lazy { RxBleClient.create(appContext) }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val _state = MutableStateFlow(NetworkExplorerState())
    val state: StateFlow<NetworkExplorerState> = _state.asStateFlow()

    private var bleScanDisposable: Disposable? = null
    private var classicReceiverRegistered = false
    private var stopJob: Job? = null

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .takeIf { it != Short.MIN_VALUE }
                        ?.toInt()
                    device?.let { addClassicBluetoothDevice(it, rssi) }
                }
            }
        }
    }

    fun startScan() {
        if (!hasRequiredPermissions()) {
            _state.update {
                it.copy(
                    isScanning = false,
                    errorMessage = "Allow nearby network permissions to scan Wi-Fi, BLE, and Bluetooth."
                )
            }
            return
        }

        stopScan(clearScanning = false)
        _state.value = NetworkExplorerState(isScanning = true)

        scanWifiNetworks()
        addBondedBluetoothDevices()
        startBleScan()
        startClassicBluetoothDiscovery()

        stopJob = viewModelScope.launch {
            delay(SCAN_WINDOW_MS)
            stopScan()
        }
    }

    fun stopScan(clearScanning: Boolean = true) {
        stopJob?.cancel()
        stopJob = null

        bleScanDisposable?.dispose()
        bleScanDisposable = null

        runCatching { bluetoothAdapter?.cancelDiscovery() }
        unregisterClassicReceiver()

        if (clearScanning) {
            _state.update { it.copy(isScanning = false, lastUpdatedMillis = System.currentTimeMillis()) }
        }
    }

    fun clearResults() {
        _state.value = NetworkExplorerState()
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    private fun scanWifiNetworks() {
        if (!wifiManager.isWifiEnabled) {
            _state.update { it.copy(errorMessage = "Wi-Fi is off. Turn it on to include Wi-Fi networks.") }
            return
        }

        runCatching { wifiManager.startScan() }
        val wifiItems = runCatching { wifiManager.scanResults }
            .getOrDefault(emptyList())
            .map { it.toNearbyNetworkItem() }
        mergeItems(wifiItems)
    }

    private fun startBleScan() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanDisposable = rxBleClient.scanBleDevices(settings)
            .subscribe(
                { scanResult ->
                    mergeItem(
                        NearbyNetworkItem(
                            id = "ble:${scanResult.bleDevice.macAddress}",
                            name = scanResult.bleDevice.name ?: "Unknown BLE Device",
                            address = scanResult.bleDevice.macAddress,
                            type = NearbyNetworkType.BLE,
                            signalDbm = scanResult.rssi,
                            detail = "BLE advertisement",
                            lastSeenMillis = System.currentTimeMillis()
                        )
                    )
                },
                { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "BLE scan failed.")
                    }
                }
            )
    }

    private fun addBondedBluetoothDevices() {
        val adapter = bluetoothAdapter ?: return
        val bondedItems = runCatching { adapter.bondedDevices.orEmpty() }
            .getOrDefault(emptySet())
            .map { device ->
                NearbyNetworkItem(
                    id = "bt:${device.address}",
                    name = device.name ?: "Paired Bluetooth Device",
                    address = device.address,
                    type = NearbyNetworkType.BLUETOOTH,
                    signalDbm = null,
                    detail = "Paired classic Bluetooth",
                    isConnectedOrBonded = true,
                    lastSeenMillis = System.currentTimeMillis()
                )
            }
        mergeItems(bondedItems)
    }

    private fun startClassicBluetoothDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _state.update { it.copy(errorMessage = "Bluetooth is off. Turn it on to include Bluetooth devices.") }
            return
        }

        registerClassicReceiver()
        runCatching {
            adapter.cancelDiscovery()
            adapter.startDiscovery()
        }.onFailure { error ->
            _state.update { it.copy(errorMessage = error.message ?: "Bluetooth discovery could not start.") }
        }
    }

    private fun registerClassicReceiver() {
        if (classicReceiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            classicReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND),
            ContextCompat.RECEIVER_EXPORTED
        )
        classicReceiverRegistered = true
    }

    private fun unregisterClassicReceiver() {
        if (!classicReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(classicReceiver) }
        classicReceiverRegistered = false
    }

    private fun addClassicBluetoothDevice(device: BluetoothDevice, rssi: Int?) {
        mergeItem(
            NearbyNetworkItem(
                id = "bt:${device.address}",
                name = device.name ?: "Bluetooth Device",
                address = device.address,
                type = NearbyNetworkType.BLUETOOTH,
                signalDbm = rssi,
                detail = "Classic Bluetooth discovery",
                isConnectedOrBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                lastSeenMillis = System.currentTimeMillis()
            )
        )
    }

    private fun mergeItem(item: NearbyNetworkItem) {
        mergeItems(listOf(item))
    }

    private fun mergeItems(items: List<NearbyNetworkItem>) {
        if (items.isEmpty()) return
        _state.update { current ->
            val merged = (current.items.associateBy { it.id } + items.associateBy { it.id })
                .values
                .sortedWith(
                    compareBy<NearbyNetworkItem> { it.type.ordinal }
                        .thenByDescending { it.signalDbm ?: Int.MIN_VALUE }
                        .thenBy { it.name.lowercase() }
                )
            current.copy(items = merged, lastUpdatedMillis = System.currentTimeMillis())
        }
    }

    private fun ScanResult.toNearbyNetworkItem(): NearbyNetworkItem {
        return NearbyNetworkItem(
            id = "wifi:$BSSID",
            name = SSID.takeIf { it.isNotBlank() } ?: "Hidden Wi-Fi Network",
            address = BSSID,
            type = NearbyNetworkType.WIFI,
            signalDbm = level,
            detail = "${securityLabel()} - ${frequency} MHz - Channel ${channelForFrequency(frequency) ?: "-"}",
            isConnectedOrBonded = false,
            lastSeenMillis = System.currentTimeMillis()
        )
    }

    private fun ScanResult.securityLabel(): String {
        return when {
            capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
            capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
            capabilities.contains("WPA", ignoreCase = true) -> "WPA"
            capabilities.contains("WEP", ignoreCase = true) -> "WEP"
            else -> "Open"
        }
    }

    private fun channelForFrequency(frequency: Int): Int? {
        return when (frequency) {
            in 2412..2484 -> if (frequency == 2484) 14 else (frequency - 2407) / 5
            in 5170..5895 -> (frequency - 5000) / 5
            in 5955..7115 -> (frequency - 5950) / 5
            else -> null
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val SCAN_WINDOW_MS = 12_000L

        fun requiredPermissions(): Array<String> {
            return buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            }.toTypedArray()
        }
    }
}
