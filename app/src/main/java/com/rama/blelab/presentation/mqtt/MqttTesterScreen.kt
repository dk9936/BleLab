package com.rama.blelab.presentation.mqtt

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttTesterScreen(
    viewModel: MqttTesterViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val subscribedTopics by viewModel.subscribedTopics.collectAsState()
    val isConnected = connectionState == MqttConnectionState.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isConnected) "MQTT Dashboard" else "MQTT Connect",
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
                    if (isConnected) {
                        IconButton(onClick = viewModel::clearLogs) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Responses")
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FA))
            )
        }
    ) { padding ->
        if (isConnected) {
            MqttDashboard(
                logs = logs,
                subscribedTopics = subscribedTopics,
                onSubscribe = viewModel::subscribe,
                onPublish = viewModel::publish,
                modifier = Modifier.padding(padding)
            )
        } else {
            MqttConnectScreen(
                connectionState = connectionState,
                latestLog = logs.lastOrNull(),
                onConnect = viewModel::connect,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun MqttConnectScreen(
    connectionState: MqttConnectionState,
    latestLog: MqttLog?,
    onConnect: (String, String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var host by remember { mutableStateOf("broker.hivemq.com") }
    var port by remember { mutableStateOf("1883") }
    var clientId by remember { mutableStateOf("BleLab") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isConnecting = connectionState == MqttConnectionState.CONNECTING

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnecting,
            label = { Text("Broker host or IP") },
            leadingIcon = {
                Icon(Icons.Default.SettingsEthernet, contentDescription = null)
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = port,
                onValueChange = { value -> port = value.filter { it.isDigit() } },
                modifier = Modifier.weight(0.34f),
                singleLine = true,
                enabled = !isConnecting,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                modifier = Modifier.weight(0.66f),
                singleLine = true,
                enabled = !isConnecting,
                label = { Text("Client ID") }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isConnecting,
                label = { Text("Username") }
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isConnecting,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation()
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = { onConnect(host, port, clientId, username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isConnecting) "Connecting" else "Connect")
        }
        if (latestLog?.type == MqttLogType.ERROR) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = latestLog.message,
                color = Color(0xFFD32F2F),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MqttDashboard(
    logs: List<MqttLog>,
    subscribedTopics: List<String>,
    onSubscribe: (String) -> Unit,
    onPublish: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var subscribeTopic by remember { mutableStateOf("blelab/test") }
    var publishTopic by remember { mutableStateOf("blelab/test") }
    var payload by remember { mutableStateOf("hello from BleLab") }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Topics",
                value = subscribedTopics.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Responses",
                value = logs.count { it.type == MqttLogType.RECEIVED }.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF8F9FA)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subscribeTopic,
                        onValueChange = { subscribeTopic = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Subscribe topic") },
                        leadingIcon = {
                            Icon(Icons.Default.Topic, contentDescription = null)
                        }
                    )
                    IconButton(
                        onClick = { onSubscribe(subscribeTopic) },
                        enabled = subscribeTopic.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Subscribe")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (subscribedTopics.isEmpty()) {
                    Text("No topics subscribed", color = Color.Gray, fontSize = 13.sp)
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subscribedTopics.forEach { topic ->
                            TopicPill(topic = topic)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF8F9FA)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = publishTopic,
                    onValueChange = { publishTopic = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Publish topic") },
                    leadingIcon = {
                        Icon(Icons.Default.Topic, contentDescription = null)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Command payload") },
                        maxLines = 4
                    )
                    IconButton(
                        onClick = { onPublish(publishTopic, payload) },
                        enabled = publishTopic.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Publish")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("RESPONSES", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
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
                    EmptyMqttLogState()
                }
            }
            items(logs) { log ->
                MqttLogItem(log)
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE3F2FD)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TopicPill(topic: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White
    ) {
        Text(
            text = topic,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF1F2937),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyMqttLogState() {
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
            Text("Waiting for MQTT traffic", color = Color.Gray)
        }
    }
}

@Composable
private fun MqttLogItem(log: MqttLog) {
    val isSent = log.type == MqttLogType.SENT
    val alignment = if (isSent) Alignment.End else Alignment.Start
    val background = when (log.type) {
        MqttLogType.SENT -> Color(0xFF1E88E5)
        MqttLogType.RECEIVED -> Color(0xFFF1F1F1)
        MqttLogType.SYSTEM -> Color(0xFFE8F5E9)
        MqttLogType.ERROR -> Color(0xFFFFEBEE)
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

private fun connectionStateColor(state: MqttConnectionState): Color {
    return when (state) {
        MqttConnectionState.DISCONNECTED -> Color.Gray
        MqttConnectionState.CONNECTING -> Color(0xFFF57C00)
        MqttConnectionState.CONNECTED -> Color(0xFF2E7D32)
    }
}
