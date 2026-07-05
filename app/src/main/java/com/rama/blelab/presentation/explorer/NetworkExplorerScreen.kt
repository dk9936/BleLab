package com.rama.blelab.presentation.explorer

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkExplorerScreen(
    viewModel: NetworkExplorerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var selectedType by remember { mutableStateOf<NearbyNetworkType?>(null) }
    var searchText by remember { mutableStateOf("") }
    var hasPermissions by remember {
        mutableStateOf(
            NetworkExplorerViewModel.requiredPermissions().all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            viewModel.startScan()
        }
    }

    val filteredItems = remember(state.items, selectedType, searchText) {
        state.items.filter { item ->
            (selectedType == null || item.type == selectedType) &&
                (searchText.isBlank() ||
                    item.name.contains(searchText, ignoreCase = true) ||
                    item.address.contains(searchText, ignoreCase = true) ||
                    item.detail.contains(searchText, ignoreCase = true))
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions && state.items.isEmpty()) {
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Network Explorer",
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearResults) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CountCard(
                    title = "Wi-Fi",
                    count = state.items.count { it.type == NearbyNetworkType.WIFI },
                    icon = Icons.Default.Wifi,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f)
                )
                CountCard(
                    title = "BLE",
                    count = state.items.count { it.type == NearbyNetworkType.BLE },
                    icon = Icons.Default.Bluetooth,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f)
                )
                CountCard(
                    title = "BT",
                    count = state.items.count { it.type == NearbyNetworkType.BLUETOOTH },
                    icon = Icons.Default.Devices,
                    color = Color(0xFF00796B),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search all networks") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { selectedType = null },
                        label = { Text("All") },
                        leadingIcon = { Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                items(NearbyNetworkType.entries) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.label()) },
                        leadingIcon = { Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (!hasPermissions) {
                Button(
                    onClick = { permissionLauncher.launch(NetworkExplorerViewModel.requiredPermissions()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.DeviceHub, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Allow Network Scan Permissions")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { if (state.isScanning) viewModel.stopScan() else viewModel.startScan() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isScanning) Color(0xFFD32F2F) else Color(0xFF1976D2)
                        )
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Stop Scan")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Scan Nearby")
                        }
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                        modifier = Modifier.weight(0.72f)
                    ) {
                        Text("Settings")
                    }
                }
            }

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = Color(0xFFD32F2F), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "NEARBY NETWORKS",
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
                if (filteredItems.isEmpty()) {
                    item { EmptyExplorerState(isScanning = state.isScanning) }
                }
                items(filteredItems, key = { it.id }) { item ->
                    NearbyNetworkRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun CountCard(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(5.dp))
            Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(count.toString(), color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NearbyNetworkRow(item: NearbyNetworkItem) {
    val color = item.type.color()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.type.icon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(34.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(item.type.label(), fontSize = 11.sp) }
                    )
                }
                Text(
                    text = item.address,
                    color = Color(0xFF1F2937),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = item.detail,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                val signal = item.signalDbm
                Text(
                    text = buildString {
                        append(signal?.let { "$it dBm - ${signalQuality(it)}" } ?: "Signal not reported")
                        if (item.isConnectedOrBonded) append(" - Paired")
                    },
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyExplorerState(isScanning: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Radar else Icons.Default.Router,
                contentDescription = null,
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isScanning) "Scanning nearby networks" else "No networks found yet",
                color = Color.Gray
            )
        }
    }
}

private fun NearbyNetworkType.label(): String {
    return when (this) {
        NearbyNetworkType.WIFI -> "Wi-Fi"
        NearbyNetworkType.BLE -> "BLE"
        NearbyNetworkType.BLUETOOTH -> "Bluetooth"
    }
}

private fun NearbyNetworkType.icon(): ImageVector {
    return when (this) {
        NearbyNetworkType.WIFI -> Icons.Default.Wifi
        NearbyNetworkType.BLE -> Icons.Default.Bluetooth
        NearbyNetworkType.BLUETOOTH -> Icons.Default.Devices
    }
}

private fun NearbyNetworkType.color(): Color {
    return when (this) {
        NearbyNetworkType.WIFI -> Color(0xFFF57C00)
        NearbyNetworkType.BLE -> Color(0xFF1976D2)
        NearbyNetworkType.BLUETOOTH -> Color(0xFF00796B)
    }
}

private fun signalQuality(rssi: Int): String {
    return when {
        rssi >= -55 -> "Excellent"
        rssi >= -67 -> "Good"
        rssi >= -75 -> "Fair"
        else -> "Weak"
    }
}
