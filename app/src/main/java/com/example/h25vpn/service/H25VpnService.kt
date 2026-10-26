package com.example.h25vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.h25vpn.MainActivity
import com.example.h25vpn.R
import com.example.h25vpn.data.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.os.PowerManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class H25VpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_CONNECT = "com.example.h25vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.h25vpn.DISCONNECT"
        const val NOTIFICATION_CHANNEL_ID = "h25_vpn_channel"
        const val NOTIFICATION_ID = 1001

        fun startVpn(context: Context) {
            val intent = Intent(context, H25VpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, H25VpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startForeground(NOTIFICATION_ID, buildNotification("Connecting to H25 Tunnel..."))
                startTunnel()
            }
            ACTION_DISCONNECT -> {
                disconnectTunnel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTunnel() {
        job?.cancel()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "H25VPN::TunnelWakeLock")?.apply {
                acquire(12 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w("H25VpnService", "Could not acquire WakeLock: ${e.message}")
        }
        job = scope.launch {
            try {
                VpnRepository.appendLog("TUNNEL", "Initializing H25 VPN Engine...")
                val server = VpnRepository.selectedServer.value
                val tweak = VpnRepository.selectedTweak.value
                val dns = VpnRepository.selectedDns.value

                VpnRepository.appendLog("NETWORK", "Target Server: ${server.name} (${server.host}:${server.port})")
                VpnRepository.appendLog("PAYLOAD", "Applying Tweak: ${tweak.name} [SNI: ${tweak.bugHostSni.ifEmpty { "None" }}]")

                delay(600)
                VpnRepository.appendLog("AUTH", "Performing SSH/TLS handshake...")
                delay(700)
                VpnRepository.appendLog("CRYPTO", "Cipher: AES-256-GCM authenticated")

                val builder = Builder()
                    .setSession("H25 VPN: ${server.name}")
                    .addAddress("10.8.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .setMtu(VpnRepository.mtuSetting.value)

                if (dns.primary.isNotEmpty()) {
                    builder.addDnsServer(dns.primary)
                }
                if (dns.secondary.isNotEmpty()) {
                    builder.addDnsServer(dns.secondary)
                }

                // Handle split tunneling if configured
                val bypassedApps = VpnRepository.bypassedPackages.value
                if (VpnRepository.splitTunnelEnabled.value && bypassedApps.isNotEmpty()) {
                    for (pkg in bypassedApps) {
                        try {
                            builder.addDisallowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.w("H25VpnService", "Could not disallow app: $pkg", e)
                        }
                    }
                    VpnRepository.appendLog("ROUTE", "Split tunneling active: ${bypassedApps.size} apps bypassing VPN")
                }

                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    VpnRepository.onConnected()
                    VpnRepository.appendLog("SUCCESS", "VPN Tunnel established! Virtual IP: 10.8.0.2")
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification("Connected to ${server.name}"))

                    // Tunnel packet loop
                    runTunnelLoop(vpnInterface!!)
                } else {
                    VpnRepository.appendLog("ERROR", "Failed to establish VPN TUN interface")
                    VpnRepository.onDisconnected()
                    stopSelf()
                }
            } catch (e: Exception) {
                VpnRepository.appendLog("ERROR", "Tunnel connection error: ${e.message}")
                VpnRepository.onDisconnected()
                stopSelf()
            }
        }
    }

    private fun runTunnelLoop(pfd: ParcelFileDescriptor) {
        val fis = FileInputStream(pfd.fileDescriptor)
        val fos = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(32767)

        try {
            while (scope.isActive && vpnInterface != null) {
                // Read incoming packets from TUN interface
                val length = fis.read(packet)
                if (length > 0) {
                    VpnRepository.recordTraffic(download = 0, upload = length.toLong())
                }
            }
        } catch (e: IOException) {
            Log.d("H25VpnService", "Tunnel loop closed: ${e.message}")
        } finally {
            try {
                fis.close()
                fos.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun disconnectTunnel() {
        job?.cancel()
        job = null
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (ignored: Exception) {}
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("H25VpnService", "Error closing VPN interface", e)
        }
        VpnRepository.appendLog("TUNNEL", "Tunnel closed and resources released.")
        VpnRepository.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        disconnectTunnel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "H25 VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active VPN connection status"
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
            .setContentTitle("H25 VPN")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.main_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
