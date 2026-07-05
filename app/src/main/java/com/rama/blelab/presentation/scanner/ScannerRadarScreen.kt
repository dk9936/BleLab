package com.rama.blelab.presentation.scanner

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rama.blelab.domain.model.BleDevice
import com.rama.blelab.domain.model.DeviceRssiPoint
import com.rama.blelab.domain.model.RadarSignalFilter
import com.rama.blelab.domain.model.ScannerDeviceProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerRadarScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val rssiHistory by viewModel.rssiHistory.collectAsState()
    val radarFilter by viewModel.radarFilter.collectAsState()
    val strongestDevices = remember(devices, profiles, radarFilter) {
        viewModel.filteredRadarDevices(devices, profiles)
            .sortedByDescending { it.rssi }
            .take(12)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Signal Radar",
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Radar else Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RadarFilterRow(
                selectedFilter = radarFilter,
                onFilterSelected = viewModel::setRadarFilter
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                color = Color(0xFF071A2D),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SignalRadar(
                        devices = strongestDevices,
                        isScanning = isScanning,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color(0x33000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = Color(0xFF6EE7F9),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${strongestDevices.size} tracked",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                text = "STRONGEST SIGNALS",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(strongestDevices, key = { it.address }) { device ->
                    RadarDeviceItem(
                        device = device,
                        profile = profiles[device.address] ?: ScannerDeviceProfile(address = device.address),
                        history = rssiHistory[device.address].orEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarFilterRow(
    selectedFilter: RadarSignalFilter,
    onFilterSelected: (RadarSignalFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp)
    ) {
        items(RadarSignalFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun SignalRadar(
    devices: List<BleDevice>,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.42f
        val ringColor = Color(0xFF2DD4BF)
        val sweepColor = Color(0xFF22D3EE)
        val labelPaint = Paint().apply {
            color = Color.White.toArgb()
            textSize = 24f
            isAntiAlias = true
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF123A5A), Color(0xFF071A2D)),
                center = center,
                radius = radius * 1.25f
            ),
            radius = radius * 1.25f,
            center = center
        )

        for (index in 1..4) {
            drawCircle(
                color = ringColor.copy(alpha = 0.18f + index * 0.05f),
                radius = radius * index / 4f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        drawLine(
            color = ringColor.copy(alpha = 0.28f),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = ringColor.copy(alpha = 0.28f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.dp.toPx()
        )

        if (isScanning) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, sweepColor.copy(alpha = 0.45f), Color.Transparent),
                    center = center
                ),
                startAngle = sweepAngle - 32f,
                sweepAngle = 32f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            val radians = sweepAngle.toRadians()
            drawLine(
                color = sweepColor.copy(alpha = 0.85f),
                start = center,
                end = Offset(
                    x = center.x + cos(radians) * radius,
                    y = center.y + sin(radians) * radius
                ),
                strokeWidth = 2.dp.toPx()
            )
        }

        devices.forEach { device ->
            val angle = stableAngle(device.address).toRadians()
            val distance = signalDistance(device.rssi) * radius
            val position = Offset(
                x = center.x + cos(angle) * distance,
                y = center.y + sin(angle) * distance
            )
            val dotColor = signalColor(device.rssi)

            drawCircle(
                color = dotColor.copy(alpha = 0.2f),
                radius = 14.dp.toPx(),
                center = position
            )
            drawCircle(
                color = dotColor,
                radius = 6.dp.toPx(),
                center = position
            )

            val label = device.name?.takeIf { it.isNotBlank() } ?: "Unknown"
            drawContext.canvas.nativeCanvas.drawText(
                label.take(16),
                position.x + 10.dp.toPx(),
                position.y - 8.dp.toPx(),
                labelPaint
            )
        }

        if (devices.isEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(
                "Scanning for BLE devices...",
                center.x - 120.dp.toPx(),
                center.y,
                labelPaint
            )
        }
    }
}

@Composable
private fun RadarDeviceItem(
    device: BleDevice,
    profile: ScannerDeviceProfile,
    history: List<DeviceRssiPoint>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = signalColor(device.rssi).copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    tint = signalColor(device.rssi),
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name ?: "Unknown Device",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF263238)
                    )
                    if (profile.favorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = device.address, color = Color.Gray, fontSize = 13.sp)
                if (profile.tag.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = profile.tag, color = Color(0xFF00796B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${signalStrengthLabel(device.rssi)} • ${history.size} pts",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private val RadarSignalFilter.label: String
    get() = when (this) {
        RadarSignalFilter.ALL -> "All"
        RadarSignalFilter.FAVORITES -> "Favorites"
        RadarSignalFilter.STRONG -> "Strong"
        RadarSignalFilter.MOVING -> "Moving"
        RadarSignalFilter.ALERTS -> "Alerts"
    }

private fun stableAngle(value: String): Float {
    val hashAngle = value.hashCode() % 360
    return (if (hashAngle < 0) hashAngle + 360 else hashAngle).toFloat()
}

private fun signalDistance(rssi: Int): Float {
    val clamped = max(-100, min(-35, rssi))
    return ((-35 - clamped).toFloat() / 65f).coerceIn(0.12f, 1f)
}

private fun signalColor(rssi: Int): Color {
    return when {
        rssi >= -55 -> Color(0xFF22C55E)
        rssi >= -72 -> Color(0xFFFACC15)
        else -> Color(0xFFF97316)
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

private fun Float.toRadians(): Float = (this * PI / 180f).toFloat()
