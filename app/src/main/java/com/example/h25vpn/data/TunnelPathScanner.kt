package com.example.h25vpn.data

import android.content.Context
import android.os.Build
import com.example.h25vpn.service.H25VpnService
import com.example.h25vpn.service.SshInjectorEngine
import com.example.h25vpn.service.Tun2SocksBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL

data class TunnelProbeOutcome(
    val tcpPingMs: Int,
    val measuredMbps: Float,
    val testedViaTunnel: Boolean,
    val tunnelStatus: String
)

interface TunnelPathProbe {
    suspend fun probeNode(
        context: Context?,
        server: VpnServer,
        tweak: CarrierTweak,
        dnsMode: DnsMode,
        activeProxyPort: Int?,
        activeServerId: String?
    ): TunnelProbeOutcome
}

/**
 * Probes each VPN server node strictly through its own isolated tunnel proxy (Xray or SSH)
 * or the active matching tunnel proxy. Never falls back to direct unproxied internet.
 */
object RealTunnelPathProbe : TunnelPathProbe {
    private const val PROBE_URL = "https://speed.cloudflare.com/__down?bytes=524288"

    override suspend fun probeNode(
        context: Context?,
        server: VpnServer,
        tweak: CarrierTweak,
        dnsMode: DnsMode,
        activeProxyPort: Int?,
        activeServerId: String?
    ): TunnelProbeOutcome {
        val tcpPingMs = VpnRepository.measureTcpLatencyMs(server.host, server.port, timeoutMs = 2200)
        if (tcpPingMs <= 0) {
            return TunnelProbeOutcome(
                tcpPingMs = -1,
                measuredMbps = 0f,
                testedViaTunnel = false,
                tunnelStatus = "TCP ไม่ตอบสนอง (${server.host}:${server.port})"
            )
        }

        // Reuse the live active tunnel HTTP proxy ONLY when probing that exact active server.
        if (activeServerId != null && server.id == activeServerId && activeProxyPort != null && activeProxyPort > 0) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", activeProxyPort))
            val mbps = measureProxiedDownloadMbps(proxy)
            return TunnelProbeOutcome(
                tcpPingMs = tcpPingMs,
                measuredMbps = mbps,
                testedViaTunnel = true,
                tunnelStatus = if (mbps > 0f) "ทดสอบผ่านอุโมงค์ที่กำลังเชื่อมต่อ (HTTP Proxy)" else "อุโมงค์เชื่อมต่ออยู่แต่ดาวน์โหลดไม่สำเร็จ"
            )
        }

