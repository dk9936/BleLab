package com.rama.blelab.presentation.websocket

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSocketScreen(
    viewModel: WebSocketViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var useSecureWebSocket by remember { mutableStateOf(true) }
    var host by remember { mutableStateOf("echo.websocket.events") }
    var port by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isConnected = connectionState == WebSocketConnectionState.CONNECTED
    val isConnecting = connectionState == WebSocketConnectionState.CONNECTING

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
                            text = "Web Socket Lab",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                        Text(
                            text = connectionState.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            color = connectionStateColor(connectionState)
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
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = !useSecureWebSocket,
                    onClick = {
                        useSecureWebSocket = false
                        if (host == "echo.websocket.events") {
                            host = "10.0.2.2"
                            port = "8080"
                        }
                    },
                    enabled = !isConnected && !isConnecting,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Local ws")
                }
                SegmentedButton(
                    selected = useSecureWebSocket,
                    onClick = {
                        useSecureWebSocket = true
                        if (host == "10.0.2.2") {
                            host = "echo.websocket.events"
                            port = ""
                        }
                    },
                    enabled = !isConnected && !isConnecting,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Server wss")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnected && !isConnecting,
                label = { Text("Host or IP") },
                leadingIcon = {
                    Icon(Icons.Default.SettingsEthernet, contentDescription = null)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter { it.isDigit() } },
                    modifier = Modifier.weight(0.38f),
                    singleLine = true,
                    enabled = !isConnected && !isConnecting,
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.weight(0.62f),
                    singleLine = true,
                    enabled = !isConnected && !isConnecting,
                    label = { Text("Path") }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildWebSocketUrl(useSecureWebSocket, host, port, path),
                fontSize = 12.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    if (isConnected || isConnecting) {
                        viewModel.disconnect()
                    } else {
                        viewModel.connect(buildWebSocketUrl(useSecureWebSocket, host, port, path))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected || isConnecting) Color(0xFFD32F2F) else Color(0xFF1976D2)
                )
            ) {
                Icon(
                    imageVector = if (isConnected || isConnecting) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isConnected || isConnecting) "Disconnect" else "Connect")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("TRAFFIC", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        EmptyLogState()
                    }
                }
                items(logs) { log ->
                    WebSocketLogItem(log)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = isConnected,
                    label = { Text("Message") }
                )
                IconButton(
                    onClick = {
                        viewModel.send(message)
                        message = ""
                    },
                    enabled = isConnected && message.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun EmptyLogState() {
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
                imageVector = Icons.Default.SettingsEthernet,
                contentDescription = null,
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Connect to start testing", color = Color.Gray)
        }
    }
}

@Composable
private fun WebSocketLogItem(log: WebSocketLog) {
    val isSent = log.type == WebSocketLogType.SENT
    val alignment = if (isSent) Alignment.End else Alignment.Start
    val background = when (log.type) {
        WebSocketLogType.SENT -> Color(0xFF1E88E5)
        WebSocketLogType.RECEIVED -> Color(0xFFF1F1F1)
        WebSocketLogType.SYSTEM -> Color(0xFFE8F5E9)
        WebSocketLogType.ERROR -> Color(0xFFFFEBEE)
    }
    val textColor = if (isSent) Color.White else Color(0xFF1F2937)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = background
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "${log.type.name} - ${log.timestamp}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.message,
                    fontSize = 14.sp,
                    color = textColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun connectionStateColor(state: WebSocketConnectionState): Color {
    return when (state) {
        WebSocketConnectionState.DISCONNECTED -> Color.Gray
        WebSocketConnectionState.CONNECTING -> Color(0xFFF57C00)
        WebSocketConnectionState.CONNECTED -> Color(0xFF2E7D32)
    }
}

private fun buildWebSocketUrl(
    useSecureWebSocket: Boolean,
    host: String,
    port: String,
    path: String
): String {
    val scheme = if (useSecureWebSocket) "wss" else "ws"
    val cleanHost = host.trim()
        .removePrefix("ws://")
        .removePrefix("wss://")
        .trimEnd('/')
    val cleanPort = port.trim()
    val cleanPath = path.trim().trimStart('/')
    val portPart = if (cleanPort.isBlank()) "" else ":$cleanPort"
    val pathPart = if (cleanPath.isBlank()) "" else "/$cleanPath"

    return "$scheme://$cleanHost$portPart$pathPart"
}
