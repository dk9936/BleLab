package com.rama.blelab.presentation.scanner

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rama.blelab.domain.model.BleDevice
import com.rama.blelab.domain.model.DeviceRssiPoint
import com.rama.blelab.domain.model.MovementState
import com.rama.blelab.domain.model.ScanListFilter
import com.rama.blelab.domain.model.ScannerDeviceProfile
import com.rama.blelab.domain.repository.GattCharacteristicInfo
import com.rama.blelab.domain.repository.GattDetailsState
import com.rama.blelab.domain.repository.GattServiceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onDeviceClick: (BleDevice) -> Unit,
    onConnectClick: (BleDevice) -> Unit,
    onRadarClick: () -> Unit = {}
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val rssiHistory by viewModel.rssiHistory.collectAsState()
    val isRecordingSession by viewModel.isRecordingSession.collectAsState()
    val sessionEntries by viewModel.sessionEntries.collectAsState()
    val scanListFilter by viewModel.scanListFilter.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val bluetoothAdapter = remember(context) { BluetoothAdapter.getDefaultAdapter() }
    var deviceToEdit by remember { mutableStateOf<BleDevice?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }

    var hasPermissions by remember {
        mutableStateOf(PermissionUtils.hasPermissions(context))
    }
    var isBluetoothEnabled by remember(hasPermissions) {
        mutableStateOf(isBluetoothEnabled(bluetoothAdapter, hasPermissions))
    }

    fun refreshBluetoothState(): Boolean {
        val enabled = isBluetoothEnabled(bluetoothAdapter, hasPermissions)
        isBluetoothEnabled = enabled
        return enabled
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermissions = perms.values.all { it }
        val bluetoothReady = refreshBluetoothState()
        if (hasPermissions && bluetoothReady) {
            viewModel.startScan()
        } else {
            viewModel.stopScan()
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val enabled = result.resultCode == Activity.RESULT_OK || refreshBluetoothState()
        isBluetoothEnabled = enabled
        if (enabled && hasPermissions) {
            viewModel.startScan()
        }
    }

    fun requestEnableBluetooth() {
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    DisposableEffect(bluetoothAdapter, hasPermissions) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val enabled = state == BluetoothAdapter.STATE_ON
                isBluetoothEnabled = enabled

                if (enabled && hasPermissions) {
                    viewModel.startScan()
                } else {
                    viewModel.stopScan()
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        isBluetoothEnabled = refreshBluetoothState()

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(hasPermissions, isBluetoothEnabled, isScanning) {
        if (hasPermissions && isBluetoothEnabled && !isScanning) {
            viewModel.startScan()
        } else if (!isBluetoothEnabled && isScanning) {
            viewModel.stopScan()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.alertMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    deviceToEdit?.let { device ->
        val profile = profiles[device.address] ?: ScannerDeviceProfile(address = device.address)
        DeviceProfileDialog(
            device = device,
            profile = profile,
            onDismiss = { deviceToEdit = null },
            onSave = { note, tag ->
                viewModel.updateDeviceNotes(device.address, note, tag)
                deviceToEdit = null
            }
        )
    }

    exportText?.let { text ->
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text("Scan Export") },
            text = {
                Column {
                    Text("CSV export is ready.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = text.take(1200),
                            modifier = Modifier.padding(12.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Export copied", Toast.LENGTH_SHORT).show()
                        exportText = null
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportText = null }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BLE Terminal", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.padding(start = 16.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (!isBluetoothEnabled) {
                BluetoothDisabledBanner(onEnableBluetooth = {
                    requestEnableBluetooth()
                })
            }
            if (!hasPermissions) {
                PermissionBanner(onRequestPermission = {
                    permissionLauncher.launch(PermissionUtils.permissions.toTypedArray())
                })
            }
            Spacer(modifier = Modifier.height(16.dp))
            SearchBar(
                text = searchText,
                onTextChange = viewModel::onSearchTextChange,
                onRefresh = {
                    if (!refreshBluetoothState()) {
                        requestEnableBluetooth()
                    } else if (hasPermissions) {
                        if (isScanning) viewModel.stopScan() else viewModel.startScan()
                    } else {
                        permissionLauncher.launch(PermissionUtils.permissions.toTypedArray())
                    }
                },
                isScanning = isScanning
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRadarClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Signal Radar")
            }
            Spacer(modifier = Modifier.height(8.dp))
            ScannerFeatureControls(
                isRecording = isRecordingSession,
                sessionCount = sessionEntries.size,
                onToggleRecording = viewModel::toggleSessionRecording,
                onClearSession = viewModel::clearSession,
                onExport = { exportText = viewModel.buildScanExport() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ScanListFilterRow(
                selectedFilter = scanListFilter,
                onFilterSelected = viewModel::setScanListFilter
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("NEARBY DEVICES", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            if (!isBluetoothEnabled) {
                BluetoothDisabledEmptyState(onEnableBluetooth = {
                    requestEnableBluetooth()
                })
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(devices) { device ->
                        DeviceItem(
                            device = device,
                            profile = profiles[device.address] ?: ScannerDeviceProfile(address = device.address),
                            history = rssiHistory[device.address].orEmpty(),
                            onDeviceClick = { onDeviceClick(device) },
                            onConnect = { onConnectClick(device) },
                            onToggleFavorite = { viewModel.toggleFavorite(device.address) },
                            onToggleAlert = { viewModel.toggleAlert(device.address) },
                            onToggleAutoConnect = { viewModel.toggleAutoConnect(device.address) },
                            onEditProfile = { deviceToEdit = device }
                        )
                    }
                }
            }
        }
    }
}

private fun isBluetoothEnabled(bluetoothAdapter: BluetoothAdapter?, hasPermissions: Boolean): Boolean {
    if (bluetoothAdapter == null) return false
    if (!hasPermissions) return true

    return try {
        bluetoothAdapter.isEnabled
    } catch (_: SecurityException) {
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    device: BleDevice?,
    profile: ScannerDeviceProfile? = null,
    history: List<DeviceRssiPoint> = emptyList(),
    gattDetailsState: GattDetailsState,
    onBack: () -> Unit,
    onDiscoverGattDetails: (String) -> Unit,
    onConnect: (BleDevice) -> Unit
) {
    LaunchedEffect(device?.address) {
        device?.address?.let(onDiscoverGattDetails)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Device Details", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (device == null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Device details unavailable", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Go back and select a scanned device again.", color = Color.Gray, fontSize = 14.sp)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DeviceDetailsHeader(device)
            if (profile?.tag?.isNotBlank() == true || profile?.note?.isNotBlank() == true || history.size >= 2) {
                Spacer(modifier = Modifier.height(16.dp))
                DeviceProfileSummary(profile = profile, history = history)
            }
            Spacer(modifier = Modifier.height(20.dp))
            DeviceDetailsSection(
                rows = listOf(
                    "Name" to (device.name ?: "Unknown Device"),
                    "MAC Address" to device.address,
                    "RSSI" to "${device.rssi} dBm",
                    "Signal Strength" to signalStrengthLabel(device.rssi),
                    "Connection Status" to if (device.isConnected) "Connected" else "Not connected"
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            GattDetailsContent(
                state = gattDetailsState,
                onRetry = { onDiscoverGattDetails(device.address) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onConnect(device) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0056B3))
            ) {
                Icon(Icons.Default.BluetoothConnected, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect")
            }
        }
    }
}

@Composable
private fun DeviceProfileSummary(
    profile: ScannerDeviceProfile?,
    history: List<DeviceRssiPoint>
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (profile?.tag?.isNotBlank() == true) {
                DetailRow(label = "Tag", value = profile.tag)
            }
            if (profile?.note?.isNotBlank() == true) {
                DetailRow(label = "Notes", value = profile.note)
            }
            if (history.size >= 2) {
                Text("RSSI History", color = Color.Gray, fontSize = 14.sp)
                MiniRssiGraph(
                    history = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailsHeader(device: BleDevice) {
    Surface(
        color = Color(0xFFE3F2FD),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF1976D2),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(device.address, color = Color(0xFF455A64), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DeviceDetailsSection(rows: List<Pair<String, String>>) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scan Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            rows.forEachIndexed { index, row ->
                DetailRow(label = row.first, value = row.second)
                if (index != rows.lastIndex) {
                    Divider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun GattDetailsContent(state: GattDetailsState, onRetry: () -> Unit) {
    when (state) {
        GattDetailsState.Idle,
        GattDetailsState.Loading -> GattLoadingState()

        is GattDetailsState.Error -> GattErrorState(message = state.message, onRetry = onRetry)

        is GattDetailsState.Success -> {
            if (state.details.services.isEmpty()) {
                GattEmptyState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("GATT Services", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${state.details.services.size} service(s) discovered",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    state.details.services.forEachIndexed { index, service ->
                        GattServiceCard(index = index + 1, service = service)
                    }
                }
            }
        }
    }
}

@Composable
private fun GattLoadingState() {
    Surface(
        color = Color(0xFFE3F2FD),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Discovering GATT services", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Connecting briefly to read UUIDs, characteristics, and descriptors.", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GattErrorState(message: String, onRetry: () -> Unit) {
    Surface(
        color = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFC62828))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GATT discovery failed", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFC62828))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = Color(0xFF5D4037), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Discovery")
            }
        }
    }
}

@Composable
private fun GattEmptyState() {
    Surface(
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "No GATT services were reported by this device.",
            modifier = Modifier.padding(16.dp),
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun GattServiceCard(index: Int, service: GattServiceInfo) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF1976D2), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "#$index",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(service.type, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2), fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            DetailRow(label = "Service UUID", value = service.uuid)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Characteristics (${service.characteristics.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (service.characteristics.isEmpty()) {
                Text("No characteristics", color = Color.Gray, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    service.characteristics.forEach { characteristic ->
                        GattCharacteristicCard(characteristic)
                    }
                }
            }
        }
    }
}

@Composable
private fun GattCharacteristicCard(characteristic: GattCharacteristicInfo) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Characteristic UUID", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(characteristic.uuid, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Properties", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                characteristic.properties.forEach { property ->
                    AssistChip(onClick = {}, label = { Text(property, fontSize = 12.sp) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Descriptors (${characteristic.descriptors.size})", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            if (characteristic.descriptors.isEmpty()) {
                Text("No descriptors", color = Color.Gray, fontSize = 12.sp)
            } else {
                characteristic.descriptors.forEach { descriptor ->
                    Text(descriptor, fontSize = 12.sp, color = Color(0xFF37474F))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF263238))
    }
}

private fun signalStrengthLabel(rssi: Int): String {
    return when {
        rssi >= -50 -> "Excellent"
        rssi >= -65 -> "Good"
        rssi >= -80 -> "Fair"
        else -> "Weak"
    }
}

private fun signalColor(rssi: Int): Color {
    return when {
        rssi >= -55 -> Color(0xFF22C55E)
        rssi >= -72 -> Color(0xFFFACC15)
        else -> Color(0xFFF97316)
    }
}

@Composable
fun BluetoothDisabledBanner(onEnableBluetooth: () -> Unit) {
    Surface(
        color = Color(0xFFFFF3E0),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFBF360C))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Bluetooth is off. Turn it on to discover nearby devices.", modifier = Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = onEnableBluetooth) {
                Text("ENABLE", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BluetoothDisabledEmptyState(onEnableBluetooth: () -> Unit) {
    Surface(
        color = Color(0xFFFFF8E1),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFBF360C)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Bluetooth is turned off",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF3E2723)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Enable Bluetooth to start scanning for nearby BLE devices.",
                fontSize = 14.sp,
                color = Color(0xFF5D4037)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onEnableBluetooth,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Bluetooth")
            }
        }
    }
}

@Composable
fun PermissionBanner(onRequestPermission: () -> Unit) {
    Surface(
        color = Color(0xFFE0E0E0),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.DarkGray)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Location/Bluetooth permission required for BLE scanning.", modifier = Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = onRequestPermission) {
                Text("ALLOW", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isScanning: Boolean
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Filter devices...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            IconButton(onClick = onRefresh) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
                } else {
                    Icon(Icons.Default.Refresh, null, tint = Color(0xFF1976D2))
                }
            }
        },
        shape = RoundedCornerShape(32.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.LightGray,
            focusedBorderColor = Color(0xFF1976D2)
        )
    )
}

@Composable
private fun ScannerFeatureControls(
    isRecording: Boolean,
    sessionCount: Int,
    onToggleRecording: () -> Unit,
    onClearSession: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = isRecording,
            onClick = onToggleRecording,
            label = { Text(if (isRecording) "Recording" else "Record") },
            leadingIcon = {
                Icon(
                    if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        AssistChip(
            onClick = onExport,
            label = { Text("Export") },
            leadingIcon = {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
        AssistChip(
            onClick = onClearSession,
            enabled = sessionCount > 0,
            label = { Text("$sessionCount rows") },
            leadingIcon = {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
    }
}

@Composable
private fun ScanListFilterRow(
    selectedFilter: ScanListFilter,
    onFilterSelected: (ScanListFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(ScanListFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                leadingIcon = {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun DeviceItem(
    device: BleDevice,
    profile: ScannerDeviceProfile,
    history: List<DeviceRssiPoint>,
    onDeviceClick: () -> Unit,
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleAlert: () -> Unit,
    onToggleAutoConnect: () -> Unit,
    onEditProfile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDeviceClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (device.isConnected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1976D2)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (profile.favorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(device.address, color = Color.Gray, fontSize = 14.sp)
                    if (profile.tag.isNotBlank() || profile.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (profile.tag.isNotBlank()) {
                                AssistChip(onClick = onEditProfile, label = { Text(profile.tag, fontSize = 11.sp) })
                            }
                            if (profile.note.isNotBlank()) {
                                AssistChip(
                                    onClick = onEditProfile,
                                    label = { Text("Note", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }
                Surface(
                    color = Color(0xFFC5CAE9),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1976D2))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${device.rssi} dBm", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            if (history.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                MiniRssiGraph(
                    history = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF03A9F4), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (profile.autoConnect) "Auto-connect enabled" else signalStrengthLabel(device.rssi),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (profile.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (profile.favorite) Color(0xFFFFB300) else Color.Gray
                        )
                    }
                    IconButton(onClick = onToggleAlert, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (profile.alertEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = "Alert",
                            tint = if (profile.alertEnabled) Color(0xFFD32F2F) else Color.Gray
                        )
                    }
                    IconButton(onClick = onToggleAutoConnect, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "Auto Connect",
                            tint = if (profile.autoConnect) Color(0xFF00796B) else Color.Gray
                        )
                    }
                    IconButton(onClick = onEditProfile, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.EditNote, contentDescription = "Edit Notes", tint = Color.Gray)
                    }
                    Button(
                        onClick = onConnect,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0056B3))
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

private val ScanListFilter.label: String
    get() = when (this) {
        ScanListFilter.ALL -> "All"
        ScanListFilter.FAVORITES -> "Favorites"
        ScanListFilter.STRONG -> "Strong"
        ScanListFilter.ALERTS -> "Alerts"
        ScanListFilter.AUTO_CONNECT -> "Auto"
    }

private val ScanListFilter.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        ScanListFilter.ALL -> Icons.Default.BluetoothSearching
        ScanListFilter.FAVORITES -> Icons.Default.Star
        ScanListFilter.STRONG -> Icons.Default.SignalCellularAlt
        ScanListFilter.ALERTS -> Icons.Default.NotificationsActive
        ScanListFilter.AUTO_CONNECT -> Icons.Default.Link
    }

@Composable
private fun MiniRssiGraph(
    history: List<DeviceRssiPoint>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val points = history.takeLast(24)
        if (points.size < 2) return@Canvas
        val minRssi = -100f
        val maxRssi = -35f
        val stepX = size.width / (points.lastIndex).coerceAtLeast(1)
        val coordinates = points.mapIndexed { index, point ->
            val normalized = ((point.rssi - minRssi) / (maxRssi - minRssi)).coerceIn(0f, 1f)
            Offset(index * stepX, size.height - normalized * size.height)
        }

        drawLine(
            color = Color(0xFFE0E0E0),
            start = Offset(0f, size.height - 1.dp.toPx()),
            end = Offset(size.width, size.height - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        coordinates.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = Color(0xFF1976D2),
                start = start,
                end = end,
                strokeWidth = 2.dp.toPx()
            )
        }
        drawCircle(
            color = signalColor(points.last().rssi),
            radius = 4.dp.toPx(),
            center = coordinates.last()
        )
    }
}

@Composable
private fun DeviceProfileDialog(
    device: BleDevice,
    profile: ScannerDeviceProfile,
    onDismiss: () -> Unit,
    onSave: (note: String, tag: String) -> Unit
) {
    var note by remember(profile.address) { mutableStateOf(profile.note) }
    var tag by remember(profile.address) { mutableStateOf(profile.tag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(device.name ?: "Device Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(device.address, color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag") },
                    placeholder = { Text("e.g. Sensor, Beacon, Lab") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Where this device lives or what it does") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(note, tag) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
