package com.rama.blelab.presentation.network

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoScreen(
    viewModel: NetworkInfoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val activeAppTraffic = state.appTraffic.filter {
        it.rxBytesPerSecond > 0L || it.txBytesPerSecond > 0L
    }
    var selectedAppTraffic by remember { mutableStateOf<AppNetworkTraffic?>(null) }

    DisposableEffect(Unit) {
        viewModel.startMonitoring()
        onDispose { viewModel.stopMonitoring() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Network Info",
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.transport,
                            color = if (state.transport == "Disconnected") Color.Gray else Color(0xFF2E7D32),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshNow) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFFF8F9FA))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RateCard(
                        title = "Download",
                        value = formatBytesPerSecond(state.rxBytesPerSecond),
                        icon = Icons.Default.Download,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.weight(1f)
                    )
                    RateCard(
                        title = "Upload",
                        value = formatBytesPerSecond(state.txBytesPerSecond),
                        icon = Icons.Default.Upload,
                        color = Color(0xFF00796B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionCard(title = "Current Network") {
                    InfoRow("Transport", state.transport)
                    InfoRow("Interface", state.interfaceName)
                    InfoRow("Local IP", state.localIp)
                    InfoRow("Wi-Fi SSID", state.wifiSsid)
                    InfoRow("Gateway", state.gatewayIp)
                    InfoRow("DNS", state.dnsServers.ifEmpty { listOf("-") }.joinToString(", "))
                }
            }

            item {
                SectionCard(title = "Device Traffic") {
                    InfoRow("Received", formatBytes(state.totalRxBytes))
                    InfoRow("Sent", formatBytes(state.totalTxBytes))
                    InfoRow("Receiving now", formatBytesPerSecond(state.rxBytesPerSecond))
                    InfoRow("Sending now", formatBytesPerSecond(state.txBytesPerSecond))
                }
            }

            item {
                SectionCard(title = "BleLab App Traffic") {
                    InfoRow("Received", formatBytes(state.appRxBytes))
                    InfoRow("Sent", formatBytes(state.appTxBytes))
                    InfoRow("Receiving now", formatBytesPerSecond(state.appRxBytesPerSecond))
                    InfoRow("Sending now", formatBytesPerSecond(state.appTxBytesPerSecond))
                }
            }

            item {
                Text(
                    text = "ACTIVE NOW",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activeAppTraffic.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8F9FA)
                    ) {
                        Text(
                            text = "No app is using noticeable network data right now.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(activeAppTraffic, key = { "active-${it.uid}" }) { traffic ->
                    AppNameRow(
                        traffic = traffic,
                        highlight = true,
                        onClick = { selectedAppTraffic = traffic }
                    )
                }
            }

            item {
                Text(
                    text = "PER-APP DATA USED",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!state.hasUsageAccess) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF8E1)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Enable Usage Access to show accurate app data used today for apps like YouTube and WhatsApp.",
                                color = Color(0xFF5D4037),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Text("Open Usage Access")
                            }
                        }
                    }
                }
            }

            if (state.appTraffic.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8F9FA)
                    ) {
                        Text(
                            text = "No app data usage available yet.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(state.appTraffic, key = { it.uid }) { traffic ->
                    AppNameRow(
                        traffic = traffic,
                        onClick = { selectedAppTraffic = traffic }
                    )
                }
            }

            item {
                SectionCard(title = "Status") {
                    InfoRow("Monitoring", if (state.isMonitoring) "Live" else "Stopped")
                    InfoRow("Last update", state.lastUpdatedMillis?.toTimeText() ?: "-")
                }
            }

            item {
                Text(
                    text = if (state.hasUsageAccess) {
                        "Per-app values show ${state.appTrafficWindow.lowercase()} data volume by Android UID, not private packet contents."
                    } else {
                        "Without Usage Access, Android may only expose limited live UID counters."
                    },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }

    selectedAppTraffic?.let { traffic ->
        AppTrafficDialog(
            traffic = traffic,
            onDismiss = { selectedAppTraffic = null }
        )
    }
}

@Composable
private fun AppNameRow(
    traffic: AppNetworkTraffic,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) Color(0xFFE3F2FD) else Color(0xFFF8F9FA)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = Color(0xFF1976D2)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = traffic.appName,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (highlight) "Using network now" else traffic.packageNames.firstOrNull().orEmpty(),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Text(
                text = formatBytes(traffic.totalBytes),
                color = Color(0xFF1976D2),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppTrafficDialog(
    traffic: AppNetworkTraffic,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(traffic.appName) },
        text = {
            Column {
                Text(
                    text = traffic.packageNames.joinToString(", "),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("Downloaded today", formatBytes(traffic.rxBytes))
                InfoRow("Uploaded today", formatBytes(traffic.txBytes))
                InfoRow("Total used today", formatBytes(traffic.totalBytes))
                InfoRow("Downloading now", formatBytesPerSecond(traffic.rxBytesPerSecond))
                InfoRow("Uploading now", formatBytesPerSecond(traffic.txBytesPerSecond))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TrafficMiniStat(
    label: String,
    value: String,
    rate: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFF111827), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(rate, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RateCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsEthernet,
                    contentDescription = null,
                    tint = Color(0xFF1976D2)
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(
            text = value,
            color = Color(0xFF1F2937),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.2f %s", value, units[unitIndex])
    }
}

private fun formatBytesPerSecond(bytes: Long): String = "${formatBytes(bytes)}/s"

private fun Long.toTimeText(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))
}
