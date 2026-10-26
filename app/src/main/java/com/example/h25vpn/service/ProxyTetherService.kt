package com.example.h25vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.h25vpn.MainActivity
import com.example.h25vpn.R
import com.example.h25vpn.data.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class ProxyTetherService : Service() {

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeClients = AtomicInteger(0)

    companion object {
        const val ACTION_START = "com.example.h25vpn.START_PROXY"
        const val ACTION_STOP = "com.example.h25vpn.STOP_PROXY"
        const val NOTIFICATION_CHANNEL_ID = "h25_proxy_channel"
        const val NOTIFICATION_ID = 2002
        const val PROXY_PORT = 8080

        fun startProxy(context: Context) {
            val intent = Intent(context, ProxyTetherService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopProxy(context: Context) {
            val intent = Intent(context, ProxyTetherService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Proxy Server Online on port $PROXY_PORT"))
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        if (serverSocket != null && !serverSocket!!.isClosed) return

        val wifiIp = getLocalWifiIpAddress()
        job = scope.launch {
            try {
                serverSocket = ServerSocket(PROXY_PORT)
                VpnRepository.updateProxyState(
                    isRunning = true,
                    localIp = wifiIp,
                    port = PROXY_PORT,
                    activeClients = 0
                )
                VpnRepository.appendLog("PROXY", "Hotspot Proxy Server listening on $wifiIp:$PROXY_PORT")

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    val clientSocket = serverSocket!!.accept()
                    val count = activeClients.incrementAndGet()
                    VpnRepository.updateProxyActiveConnections(count)
                    scope.launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.d("ProxyTetherService", "Server socket closed: ${e.message}")
            } finally {
                stopServer()
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 15000
            val input: InputStream = client.getInputStream()
            val output: OutputStream = client.getOutputStream()

            // Read simple HTTP request line
            val buffer = ByteArray(2048)
            val bytesRead = input.read(buffer)
            if (bytesRead > 0) {
                val reqStr = String(buffer, 0, bytesRead)
                if (reqStr.startsWith("CONNECT")) {
                    val okResponse = "HTTP/1.1 200 Connection Established\r\n\r\n"
                    output.write(okResponse.toByteArray())
                    output.flush()
                } else {
                    val okHtml = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 17\r\n\r\nH25 Proxy Online\n"
                    output.write(okHtml.toByteArray())
                    output.flush()
                }
            }
        } catch (ignored: Exception) {
        } finally {
            try {
                client.close()
            } catch (ignored: Exception) {}
            val count = activeClients.decrementAndGet().coerceAtLeast(0)
            VpnRepository.updateProxyActiveConnections(count)
        }
    }

    private fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e("ProxyTetherService", "Error stopping server", e)
        }
        job?.cancel()
        activeClients.set(0)
        VpnRepository.updateProxyState(isRunning = false)
        VpnRepository.appendLog("PROXY", "Hotspot Proxy Server stopped.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getLocalWifiIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            } else {
                "192.168.43.1"
            }
        } catch (e: Exception) {
            "192.168.43.1"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "H25 Hotspot Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Hotspot Proxy tethering status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("H25 Hotspot Proxy")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.main_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
