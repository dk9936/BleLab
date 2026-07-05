package com.rama.blelab.presentation.esp

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspTesterScreen(
    viewModel: EspTesterViewModel,
    onBack: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val espDevices by viewModel.espDevices.collectAsState()
    var host by remember { mutableStateOf("192.168.4.1") }
    var customPath by remember { mutableStateOf("/status") }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ESP Tester",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                        Text(
                            text = "HTTP checks for ESP32 / ESP8266 firmware",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearLogs) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FA))
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("ESP IP / host") },
                        placeholder = { Text("192.168.4.1 or esp32.local") },
                        leadingIcon = {
                            Icon(Icons.Default.SettingsEthernet, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    Button(
                        onClick = viewModel::scanEspDevices,
                        enabled = !isScanning && !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isScanning) "Scanning ESP Devices" else "Scan ESP Devices")
                    }

                    if (espDevices.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(espDevices) { device ->
                                EspDeviceChip(
                                    device = device,
                                    onClick = { host = device.ipAddress }
                                )
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.quickActions) { action ->
                            AssistChip(
                                onClick = { viewModel.sendRequest(host, action.path) },
                                enabled = !isLoading,
                                label = { Text(action.label) },
                                leadingIcon = {
                                    Icon(
                                        quickActionIcon(action.label),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customPath,
                            onValueChange = { customPath = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Custom path") },
                            placeholder = { Text("/status") },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.sendRequest(host, customPath) },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0056B3))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "REQUEST LOG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        EmptyEspLog()
                    }
                }
                items(logs) { log ->
                    EspLogItem(log)
                }
            }
        }
    }
}

@Composable
private fun EmptyEspLog() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Send a quick action or custom endpoint to test your ESP firmware.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EspDeviceChip(
    device: EspCandidateDevice,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Column {
                Text(device.ipAddress, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${device.hint} • ${device.matchedPath}", fontSize = 10.sp, color = Color.Gray)
            }
        },
        leadingIcon = {
            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    )
}

@Composable
private fun EspLogItem(log: EspTesterLog) {
    val color = when (log.type) {
        EspTesterLogType.REQUEST -> Color(0xFF1976D2)
        EspTesterLogType.RESPONSE -> Color(0xFF00796B)
        EspTesterLogType.SYSTEM -> Color.Gray
        EspTesterLogType.ERROR -> Color(0xFFD32F2F)
    }
    val icon = when (log.type) {
        EspTesterLogType.REQUEST -> Icons.AutoMirrored.Filled.Send
        EspTesterLogType.RESPONSE -> Icons.Default.CheckCircle
        EspTesterLogType.SYSTEM -> Icons.Default.Memory
        EspTesterLogType.ERROR -> Icons.Default.ErrorOutline
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${log.timestamp} • ${log.type.name}",
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = log.message,
                color = Color(0xFF263238),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun quickActionIcon(label: String) = when (label) {
    "LED On", "LED Off" -> Icons.Default.Bolt
    "Restart" -> Icons.Default.Memory
    else -> Icons.Default.Link
}
