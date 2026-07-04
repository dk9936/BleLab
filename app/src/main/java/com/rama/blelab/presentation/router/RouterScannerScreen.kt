package com.rama.blelab.presentation.router

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterScannerScreen(
    viewModel: RouterScannerViewModel,
    onBack: () -> Unit,
    onRouterClick: (NearbyRouter) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var hasPermission by remember { mutableStateOf(hasRouterScanPermission(context)) }
    var searchText by remember { mutableStateOf("") }
    val filteredRouters = remember(searchText, state.routers) {
        state.routers.filter { router ->
            searchText.isBlank() || router.name.contains(searchText, ignoreCase = true)
        }
    }

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
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search router by name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            when {
                                !hasPermission -> permissionLauncher.launch(routerScanPermissions())
                                !state.isWifiEnabled -> context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                else -> viewModel.startScan(hasPermission)
                            }
                        },
                        enabled = !state.isScanning
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1976D2)
                            )
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = "Scan nearby routers")
                        }
                    }
                }
            )

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(message, color = Color(0xFFD32F2F), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "NEARBY ROUTERS",
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
                if (!state.isScanning && state.routers.isEmpty()) {
                    item {
                        EmptyRouterState()
                    }
                } else if (!state.isScanning && filteredRouters.isEmpty()) {
                    item {
                        EmptyRouterState("No router names match your search")
                    }
                }
                items(filteredRouters) { router ->
                    RouterItem(
                        router = router,
                        onClick = { onRouterClick(router) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRouterState(message: String = "No nearby routers found yet") {
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
            Text(message, color = Color.Gray)
        }
    }
}

@Composable
private fun RouterItem(
    router: NearbyRouter,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (router.isConnected) Color(0xFFE3F2FD) else Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (router.isConnected) Icons.Default.Router else Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (router.isConnected) Color(0xFF1976D2) else Color(0xFF2E7D32)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = router.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = router.bssid,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF1F2937)
                )
                Text(
                    text = "${router.security} - ${router.signalDbm} dBm - ${frequencyText(router)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = signalText(router.signalLevel),
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterDetailsScreen(
    router: NearbyRouter?,
    onBack: () -> Unit,
    onOpenConnectedTools: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Router Details",
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
        if (router == null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Router details unavailable", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Go back and select a scanned router again.", color = Color.Gray, fontSize = 14.sp)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (router.isConnected) Color(0xFFE3F2FD) else Color(0xFFF8F9FA)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (router.isConnected) Icons.Default.Router else Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = if (router.isConnected) Color(0xFF1976D2) else Color(0xFF2E7D32)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(router.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111827))
                        Text(if (router.isConnected) "Connected router" else "Nearby router", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            RouterDetailRows(router)

            if (router.isConnected) {
                Button(
                    onClick = onOpenConnectedTools,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.Router, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Open Router Tools")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterToolsScreen(
    viewModel: RouterScannerViewModel,
    onBack: () -> Unit,
    onOpenSpeedGraph: () -> Unit
) {
    val context = LocalContext.current
    val toolsState by viewModel.routerToolsState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startConnectedRouterToolsLiveUpdates()
        onDispose {
            viewModel.stopConnectedRouterToolsLiveUpdates()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Connected Router",
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                toolsState.networkInfo?.let { info ->
                    RouterToolsSummary(
                        info = info,
                        onOpenAdminPanel = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.adminUrl)))
                        }
                    )
                } ?: RouterToolsMessage(
                    message = toolsState.errorMessage ?: "Router tools need an active Wi-Fi connection."
                )
            }

            item {
                RouterInternetSection(
                    state = toolsState,
                    onSpeedClick = onOpenSpeedGraph
                )
            }

            item {
                Text(
                    text = "CONNECTED DEVICES",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            if (toolsState.connectedDevices.isNotEmpty()) {
                items(toolsState.connectedDevices) { device ->
                    ConnectedRouterDeviceItem(device)
                }
            } else if (toolsState.isLoading) {
                item {
                    RouterToolsLoading()
                }
            } else if (toolsState.connectedDevices.isEmpty()) {
                item {
                    RouterToolsMessage("No connected devices found yet")
                }
            }
        }
    }
}

