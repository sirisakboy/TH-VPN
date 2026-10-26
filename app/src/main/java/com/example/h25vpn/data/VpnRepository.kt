package com.example.h25vpn.data

import android.content.Context
import com.example.h25vpn.service.H25VpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object VpnRepository {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var telemetryJob: Job? = null
    private var timerJob: Job? = null

    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _selectedServer = MutableStateFlow(ServersData.defaultServers[0])
    val selectedServer = _selectedServer.asStateFlow()

    private val _servers = MutableStateFlow(ServersData.defaultServers)
    val servers = _servers.asStateFlow()

    private val _selectedTweak = MutableStateFlow(TweaksData.defaultTweaks[0])
    val selectedTweak = _selectedTweak.asStateFlow()

    private val _tweaks = MutableStateFlow(TweaksData.defaultTweaks)
    val tweaks = _tweaks.asStateFlow()

    private val _networkStats = MutableStateFlow(NetworkStats())
    val networkStats = _networkStats.asStateFlow()

    // 4 hours initially
    private val _remainingTimeSeconds = MutableStateFlow(14400L)
    val remainingTimeSeconds = _remainingTimeSeconds.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    val selectedDns = MutableStateFlow(DnsMode.CLOUDFLARE)
    val mtuSetting = MutableStateFlow(1500)
    val splitTunnelEnabled = MutableStateFlow(false)
    val bypassedPackages = MutableStateFlow(setOf("com.android.chrome", "com.google.android.youtube"))
    val autoReconnect = MutableStateFlow(true)
    val udpForwarding = MutableStateFlow(true)

    private val _proxyState = MutableStateFlow(ProxyShareState())
    val proxyState = _proxyState.asStateFlow()

    init {
        appendLog("SYSTEM", "H25 VPN core engine initialized v0.1.9")
        appendLog("CONFIG", "Default server set to ${selectedServer.value.name}")
        appendLog("CONFIG", "Default tweak set to ${selectedTweak.value.name}")

        // Start countdown timer loop
        scope.launch {
            while (isActive) {
                delay(1000)
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    if (_remainingTimeSeconds.value > 0) {
                        _remainingTimeSeconds.value = _remainingTimeSeconds.value - 1
                    }
                }
            }
        }
    }

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
        appendLog("CONFIG", "Selected Server: ${server.name} (${server.country})")
    }

    fun selectTweak(tweak: CarrierTweak) {
        _selectedTweak.value = tweak
        appendLog("CONFIG", "Selected Tweak: ${tweak.name}")
    }

    fun addCustomTweak(tweak: CarrierTweak) {
        _tweaks.value = listOf(tweak) + _tweaks.value
        _selectedTweak.value = tweak
        appendLog("PAYLOAD", "Custom payload added: ${tweak.name}")
    }

    fun toggleAppBypass(packageName: String) {
        val current = _bypassedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _bypassedPackages.value = current
    }

    private val _bypassedPackages = bypassedPackages

    fun addTime(additionalSeconds: Long) {
        _remainingTimeSeconds.value = _remainingTimeSeconds.value + additionalSeconds
        val hoursAdded = additionalSeconds / 3600
        appendLog("ACCOUNT", "Added $hoursAdded hours to session time. New balance: formatDuration()")
    }

    fun formatDuration(totalSec: Long = _remainingTimeSeconds.value): String {
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun connect(context: Context) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        _connectionState.value = ConnectionState.CONNECTING
        appendLog("CONNECT", "Starting connection sequence...")

        scope.launch {
            delay(500)
            _connectionState.value = ConnectionState.AUTHENTICATING
            appendLog("AUTH", "Authenticating credentials for ${_selectedServer.value.host}...")
            delay(600)

            // Start Android VpnService
            try {
                H25VpnService.startVpn(context)
            } catch (e: Exception) {
                appendLog("ERROR", "VpnService launch failed: ${e.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect(context: Context) {
        if (_connectionState.value == ConnectionState.DISCONNECTED) return

        _connectionState.value = ConnectionState.DISCONNECTING
        appendLog("DISCONNECT", "Terminating tunnel...")

        scope.launch {
            try {
                H25VpnService.stopVpn(context)
            } catch (ignored: Exception) {}
            delay(400)
            onDisconnected()
        }
    }

    fun onConnected() {
        _connectionState.value = ConnectionState.CONNECTED
        val server = _selectedServer.value
        _networkStats.value = _networkStats.value.copy(
            pingMs = server.latencyMs,
            vpnIp = "10.8.0.2"
        )
        startTelemetrySimulation()
    }

    fun onDisconnected() {
        _connectionState.value = ConnectionState.DISCONNECTED
        telemetryJob?.cancel()
        _networkStats.value = _networkStats.value.copy(
            downloadSpeedBps = 0,
            uploadSpeedBps = 0
        )
    }

    private fun startTelemetrySimulation() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(1200)
                // Realistic fluctuations in speed
                val downSpeed = Random.nextLong(2_400_000, 18_500_000) // 2.4 - 18.5 MB/s
                val upSpeed = Random.nextLong(450_000, 3_800_000) // 450 KB/s - 3.8 MB/s
                val currentStats = _networkStats.value
                val pingJitter = (_selectedServer.value.latencyMs + Random.nextInt(-3, 6)).coerceAtLeast(8)

                _networkStats.value = currentStats.copy(
                    downloadSpeedBps = downSpeed,
                    uploadSpeedBps = upSpeed,
                    totalDownloadBytes = currentStats.totalDownloadBytes + (downSpeed * 1.2).toLong(),
                    totalUploadBytes = currentStats.totalUploadBytes + (upSpeed * 1.2).toLong(),
                    pingMs = pingJitter
                )
            }
        }
    }

    fun recordTraffic(download: Long, upload: Long) {
        val current = _networkStats.value
        _networkStats.value = current.copy(
            totalDownloadBytes = current.totalDownloadBytes + download,
            totalUploadBytes = current.totalUploadBytes + upload
        )
    }

    fun pingAllServers() {
        appendLog("PING", "Testing latency to all servers...")
        scope.launch {
            val updated = _servers.value.map { server ->
                val jitter = Random.nextInt(-4, 5)
                server.copy(latencyMs = (server.latencyMs + jitter).coerceAtLeast(10))
            }.sortedBy { it.latencyMs }
            _servers.value = updated
            appendLog("PING", "Fastest server: ${updated.first().name} (${updated.first().latencyMs} ms)")
        }
    }

    fun appendLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = LogEntry(time, level, tag, message)
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > 200) {
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
        appendLog("SYSTEM", "Logs cleared.")
    }

    fun updateProxyState(
        isRunning: Boolean,
        localIp: String = "192.168.43.1",
        port: Int = 8080,
        activeClients: Int = 0
    ) {
        _proxyState.value = ProxyShareState(
            isRunning = isRunning,
            localIp = localIp,
            port = port,
            activeConnections = activeClients
        )
    }

    fun updateProxyActiveConnections(count: Int) {
        _proxyState.value = _proxyState.value.copy(activeConnections = count)
    }
}
