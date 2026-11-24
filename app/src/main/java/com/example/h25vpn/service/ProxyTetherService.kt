package com.example.h25vpn.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.h25vpn.MainActivity
import com.example.h25vpn.data.LogLevel
import com.example.h25vpn.data.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Wi-Fi Hotspot / LAN HTTP & HTTPS CONNECT Proxy Server (`0.0.0.0:8080`).
 * Allows other phones, tablets, Smart TVs, or PCs on the user's Hotspot to share the VPN connection
 * without needing root access!
 */
class ProxyTetherService : Service() {

    companion object {
        const val ACTION_START = "com.example.h25vpn.START_PROXY"
        const val ACTION_STOP = "com.example.h25vpn.STOP_PROXY"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "h25_proxy_channel"
    }

    private var serverSocket: ServerSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeClients = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProxyServer()
            ACTION_STOP -> stopProxyServer()
        }
        return START_STICKY
    }

    private fun startProxyServer() {
        createNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val port = VpnRepository.proxyShareState.value.port
        val ip = VpnRepository.detectLocalHotspotIp()
        VpnRepository.updateProxyLocalIp(ip)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TH-VPN Hotspot Share Active")
            .setContentText("Proxy listening on $ip:$port")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                serverSocket?.close()
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port))
                serverSocket = ss
                VpnRepository.addLog(LogLevel.SUCCESS, "HOTSPOT", "Real HTTP/HTTPS Proxy listening on $ip:$port")

                while (isActive && serverSocket?.isClosed == false) {
                    val client = ss.accept()
                    val count = activeClients.incrementAndGet()
                    VpnRepository.updateProxyClientCount(count)
                    launch {
                        handleProxyClient(client)
                    }
                }
            } catch (e: Exception) {
                if (serverSocket?.isClosed == false) {
                    VpnRepository.addLog(LogLevel.ERROR, "HOTSPOT", "Proxy error: ${e.message}")
                }
            }
        }
    }

    private fun handleProxyClient(clientSocket: Socket) {
        var upstreamSocket: Socket? = null
        try {
            clientSocket.soTimeout = 30_000
            clientSocket.tcpNoDelay = true
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerBytes = readHttpHeaderBytes(clientIn)
            if (headerBytes.isEmpty()) return

            val headerStr = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerStr.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val targetRaw = parts[1]

            if (method == "CONNECT") {
                val hostPort = targetRaw.split(":")
                val targetHost = hostPort[0]
                val targetPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                upstreamSocket = Socket()
                upstreamSocket.tcpNoDelay = true
                upstreamSocket.connect(InetSocketAddress(targetHost, targetPort), 12_000)

                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()

                relayBidirectional(clientSocket, upstreamSocket)
            } else {
                // Plain HTTP Proxy request (e.g. GET http://example.com/path HTTP/1.1)
                val uri = try {
                    URI(targetRaw)
                } catch (_: Exception) {
                    null
                }
                val hostHeader = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim() ?: ""
                val targetHost = uri?.host ?: hostHeader.substringBefore(":")
                if (targetHost.isBlank()) return
                val targetPort = when {
                    uri != null && uri.port > 0 -> uri.port
                    hostHeader.contains(":") -> hostHeader.substringAfter(":").toIntOrNull() ?: 80
                    else -> 80
                }
                val relativePath = if (uri != null) {
                    val p = uri.rawPath.ifNullOrBlank("/")
                    val q = uri.rawQuery
                    if (q.isNullOrBlank()) p else "$p?$q"
                } else {
                    targetRaw
                }

                upstreamSocket = Socket()
                upstreamSocket.tcpNoDelay = true
                upstreamSocket.connect(InetSocketAddress(targetHost, targetPort), 12_000)

                val rewrittenFirstLine = "$method $relativePath ${parts.getOrElse(2) { "HTTP/1.1" }}"
                val rewrittenHeader = buildString {
                    append(rewrittenFirstLine).append("\r\n")
                    for (i in 1 until lines.size) {
                        val line = lines[i]
                        if (line.isEmpty()) continue
                        if (line.startsWith("Proxy-Connection:", ignoreCase = true)) continue
                        append(line).append("\r\n")
                    }
                    append("Connection: close\r\n\r\n")
                }

                val upOut = upstreamSocket.getOutputStream()
                upOut.write(rewrittenHeader.toByteArray(Charsets.ISO_8859_1))
                upOut.flush()

                relayBidirectional(clientSocket, upstreamSocket)
            }
        } catch (_: Exception) {
        } finally {
            try {
                upstreamSocket?.close()
            } catch (_: Exception) {
            }
            try {
                clientSocket.close()
            } catch (_: Exception) {
            }
            val remaining = activeClients.decrementAndGet().coerceAtLeast(0)
            VpnRepository.updateProxyClientCount(remaining)
        }
    }

    private fun readHttpHeaderBytes(input: InputStream): ByteArray {
        val buf = java.io.ByteArrayOutputStream(512)
        var state = 0
        var count = 0
        while (count < 16_384) {
            val b = input.read()
            if (b == -1) break
            buf.write(b)
            count++
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) break
        }
        return buf.toByteArray()
    }

    private fun relayBidirectional(sockA: Socket, sockB: Socket) {
        val t1 = Thread({
            pipeStream(sockA.getInputStream(), sockB.getOutputStream())
            try {
                sockB.shutdownOutput()
            } catch (_: Exception) {
            }
        }, "ProxyRelay-C2S")

        val t2 = Thread({
            pipeStream(sockB.getInputStream(), sockA.getOutputStream())
            try {
                sockA.shutdownOutput()
            } catch (_: Exception) {
            }
        }, "ProxyRelay-S2C")

        t1.start()
        t2.start()
        try {
            t1.join()
            t2.join()
        } catch (_: InterruptedException) {
        }
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        try {
            val buffer = ByteArray(16_384)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun String?.ifNullOrBlank(fallback: String): String =
        if (this.isNullOrBlank()) fallback else this

    private fun stopProxyServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        activeClients.set(0)
        VpnRepository.updateProxyClientCount(0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopProxyServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TH-VPN Hotspot Proxy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active Wi-Fi Hotspot proxy sharing"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
