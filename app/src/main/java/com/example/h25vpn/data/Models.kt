package com.example.h25vpn.data

import kotlinx.serialization.Serializable

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

enum class TunnelProtocol(val displayName: String, val badgeColor: Long) {
    SSH_DIRECT("SSH Direct", 0xFF00E5FF),
    SSH_SSL("SSH SSL/TLS", 0xFF10B981),
    OPENVPN_TCP("OpenVPN TCP", 0xFFF59E0B),
    OPENVPN_UDP("OpenVPN UDP", 0xFF8B5CF6),
    WEBSOCKET("WebSocket", 0xFF3B82F6),
    V2RAY("V2Ray VMess", 0xFFEC4899)
}

@Serializable
data class VpnServer(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val host: String,
    val port: Int,
    val protocol: TunnelProtocol = TunnelProtocol.SSH_SSL,
    var latencyMs: Int = 35,
    val loadPercent: Int = 30,
    val isVip: Boolean = false
)

@Serializable
data class CarrierTweak(
    val id: String,
    val name: String,
    val carrier: String,
    val description: String,
    val protocol: TunnelProtocol = TunnelProtocol.SSH_SSL,
    val bugHostSni: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val customPayload: String = "",
    val isCustom: Boolean = false
)

data class NetworkStats(
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val pingMs: Int = 0,
    val realIp: String = "182.232.14.92",
    val vpnIp: String = "10.8.0.2"
)

enum class LogLevel {
    INFO,
    WARN,
    SUCCESS,
    ERROR
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class DnsMode(val title: String, val primary: String, val secondary: String) {
    CLOUDFLARE("Cloudflare (1.1.1.1)", "1.1.1.1", "1.0.0.1"),
    GOOGLE("Google (8.8.8.8)", "8.8.8.8", "8.8.4.4"),
    ADGUARD("AdGuard AdBlock", "94.140.14.14", "94.140.15.15"),
    QUAD9("Quad9 Secure", "9.9.9.9", "149.112.112.112"),
    CUSTOM("Custom DNS", "", "")
}

data class ProxyShareState(
    val isRunning: Boolean = false,
    val localIp: String = "192.168.43.1",
    val port: Int = 8080,
    val activeConnections: Int = 0
)

data class AppItem(
    val packageName: String,
    val appName: String,
    val isBypassed: Boolean = false
)
