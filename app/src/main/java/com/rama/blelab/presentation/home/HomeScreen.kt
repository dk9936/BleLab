package com.rama.blelab.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBleLabClick: () -> Unit,
    onNetworkExplorerClick: () -> Unit,
    onWebSocketLabClick: () -> Unit,
    onMqttTesterClick: () -> Unit,
    onNetworkInfoClick: () -> Unit,
    onStorageInfoClick: () -> Unit,
    onRouterScannerClick: () -> Unit,
    onEspTesterClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Lab",
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Choose a lab",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pick the workspace you want to test.",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(24.dp))
            HomeOption(
                title = "BLE Lab",
                subtitle = "Scan devices, inspect GATT details, and use the BLE terminal.",
                icon = Icons.Default.Bluetooth,
                iconBackground = Color(0xFFE3F2FD),
                iconTint = Color(0xFF1976D2),
                onClick = onBleLabClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "Network Explorer",
                subtitle = "See nearby Wi-Fi, routers, BLE, and Bluetooth devices in one place.",
                icon = Icons.Default.Router,
                iconBackground = Color(0xFFE8EAF6),
                iconTint = Color(0xFF3949AB),
                onClick = onNetworkExplorerClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "Web Socket Lab",
                subtitle = "Connect to a WebSocket URL, send messages, and inspect traffic.",
                icon = Icons.Default.SettingsEthernet,
                iconBackground = Color(0xFFE8F5E9),
                iconTint = Color(0xFF2E7D32),
                onClick = onWebSocketLabClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "MQTT Tester",
                subtitle = "Connect to an MQTT broker, subscribe to topics, and publish payloads.",
                icon = Icons.Default.SettingsEthernet,
                iconBackground = Color(0xFFEDE7F6),
                iconTint = Color(0xFF5E35B1),
                onClick = onMqttTesterClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "Network Info",
                subtitle = "View this device connection details and live upload/download counters.",
                icon = Icons.Default.SettingsEthernet,
                iconBackground = Color(0xFFE0F7FA),
                iconTint = Color(0xFF00838F),
                onClick = onNetworkInfoClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "Storage Info",
                subtitle = "See device storage usage for images, videos, audio, downloads, and documents.",
                icon = Icons.Default.Storage,
                iconBackground = Color(0xFFFFF3E0),
                iconTint = Color(0xFFF57C00),
                onClick = onStorageInfoClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "Router Scanner",
                subtitle = "Scan the current Wi-Fi router subnet for connected devices.",
                icon = Icons.Default.Router,
                iconBackground = Color(0xFFFFF3E0),
                iconTint = Color(0xFFF57C00),
                onClick = onRouterScannerClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeOption(
                title = "ESP Tester",
                subtitle = "Test ESP32/ESP8266 HTTP endpoints, GPIO actions, and firmware status.",
                icon = Icons.Default.Memory,
                iconBackground = Color(0xFFE0F2F1),
                iconTint = Color(0xFF00796B),
                onClick = onEspTesterClick
            )
        }
    }
}

@Composable
private fun HomeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBackground, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}
