package com.example.h25vpn.service

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.h25vpn.data.TunnelRoutes
import com.example.h25vpn.data.TunnelProtocol
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Never fall back to the device's default/direct internet connection. */
class TunnelSpeedProbe {
    @Volatile private var active: HttpURLConnection? = null

    fun cancel() { active?.disconnect() }

    data class Result(val bytes: Long, val nanos: Long) {
        val mbps get() = if (nanos > 0) bytes.toDouble() * 8_000 / nanos else 0.0
        val passed get() = TunnelRoutes.passes(bytes, nanos)
    }

    suspend fun measure(protocol: TunnelProtocol, connectivity: ConnectivityManager): Result {
        val context = currentCoroutineContext()
        context.ensureActive()
        // HTTPS certificate validation remains enabled; redirects, compression and caching are disabled.
        val url = URL("https://speed.cloudflare.com/__down?bytes=1048576&nonce=${System.nanoTime()}")
        val openVpn = protocol == TunnelProtocol.OPENVPN_TCP || protocol == TunnelProtocol.OPENVPN_UDP
        val conn = if (openVpn) {
            val network = connectivity.allNetworks.firstOrNull {
                connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            } ?: error("No VPN network available for speed verification")
            network.openConnection(url) as HttpURLConnection
        } else {
            val ssh = protocol == TunnelProtocol.SSH_DIRECT || protocol == TunnelProtocol.SSH_SSL
            val proxy = Proxy(if (ssh) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                InetSocketAddress("127.0.0.1", if (ssh) H25VpnService.LOCAL_SOCKS_PORT else H25VpnService.LOCAL_HTTP_PORT))
            url.openConnection(proxy) as HttpURLConnection
        }
        active = conn
        val started = System.nanoTime()
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 3000
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("Cache-Control", "no-cache, no-store")
            check(conn.responseCode == 200) { "Speed endpoint returned HTTP ${conn.responseCode}" }
            check(conn.contentLengthLong == 1048576L) { "Unexpected speed response length" }
            var total = 0L
            val buffer = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                while (total < 1048576L) {
                    context.ensureActive()
                    check(System.nanoTime() - started < 12_000_000_000L) { "Speed test timed out" }
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), 1048576L - total).toInt())
                    if (count < 0) break
                    total += count
                }
            }
            context.ensureActive()
            return Result(total, System.nanoTime() - started)
        } finally {
            conn.disconnect()
            if (active === conn) active = null
        }
    }
}
