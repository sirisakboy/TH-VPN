package com.example.h25vpn.data

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** A separate, closed schema: do not substitute raw logs or serialized VPN configs. */
object ReportPayload {
    private val events = setOf("connect_requested", "disconnect_requested", "tunnel_connected",
        "route_attempt_started", "route_probe_passed", "route_probe_slow", "route_attempt_failed",
        "scan_exhausted", "external_vpn_probe_started", "external_vpn_probe_passed",
        "external_vpn_probe_slow", "external_vpn_probe_failed")

    fun create(event: String, server: VpnServer?, mbps: Double?, salt: String, time: Long, api: Int): JSONObject {
        require(event in events) { "Unknown report event" }
        return JSONObject().apply {
            put("schema", 1)
            put("reportId", UUID.randomUUID().toString())
            put("timeUtcMillis", time)
            put("event", event)
            put("androidApi", api)
            put("thresholdMbps", 1)
            if (mbps != null && mbps.isFinite() && mbps >= 0) put("downloadMbps", mbps)
            if (server != null) {
                val digest = MessageDigest.getInstance("SHA-256").digest((salt + TunnelRoutes.identity(server)).toByteArray())
                put("routeFingerprint", digest.joinToString("") { "%02x".format(it.toInt() and 255) })
                put("protocol", server.protocol.name)
                put("port", server.port)
                put("network", server.networkType.takeIf { it in setOf("tcp", "ws", "grpc", "http", "h2", "httpupgrade", "splithttp") } ?: "other")
                put("tls", server.tlsMode.takeIf { it in setOf("", "tls", "reality") } ?: "other")
                put("tcpPingMs", server.latencyMs)
            }
        }
    }
}
