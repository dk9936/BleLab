package com.rama.blelab.presentation.network

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager.TYPE_MOBILE
import android.net.ConnectivityManager.TYPE_WIFI
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkInfoState(
    val isMonitoring: Boolean = false,
    val transport: String = "Disconnected",
    val interfaceName: String = "-",
    val localIp: String = "-",
    val wifiSsid: String = "-",
    val gatewayIp: String = "-",
    val dnsServers: List<String> = emptyList(),
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val appRxBytes: Long = 0L,
    val appTxBytes: Long = 0L,
    val rxBytesPerSecond: Long = 0L,
    val txBytesPerSecond: Long = 0L,
    val appRxBytesPerSecond: Long = 0L,
    val appTxBytesPerSecond: Long = 0L,
    val appTraffic: List<AppNetworkTraffic> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val appTrafficWindow: String = "Today",
    val lastUpdatedMillis: Long? = null
)

data class AppNetworkTraffic(
    val uid: Int,
    val appName: String,
    val packageNames: List<String>,
    val rxBytes: Long,
    val txBytes: Long,
    val rxBytesPerSecond: Long,
    val txBytesPerSecond: Long
) {
    val totalBytes: Long = rxBytes + txBytes
}

class NetworkInfoViewModel(
    private val context: Context
) : ViewModel() {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    private val _state = MutableStateFlow(NetworkInfoState())
    val state: StateFlow<NetworkInfoState> = _state.asStateFlow()

    private var monitorJob: Job? = null
    private var previousSample: TrafficSample? = null
    private val appIdentities by lazy { loadAppIdentities() }

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return

        previousSample = readTrafficSample()
        refreshNetworkInfo()
        monitorJob = viewModelScope.launch {
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                val currentSample = readTrafficSample()
                val previous = previousSample
                previousSample = currentSample

                val elapsedSeconds = previous
                    ?.let { ((currentSample.timestampMillis - it.timestampMillis).coerceAtLeast(1L) / 1000.0) }
                    ?: 1.0

                refreshNetworkInfo(
                    sample = currentSample,
                    previousSample = previous,
                    elapsedSeconds = elapsedSeconds
                )
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _state.update { it.copy(isMonitoring = false) }
    }

    fun refreshNow() {
        val currentSample = readTrafficSample()
        val previous = previousSample
        previousSample = currentSample
        refreshNetworkInfo(
            sample = currentSample,
            previousSample = previous,
            elapsedSeconds = previous
                ?.let { ((currentSample.timestampMillis - it.timestampMillis).coerceAtLeast(1L) / 1000.0) }
                ?: 1.0
        )
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }

    private fun refreshNetworkInfo(
        sample: TrafficSample = readTrafficSample(),
        previousSample: TrafficSample? = null,
        elapsedSeconds: Double = 1.0
    ) {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
        val dhcpInfo = runCatching { wifiManager.dhcpInfo }.getOrNull()

        _state.value = NetworkInfoState(
            isMonitoring = monitorJob?.isActive == true,
            transport = capabilities.transportName(),
            interfaceName = linkProperties?.interfaceName ?: "-",
            localIp = linkProperties
                ?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
                ?.address
                ?.hostAddress
                ?: fallbackLocalIp(),
            wifiSsid = currentWifiSsid(),
            gatewayIp = dhcpInfo?.gateway?.toIpAddress() ?: "-",
            dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
            totalRxBytes = sample.totalRxBytes,
            totalTxBytes = sample.totalTxBytes,
            appRxBytes = sample.appRxBytes,
            appTxBytes = sample.appTxBytes,
            rxBytesPerSecond = ratePerSecond(sample.totalRxBytes, previousSample?.totalRxBytes, elapsedSeconds),
            txBytesPerSecond = ratePerSecond(sample.totalTxBytes, previousSample?.totalTxBytes, elapsedSeconds),
            appRxBytesPerSecond = ratePerSecond(sample.appRxBytes, previousSample?.appRxBytes, elapsedSeconds),
            appTxBytesPerSecond = ratePerSecond(sample.appTxBytes, previousSample?.appTxBytes, elapsedSeconds),
            appTraffic = buildAppTraffic(sample, previousSample, elapsedSeconds),
            hasUsageAccess = hasUsageAccess(),
            lastUpdatedMillis = sample.timestampMillis
        )
    }

    private fun readTrafficSample(): TrafficSample {
        val uid = Process.myUid()
        return TrafficSample(
            totalRxBytes = TrafficStats.getTotalRxBytes().positiveOrZero(),
            totalTxBytes = TrafficStats.getTotalTxBytes().positiveOrZero(),
            appRxBytes = TrafficStats.getUidRxBytes(uid).positiveOrZero(),
            appTxBytes = TrafficStats.getUidTxBytes(uid).positiveOrZero(),
            uidTraffic = readUidTraffic(),
            timestampMillis = System.currentTimeMillis()
        )
    }

    private fun readUidTraffic(): Map<Int, UidTrafficCounters> {
        return appIdentities.keys.associateWith { uid ->
            UidTrafficCounters(
                rxBytes = TrafficStats.getUidRxBytes(uid).positiveOrZero(),
                txBytes = TrafficStats.getUidTxBytes(uid).positiveOrZero()
            )
        }
    }

    private fun buildAppTraffic(
        sample: TrafficSample,
        previousSample: TrafficSample?,
        elapsedSeconds: Double
    ): List<AppNetworkTraffic> {
        val usageByUid = if (hasUsageAccess()) readTodayUsageByUid() else emptyMap()
        return appIdentities.mapNotNull { (uid, identity) ->
            val counters = sample.uidTraffic[uid] ?: return@mapNotNull null
            val previousCounters = previousSample?.uidTraffic?.get(uid)
            val usageCounters = usageByUid[uid]
            val rxBytes = usageCounters?.rxBytes ?: counters.rxBytes
            val txBytes = usageCounters?.txBytes ?: counters.txBytes
            val hasAnyTraffic = rxBytes > 0L ||
                txBytes > 0L ||
                counters.rxBytes > 0L ||
                counters.txBytes > 0L ||
                (previousCounters?.rxBytes ?: 0L) > 0L ||
                (previousCounters?.txBytes ?: 0L) > 0L
            if (!hasAnyTraffic) return@mapNotNull null

            AppNetworkTraffic(
                uid = uid,
                appName = identity.appName,
                packageNames = identity.packageNames,
                rxBytes = rxBytes,
                txBytes = txBytes,
                rxBytesPerSecond = ratePerSecond(counters.rxBytes, previousCounters?.rxBytes, elapsedSeconds),
                txBytesPerSecond = ratePerSecond(counters.txBytes, previousCounters?.txBytes, elapsedSeconds)
            )
        }.sortedWith(
            compareByDescending<AppNetworkTraffic> { it.rxBytesPerSecond + it.txBytesPerSecond }
                .thenByDescending { it.rxBytesPerSecond }
                .thenByDescending { it.totalBytes }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun readTodayUsageByUid(): Map<Int, UidTrafficCounters> {
        val startMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endMillis = System.currentTimeMillis()
        val wifiUsage = queryUsageByUid(TYPE_WIFI, startMillis, endMillis)
        val mobileUsage = queryUsageByUid(TYPE_MOBILE, startMillis, endMillis)
        return (wifiUsage.keys + mobileUsage.keys).associateWith { uid ->
            val wifi = wifiUsage[uid]
            val mobile = mobileUsage[uid]
            UidTrafficCounters(
                rxBytes = (wifi?.rxBytes ?: 0L) + (mobile?.rxBytes ?: 0L),
                txBytes = (wifi?.txBytes ?: 0L) + (mobile?.txBytes ?: 0L)
            )
        }
    }

    private fun queryUsageByUid(
        networkType: Int,
        startMillis: Long,
        endMillis: Long
    ): Map<Int, UidTrafficCounters> {
        val usage = mutableMapOf<Int, UidTrafficCounters>()
        val stats = runCatching {
            networkStatsManager.querySummary(networkType, null, startMillis, endMillis)
        }.getOrNull() ?: return emptyMap()

        stats.use { networkStats ->
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                if (bucket.uid <= 0) continue
                val existing = usage[bucket.uid]
                usage[bucket.uid] = UidTrafficCounters(
                    rxBytes = (existing?.rxBytes ?: 0L) + bucket.rxBytes.positiveOrZero(),
                    txBytes = (existing?.txBytes ?: 0L) + bucket.txBytes.positiveOrZero()
                )
            }
        }
        return usage
    }

    private fun hasUsageAccess(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadAppIdentities(): Map<Int, AppIdentity> {
        val packageManager = context.packageManager
        val applications = runCatching {
            packageManager.getInstalledApplications(0)
        }.getOrDefault(emptyList())

        return applications
            .filter { it.uid > 0 }
            .groupBy { it.uid }
            .mapValues { (_, apps) ->
                val sortedApps = apps.sortedBy { packageInfo ->
                    packageInfo.loadLabel(packageManager).toString().lowercase()
                }
                val primaryApp = sortedApps.first()
                val appName = primaryApp.loadLabel(packageManager).toString()
                    .takeIf { it.isNotBlank() }
                    ?: primaryApp.packageName
                AppIdentity(
                    appName = appName,
                    packageNames = sortedApps.map(ApplicationInfo::packageName)
                )
            }
    }

    private fun NetworkCapabilities?.transportName(): String {
        return when {
            this == null -> "Disconnected"
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
    }

    private fun currentWifiSsid(): String {
        val ssid = runCatching { wifiManager.connectionInfo?.ssid }.getOrNull()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
        return ssid ?: "-"
    }

    private fun fallbackLocalIp(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        }.getOrNull() ?: "-"
    }

    private data class TrafficSample(
        val totalRxBytes: Long,
        val totalTxBytes: Long,
        val appRxBytes: Long,
        val appTxBytes: Long,
        val uidTraffic: Map<Int, UidTrafficCounters>,
        val timestampMillis: Long
    )

    private data class UidTrafficCounters(
        val rxBytes: Long,
        val txBytes: Long
    )

    private data class AppIdentity(
        val appName: String,
        val packageNames: List<String>
    )

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}

private fun Long.positiveOrZero(): Long = if (this < 0L) 0L else this

private fun ratePerSecond(currentValue: Long, previousValue: Long?, elapsedSeconds: Double): Long {
    if (previousValue == null) return 0L
    return ((currentValue - previousValue).coerceAtLeast(0L) / elapsedSeconds).toLong()
}

private fun Int.toIpAddress(): String {
    return listOf(
        this and 0xFF,
        this shr 8 and 0xFF,
        this shr 16 and 0xFF,
        this shr 24 and 0xFF
    ).joinToString(".")
}