@Composable
private fun RouterToolsSummary(
    info: ConnectedNetworkInfo,
    onOpenAdminPanel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(info.ssid, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
            RouterDetailRow("Phone IP", info.phoneIp)
            RouterDetailRow("Gateway", info.gatewayIp)
            RouterDetailRow("Subnet", info.subnetMask)
            RouterDetailRow("Admin URL", info.adminUrl)
            Button(
                onClick = onOpenAdminPanel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Icon(Icons.Default.Router, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Open Admin Panel")
            }
        }
    }
}

@Composable
private fun RouterInternetSection(
    state: RouterToolsState,
    onSpeedClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Internet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
            Spacer(modifier = Modifier.height(8.dp))
            RouterDetailRow(
                label = "Status",
                value = when (state.hasInternet) {
                    true -> "Online"
                    false -> "Offline"
                    null -> if (state.isLoading) "Checking" else "Unknown"
                }
            )
            RouterDetailRow("Service Provider", state.serviceProvider ?: if (state.isLoading) "Fetching" else "Unknown")
            RouterDetailRow("Public IP", state.publicIp ?: if (state.isLoading) "Fetching" else "Unknown")
            RouterDetailRow(
                label = "Download Speed",
                value = speedValueText(state.downloadMbps, state.isSpeedTesting),
                valueColor = speedTrendColor(state.downloadTrend),
                onClick = onSpeedClick
            )
            RouterDetailRow(
                label = "Upload Speed",
                value = speedValueText(state.uploadMbps, state.isSpeedTesting),
                valueColor = speedTrendColor(state.uploadTrend),
                onClick = onSpeedClick
            )
            state.speedErrorMessage?.let { message ->
                RouterDetailRow(
                    label = "Speed Test",
                    value = message,
                    valueColor = Color(0xFFD32F2F)
                )
            }
            RouterDetailRow("Live Update", state.lastUpdatedMillis?.let { "Every 5 seconds" } ?: "Starting")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedGraphScreen(
    viewModel: RouterScannerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.routerToolsState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startConnectedRouterToolsLiveUpdates()
        onDispose {
            viewModel.stopConnectedRouterToolsLiveUpdates()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Speed Graph",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8F9FA)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouterDetailRow(
                        label = "Download",
                        value = speedValueText(state.downloadMbps, state.isSpeedTesting),
                        valueColor = speedTrendColor(state.downloadTrend)
                    )
                    RouterDetailRow(
                        label = "Upload",
                        value = speedValueText(state.uploadMbps, state.isSpeedTesting),
                        valueColor = speedTrendColor(state.uploadTrend)
                    )
                    RouterDetailRow(
                        label = "Status",
                        value = when (state.hasInternet) {
                            true -> "Online"
                            false -> "Offline"
                            null -> if (state.isLoading) "Checking" else "Unknown"
                        }
                    )
                }
            }

            SpeedChart(
                samples = state.speedHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }
    }
}

@Composable
private fun SpeedChart(
    samples: List<SpeedSample>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        if (samples.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Collecting speed samples...", color = Color.Gray)
            }
            return@Surface
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val maxSpeed = samples
                .flatMap { listOfNotNull(it.downloadMbps, it.uploadMbps) }
                .maxOrNull()
                ?.coerceAtLeast(1.0)
                ?: 1.0
            val widthStep = size.width / (samples.lastIndex.coerceAtLeast(1))
            val chartHeight = size.height

            fun buildPath(values: List<Double?>): Path {
                val path = Path()
                var started = false
                values.forEachIndexed { index, value ->
                    if (value == null) return@forEachIndexed
                    val x = widthStep * index
                    val y = chartHeight - ((value / maxSpeed).toFloat() * chartHeight)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                return path
            }

            drawLine(
                color = Color(0xFFE0E0E0),
                start = androidx.compose.ui.geometry.Offset(0f, chartHeight),
                end = androidx.compose.ui.geometry.Offset(size.width, chartHeight),
                strokeWidth = 2f
            )
            drawPath(
                path = buildPath(samples.map { it.downloadMbps }),
                color = Color(0xFF2E7D32),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawPath(
                path = buildPath(samples.map { it.uploadMbps }),
                color = Color(0xFF1976D2),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun RouterToolsLoading() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
            Spacer(modifier = Modifier.size(10.dp))
            Text("Checking router tools...", color = Color.Gray)
        }
    }
}

@Composable
private fun RouterToolsMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = Color.Gray
        )
    }
}

