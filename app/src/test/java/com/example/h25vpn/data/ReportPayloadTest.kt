package com.example.h25vpn.data

import org.junit.Assert.*
import org.junit.Test

class ReportPayloadTest {
    private val server = VpnServer("secret-id", "secret-name", "secret-country", "th", "secret-city",
        "secret-host", 443, uuid = "secret-uuid", username = "secret-user", password = "secret-password",
        wsHost = "secret-wshost", wsPath = "/secret-path", sni = "secret-sni", rawUri = "secret-uri",
        ovpnConfig = "secret-config", sourceTag = "secret-source")

    @Test fun emitsOnlyAllowlistedFieldsAndNoSecrets() {
        val payload = ReportPayload.create("route_probe_passed", server, 1.5, "private-salt", 1234, 36)
        assertEquals(setOf("schema", "reportId", "timeUtcMillis", "event", "androidApi", "thresholdMbps",
            "downloadMbps", "routeFingerprint", "protocol", "port", "network", "tls", "tcpPingMs"), payload.keySet())
        assertFalse(payload.toString().contains("secret"))
        assertFalse(payload.toString().contains("private-salt"))
        assertEquals(64, payload.getString("routeFingerprint").length)
    }

    @Test fun fingerprintsDistinguishRoutesAndInstallations() {
        fun hash(s: VpnServer, salt: String) = ReportPayload.create("route_attempt_started", s, null, salt, 1, 36).getString("routeFingerprint")
        assertEquals(hash(server, "a"), hash(server, "a"))
        assertNotEquals(hash(server, "a"), hash(server.copy(wsPath = "/different"), "a"))
        assertNotEquals(hash(server, "a"), hash(server, "b"))
    }

    @Test fun externalProbeNeverClaimsToKnowOtherAppsRoute() {
        val payload = ReportPayload.create("external_vpn_probe_passed", null, 2.0, "a", 1, 36)
        assertFalse(payload.has("routeFingerprint"))
        assertFalse(payload.has("protocol"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsArbitraryLogMessages() {
        ReportPayload.create("secret raw log message", server, null, "a", 1, 36)
    }

    @Test fun ignoresInvalidSpeedsAndFreeTextTransportFields() {
        val payload = ReportPayload.create("route_attempt_failed", server.copy(networkType = "secret-network", tlsMode = "secret-mode"), Double.NaN, "a", 1, 36)
        assertFalse(payload.has("downloadMbps"))
        assertEquals("other", payload.getString("network"))
        assertEquals("other", payload.getString("tls"))
    }
}
