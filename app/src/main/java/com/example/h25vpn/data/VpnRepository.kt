package com.example.h25vpn.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.example.h25vpn.service.H25VpnService
import com.example.h25vpn.service.ProxyTetherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VpnRepository {

    private const val PREFS_NAME = "th_vpn_prefs"
    private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
    private const val KEY_SELECTED_TWEAK_ID = "selected_tweak_id"
    private const val KEY_DNS_MODE = "dns_mode"
    private const val KEY_KILL_SWITCH = "kill_switch"
    private const val KEY_AUTO_ROUTE_SCAN = "auto_route_scan"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_SPLIT_TUNNEL = "split_tunnel_enabled"
    private const val KEY_BYPASSED_PKGS = "bypassed_packages"
    private const val KEY_CUSTOM_SERVERS = "custom_servers_json"
    private const val KEY_CLOUD_SERVERS = "cloud_servers_json"
    private const val KEY_CUSTOM_TWEAKS = "custom_tweaks_json"
    private const val KEY_CUSTOM_SUB_URLS = "custom_sub_urls"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _servers = MutableStateFlow(ServersData.defaultServers)
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow(ServersData.defaultServers.first())
    val selectedServer: StateFlow<VpnServer> = _selectedServer.asStateFlow()

    private val _tweaks = MutableStateFlow(TweaksData.defaultTweaks)
    val tweaks: StateFlow<List<CarrierTweak>> = _tweaks.asStateFlow()

    private val _selectedTweak = MutableStateFlow(TweaksData.defaultTweaks.first())
    val selectedTweak: StateFlow<CarrierTweak> = _selectedTweak.asStateFlow()

    private val _networkStats = MutableStateFlow(NetworkStats())
    val networkStats: StateFlow<NetworkStats> = _networkStats.asStateFlow()

    private val _sessionDurationSec = MutableStateFlow(0L)
    val sessionDurationSec: StateFlow<Long> = _sessionDurationSec.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _dnsMode = MutableStateFlow(DnsMode.CLOUDFLARE)
    val dnsMode: StateFlow<DnsMode> = _dnsMode.asStateFlow()

    private val _killSwitch = MutableStateFlow(true)
    val killSwitch: StateFlow<Boolean> = _killSwitch.asStateFlow()

    private val _autoRouteScan = MutableStateFlow(true)
    val autoRouteScan: StateFlow<Boolean> = _autoRouteScan.asStateFlow()

    fun setAutoRouteScan(enabled: Boolean) {
        _autoRouteScan.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_ROUTE_SCAN, enabled)?.apply()
    }

    /** Used only by the active service attempt; must not trigger a second reconnect. */
    fun selectAttemptServer(server: VpnServer) {
        _selectedServer.value = server
    }

    fun rememberVerifiedRoute() {
        prefs?.edit()?.putString(KEY_SELECTED_SERVER_ID, _selectedServer.value.id)?.apply()
    }

    private val _autoReconnect = MutableStateFlow(true)
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()

    private val _proxyShareState = MutableStateFlow(ProxyShareState())
    val proxyShareState: StateFlow<ProxyShareState> = _proxyShareState.asStateFlow()

    private val _splitTunnelEnabled = MutableStateFlow(false)
    val splitTunnelEnabled: StateFlow<Boolean> = _splitTunnelEnabled.asStateFlow()

    private val _bypassedPackages = MutableStateFlow<Set<String>>(emptySet())
    val bypassedPackages: StateFlow<Set<String>> = _bypassedPackages.asStateFlow()

    private val _isSyncingServers = MutableStateFlow(false)
    val isSyncingServers: StateFlow<Boolean> = _isSyncingServers.asStateFlow()

    private val _customSubscriptionUrls = MutableStateFlow<List<String>>(emptyList())
    val customSubscriptionUrls: StateFlow<List<String>> = _customSubscriptionUrls.asStateFlow()

    private var timerJob: Job? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appCtx = context.applicationContext
        appContext = appCtx
        val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        // Restore persisted tweaks
        val savedCustomTweaks = sp.getString(KEY_CUSTOM_TWEAKS, null)?.let {
            runCatching { json.decodeFromString<List<CarrierTweak>>(it) }.getOrNull()
        } ?: emptyList()
        val combinedTweaks = TweaksData.defaultTweaks + savedCustomTweaks
        _tweaks.value = combinedTweaks

        val selectedTweakId = sp.getString(KEY_SELECTED_TWEAK_ID, null)
        combinedTweaks.firstOrNull { it.id == selectedTweakId }?.let {
            _selectedTweak.value = it
        }

        // Restore persisted custom & cached cloud servers
        val savedCustomServers = sp.getString(KEY_CUSTOM_SERVERS, null)?.let {
            runCatching { json.decodeFromString<List<VpnServer>>(it) }.getOrNull()
        } ?: emptyList()
        val savedCloudServers = sp.getString(KEY_CLOUD_SERVERS, null)?.let {
            runCatching { json.decodeFromString<List<VpnServer>>(it) }.getOrNull()
        } ?: emptyList()

        val mergedServers = mergeServerLists(
            custom = savedCustomServers,
            cloud = savedCloudServers,
            defaults = ServersData.defaultServers
        )
        _servers.value = mergedServers

        val selectedSrvId = sp.getString(KEY_SELECTED_SERVER_ID, null)
        mergedServers.firstOrNull { it.id == selectedSrvId }?.let {
            _selectedServer.value = it
        } ?: run {
            _selectedServer.value = mergedServers.first()
        }

        // Restore Settings
        sp.getString(KEY_DNS_MODE, null)?.let { name ->
            runCatching { DnsMode.valueOf(name) }.getOrNull()?.let { _dnsMode.value = it }
        }
        _killSwitch.value = sp.getBoolean(KEY_KILL_SWITCH, true)
        _autoRouteScan.value = sp.getBoolean(KEY_AUTO_ROUTE_SCAN, true)
        _autoReconnect.value = sp.getBoolean(KEY_AUTO_RECONNECT, true)
        _mtuSetting.value = sp.getInt("mtu_setting", 1500)
        _udpForwarding.value = sp.getBoolean("udp_forwarding", true)
        sp.getString("last_speed_test", null)?.let { raw ->
            runCatching { json.decodeFromString<SpeedTestSummary>(raw) }.getOrNull()?.let {
                _lastSpeedTest.value = it
            }
        }
        _splitTunnelEnabled.value = sp.getBoolean(KEY_SPLIT_TUNNEL, false)
        _bypassedPackages.value = sp.getStringSet(KEY_BYPASSED_PKGS, emptySet())?.toSet() ?: emptySet()
        _customSubscriptionUrls.value = sp.getStringSet(KEY_CUSTOM_SUB_URLS, emptySet())?.toList() ?: emptyList()

        _proxyShareState.value = _proxyShareState.value.copy(localIp = detectLocalHotspotIp())

        addLog(LogLevel.INFO, "INIT", "TH-VPN Engine v1.0 Ready (Xray VLESS/VMess/Trojan + SSH/SSL + OpenVPN3)")

        // Background tasks: detect real IP + sync live free cloud servers + real TCP ping!
        scope.launch(Dispatchers.IO) {
            refreshPublicIpDirect()
            syncFreeCloudServers(silent = true)
        }
    }

    private fun mergeServerLists(
        custom: List<VpnServer>,
        cloud: List<VpnServer>,
        defaults: List<VpnServer>
    ): List<VpnServer> {
        val seen = HashSet<String>()
        val result = mutableListOf<VpnServer>()
        for (srv in (custom + cloud + defaults)) {
            val k = TunnelRoutes.identity(srv)
            if (seen.add(k)) {
                result.add(srv)
            }
        }
        return result.ifEmpty { defaults }
    }

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
        prefs?.edit()?.putString(KEY_SELECTED_SERVER_ID, server.id)?.apply()
        addLog(LogLevel.INFO, "CONFIG", "Selected Server: ${server.name} (${server.host}:${server.port})")
        val ctx = appContext
        if (ctx != null && _connectionState.value == ConnectionState.CONNECTED) {
            reconnect(ctx)
        }
    }

    fun selectTweak(tweak: CarrierTweak) {
        _selectedTweak.value = tweak
        prefs?.edit()?.putString(KEY_SELECTED_TWEAK_ID, tweak.id)?.apply()
        addLog(LogLevel.INFO, "INJECTOR", "Applied Thai Carrier Tweak: ${tweak.name}")
        val ctx = appContext
        if (ctx != null && _connectionState.value == ConnectionState.CONNECTED) {
            reconnect(ctx)
        }
    }

    fun selectTweakById(tweakId: String): Boolean {
        val match = _tweaks.value.firstOrNull { it.id == tweakId } ?: return false
        selectTweak(match)
        return true
    }

    fun addCustomTweak(tweak: CarrierTweak) {
        val customMarked = tweak.copy(isCustom = true)
        val updated = _tweaks.value + customMarked
        _tweaks.value = updated
        _selectedTweak.value = customMarked
        saveCustomTweaks(updated.filter { it.isCustom })
        prefs?.edit()?.putString(KEY_SELECTED_TWEAK_ID, customMarked.id)?.apply()
        addLog(LogLevel.SUCCESS, "PAYLOAD", "Saved Custom Payload Tweak: ${customMarked.name}")
    }

    fun importServersFromText(rawText: String, sourceLabel: String = "Custom"): Int {
        val parsed = VpnConfigParser.parseInputText(rawText, sourceTag = sourceLabel)
        if (parsed.isEmpty()) {
            addLog(LogLevel.WARN, "IMPORT", "No valid vless/vmess/trojan/ss/ssh/ovpn config found in input.")
            return 0
        }
        val current = _servers.value
        val combined = mergeServerLists(
            custom = parsed + current.filter { it.sourceTag == "Custom" || it.sourceTag == "Imported" },
            cloud = current.filter { it.sourceTag != "Custom" && it.sourceTag != "Imported" && it.sourceTag != "TH Free-Net" },
            defaults = ServersData.defaultServers
        )
        _servers.value = combined
        _selectedServer.value = parsed.first()
        saveCustomServers(combined.filter { it.sourceTag == "Custom" || it.sourceTag == "Imported" })
        prefs?.edit()?.putString(KEY_SELECTED_SERVER_ID, parsed.first().id)?.apply()
        addLog(LogLevel.SUCCESS, "IMPORT", "Imported ${parsed.size} real server node(s)!")
        pingAllServers()
        return parsed.size
    }

    fun addCustomSubscriptionUrl(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("http")) return
        val updated = (_customSubscriptionUrls.value + clean).distinct()
        _customSubscriptionUrls.value = updated
        prefs?.edit()?.putStringSet(KEY_CUSTOM_SUB_URLS, updated.toSet())?.apply()
        syncFreeCloudServers(silent = false)
    }

    fun syncFreeCloudServers(silent: Boolean = false) {
        if (_isSyncingServers.value) return
        scope.launch(Dispatchers.IO) {
            _isSyncingServers.value = true
            if (!silent) {
                addLog(LogLevel.INFO, "CLOUD", "Fetching live Free V2Ray (TH/SG/JP) & VPNGate servers...")
            }
            try {
                val fetched = VpnConfigParser.fetchCloudFreeServers(
                    customSubUrls = _customSubscriptionUrls.value,
                    includeVpnGate = true,
                    maxPerSource = 14
                )
                if (fetched.isNotEmpty()) {
                    val customOnes = _servers.value.filter { it.sourceTag == "Custom" || it.sourceTag == "Imported" }
                    val merged = mergeServerLists(
                        custom = customOnes,
                        cloud = fetched,
                        defaults = ServersData.defaultServers
                    )
                    _servers.value = merged
                    prefs?.edit()?.putString(KEY_CLOUD_SERVERS, json.encodeToString(fetched.take(45)))?.apply()
                    addLog(LogLevel.SUCCESS, "CLOUD", "Synced ${fetched.size} live free servers (TH/SG/JP/HK + VPNGate)")
                    pingAllServers()
                } else if (!silent) {
                    addLog(LogLevel.WARN, "CLOUD", "Cloud sync unreachable (check internet or connect via TH Free-Net node first)")
                    pingAllServers()
                }
            } catch (e: Exception) {
                addLog(LogLevel.WARN, "CLOUD", "Cloud sync warning: ${e.message}")
            } finally {
                _isSyncingServers.value = false
            }
        }
    }

    fun toggleConnection(context: Context) {
        appContext = context.applicationContext
        when (_connectionState.value) {
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> connect(context)
            ConnectionState.CONNECTED, ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> disconnect(context)
            ConnectionState.DISCONNECTING -> {}
        }
    }

    fun connect(context: Context) {
        appContext = context.applicationContext
        _connectionState.value = ConnectionState.CONNECTING
        val intent = Intent(context, H25VpnService::class.java).apply {
            action = H25VpnService.ACTION_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disconnect(context: Context) {
        timerJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTING
        addLog(LogLevel.WARN, "TUNNEL", "Stopping active VPN engine...")

        val intent = Intent(context, H25VpnService::class.java).apply {
            action = H25VpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    private fun reconnect(context: Context) {
        scope.launch {
            disconnect(context)
            delay(700L)
            connect(context)
        }
    }

    fun setEngineState(state: ConnectionState) {
        _connectionState.value = state
        if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
            timerJob?.cancel()
            _networkStats.value = _networkStats.value.copy(
                downloadSpeedBps = 0L,
                uploadSpeedBps = 0L
            )
        }
    }

    fun onTunnelEstablished(vpnIp: String) {
        _connectionState.value = ConnectionState.CONNECTED
        _sessionDurationSec.value = 0L
        val srv = _selectedServer.value
        _networkStats.value = _networkStats.value.copy(
            vpnIp = vpnIp,
            pingMs = if (srv.latencyMs > 0) srv.latencyMs else 35,
            downloadSpeedBps = 0L,
            uploadSpeedBps = 0L
        )
        addLog(LogLevel.SUCCESS, "TUNNEL", "CONNECTED! Virtual IP: $vpnIp via ${srv.host}:${srv.port}")
        startSessionClock()
    }

    fun onTunnelDisconnected() {
        timerJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
        _sessionDurationSec.value = 0L
        _networkStats.value = _networkStats.value.copy(
            downloadSpeedBps = 0L,
            uploadSpeedBps = 0L
        )
        addLog(LogLevel.INFO, "TUNNEL", "Disconnected cleanly.")
    }

    private fun startSessionClock() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(1000L)
                _sessionDurationSec.value += 1L
            }
        }
    }

    fun recordRealTrafficDelta(rxDeltaBytes: Long, txDeltaBytes: Long) {
        val current = _networkStats.value
        _networkStats.value = current.copy(
            downloadSpeedBps = rxDeltaBytes.coerceAtLeast(0L),
            uploadSpeedBps = txDeltaBytes.coerceAtLeast(0L),
            totalDownloadBytes = current.totalDownloadBytes + rxDeltaBytes.coerceAtLeast(0L),
            totalUploadBytes = current.totalUploadBytes + txDeltaBytes.coerceAtLeast(0L)
        )
    }

    fun updateLivePing(ms: Int) {
        if (ms > 0) {
            _networkStats.value = _networkStats.value.copy(pingMs = ms)
        }
    }

    /**
     * Real concurrent TCP socket handshake latency measurement across servers!
     */
    fun pingAllServers() {
        scope.launch(Dispatchers.IO) {
            addLog(LogLevel.INFO, "PING", "Testing real TCP socket latency to ${_servers.value.size} nodes...")
            val snapshot = _servers.value
            val measured = snapshot.map { srv ->
                async {
                    val ms = measureTcpLatencyMs(srv.host, srv.port, timeoutMs = 2500)
                    srv.copy(latencyMs = ms)
                }
            }.awaitAll()

            // Sort reachable lowest-latency nodes first (keeping unreachable -1 at bottom)
            val sorted = measured.sortedWith(
                compareBy<VpnServer> { if (it.latencyMs <= 0) 1 else 0 }
                    .thenBy { if (it.countryCode == "th") 0 else 1 }
                    .thenBy { if (it.latencyMs <= 0) Int.MAX_VALUE else it.latencyMs }
            )
            _servers.value = sorted
            sorted.firstOrNull { it.id == _selectedServer.value.id }?.let {
                _selectedServer.value = it
            }
            val onlineCount = sorted.count { it.latencyMs > 0 }
            addLog(LogLevel.SUCCESS, "PING", "Latency test complete: $onlineCount/${sorted.size} servers online")
        }
    }

    fun measureTcpLatencyMs(host: String, port: Int, timeoutMs: Int = 2500): Int {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
            elapsedMs.coerceAtLeast(1)
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Tests whether a Thai Carrier Bug Host (SNI / HTTP Host) responds on port 80/443 over the user's SIM!
     */
    suspend fun testCarrierBugHost(host: String): String = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (cleanHost.isBlank()) return@withContext "กรุณาระบุโดเมน Bug Host"

        val tcp443 = measureTcpLatencyMs(cleanHost, 443, 3000)
        val tcp80 = measureTcpLatencyMs(cleanHost, 80, 3000)

        var httpStatus = ""
        try {
            val url = URL("https://$cleanHost/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = false
                requestMethod = "HEAD"
            }
            httpStatus = "HTTP ${conn.responseCode}"
            conn.disconnect()
        } catch (e: Exception) {
            httpStatus = e.javaClass.simpleName
        }

        if (tcp443 > 0 || tcp80 > 0) {
            "✅ ใช้งานได้! $cleanHost -> Port 443: ${if (tcp443 > 0) "${tcp443}ms" else "ปิด"} | Port 80: ${if (tcp80 > 0) "${tcp80}ms" else "ปิด"} ($httpStatus)"
        } else {
            "❌ เชื่อมต่อ $cleanHost ไม่สำเร็จ (ซิมอาจบล็อกโดเมนนี้ หรือไม่มีสัญญาณ)"
        }
    }

    suspend fun refreshPublicIpDirect() = withContext(Dispatchers.IO) {
        val ip = fetchIpString(proxy = null)
        if (ip.isNotBlank()) {
            val stats = _networkStats.value
            if (_connectionState.value == ConnectionState.CONNECTED) {
                _networkStats.value = stats.copy(vpnIp = ip)
            } else {
                _networkStats.value = stats.copy(realIp = ip)
            }
        }
    }

    suspend fun refreshPublicIpThroughTunnel(httpProxyPort: Int) = withContext(Dispatchers.IO) {
        delay(800L)
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", httpProxyPort))
        val ip = fetchIpString(proxy = proxy)
        if (ip.isNotBlank()) {
            _networkStats.value = _networkStats.value.copy(vpnIp = ip)
            addLog(LogLevel.SUCCESS, "IP", "Exit Public IP verified: $ip")
        }
    }

    private fun fetchIpString(proxy: Proxy?): String {
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "http://checkip.amazonaws.com"
        )
        for (ep in endpoints) {
            try {
                val url = URL(ep)
                val conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = 4500
                conn.readTimeout = 4500
                if (conn.responseCode in 200..299) {
                    val txt = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readLine()?.trim() ?: "" }
                    conn.disconnect()
                    if (txt.length in 7..45) return txt
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
        return ""
    }

    fun detectLocalHotspotIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            // Prefer hotspot / tethering / wlan interfaces (ap0, wlan1, wlan0, rndis0, swlan0)
            val candidates = mutableListOf<Pair<String, String>>()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("rmnet") || name.startsWith("ccmni")) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        candidates.add(name to hostAddr)
                    }
                }
            }
            candidates.firstOrNull { it.first.contains("ap") || it.first.contains("swlan") || it.second.startsWith("192.168.43.") }?.let {
                return it.second
            }
            candidates.firstOrNull { it.first.startsWith("wlan") || it.second.startsWith("192.168.") }?.let {
                return it.second
            }
        } catch (_: Exception) {
        }
        return "192.168.43.1"
    }

    fun setDnsMode(mode: DnsMode) {
        _dnsMode.value = mode
        prefs?.edit()?.putString(KEY_DNS_MODE, mode.name)?.apply()
        addLog(LogLevel.INFO, "DNS", "DNS Resolver switched to ${mode.title}")
    }

    fun setKillSwitch(enabled: Boolean) {
        _killSwitch.value = enabled
        prefs?.edit()?.putBoolean(KEY_KILL_SWITCH, enabled)?.apply()
        addLog(LogLevel.INFO, "SECURITY", "Kill Switch ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun setAutoReconnect(enabled: Boolean) {
        _autoReconnect.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_RECONNECT, enabled)?.apply()
    }

    fun toggleProxyShare(context: Context) {
        val current = _proxyShareState.value
        val nextState = !current.isRunning
        val liveIp = detectLocalHotspotIp()
        _proxyShareState.value = current.copy(
            isRunning = nextState,
            localIp = liveIp,
            activeConnections = 0
        )

        val intent = Intent(context, ProxyTetherService::class.java).apply {
            action = if (nextState) ProxyTetherService.ACTION_START else ProxyTetherService.ACTION_STOP
        }
        if (nextState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.startService(intent)
            addLog(LogLevel.WARN, "HOTSPOT", "Hotspot Proxy Share stopped")
        }
    }

    fun updateProxyLocalIp(ip: String) {
        _proxyShareState.value = _proxyShareState.value.copy(localIp = ip)
    }

    fun updateProxyClientCount(count: Int) {
        _proxyShareState.value = _proxyShareState.value.copy(activeConnections = count)
    }

    fun setSplitTunnelEnabled(enabled: Boolean) {
        _splitTunnelEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SPLIT_TUNNEL, enabled)?.apply()
        addLog(LogLevel.INFO, "SPLIT", "Split Tunneling ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun toggleAppBypass(packageName: String) {
        val current = _bypassedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _bypassedPackages.value = current
        prefs?.edit()?.putStringSet(KEY_BYPASSED_PKGS, current)?.apply()
    }

    private fun saveCustomTweaks(list: List<CarrierTweak>) {
        runCatching {
            prefs?.edit()?.putString(KEY_CUSTOM_TWEAKS, json.encodeToString(list))?.apply()
        }
    }

    private fun saveCustomServers(list: List<VpnServer>) {
        runCatching {
            prefs?.edit()?.putString(KEY_CUSTOM_SERVERS, json.encodeToString(list))?.apply()
        }
    }

    private val _mtuSetting = MutableStateFlow(1500)
    val mtuSetting: StateFlow<Int> = _mtuSetting.asStateFlow()

    private val _udpForwarding = MutableStateFlow(true)
    val udpForwarding: StateFlow<Boolean> = _udpForwarding.asStateFlow()

    private val _lastSpeedTest = MutableStateFlow(SpeedTestSummary())
    val lastSpeedTest: StateFlow<SpeedTestSummary> = _lastSpeedTest.asStateFlow()

    val selectedDns: StateFlow<DnsMode> get() = dnsMode
    val proxyState: StateFlow<ProxyShareState> get() = proxyShareState

    fun setMtuSetting(mtu: Int) {
        _mtuSetting.value = mtu.coerceIn(1280, 1500)
        prefs?.edit()?.putInt("mtu_setting", _mtuSetting.value)?.apply()
        addLog(LogLevel.INFO, "CONFIG", "MTU size updated to ${_mtuSetting.value} bytes")
    }

    fun setUdpForwarding(enabled: Boolean) {
        _udpForwarding.value = enabled
        prefs?.edit()?.putBoolean("udp_forwarding", enabled)?.apply()
    }

    fun recordSpeedTestResult(downloadMbps: Float, uploadMbps: Float, pingMs: Int) {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        val summary = SpeedTestSummary(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            pingMs = pingMs,
            testedAt = sdf.format(Date())
        )
        _lastSpeedTest.value = summary
        runCatching {
            prefs?.edit()?.putString("last_speed_test", json.encodeToString(summary))?.apply()
        }
        addLog(
            LogLevel.SUCCESS,
            "SPEED",
            String.format(Locale.US, "Speed Test: ↓ %.1f Mbps | ↑ %.1f Mbps | Ping %d ms", downloadMbps, uploadMbps, pingMs)
        )
    }

    fun formatDuration(totalSeconds: Long): String {
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun addLog(level: LogLevel, tag: String, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        val entry = LogEntry(
            timestamp = sdf.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        _logs.value = (_logs.value + entry).takeLast(120)
    }
}
