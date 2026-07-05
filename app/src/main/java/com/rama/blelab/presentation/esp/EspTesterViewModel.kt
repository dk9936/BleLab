package com.rama.blelab.presentation.esp

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class EspTesterLog(
    val message: String,
    val type: EspTesterLogType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

enum class EspTesterLogType {
    REQUEST,
    RESPONSE,
    SYSTEM,
    ERROR
}

data class EspQuickAction(
    val label: String,
    val path: String,
    val description: String
)

data class EspCandidateDevice(
    val ipAddress: String,
    val name: String,
    val matchedPath: String,
    val statusCode: Int,
    val hint: String
)

class EspTesterViewModel(
    context: Context
) : ViewModel() {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    private val scanClient = OkHttpClient.Builder()
        .connectTimeout(550, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .writeTimeout(550, TimeUnit.MILLISECONDS)
        .build()

    private val _logs = MutableStateFlow<List<EspTesterLog>>(emptyList())
    val logs: StateFlow<List<EspTesterLog>> = _logs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _espDevices = MutableStateFlow<List<EspCandidateDevice>>(emptyList())
    val espDevices: StateFlow<List<EspCandidateDevice>> = _espDevices.asStateFlow()

    val quickActions = listOf(
        EspQuickAction("Root", "/", "Open the default route"),
        EspQuickAction("Status", "/status", "Read device health JSON/text"),
        EspQuickAction("Info", "/info", "Read firmware and board info"),
        EspQuickAction("LED On", "/gpio?pin=2&state=1", "Set GPIO 2 high"),
        EspQuickAction("LED Off", "/gpio?pin=2&state=0", "Set GPIO 2 low"),
        EspQuickAction("Restart", "/restart", "Ask firmware to restart")
    )

    fun scanEspDevices() {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            _espDevices.value = emptyList()
            addLog("Scanning local Wi-Fi subnet for ESP devices", EspTesterLogType.SYSTEM)

            val devices = scanSubnetForEspDevices()
            _espDevices.value = devices
            addLog(
                if (devices.isEmpty()) "No ESP HTTP devices found." else "Found ${devices.size} ESP candidate(s).",
                if (devices.isEmpty()) EspTesterLogType.ERROR else EspTesterLogType.SYSTEM
            )
            _isScanning.value = false
        }
    }

    fun sendRequest(baseUrl: String, path: String) {
        val url = buildUrl(baseUrl, path)
        if (url == null) {
            addLog("Enter an ESP IP or host first.", EspTesterLogType.ERROR)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            addLog("GET $url", EspTesterLogType.REQUEST)

            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val preview = body.ifBlank { "<empty body>" }.take(1_200)
                    addLog("HTTP ${response.code} ${response.message}\n$preview", EspTesterLogType.RESPONSE)
                }
            } catch (error: Exception) {
                addLog(error.message ?: "ESP request failed", EspTesterLogType.ERROR)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        client.dispatcher.executorService.shutdown()
        scanClient.dispatcher.executorService.shutdown()
        super.onCleared()
    }

    private suspend fun scanSubnetForEspDevices(): List<EspCandidateDevice> = withContext(Dispatchers.IO) {
        val network = connectedNetwork() ?: return@withContext emptyList()
        val arpEntries = readArpTable()
        val hosts = subnetHosts(network.phoneIp, network.subnetMask)

        hosts
            .chunked(32)
            .flatMap { chunk ->
                chunk.map { ip ->
                    async {
                        val reachable = ip == network.gatewayIp || arpEntries.containsKey(ip) || isReachable(ip)
                        if (!reachable) null else probeEsp(ip)
                    }
                }.awaitAll().filterNotNull()
            }
            .distinctBy { it.ipAddress }
            .sortedBy { ipv4ToLong(it.ipAddress) }
    }

    private fun probeEsp(ip: String): EspCandidateDevice? {
        val paths = listOf("/status", "/info", "/")
        paths.forEach { path ->
            val url = "http://$ip$path"
            val result = runCatching {
                val request = Request.Builder().url(url).get().build()
                scanClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code in 200..499 && looksLikeEsp(ip, body, response.header("Server"))) {
                        EspCandidateDevice(
                            ipAddress = ip,
                            name = espName(body) ?: "ESP Device",
                            matchedPath = path,
                            statusCode = response.code,
                            hint = espHint(body, response.header("Server"))
                        )
                    } else {
                        null
                    }
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun looksLikeEsp(ip: String, body: String, server: String?): Boolean {
        val text = "$body ${server.orEmpty()}".lowercase()
        return ip == "192.168.4.1" ||
            text.contains("esp") ||
            text.contains("esp32") ||
            text.contains("esp8266") ||
            text.contains("arduino") ||
            text.contains("nodemcu") ||
            text.contains("ota") ||
            text.contains("gpio") ||
            text.contains("heap") ||
            text.contains("chip")
    }

    private fun espName(body: String): String? {
        val text = body.take(500)
        val nameRegexes = listOf(
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("\"device\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
        )
        return nameRegexes.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun espHint(body: String, server: String?): String {
        val text = "$body ${server.orEmpty()}".lowercase()
        return when {
            text.contains("esp32") -> "ESP32"
            text.contains("esp8266") -> "ESP8266"
            text.contains("nodemcu") -> "NodeMCU"
            text.contains("arduino") -> "Arduino ESP"
            text.contains("gpio") -> "GPIO endpoint"
            else -> "HTTP endpoint"
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String? {
        val trimmedBase = baseUrl.trim()
        if (trimmedBase.isBlank()) return null

        val normalizedBase = if (trimmedBase.startsWith("http://") || trimmedBase.startsWith("https://")) {
            trimmedBase.trimEnd('/')
        } else {
            "http://${trimmedBase.trimEnd('/')}"
        }
        val normalizedPath = path.trim().ifBlank { "/" }
        return if (normalizedPath.startsWith("/")) {
            normalizedBase + normalizedPath
        } else {
            "$normalizedBase/$normalizedPath"
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectedNetwork(): EspNetworkInfo? {
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val phoneIp = formatLittleEndianIp(wifiInfo.ipAddress)
        val gatewayIp = formatLittleEndianIp(dhcpInfo.gateway)
        val subnetMask = formatLittleEndianIp(dhcpInfo.netmask).takeUnless { it == "0.0.0.0" } ?: "255.255.255.0"
        return EspNetworkInfo(phoneIp = phoneIp, gatewayIp = gatewayIp, subnetMask = subnetMask)
    }

    private fun readArpTable(): Map<String, String> {
        return try {
            File("/proc/net/arp")
                .readLines()
                .drop(1)
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    val ip = parts.getOrNull(0)
                    val mac = parts.getOrNull(3)
                    if (ip != null && mac != null && mac != "00:00:00:00:00:00") ip to mac else null
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun isReachable(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip).isReachable(300)
        } catch (_: Exception) {
            false
        }
    }

    private fun subnetHosts(phoneIp: String, subnetMask: String): List<String> {
        val phone = ipv4ToLong(phoneIp)
        val mask = ipv4ToLong(subnetMask).takeUnless { it == 0L } ?: ipv4ToLong("255.255.255.0")
        val network = phone and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        val hostCount = broadcast - network - 1

        if (hostCount !in 1L..512L) {
            val prefix = phoneIp.split(".").take(3).joinToString(".")
            return (1..254).map { "$prefix.$it" }
        }

        return (network + 1..broadcast - 1).map(::longToIpv4)
    }

    private fun formatLittleEndianIp(value: Int): String {
        return listOf(
            value and 0xFF,
            value shr 8 and 0xFF,
            value shr 16 and 0xFF,
            value shr 24 and 0xFF
        ).joinToString(".")
    }

    private fun ipv4ToLong(ip: String): Long {
        return try {
            val address = InetAddress.getByName(ip)
            if (address is Inet4Address) {
                address.address.fold(0L) { result, byte -> (result shl 8) or (byte.toLong() and 0xFF) }
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun longToIpv4(value: Long): String {
        return listOf(
            value shr 24 and 0xFF,
            value shr 16 and 0xFF,
            value shr 8 and 0xFF,
            value and 0xFF
        ).joinToString(".")
    }

    private fun addLog(message: String, type: EspTesterLogType) {
        _logs.update { (it + EspTesterLog(message, type)).takeLast(200) }
    }
}

private data class EspNetworkInfo(
    val phoneIp: String,
    val gatewayIp: String,
    val subnetMask: String
)