        return when (server.protocol) {
            TunnelProtocol.OPENVPN_TCP, TunnelProtocol.OPENVPN_UDP -> {
                // OpenVPN3 native core binds exclusively to Android VpnService TUN interface.
                // Without switching the system VPN to this node, report honestly rather than estimating.
                TunnelProbeOutcome(
                    tcpPingMs = tcpPingMs,
                    measuredMbps = 0f,
                    testedViaTunnel = false,
                    tunnelStatus = "OpenVPN ต้องสลับเชื่อมต่อ TUN หลักก่อน (ไม่ประมาณค่า)"
                )
            }

            TunnelProtocol.SSH_DIRECT, TunnelProtocol.SSH_SSL -> {
                probeViaIsolatedSshTunnel(server, tweak, tcpPingMs)
            }

            else -> {
                if (context == null) {
                    TunnelProbeOutcome(
                        tcpPingMs = tcpPingMs,
                        measuredMbps = 0f,
                        testedViaTunnel = false,
                        tunnelStatus = "ไม่มี Context สำหรับเปิด Xray-core"
                    )
                } else {
                    probeViaIsolatedXrayTunnel(context, server, tweak, dnsMode, tcpPingMs)
                }
            }
        }
    }

    private fun probeViaIsolatedSshTunnel(
        server: VpnServer,
        tweak: CarrierTweak,
        tcpPingMs: Int
    ): TunnelProbeOutcome {
        val socksPort = allocateFreePort()
        if (socksPort <= 0) {
            return TunnelProbeOutcome(tcpPingMs, 0f, false, "จัดสรรพอร์ต SOCKS5 ชั่วคราวไม่สำเร็จ")
        }

        val engine = SshInjectorEngine(
            protectSocket = { sock -> H25VpnService.activeInstance?.protect(sock) ?: true },
            onLog = {}
        )
        return try {
            engine.connectAndStartSocks(
                server = server,
                tweak = tweak,
                localSocksPort = socksPort
            )
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val mbps = measureProxiedDownloadMbps(proxy)
            TunnelProbeOutcome(
                tcpPingMs = tcpPingMs,
                measuredMbps = mbps,
                testedViaTunnel = true,
                tunnelStatus = if (mbps > 0f) "ผ่านอุโมงค์ SSH/SOCKS5 จริง" else "เชื่อม SSH ได้แต่โหลดผ่านอุโมงค์ไม่ผ่าน"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            TunnelProbeOutcome(
                tcpPingMs = tcpPingMs,
                measuredMbps = 0f,
                testedViaTunnel = false,
                tunnelStatus = "เปิดอุโมงค์ SSH ไม่สำเร็จ: ${e.message?.take(48) ?: e.javaClass.simpleName}"
            )
        } finally {
            runCatching { engine.stop() }
        }
    }

    private suspend fun probeViaIsolatedXrayTunnel(
        context: Context,
        server: VpnServer,
        tweak: CarrierTweak,
        dnsMode: DnsMode,
        tcpPingMs: Int
    ): TunnelProbeOutcome {
        val socksPort = allocateFreePort()
        val httpPort = allocateFreePort(exclude = socksPort)
        if (socksPort <= 0 || httpPort <= 0) {
            return TunnelProbeOutcome(tcpPingMs, 0f, false, "จัดสรรพอร์ต Xray ชั่วคราวไม่สำเร็จ")
        }

        var point: V2RayPoint? = null
        return try {
            Tun2SocksBridge.ensureXrayAssetsExtracted(context.applicationContext)
            H25VpnService.ensureNativeEnginesLoaded(context.applicationContext) {}

            val xrayJson = VpnConfigParser.buildXrayJsonConfig(
                server = server,
                tweak = tweak,
                socksPort = socksPort,
                httpPort = httpPort,
                dnsMode = dnsMode
            )

            val callback = object : V2RayVPNServiceSupportsSet {
                override fun shutdown(): Long = 0L
                override fun prepare(): Long = 0L
                override fun protect(fd: Long): Boolean {
                    return H25VpnService.activeInstance?.protect(fd.toInt()) ?: true
                }
                override fun onEmitStatus(code: Long, status: String?): Long = 0L
                override fun setup(conf: String?): Long = 0L
            }

            point = Libv2ray.newV2RayPoint(callback, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            point.configureFileContent = xrayJson
            point.domainName = "${server.host}:${server.port}"
            point.runLoop(false)

            // Allow local inbound listener to bind cleanly
            delay(200L)
            if (!point.isRunning) {
                return TunnelProbeOutcome(
                    tcpPingMs = tcpPingMs,
                    measuredMbps = 0f,
                    testedViaTunnel = false,
                    tunnelStatus = "Xray-core ไม่สามารถเริ่มอุโมงค์ได้"
                )
            }

            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", httpPort))
            val mbps = measureProxiedDownloadMbps(proxy)
            TunnelProbeOutcome(
                tcpPingMs = tcpPingMs,
                measuredMbps = mbps,
                testedViaTunnel = true,
                tunnelStatus = if (mbps > 0f) "ผ่านอุโมงค์ ${server.protocol.displayName} จริง" else "เปิด Xray ได้แต่โหลดผ่านอุโมงค์ล้มเหลว"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            TunnelProbeOutcome(
                tcpPingMs = tcpPingMs,
                measuredMbps = 0f,
                testedViaTunnel = false,
                tunnelStatus = "เปิดอุโมงค์ Xray ไม่สำเร็จ: ${e.message?.take(48) ?: e.javaClass.simpleName}"
            )
        } finally {
            runCatching {
                if (point?.isRunning == true) {
                    point.stopLoop()
                }
            }
        }
    }

    /**
     * Strictly requires an explicit non-null [Proxy] (SOCKS or HTTP) — never falls back to direct internet.
     */
    fun measureProxiedDownloadMbps(proxy: Proxy): Float {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(PROBE_URL)
            conn = (url.openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("User-Agent", "TH-VPN-TunnelProbe/1.0")
            }
            if (conn.responseCode !in 200..299) {
                return 0f
            }
            val startNs = System.nanoTime()
            var totalBytes = 0L
            val buf = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    totalBytes += read
                }
            }
            if (totalBytes <= 0L) return 0f
            val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
            val rawMbps = ((totalBytes * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
            ((rawMbps * 100f).toInt() / 100f)
        } catch (_: Exception) {
            0f
        } finally {
            conn?.disconnect()
        }
    }

    private fun allocateFreePort(exclude: Int = -1): Int {
        repeat(5) {
            try {
                ServerSocket(0).use { socket ->
                    val port = socket.localPort
                    if (port > 0 && port != exclude) return port
                }
            } catch (_: Exception) {
            }
        }
        return -1
    }
}
