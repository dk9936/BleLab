package com.rama.blelab.presentation.router

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RouterScannerState(
    val isWifiEnabled: Boolean = false,
    val isConnectedToWifi: Boolean = false,
    val isScanning: Boolean = false,
    val connectedRouter: NearbyRouter? = null,
    val routers: List<NearbyRouter> = emptyList(),
    val errorMessage: String? = null
)

data class NearbyRouter(
    val name: String,
    val bssid: String,
    val signalDbm: Int,
    val signalLevel: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val security: String,
    val isConnected: Boolean
)

data class RouterToolsState(
    val isLoading: Boolean = false,
    val networkInfo: ConnectedNetworkInfo? = null,
    val hasInternet: Boolean? = null,
    val serviceProvider: String? = null,
    val publicIp: String? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val downloadTrend: SpeedTrend = SpeedTrend.Neutral,
    val uploadTrend: SpeedTrend = SpeedTrend.Neutral,
    val speedHistory: List<SpeedSample> = emptyList(),
    val isSpeedTesting: Boolean = false,
    val speedErrorMessage: String? = null,
    val lastUpdatedMillis: Long? = null,
    val connectedDevices: List<ConnectedRouterDevice> = emptyList(),
    val errorMessage: String? = null
)

data class NsdScannerState(
    val isScanning: Boolean = false,
    val services: List<NsdServiceDetails> = emptyList(),
    val errorMessage: String? = null
)

data class NsdServiceDetails(
    val name: String,
    val type: String,
    val host: String?,
    val port: Int?,
    val attributes: Map<String, String> = emptyMap()
)

enum class SpeedTrend {
    Up,
    Down,
    Neutral
}

data class SpeedSample(
    val timestampMillis: Long,
    val downloadMbps: Double?,
    val uploadMbps: Double?
)

data class ConnectedNetworkInfo(
    val ssid: String,
    val phoneIp: String,
    val gatewayIp: String,
    val subnetMask: String,
    val adminUrl: String
)

data class ConnectedRouterDevice(
    val ipAddress: String,
    val displayName: String,
    val deviceType: String,
    val hostName: String?,
    val macAddress: String?,
    val isRouter: Boolean,
    val isCurrentDevice: Boolean
)

