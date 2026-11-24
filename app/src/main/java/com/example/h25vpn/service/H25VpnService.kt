package com.example.h25vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.h25vpn.MainActivity
import com.example.h25vpn.data.CarrierTweak
import com.example.h25vpn.data.ConnectionState
import com.example.h25vpn.data.LogLevel
import com.example.h25vpn.data.TunnelProtocol
import com.example.h25vpn.data.VpnConfigParser
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.data.VpnServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet

class H25VpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.h25vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.h25vpn.DISCONNECT"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "h25_vpn_channel"
        const val LOCAL_SOCKS_PORT = 10808
        const val LOCAL_HTTP_PORT = 10809

        private var xrayInitialized = false
        private var ovpnLoaded = false

        @Synchronized
        fun ensureNativeEnginesLoaded(context: android.content.Context, onLog: (String) -> Unit) {
            if (!ovpnLoaded) {
                try {
                    System.loadLibrary("ovpncli")
                    ovpnLoaded = true
                    onLog("OpenVPN3 native core (libovpncli.so) ready.")
                } catch (t: Throwable) {
                    onLog("OpenVPN3 load warning: ${t.message}")
                }
            }
            if (!xrayInitialized) {
                try {
                    go.Seq.setContext(context.applicationContext)
                    Libv2ray.initV2Env(context.filesDir.absolutePath)
                    xrayInitialized = true
                    val ver = try {
                        Libv2ray.checkVersionX()
                    } catch (_: Throwable) {
                        "Xray-core"
                    }
                    onLog("Xray engine initialized: $ver")
                } catch (t: Throwable) {
                    onLog("Xray init warning: ${t.message}")
                }
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var v2rayPoint: V2RayPoint? = null
    private var tun2SocksBridge: Tun2SocksBridge? = null
    private var sshEngine: SshInjectorEngine? = null
    private var openVpnEngine: OpenVpnEngine? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var telemetryJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                createNotificationChannel()
                val notification = buildNotification("Connecting to TH-VPN...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startRealTunnel()
            }
            ACTION_DISCONNECT -> {
                stopRealTunnel()
            }
        }
        return START_STICKY
    }

    private fun startRealTunnel() {
        connectJob?.cancel()
        connectJob = serviceScope.launch {
            val repo = VpnRepository
            val server = repo.selectedServer.value
            val tweak = repo.selectedTweak.value

            try {
                repo.setEngineState(ConnectionState.CONNECTING)
                Tun2SocksBridge.ensureXrayAssetsExtracted(applicationContext)
                ensureNativeEnginesLoaded(applicationContext) { msg ->
                    repo.addLog(LogLevel.INFO, "CORE", msg)
                }

                val spoofInfo = if (!tweak.isDirectNoTweak && tweak.bugHostSni.isNotBlank()) {
                    " [Carrier BugHost: ${tweak.bugHostSni}]"
                } else {
                    " [Direct]"
                }
                repo.addLog(
                    LogLevel.INFO,
                    "TUNNEL",
                    "Connecting ${server.name} (${server.host}:${server.port} • ${server.protocol.displayName})$spoofInfo"
                )

                when (server.protocol) {
                    TunnelProtocol.OPENVPN_TCP, TunnelProtocol.OPENVPN_UDP -> {
                        startOpenVpnMode(server, tweak)
                    }
                    TunnelProtocol.SSH_DIRECT, TunnelProtocol.SSH_SSL -> {
                        startSshInjectorMode(server, tweak)
                    }
                    else -> {
                        startXrayV2RayMode(server, tweak)
                    }
                }
            } catch (e: Throwable) {
                repo.addLog(LogLevel.ERROR, "TUNNEL", "Connection failed: ${e.message ?: e.javaClass.simpleName}")
                teardownEnginesOnly()
                repo.setEngineState(ConnectionState.ERROR)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun startXrayV2RayMode(server: VpnServer, tweak: CarrierTweak) {
        val repo = VpnRepository
        teardownEnginesOnly()

        repo.setEngineState(ConnectionState.AUTHENTICATING)
        val xrayJson = VpnConfigParser.buildXrayJsonConfig(
            server = server,
            tweak = tweak,
            socksPort = LOCAL_SOCKS_PORT,
            httpPort = LOCAL_HTTP_PORT,
            dnsMode = repo.dnsMode.value
        )

        val callbackSupport = object : V2RayVPNServiceSupportsSet {
            override fun shutdown(): Long {
                repo.addLog(LogLevel.WARN, "XRAY", "Core requested shutdown")
                return 0L
            }

            override fun prepare(): Long = 0L

            override fun protect(fd: Long): Boolean {
                return this@H25VpnService.protect(fd.toInt())
            }

            override fun onEmitStatus(code: Long, status: String?): Long {
                if (!status.isNullOrBlank()) {
                    repo.addLog(LogLevel.INFO, "XRAY", status.take(120))
                }
                return 0L
            }

            override fun setup(conf: String?): Long = 0L
        }

        val point = Libv2ray.newV2RayPoint(callbackSupport, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
        point.configureFileContent = xrayJson
        point.domainName = "${server.host}:${server.port}"
        point.runLoop(false)

        if (!point.isRunning) {
            throw IllegalStateException("Xray-core failed to start for ${server.host}:${server.port}")
        }
        v2rayPoint = point

        // Establish Android TUN interface and attach native badvpn-tun2socks -> 127.0.0.1:10808
        val pfd = buildAndroidTunInterface(sessionLabel = "TH-VPN (${server.country})")
            ?: throw IllegalStateException("Android VpnService.Builder.establish() returned null")
        vpnInterface = pfd

        val bridge = Tun2SocksBridge(applicationContext) { msg ->
            repo.addLog(LogLevel.INFO, "TUN2SOCKS", msg)
        }
        val ok = bridge.start(
            tunInterface = pfd,
            socksPort = LOCAL_SOCKS_PORT,
            dnsPrimary = repo.dnsMode.value.primary,
            dnsSecondary = repo.dnsMode.value.secondary,
            enableUdpGw = repo.udpForwarding.value
        )
        if (!ok) {
            throw IllegalStateException("Failed to attach TUN interface to tun2socks")
        }
        tun2SocksBridge = bridge

        // Measure real tunnel round-trip delay via Xray-core
        repo.onTunnelEstablished(vpnIp = "26.26.26.1")
        updateNotification("Connected: ${server.name} (${tweak.carrier})")
        startRealTrafficTelemetry(useXrayStats = true)

        serviceScope.launch {
            try {
                val delayMs = point.measureDelay()
                if (delayMs > 0) {
                    repo.updateLivePing(delayMs.toInt())
                    repo.addLog(LogLevel.SUCCESS, "XRAY", "Tunnel verified! Real HTTP RTT: ${delayMs}ms")
                }
            } catch (_: Throwable) {
            }
            repo.refreshPublicIpThroughTunnel(httpProxyPort = LOCAL_HTTP_PORT)
        }
    }

    private fun startSshInjectorMode(server: VpnServer, tweak: CarrierTweak) {
        val repo = VpnRepository
        teardownEnginesOnly()
        repo.setEngineState(ConnectionState.AUTHENTICATING)

        val engine = SshInjectorEngine(this) { msg ->
            repo.addLog(LogLevel.INFO, "SSH-INJ", msg)
        }
        engine.connectAndStartSocks(
            server = server,
            tweak = tweak,
            localSocksPort = LOCAL_SOCKS_PORT
        )
        sshEngine = engine

        val pfd = buildAndroidTunInterface(sessionLabel = "TH-VPN SSH (${server.country})")
            ?: throw IllegalStateException("Android VpnService.Builder.establish() returned null")
        vpnInterface = pfd

        val bridge = Tun2SocksBridge(applicationContext) { msg ->
            repo.addLog(LogLevel.INFO, "TUN2SOCKS", msg)
        }
        if (!bridge.start(
                tunInterface = pfd,
                socksPort = LOCAL_SOCKS_PORT,
                dnsPrimary = repo.dnsMode.value.primary,
                dnsSecondary = repo.dnsMode.value.secondary,
                enableUdpGw = false
            )
        ) {
            throw IllegalStateException("Failed to attach TUN to SSH SOCKS5 forwarder")
        }
        tun2SocksBridge = bridge

        repo.onTunnelEstablished(vpnIp = "26.26.26.1")
        updateNotification("Connected SSH: ${server.name}")
        startRealTrafficTelemetry(useXrayStats = false)
        serviceScope.launch {
            repo.refreshPublicIpDirect()
        }
    }

    private fun startOpenVpnMode(server: VpnServer, tweak: CarrierTweak) {
        val repo = VpnRepository
        teardownEnginesOnly()
        repo.setEngineState(ConnectionState.AUTHENTICATING)

        val ovpn = OpenVpnEngine(
            vpnService = this,
            splitTunnelEnabled = repo.splitTunnelEnabled.value,
            bypassedPackages = repo.bypassedPackages.value,
            dnsMode = repo.dnsMode.value,
            onConnected = { assignedIp ->
                repo.onTunnelEstablished(vpnIp = assignedIp)
                updateNotification("Connected OpenVPN: ${server.name}")
                startRealTrafficTelemetry(useXrayStats = false)
                serviceScope.launch {
                    repo.refreshPublicIpDirect()
                }
            },
            onError = { errMsg ->
                repo.addLog(LogLevel.ERROR, "OVPN", errMsg)
                stopRealTunnel(markError = true)
            },
            onLog = { msg ->
                repo.addLog(LogLevel.INFO, "OVPN", msg)
            }
        )
        openVpnEngine = ovpn
        ovpn.startOpenVpn(server, tweak)
    }

    private fun buildAndroidTunInterface(sessionLabel: String): ParcelFileDescriptor? {
        val repo = VpnRepository
        val builder = Builder()
            .setSession(sessionLabel)
            .setMtu(repo.mtuSetting.value.coerceIn(1280, 1500))
            .addAddress("26.26.26.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(repo.dnsMode.value.primary)
            .addDnsServer(repo.dnsMode.value.secondary)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Apply Split Tunneling per-app bypass!
        if (repo.splitTunnelEnabled.value) {
            val bypassed = repo.bypassedPackages.value
            for (pkg in bypassed) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: Exception) {
                }
            }
            if (bypassed.isNotEmpty()) {
                repo.addLog(LogLevel.INFO, "SPLIT", "Bypassing ${bypassed.size} selected apps from VPN")
            }
        }

        return builder.establish()
    }

    /**
     * Reads genuine byte counters from Xray-core (`queryStats`) or Android OS `TrafficStats.getUidRxBytes`
     * — zero simulated or random numbers!
     */
    private fun startRealTrafficTelemetry(useXrayStats: Boolean) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            val uid = Process.myUid()
            var lastUidRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
            var lastUidTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)

            while (isActive && VpnRepository.connectionState.value == ConnectionState.CONNECTED) {
                delay(1000L)
                var deltaDown = 0L
                var deltaUp = 0L

                val point = v2rayPoint
                if (useXrayStats && point != null && point.isRunning) {
                    try {
                        // Xray queryStats("proxy", "downlink") returns delta bytes when reset=true
                        deltaDown = point.queryStats("proxy", "downlink").coerceAtLeast(0L)
                        deltaUp = point.queryStats("proxy", "uplink").coerceAtLeast(0L)
                    } catch (_: Throwable) {
                    }
                }

                if (deltaDown == 0L && deltaUp == 0L) {
                    val nowRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
                    val nowTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
                    deltaDown = (nowRx - lastUidRx).coerceAtLeast(0L)
                    deltaUp = (nowTx - lastUidTx).coerceAtLeast(0L)
                    lastUidRx = nowRx
                    lastUidTx = nowTx
                } else {
                    lastUidRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
                    lastUidTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
                }

                VpnRepository.recordRealTrafficDelta(deltaDown, deltaUp)
            }
        }
    }

    private fun teardownEnginesOnly() {
        telemetryJob?.cancel()
        telemetryJob = null

        try {
            tun2SocksBridge?.stop()
        } catch (_: Throwable) {
        }
        tun2SocksBridge = null

        try {
            v2rayPoint?.let {
                if (it.isRunning) it.stopLoop()
            }
        } catch (_: Throwable) {
        }
        v2rayPoint = null

        try {
            sshEngine?.stop()
        } catch (_: Throwable) {
        }
        sshEngine = null

        try {
            openVpnEngine?.stopOpenVpn()
        } catch (_: Throwable) {
        }
        openVpnEngine = null

        try {
            vpnInterface?.close()
        } catch (_: Throwable) {
        }
        vpnInterface = null
    }

    private fun stopRealTunnel(markError: Boolean = false) {
        connectJob?.cancel()
        teardownEnginesOnly()
        if (markError) {
            VpnRepository.setEngineState(ConnectionState.ERROR)
        } else {
            VpnRepository.onTunnelDisconnected()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        VpnRepository.addLog(LogLevel.WARN, "TUNNEL", "VPN permission revoked by system")
        stopRealTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownEnginesOnly()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TH-VPN Active Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays real-time TH-VPN tunnel connection status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, H25VpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arena VPN • Free-Net & Promo Tunnel")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingDisconnect)
            .build()
    }
}
