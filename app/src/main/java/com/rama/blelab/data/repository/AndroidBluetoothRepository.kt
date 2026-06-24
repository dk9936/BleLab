package com.rama.blelab.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.scan.ScanSettings
import com.rama.blelab.domain.model.BleDevice
import com.rama.blelab.domain.repository.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
class AndroidBluetoothRepository(
    private val context: Context
) : BluetoothRepository {

    private val rxBleClient by lazy { RxBleClient.create(context) }
    
    private var scanDisposable: Disposable? = null
    private var connectionDisposable: Disposable? = null
    private val notificationDisposables = CompositeDisposable()
    private var rxBleConnection: RxBleConnection? = null

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<BleMessage>(replay = 10, extraBufferCapacity = 20)
    override val messages: Flow<BleMessage> = _messages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var rxBuffer = mutableListOf<Byte>()
    private var accumulationJob: kotlinx.coroutines.Job? = null

    private val TX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    override fun startScanning() {
        if (_isScanning.value) return
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanDisposable = rxBleClient.scanBleDevices(settings)
            .subscribe(
                { scanResult ->
                    val device = BleDevice(
                        name = scanResult.bleDevice.name,
                        address = scanResult.bleDevice.macAddress,
                        rssi = scanResult.rssi
                    )
                    updateScannedDevices(device)
                },
                { _ ->
                    _isScanning.value = false
                }
            )
    }

    override fun stopScanning() {
        scanDisposable?.dispose()
        scanDisposable = null
        _isScanning.value = false
    }

    override fun connect(address: String) {
        stopScanning()
        disconnect()
        
        _connectionState.value = ConnectionState.Connecting

        val rxBleDevice = rxBleClient.getBleDevice(address)
        
        // Monitor connection state
        val stateDisposable = rxBleDevice.observeConnectionStateChanges()
            .subscribe { state ->
                if (state == RxBleConnection.RxBleConnectionState.DISCONNECTED) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        notificationDisposables.add(stateDisposable)

        connectionDisposable = rxBleDevice.establishConnection(false)
            .flatMapSingle { connection ->
                connection.requestMtu(256).map { connection }
                    .onErrorReturn { connection }
            }
            .subscribe(
                { connection ->
                    rxBleConnection = connection
                    _connectionState.value = ConnectionState.Connected(
                        BleDevice(rxBleDevice.name, rxBleDevice.macAddress, 0, true)
                    )
                    // Wait a bit before setting up notifications
                    io.reactivex.Observable.timer(500, TimeUnit.MILLISECONDS)
                        .subscribe { setupNotification(connection) }
                        .also { notificationDisposables.add(it) }
                },
                { _ ->
                    _connectionState.value = ConnectionState.Disconnected
                    rxBleConnection = null
                }
            )
    }

    private fun setupNotification(connection: RxBleConnection) {
        Log.d("BLE_REPO", "Setting up notifications for $RX_CHAR_UUID")
        val disposable = connection.setupNotification(RX_CHAR_UUID)
            .flatMap { notificationObservable -> notificationObservable }
            .subscribe(
                { bytes -> 
                    Log.d("BLE_REPO", "Received Notification Data")
                    broadcastMessage(bytes, MessageType.RX) 
                },
                { error -> 
                    Log.e("BLE_REPO", "Notification Setup Error: ${error.message}")
                }
            )
        notificationDisposables.add(disposable)
    }

    override fun disconnect() {
        connectionDisposable?.dispose()
        connectionDisposable = null
        notificationDisposables.clear()
        rxBleConnection = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sendMessage(content: String, isHex: Boolean) {
        val connection = rxBleConnection ?: return
        Log.d("BLE_REPO", "Attempting to send: $content (Hex: $isHex)")
        
        val bytes = try {
            if (isHex) {
                content.filter { !it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                content.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("BLE_REPO", "Failed to parse input: ${e.message}")
            return
        }

        val disposable = connection.writeCharacteristic(TX_CHAR_UUID, bytes)
            .subscribe(
                { writtenBytes -> 
                    Log.d("BLE_REPO", "Write Successful")
                    broadcastMessage(writtenBytes, MessageType.TX) 
                },
                { error -> 
                    Log.e("BLE_REPO", "Write Failed: ${error.message}")
                    error.printStackTrace()
                }
            )
        notificationDisposables.add(disposable)
    }

    private fun updateScannedDevices(device: BleDevice) {
        val currentList = _scannedDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.address == device.address }
        if (index != -1) {
            currentList[index] = device
        } else {
            currentList.add(device)
        }
        _scannedDevices.value = currentList
    }

    private fun broadcastMessage(data: ByteArray, type: MessageType) {
        if (type == MessageType.TX) {
            emitMessage(data, type)
            return
        }

        synchronized(rxBuffer) {
            rxBuffer.addAll(data.toList())
        }

        accumulationJob?.cancel()
        accumulationJob = scope.launch {
            kotlinx.coroutines.delay(100.milliseconds) // Adjust delay as needed (100ms is usually safe)
            val combinedData = synchronized(rxBuffer) {
                val array = rxBuffer.toByteArray()
                rxBuffer.clear()
                array
            }
            if (combinedData.isNotEmpty()) {
                emitMessage(combinedData, MessageType.RX)
            }
        }
    }

    private fun emitMessage(data: ByteArray, type: MessageType) {
        Log.d("BLE_REPO", "Emit: ${type.name} - ${data.joinToString(",") { it.toString() }}")
        
        // Use smart formatting for both RX and TX
        val isPrintable = data.all { it in 32..126 || it == 10.toByte() || it == 13.toByte() }
        val content = if (isPrintable) {
            String(data)
        } else {
            data.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
        }
        
        scope.launch {
            _messages.emit(
                BleMessage(
                    content = content,
                    type = type,
                    rawData = data,
                    format = if (isPrintable) MessageFormat.TEXT else MessageFormat.HEX
                )
            )
        }
    }
}
