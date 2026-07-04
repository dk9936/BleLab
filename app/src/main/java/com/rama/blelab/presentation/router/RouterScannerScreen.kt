package com.rama.blelab.presentation.router

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterScannerScreen(
    viewModel: RouterScannerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var hasPermission by remember { mutableStateOf(hasRouterScanPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
        viewModel.refreshNetworkState(hasPermission)
        if (hasPermission) {
            viewModel.startScan(hasPermission = true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkState(hasPermission)
        if (hasPermission) {
            viewModel.startScan(hasPermission = true)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Router Scanner",
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
        ) {
            RouterStatusPanel(
                hasPermission = hasPermission,
                state = state,
                onRequestPermission = {
                    permissionLauncher.launch(routerScanPermissions())
                },
                onOpenWifiSettings = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startScan(hasPermission) },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasPermission && state.isWifiEnabled && state.isConnectedToWifi && !state.isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (state.isScanning) "Scanning" else "Scan Connected Devices")
            }

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(message, color = Color(0xFFD32F2F), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "CONNECTED DEVICES",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (!state.isScanning && state.devices.isEmpty()) {
                    item {
                        EmptyRouterDeviceState()
                    }
                }
                items(state.devices) { device ->
                    RouterDeviceItem(device)
                }
            }
        }
    }
}

@Composable
private fun RouterStatusPanel(
    hasPermission: Boolean,
    state: RouterScannerState,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            StatusRow(
                label = "Permission",
                value = if (hasPermission) "Allowed" else "Required",
                good = hasPermission
            )
            StatusRow(
                label = "Wi-Fi",
                value = if (state.isWifiEnabled) "Enabled" else "Disabled",
                good = state.isWifiEnabled
            )
            StatusRow(
                label = "Router",
                value = if (state.isConnectedToWifi) "Connected" else "Not connected",
                good = state.isConnectedToWifi
            )

            state.routerInfo?.let { info ->
                Spacer(modifier = Modifier.height(10.dp))
                RouterInfoRows(info)
            }

            if (!hasPermission || !state.isWifiEnabled || !state.isConnectedToWifi) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!hasPermission) {
                        OutlinedButton(onClick = onRequestPermission, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Permission")
                        }
                    }
                    if (!state.isWifiEnabled || !state.isConnectedToWifi) {
                        OutlinedButton(onClick = onOpenWifiSettings, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Wi-Fi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, good: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (good) Icons.Default.Wifi else Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (good) Color(0xFF2E7D32) else Color(0xFFD32F2F)
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            color = Color.Gray,
            fontSize = 13.sp
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (good) Color(0xFF2E7D32) else Color(0xFFD32F2F)
        )
    }
}

@Composable
private fun RouterInfoRows(info: RouterInfo) {
    InfoLine(label = "SSID", value = info.ssid.ifBlank { "Unknown" })
    InfoLine(label = "Phone IP", value = info.phoneIp)
    InfoLine(label = "Gateway", value = info.gatewayIp)
    InfoLine(label = "Subnet", value = info.subnetMask)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF1F2937))
    }
}

@Composable
private fun EmptyRouterDeviceState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Router, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("No scan results yet", color = Color.Gray)
        }
    }
}

@Composable
private fun RouterDeviceItem(device: RouterDevice) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (device.isRouter) Color(0xFFE3F2FD) else Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isRouter) Icons.Default.Router else Icons.Default.Devices,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (device.isRouter) Color(0xFF1976D2) else Color(0xFF2E7D32)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = if (device.isRouter) "Router" else device.hostName ?: "Network Device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = device.ipAddress,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF1F2937)
                )
                device.macAddress?.let { mac ->
                    Text(
                        text = mac,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

private fun hasRouterScanPermission(context: android.content.Context): Boolean {
    val permissions = routerScanPermissions()
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun routerScanPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
