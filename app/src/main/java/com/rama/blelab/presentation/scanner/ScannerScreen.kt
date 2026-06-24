package com.rama.blelab.presentation.scanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rama.blelab.domain.model.BleDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onDeviceClick: (BleDevice) -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val context = LocalContext.current

    var hasPermissions by remember {
        mutableStateOf(PermissionUtils.hasPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermissions = perms.values.all { it }
        if (hasPermissions) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermissions) {
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BLE Terminal", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.padding(start = 16.dp))
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Search, null) })
                NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Terminal, null) })
                NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.History, null) })
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
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
                    if (hasPermissions) {
                        if (isScanning) viewModel.stopScan() else viewModel.startScan()
                    } else {
                        permissionLauncher.launch(PermissionUtils.permissions.toTypedArray())
                    }
                },
                isScanning = isScanning
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("NEARBY DEVICES", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(device = device, onConnect = { onDeviceClick(device) })
                }
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
fun DeviceItem(device: BleDevice, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Column {
                    Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(device.address, color = Color.Gray, fontSize = 14.sp)
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
                    Text("Ready to pair", fontSize = 14.sp, color = Color.Gray)
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