class RouterScannerViewModel(
    private val context: Context
) : ViewModel() {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _state = MutableStateFlow(RouterScannerState())
    val state: StateFlow<RouterScannerState> = _state.asStateFlow()

    private val _selectedRouter = MutableStateFlow<NearbyRouter?>(null)
    val selectedRouter: StateFlow<NearbyRouter?> = _selectedRouter.asStateFlow()

    private val _routerToolsState = MutableStateFlow(RouterToolsState())
    val routerToolsState: StateFlow<RouterToolsState> = _routerToolsState.asStateFlow()
    private var routerToolsLiveJob: Job? = null

    private val _nsdScannerState = MutableStateFlow(NsdScannerState())
    val nsdScannerState: StateFlow<NsdScannerState> = _nsdScannerState.asStateFlow()
    private val nsdDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun selectRouter(router: NearbyRouter) {
        _selectedRouter.value = router
    }

    fun loadConnectedRouterTools() {
        viewModelScope.launch {
            refreshConnectedRouterTools(
                showLoading = true,
                refreshProvider = true,
                testSpeed = true,
                refreshDevices = true
            )
        }
    }

    fun startConnectedRouterToolsLiveUpdates() {
        if (routerToolsLiveJob?.isActive == true) return

        routerToolsLiveJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                refreshConnectedRouterTools(
                    showLoading = tick == 0,
                    refreshProvider = tick % PROVIDER_REFRESH_TICKS == 0,
                    testSpeed = tick == 1 || (tick > 1 && tick % SPEED_REFRESH_TICKS == 0),
                    refreshDevices = tick % DEVICES_REFRESH_TICKS == 0
                )
                tick++
                delay(TOOLS_REFRESH_MS)
            }
        }
    }

    fun stopConnectedRouterToolsLiveUpdates() {
        routerToolsLiveJob?.cancel()
        routerToolsLiveJob = null
    }

    fun startNsdScan() {
        if (_nsdScannerState.value.isScanning) return
        stopNsdScan()

        if (!isConnectedToWifi()) {
            _nsdScannerState.update {
                it.copy(
                    isScanning = false,
                    services = emptyList(),
                    errorMessage = "Connect to Wi-Fi to scan NSD services."
                )
            }
            return
        }

        _nsdScannerState.value = NsdScannerState(isScanning = true)
        NSD_SERVICE_TYPES.forEach { serviceType ->
            val listener = createNsdDiscoveryListener(serviceType)
            nsdDiscoveryListeners.add(listener)
            runCatching {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { error ->
                _nsdScannerState.update {
                    it.copy(errorMessage = error.message ?: "Could not start NSD scan for $serviceType")
                }
            }
        }
    }

    fun stopNsdScan() {
        nsdDiscoveryListeners.forEach { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        nsdDiscoveryListeners.clear()
        _nsdScannerState.update { it.copy(isScanning = false) }
    }

    fun refreshNetworkState(hasPermission: Boolean) {
        val wifiEnabled = wifiManager.isWifiEnabled
        val connectedToWifi = isConnectedToWifi()

        _state.update {
            it.copy(
                isWifiEnabled = wifiEnabled,
                isConnectedToWifi = connectedToWifi,
                connectedRouter = if (wifiEnabled && connectedToWifi && hasPermission) connectedRouter() else null,
                errorMessage = null
            )
        }
    }

    fun startScan(hasPermission: Boolean) {
        refreshNetworkState(hasPermission)

        val currentState = _state.value
        when {
            !hasPermission -> {
                _state.update { it.copy(errorMessage = "Allow Wi-Fi permission to scan nearby routers.") }
                return
            }

            !currentState.isWifiEnabled -> {
                _state.update { it.copy(errorMessage = "Turn on Wi-Fi to scan nearby routers.") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, routers = emptyList(), errorMessage = null) }

            val scanStarted = requestWifiScan()
            if (scanStarted) {
                delay(WIFI_SCAN_SETTLE_MS)
            }

            val routers = nearbyRouters()
            _state.update {
                it.copy(
                    isScanning = false,
                    routers = routers,
                    connectedRouter = connectedRouter(),
                    errorMessage = when {
                        routers.isNotEmpty() -> null
                        scanStarted -> "No nearby routers found. Make sure location is enabled for Wi-Fi scanning."
                        else -> "Wi-Fi scan could not start. Showing cached results if Android allows them."
                    }
                )
            }
        }
    }

    private fun isConnectedToWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun createNsdDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveNsdService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _nsdScannerState.update { state ->
                    state.copy(
                        services = state.services.filterNot {
                            it.name == serviceInfo.serviceName && it.type == serviceInfo.serviceType
                        }
                    )
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
                _nsdScannerState.update {
                    it.copy(
                        isScanning = nsdDiscoveryListeners.isNotEmpty(),
                        errorMessage = "NSD scan failed for $serviceType ($errorCode)"
                    )
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                addNsdService(serviceInfo.toNsdServiceDetails())
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                addNsdService(serviceInfo.toNsdServiceDetails())
            }
        }
        runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
            .onFailure { addNsdService(serviceInfo.toNsdServiceDetails()) }
    }

    private fun addNsdService(service: NsdServiceDetails) {
        _nsdScannerState.update { state ->
            val updated = (state.services.filterNot { it.name == service.name && it.type == service.type } + service)
                .sortedWith(compareBy<NsdServiceDetails> { it.type }.thenBy { it.name })
            state.copy(services = updated, errorMessage = null)
        }
    }

    private fun NsdServiceInfo.toNsdServiceDetails(): NsdServiceDetails {
        return NsdServiceDetails(
            name = serviceName,
            type = serviceType,
            host = host?.hostAddress,
            port = port.takeIf { it > 0 },
            attributes = attributes.mapValues { entry ->
                entry.value.toString(Charsets.UTF_8)
            }
        )
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun refreshConnectedRouterTools(
        showLoading: Boolean,
        refreshProvider: Boolean,
        testSpeed: Boolean,
        refreshDevices: Boolean
    ) {
        val current = _routerToolsState.value
        _routerToolsState.update {
            it.copy(
                isLoading = showLoading && current.networkInfo == null,
                isSpeedTesting = testSpeed,
                errorMessage = null
            )
        }

        val networkInfo = connectedNetworkInfo()
        if (networkInfo == null) {
            _routerToolsState.update {
                it.copy(
                    isLoading = false,
                    networkInfo = null,
                    hasInternet = false,
                    serviceProvider = null,
                    publicIp = null,
                    downloadMbps = null,
                    uploadMbps = null,
                    downloadTrend = SpeedTrend.Neutral,
                    uploadTrend = SpeedTrend.Neutral,
                    isSpeedTesting = false,
                    speedErrorMessage = null,
                    connectedDevices = emptyList(),
                    lastUpdatedMillis = System.currentTimeMillis(),
                    errorMessage = "Connect to a Wi-Fi router to use these tools."
                )
            }
            return
        }

        val hasInternet = hasValidatedInternet() || hasInternetProbe()

        _routerToolsState.update {
            it.copy(
                isLoading = false,
                networkInfo = networkInfo,
                hasInternet = hasInternet,
                serviceProvider = if (hasInternet) it.serviceProvider else null,
                publicIp = if (hasInternet) it.publicIp else null,
                downloadMbps = if (hasInternet) it.downloadMbps else null,
                uploadMbps = if (hasInternet) it.uploadMbps else null,
                downloadTrend = if (hasInternet) it.downloadTrend else SpeedTrend.Neutral,
                uploadTrend = if (hasInternet) it.uploadTrend else SpeedTrend.Neutral,
                speedHistory = if (hasInternet) it.speedHistory else emptyList(),
                speedErrorMessage = if (hasInternet) it.speedErrorMessage else null,
                lastUpdatedMillis = System.currentTimeMillis(),
                errorMessage = null
            )
        }

        val provider = if (hasInternet && refreshProvider) fetchServiceProvider() else current.serviceProvider to current.publicIp
        val downloadSpeed = if (hasInternet && testSpeed) measureDownloadSpeedMbps() else current.downloadMbps
        val uploadSpeed = if (hasInternet && testSpeed) measureUploadSpeedMbps() else current.uploadMbps
        val speedErrorMessage = when {
            !hasInternet -> null
            testSpeed && downloadSpeed == null && uploadSpeed == null -> "Speed test failed. Try again on a stable internet connection."
            testSpeed && downloadSpeed == null -> "Download test failed."
            testSpeed && uploadSpeed == null -> "Upload test failed."
            else -> current.speedErrorMessage
        }
        val downloadTrend = if (hasInternet && testSpeed) speedTrend(current.downloadMbps, downloadSpeed) else current.downloadTrend
        val uploadTrend = if (hasInternet && testSpeed) speedTrend(current.uploadMbps, uploadSpeed) else current.uploadTrend
        val speedHistory = if (hasInternet && testSpeed) {
            (current.speedHistory + SpeedSample(System.currentTimeMillis(), downloadSpeed, uploadSpeed))
                .takeLast(SPEED_HISTORY_LIMIT)
        } else {
            current.speedHistory
        }
        val devices = if (refreshDevices) scanConnectedDevices(networkInfo) else current.connectedDevices

        _routerToolsState.update {
            it.copy(
                isLoading = false,
                networkInfo = networkInfo,
                hasInternet = hasInternet,
                serviceProvider = if (hasInternet) provider.first else null,
                publicIp = if (hasInternet) provider.second else null,
                downloadMbps = if (hasInternet) downloadSpeed else null,
                uploadMbps = if (hasInternet) uploadSpeed else null,
                downloadTrend = if (hasInternet) downloadTrend else SpeedTrend.Neutral,
                uploadTrend = if (hasInternet) uploadTrend else SpeedTrend.Neutral,
                speedHistory = if (hasInternet) speedHistory else emptyList(),
                isSpeedTesting = false,
                speedErrorMessage = if (hasInternet) speedErrorMessage else null,
                connectedDevices = devices,
                lastUpdatedMillis = System.currentTimeMillis(),
                errorMessage = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun requestWifiScan(): Boolean {
        return runCatching { wifiManager.startScan() }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun nearbyRouters(): List<NearbyRouter> {
        val connectedBssid = wifiManager.connectionInfo?.bssid
        return wifiManager.scanResults
            .map(::toNearbyRouter)
            .map { router -> router.copy(isConnected = router.bssid.equals(connectedBssid, ignoreCase = true)) }
            .distinctBy { it.bssid.lowercase() }
            .sortedWith(
                compareByDescending<NearbyRouter> { it.isConnected }
                    .thenByDescending { it.signalDbm }
                    .thenBy { it.name }
            )
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun connectedRouter(): NearbyRouter? {
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val bssid = wifiInfo.bssid?.takeUnless { it == "02:00:00:00:00:00" } ?: return null
        val name = wifiInfo.ssid
            ?.trim('"')
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
            ?: "Connected Router"

        return NearbyRouter(
            name = name,
            bssid = bssid,
            signalDbm = wifiInfo.rssi,
            signalLevel = WifiManager.calculateSignalLevel(wifiInfo.rssi, SIGNAL_LEVEL_COUNT),
            frequencyMhz = wifiInfo.frequency,
            channel = frequencyToChannel(wifiInfo.frequency),
            security = "Connected",
            isConnected = true
        )
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun connectedNetworkInfo(): ConnectedNetworkInfo? {
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val ssid = wifiInfo.ssid
            ?.trim('"')
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
            ?: "Connected Router"
        val phoneIp = formatLittleEndianIp(wifiInfo.ipAddress)
        val gatewayIp = formatLittleEndianIp(dhcpInfo.gateway)
        val subnetMask = formatLittleEndianIp(dhcpInfo.netmask).takeUnless { it == "0.0.0.0" } ?: "255.255.255.0"

        return ConnectedNetworkInfo(
            ssid = ssid,
            phoneIp = phoneIp,
            gatewayIp = gatewayIp,
            subnetMask = subnetMask,
            adminUrl = "http://$gatewayIp"
        )
    }

    private suspend fun fetchServiceProvider(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://ipapi.co/json/").openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
            }
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val provider = json.optString("org").ifBlank { json.optString("asn") }.ifBlank { null }
                val publicIp = json.optString("ip").ifBlank { null }
                provider to publicIp
            }
        }.getOrDefault(null to null)
    }

    private suspend fun measureDownloadSpeedMbps(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://speed.cloudflare.com/__down?bytes=250000").openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = SPEED_TIMEOUT_MS
            }
            if (connection.responseCode !in 200..299) return@runCatching null
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesReadTotal = 0L
            val startedAt = System.nanoTime()
            connection.inputStream.use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    bytesReadTotal += bytesRead
                }
            }
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
            if (seconds > 0.0 && bytesReadTotal > 0) {
                (bytesReadTotal * 8.0) / seconds / 1_000_000.0
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun measureUploadSpeedMbps(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val payload = ByteArray(200_000) { index -> (index % 251).toByte() }
            val connection = (URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = SPEED_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(payload.size)
            }
            val startedAt = System.nanoTime()
            connection.outputStream.use { output ->
                output.write(payload)
                output.flush()
            }
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.close()
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
            if (seconds > 0.0) {
                (payload.size * 8.0) / seconds / 1_000_000.0
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun hasInternetProbe(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
            }
            connection.responseCode in 200..299
        }.getOrDefault(false)
    }

    private fun speedTrend(previous: Double?, current: Double?): SpeedTrend {
        if (previous == null || current == null) return SpeedTrend.Neutral
        val delta = current - previous
        return when {
            delta > SPEED_TREND_EPSILON -> SpeedTrend.Up
            delta < -SPEED_TREND_EPSILON -> SpeedTrend.Down
            else -> SpeedTrend.Neutral
        }
    }

    private suspend fun scanConnectedDevices(networkInfo: ConnectedNetworkInfo): List<ConnectedRouterDevice> = withContext(Dispatchers.IO) {
        val arpEntries = readArpTable()
        val subnetIps = subnetHosts(networkInfo.phoneIp, networkInfo.subnetMask)

        subnetIps
            .chunked(32)
            .flatMap { chunk ->
                chunk.map { ip ->
                    async {
                        val arpMac = arpEntries[ip]
                        val reachable = ip == networkInfo.gatewayIp || ip == networkInfo.phoneIp || arpMac != null || isReachable(ip)
                        if (!reachable) {
                            null
                        } else {
                            val hostName = resolveHostName(ip)
                            val isRouter = ip == networkInfo.gatewayIp
                            val isCurrentDevice = ip == networkInfo.phoneIp
                            ConnectedRouterDevice(
                                ipAddress = ip,
                                displayName = deviceDisplayName(
                                    ipAddress = ip,
                                    hostName = hostName,
                                    isRouter = isRouter,
                                    isCurrentDevice = isCurrentDevice
                                ),
                                deviceType = deviceType(
                                    hostName = hostName,
                                    isRouter = isRouter,
                                    isCurrentDevice = isCurrentDevice
                                ),
                                hostName = hostName,
                                macAddress = arpMac,
                                isRouter = isRouter,
                                isCurrentDevice = isCurrentDevice
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            .distinctBy { it.ipAddress }
            .sortedWith(compareByDescending<ConnectedRouterDevice> { it.isRouter }.thenBy { ipv4ToLong(it.ipAddress) })
    }

    private fun isReachable(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip).isReachable(450)
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveHostName(ip: String): String? {
        return try {
            val address = InetAddress.getByName(ip)
            address.canonicalHostName.takeIf { it != ip }
        } catch (_: Exception) {
            null
        }
    }

    private fun deviceDisplayName(
        ipAddress: String,
        hostName: String?,
        isRouter: Boolean,
        isCurrentDevice: Boolean
    ): String {
        return when {
            isRouter -> "Router"
            isCurrentDevice -> "This Device (${deviceModelName()})"
            !hostName.isNullOrBlank() -> hostName
            else -> "Network Device ($ipAddress)"
        }
    }

    private fun deviceType(
        hostName: String?,
        isRouter: Boolean,
        isCurrentDevice: Boolean
    ): String {
        if (isRouter) return "Router"
        if (isCurrentDevice) return "This Android device"

        val name = hostName.orEmpty().lowercase()
        return when {
            name.contains("iphone") || name.contains("ipad") || name.contains("android") || name.contains("phone") -> "Mobile device"
            name.contains("macbook") || name.contains("laptop") || name.contains("notebook") -> "Laptop"
            name.contains("desktop") || name.contains("pc") || name.contains("windows") -> "Computer"
            name.contains("tv") || name.contains("chromecast") || name.contains("roku") || name.contains("firetv") -> "Media device"
            name.contains("printer") || name.contains("print") -> "Printer"
            name.contains("camera") || name.contains("cam") -> "Camera"
            name.contains("echo") || name.contains("alexa") || name.contains("google-home") -> "Smart speaker"
            name.contains("watch") -> "Wearable"
            else -> "Network device"
        }
    }

    private fun deviceModelName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(" ")
            .ifBlank { "Android" }
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

    @Suppress("DEPRECATION")
    private fun toNearbyRouter(result: ScanResult): NearbyRouter {
        return NearbyRouter(
            name = result.SSID.takeUnless { it.isBlank() } ?: "Hidden network",
            bssid = result.BSSID.orEmpty(),
            signalDbm = result.level,
            signalLevel = WifiManager.calculateSignalLevel(result.level, SIGNAL_LEVEL_COUNT),
            frequencyMhz = result.frequency,
            channel = frequencyToChannel(result.frequency),
            security = securityLabel(result.capabilities),
            isConnected = false
        )
    }

    private fun securityLabel(capabilities: String): String {
        return when {
            capabilities.contains("SAE", ignoreCase = true) -> "WPA3"
            capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
            capabilities.contains("WPA", ignoreCase = true) -> "WPA"
            capabilities.contains("WEP", ignoreCase = true) -> "WEP"
            capabilities.contains("EAP", ignoreCase = true) -> "Enterprise"
            capabilities.isBlank() -> "Open"
            else -> capabilities
                .replace("[", "")
                .replace("]", " ")
                .trim()
                .ifBlank { "Unknown" }
        }
    }

    private fun frequencyToChannel(frequencyMhz: Int): Int? {
        return when (frequencyMhz) {
            2484 -> 14
            in 2412..2472 -> (frequencyMhz - 2407) / 5
            in 5160..5885 -> (frequencyMhz - 5000) / 5
            in 5955..7115 -> (frequencyMhz - 5950) / 5
            else -> null
        }
    }

    private fun subnetHosts(phoneIp: String, subnetMask: String): List<String> {
        val phone = ipv4ToLong(phoneIp)
        val mask = ipv4ToLong(subnetMask).takeUnless { it == 0L } ?: ipv4ToLong("255.255.255.0")
        val network = phone and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        val hostCount = broadcast - network - 1

        val start: Long
        val end: Long
        if (hostCount in 1L..512L) {
            start = network + 1
            end = broadcast - 1
        } else {
            val octets = phoneIp.split(".")
            val prefix = octets.take(3).joinToString(".")
            return (1..254).map { "$prefix.$it" }
        }

        return (start..end).map(::longToIpv4)
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

    private companion object {
        const val TOOLS_REFRESH_MS = 5_000L
        const val SPEED_REFRESH_TICKS = 6
        const val PROVIDER_REFRESH_TICKS = 12
        const val DEVICES_REFRESH_TICKS = 12
        const val SPEED_HISTORY_LIMIT = 30
        const val SPEED_TREND_EPSILON = 0.10
        const val HTTP_TIMEOUT_MS = 5_000
        const val SPEED_TIMEOUT_MS = 6_000
        const val SIGNAL_LEVEL_COUNT = 5
        const val WIFI_SCAN_SETTLE_MS = 2_000L
        val NSD_SERVICE_TYPES = listOf(
            "_http._tcp.",
            "_https._tcp.",
            "_workstation._tcp.",
            "_printer._tcp.",
            "_ipp._tcp.",
            "_airplay._tcp.",
            "_googlecast._tcp.",
            "_hap._tcp.",
            "_mqtt._tcp.",
            "_arduino._tcp.",
            "_esphomelib._tcp."
        )
    }
}
