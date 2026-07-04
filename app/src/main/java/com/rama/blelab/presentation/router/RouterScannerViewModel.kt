package com.rama.blelab.presentation.router

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RouterScannerState(
    val isWifiEnabled: Boolean = false,
    val isConnectedToWifi: Boolean = false,
    val isScanning: Boolean = false,
    val routerInfo: RouterInfo? = null,
    val devices: List<RouterDevice> = emptyList(),
    val errorMessage: String? = null
)

data class RouterInfo(
    val ssid: String,
    val phoneIp: String,
    val gatewayIp: String,
    val subnetMask: String
)

data class RouterDevice(
    val ipAddress: String,
    val hostName: String?,
    val macAddress: String?,
    val isRouter: Boolean
)

class RouterScannerViewModel(
    private val context: Context
) : ViewModel() {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(RouterScannerState())
    val state: StateFlow<RouterScannerState> = _state.asStateFlow()

    fun refreshNetworkState(hasPermission: Boolean) {
        val wifiEnabled = wifiManager.isWifiEnabled
        val connectedToWifi = isConnectedToWifi()
        val routerInfo = if (wifiEnabled && connectedToWifi && hasPermission) getRouterInfo() else null

        _state.update {
            it.copy(
                isWifiEnabled = wifiEnabled,
                isConnectedToWifi = connectedToWifi,
                routerInfo = routerInfo,
                errorMessage = null
            )
        }
    }

    fun startScan(hasPermission: Boolean) {
        refreshNetworkState(hasPermission)

        val currentState = _state.value
        when {
            !hasPermission -> {
                _state.update { it.copy(errorMessage = "Allow location permission to read Wi-Fi network details.") }
                return
            }

            !currentState.isWifiEnabled -> {
                _state.update { it.copy(errorMessage = "Turn on Wi-Fi to scan router devices.") }
                return
            }

            !currentState.isConnectedToWifi -> {
                _state.update { it.copy(errorMessage = "Connect this phone to a Wi-Fi router first.") }
                return
            }

            currentState.routerInfo == null -> {
                _state.update { it.copy(errorMessage = "Unable to read current Wi-Fi network details.") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, devices = emptyList(), errorMessage = null) }
            val routerInfo = currentState.routerInfo
            val devices = scanSubnet(routerInfo)
            _state.update {
                it.copy(
                    isScanning = false,
                    devices = devices,
                    errorMessage = if (devices.isEmpty()) "No reachable devices found on this subnet." else null
                )
            }
        }
    }

    private fun isConnectedToWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getRouterInfo(): RouterInfo? {
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val phoneIp = formatLittleEndianIp(wifiInfo.ipAddress)
        val gatewayIp = formatLittleEndianIp(dhcpInfo.gateway)
        val subnetMask = formatLittleEndianIp(dhcpInfo.netmask).takeUnless { it == "0.0.0.0" } ?: "255.255.255.0"

        return RouterInfo(
            ssid = wifiInfo.ssid?.trim('"')?.takeUnless { it == "<unknown ssid>" }.orEmpty(),
            phoneIp = phoneIp,
            gatewayIp = gatewayIp,
            subnetMask = subnetMask
        )
    }

    private suspend fun scanSubnet(routerInfo: RouterInfo): List<RouterDevice> = withContext(Dispatchers.IO) {
        val arpEntries = readArpTable()
        val subnetIps = subnetHosts(routerInfo.phoneIp, routerInfo.subnetMask)

        subnetIps
            .chunked(32)
            .flatMap { chunk ->
                chunk.map { ip ->
                    async {
                        val arpMac = arpEntries[ip]
                        val reachable = ip == routerInfo.gatewayIp || arpMac != null || isReachable(ip)
                        if (!reachable) {
                            null
                        } else {
                            RouterDevice(
                                ipAddress = ip,
                                hostName = resolveHostName(ip),
                                macAddress = arpMac,
                                isRouter = ip == routerInfo.gatewayIp
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            .distinctBy { it.ipAddress }
            .sortedWith(compareByDescending<RouterDevice> { it.isRouter }.thenBy { ipv4ToLong(it.ipAddress) })
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
}