@Composable
private fun ConnectedRouterDeviceItem(device: ConnectedRouterDevice) {
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
                imageVector = deviceIcon(device),
                contentDescription = device.deviceType,
                modifier = Modifier.size(32.dp),
                tint = deviceIconTint(device)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = device.displayName,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = if (device.isCurrentDevice) "${device.deviceType} - Connected to this router" else device.deviceType,
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
                Text(device.ipAddress, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF1F2937))
                device.hostName?.let { hostName ->
                    Text(hostName, fontSize = 12.sp, color = Color.Gray)
                }
                Text(device.macAddress ?: "MAC unavailable", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

private fun deviceIcon(device: ConnectedRouterDevice): ImageVector {
    if (device.isRouter) return Icons.Default.Router
    if (device.isCurrentDevice) return Icons.Default.PhoneAndroid

    return when (device.deviceType) {
        "Mobile device" -> Icons.Default.PhoneAndroid
        "Laptop" -> Icons.Default.Laptop
        "Computer" -> Icons.Default.DesktopWindows
        "Media device" -> Icons.Default.Tv
        "Printer" -> Icons.Default.Print
        "Camera" -> Icons.Default.PhotoCamera
        "Smart speaker" -> Icons.Default.Speaker
        "Wearable" -> Icons.Default.Watch
        else -> Icons.Default.Devices
    }
}

private fun deviceIconTint(device: ConnectedRouterDevice): Color {
    return when {
        device.isRouter -> Color(0xFF1976D2)
        device.isCurrentDevice -> Color(0xFF6A1B9A)
        else -> Color(0xFF2E7D32)
    }
}

@Composable
private fun RouterDetailRows(router: NearbyRouter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            RouterDetailRow("Name", router.name)
            RouterDetailRow("BSSID", router.bssid)
            RouterDetailRow("Password", "Not available from router scan")
            RouterDetailRow("Security", router.security)
            RouterDetailRow("Signal", "${router.signalDbm} dBm")
            RouterDetailRow("Signal Quality", signalText(router.signalLevel))
            RouterDetailRow("Frequency", "${router.frequencyMhz} MHz")
            RouterDetailRow("Channel", router.channel?.toString() ?: "Unknown")
            RouterDetailRow("Status", if (router.isConnected) "Connected" else "Not connected")
        }
    }
}

@Composable
private fun RouterDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF111827),
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            color = Color.Gray,
            fontSize = 13.sp
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(0.62f)
                .padding(start = 16.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = if (label == "BSSID") FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End
        )
    }
}

private fun speedValueText(speedMbps: Double?, isTesting: Boolean): String {
    return when {
        isTesting && speedMbps == null -> "Testing"
        isTesting && speedMbps != null -> "${String.format("%.2f Mbps", speedMbps)} - updating"
        speedMbps != null -> String.format("%.2f Mbps", speedMbps)
        else -> "Unknown"
    }
}

private fun speedTrendColor(trend: SpeedTrend): Color {
    return when (trend) {
        SpeedTrend.Up -> Color(0xFF2E7D32)
        SpeedTrend.Down -> Color(0xFFD32F2F)
        SpeedTrend.Neutral -> Color(0xFF111827)
    }
}

private fun frequencyText(router: NearbyRouter): String {
    val channel = router.channel?.let { "Ch $it" }
    return listOfNotNull("${router.frequencyMhz} MHz", channel).joinToString(" / ")
}

private fun signalText(signalLevel: Int): String {
    return when (signalLevel) {
        4 -> "Excellent signal"
        3 -> "Good signal"
        2 -> "Fair signal"
        1 -> "Weak signal"
        else -> "Very weak signal"
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
