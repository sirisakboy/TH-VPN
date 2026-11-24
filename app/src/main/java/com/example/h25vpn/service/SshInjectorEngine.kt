package com.example.h25vpn.service

import android.net.VpnService
import com.example.h25vpn.data.CarrierTweak
import com.example.h25vpn.data.VpnServer
import com.trilead.ssh2.Connection
import com.trilead.ssh2.DynamicPortForwarder
import com.trilead.ssh2.ProxyData
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Real SSH2 + SSL/TLS SNI + HTTP/WebSocket Payload Injector tunnel engine.
 * Protects every outbound TCP socket via active [VpnService.protect] so traffic never loops!
 */
class SshInjectorEngine(
    private val protectSocket: (Socket) -> Boolean,
    private val onLog: (String) -> Unit
) {
    constructor(vpnService: VpnService, onLog: (String) -> Unit) : this(
        protectSocket = { sock -> vpnService.protect(sock) },
        onLog = onLog
    )
    @Volatile
    private var sshConnection: Connection? = null
    private var portForwarder: DynamicPortForwarder? = null

    fun connectAndStartSocks(
        server: VpnServer,
        tweak: CarrierTweak,
        localSocksPort: Int = 10808
    ): Boolean {
        stop()
        val targetHost = server.host
        val targetPort = if (tweak.overridePort > 0) tweak.overridePort else server.port

        val conn = Connection(targetHost, targetPort)
        val proxyData = VpnProtectedInjectorProxy(
            protectSocket = protectSocket,
            server = server,
            tweak = tweak,
            onLog = onLog
        )
        conn.setProxyData(proxyData)
        conn.setTCPNoDelay(true)

        onLog("Connecting SSH transport to $targetHost:$targetPort...")
        conn.connect(
            { _, _, _, _ -> true },
            12_000,
            15_000
        )

        val user = server.username.ifBlank { "vpn" }
        val pass = server.password
        onLog("Authenticating SSH user '$user'...")
        val authenticated = conn.authenticateWithPassword(user, pass)
        if (!authenticated) {
            conn.close()
            throw IOException("SSH authentication rejected for user '$user'")
        }

        onLog("Opening local SOCKS5 dynamic port forwarder on 127.0.0.1:$localSocksPort")
        val dpf = conn.createDynamicPortForwarder(InetSocketAddress("127.0.0.1", localSocksPort))
        sshConnection = conn
        portForwarder = dpf
        return true
    }

    fun stop() {
        try {
            portForwarder?.close()
        } catch (_: Exception) {
        }
        portForwarder = null

        try {
            sshConnection?.close()
        } catch (_: Exception) {
        }
        sshConnection = null
    }

    private class VpnProtectedInjectorProxy(
        private val protectSocket: (Socket) -> Boolean,
        private val server: VpnServer,
        private val tweak: CarrierTweak,
        private val onLog: (String) -> Unit
    ) : ProxyData {
        private var rawSocket: Socket? = null
        private var wrappedSocket: Socket? = null

        override fun openConnection(
            hostname: String,
            port: Int,
            connectTimeout: Int,
            readTimeout: Int
        ): Socket {
            val connectHost = if (!tweak.isDirectNoTweak && tweak.proxyHost.isNotBlank()) {
                tweak.proxyHost
            } else {
                hostname
            }
            val connectPort = if (!tweak.isDirectNoTweak && tweak.proxyHost.isNotBlank() && tweak.proxyPort > 0) {
                tweak.proxyPort
            } else {
                port
            }

            val tcpSocket = Socket()
            if (!protectSocket(tcpSocket)) {
                onLog("Warning: protectSocket(socket) returned false")
            }
            val resolved = InetAddress.getByName(connectHost)
            tcpSocket.connect(InetSocketAddress(resolved, connectPort), connectTimeout)
            tcpSocket.soTimeout = readTimeout
            rawSocket = tcpSocket

            val useTlsSni = server.protocol == com.example.h25vpn.data.TunnelProtocol.SSH_SSL ||
                port == 443 ||
                (!tweak.isDirectNoTweak && tweak.protocol == com.example.h25vpn.data.TunnelProtocol.SSH_SSL)

            val activeSocket: Socket = if (useTlsSni) {
                val sniHost = if (!tweak.isDirectNoTweak && tweak.bugHostSni.isNotBlank()) {
                    tweak.bugHostSni
                } else {
                    server.sni.ifBlank { hostname }
                }
                onLog("Performing TLS/SSL handshake with SNI: $sniHost")
                upgradeToTlsWithSni(tcpSocket, sniHost)
            } else {
                tcpSocket
            }

            // Send HTTP / WebSocket Payload Injection if configured
            if (!tweak.isDirectNoTweak && tweak.customPayload.isNotBlank() && !useTlsSni) {
                val formattedPayload = formatInjectorPayload(
                    template = tweak.customPayload,
                    targetHost = hostname,
                    targetPort = port,
                    bugHost = tweak.wsHostHeader.ifBlank { tweak.bugHostSni.ifBlank { hostname } }
                )
                onLog("Injecting carrier HTTP/WS payload (${formattedPayload.length} bytes)")
                val out: OutputStream = activeSocket.getOutputStream()
                out.write(formattedPayload.toByteArray(Charsets.ISO_8859_1))
                out.flush()

                // Read HTTP 101 / 200 response header before SSH banner if server acts as HTTP/WS proxy
                consumeHttpUpgradeIfPresent(activeSocket.getInputStream())
            }

            wrappedSocket = activeSocket
            return activeSocket
        }

        private fun upgradeToTlsWithSni(plainSocket: Socket, sniHost: String): SSLSocket {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())
            val sslSocket = sslContext.socketFactory.createSocket(
                plainSocket,
                sniHost,
                plainSocket.port,
                true
            ) as SSLSocket

            val params: SSLParameters = sslSocket.sslParameters
            if (sniHost.isNotBlank()) {
                params.serverNames = listOf(SNIHostName(sniHost))
            }
            sslSocket.sslParameters = params
            sslSocket.enabledProtocols = sslSocket.supportedProtocols.filter {
                it.equals("TLSv1.2", true) || it.equals("TLSv1.3", true)
            }.toTypedArray()
            sslSocket.startHandshake()
            return sslSocket
        }

        private fun formatInjectorPayload(
            template: String,
            targetHost: String,
            targetPort: Int,
            bugHost: String
        ): String {
            return template
                .replace("[host_port]", "$targetHost:$targetPort", ignoreCase = true)
                .replace("[host]", targetHost, ignoreCase = true)
                .replace("[port]", targetPort.toString(), ignoreCase = true)
                .replace("[sni]", bugHost, ignoreCase = true)
                .replace("[bughost]", bugHost, ignoreCase = true)
                .replace("[protocol]", "HTTP/1.1", ignoreCase = true)
                .replace("[ua]", "Mozilla/5.0 (Android 14; Mobile)", ignoreCase = true)
                .replace("[crlf]", "\r\n", ignoreCase = true)
                .replace("[lf]", "\n", ignoreCase = true)
                .replace("\\r\\n", "\r\n")
        }

        private fun consumeHttpUpgradeIfPresent(input: InputStream) {
            input.mark(4)
            // Peek first byte: if 'H' ("HTTP/1.1 101..." or "HTTP/1.1 200..."), consume until \r\n\r\n.
            // If 'S' ("SSH-2.0-..."), we don't want to swallow the SSH banner!
            // Note: SSLSocket/Socket InputStream may not support mark(), so only read HTTP header if payload used Upgrade/CONNECT.
            val sb = StringBuilder()
            val buf = ByteArray(1)
            var maxBytes = 1024
            while (maxBytes-- > 0) {
                val r = input.read(buf)
                if (r <= 0) break
                val ch = buf[0].toInt().toChar()
                sb.append(ch)
                if (sb.length == 4 && sb.toString() == "SSH-") {
                    // Server jumped straight to SSH banner; this shouldn't happen on pure WS port 80,
                    // so only WS/HTTP proxies return HTTP/1.1 101 Switching Protocols\r\n\r\n first.
                    break
                }
                if (sb.endsWith("\r\n\r\n")) {
                    val statusLine = sb.lineSequence().firstOrNull() ?: "HTTP Response"
                    onLog("Payload response: $statusLine")
                    break
                }
            }
        }

        override fun close() {
            try {
                wrappedSocket?.close()
            } catch (_: Exception) {
            }
            try {
                rawSocket?.close()
            } catch (_: Exception) {
            }
        }
    }
}
