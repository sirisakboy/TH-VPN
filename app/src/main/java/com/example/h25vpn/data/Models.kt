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
    V2RAY_VLESS("VLESS / Reality", 0xFF00E5FF),
    V2RAY_VMESS("V2Ray VMess", 0xFFEC4899),
    V2RAY_TROJAN("Trojan TLS", 0xFF8B5CF6),
    SHADOWSOCKS("Shadowsocks", 0xFF3B82F6),
    SSH_DIRECT("SSH Direct / Payload", 0xFF14B8A6),
    SSH_SSL("SSH SSL / SNI", 0xFF10B981),
    OPENVPN_TCP("OpenVPN TCP", 0xFFF59E0B),
    OPENVPN_UDP("OpenVPN UDP", 0xFFA855F7),
    WEBSOCKET("WS + TLS SNI", 0xFF06B6D4)
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
    val protocol: TunnelProtocol = TunnelProtocol.V2RAY_VLESS,
    var latencyMs: Int = -1,
    val loadPercent: Int = 25,
    val isVip: Boolean = false,
    // Real Engine Connection Fields
    val username: String = "",
    val password: String = "",
    val uuid: String = "",
    val alterId: Int = 0,
    val security: String = "auto",
    val networkType: String = "tcp", // tcp, ws, grpc, httpupgrade, splithttp
    val tlsMode: String = "", // "", "tls", "reality"
    val sni: String = "",
    val wsHost: String = "",
    val wsPath: String = "/",
    val realityPubKey: String = "",
    val realityShortId: String = "",
    val fingerprint: String = "chrome",
    val flow: String = "",
    val ssMethod: String = "chacha20-ietf-poly1305",
    val ovpnConfig: String = "",
    val rawUri: String = "",
    val sourceTag: String = "Built-in" // Built-in, Cloud Free, VPNGate, Custom
)

@Serializable
data class CarrierTweak(
    val id: String,
    val name: String,
    val carrier: String,
    val description: String,
    val promoHint: String = "",
    val ussdCode: String = "",
    val protocol: TunnelProtocol = TunnelProtocol.WEBSOCKET,
    val bugHostSni: String = "",
    val wsHostHeader: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val customPayload: String = "",
    val overridePort: Int = 0,
    val isDirectNoTweak: Boolean = false,
    val isCustom: Boolean = false
)

data class SimPromoItem(
    val id: String,
    val carrier: String,
    val title: String,
    val priceText: String,
    val speedText: String,
    val ussdCode: String = "",
    val steps: List<String>,
    val note: String = "",
    val tweakIdToApply: String = "",
    val externalUrl: String = ""
)

data class CarrierConfigLink(
    val id: String,
    val carrier: String,
    val title: String,
    val subtitle: String,
    val appRequired: String,
    val platform: String, // Android, iOS
    val downloadUrl: String,
    val pageUrl: String,
    val matchingTweakId: String = ""
)

data class NetworkStats(
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val pingMs: Int = 0,
    val realIp: String = "Checking...",
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
    CUSTOM("Custom DNS (1.1.1.1)", "1.1.1.1", "8.8.8.8")
}

data class ProxyShareState(
    val isRunning: Boolean = false,
    val localIp: String = "192.168.43.1",
    val port: Int = 8080,
    val activeConnections: Int = 0
)

@Serializable
data class SpeedTestSummary(
    val downloadMbps: Float = 0f,
    val uploadMbps: Float = 0f,
    val pingMs: Int = 0,
    val testedAt: String = ""
)

data class AppItem(
    val packageName: String,
    val appName: String,
    val isBypassed: Boolean = false
)
