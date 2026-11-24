package com.example.h25vpn.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionRoutesTest {
    private val base = "vless://11111111-1111-1111-1111-111111111111@example.com:443?type=ws&security=tls&host=cdn.example.com&sni=cdn.example.com&path="

    @Test fun sameDisplayNameDoesNotDropDistinctTunnelRoutes() {
        val text = listOf(base + "%2Fa#Same", base + "%2Fb#Same",
            base.replace("sni=cdn.example.com", "sni=other.example.com") + "%2Fa#Same",
            base + "%2Fa#Renamed").joinToString("\n")
        val parsed = VpnConfigParser.parseInputText(text)
        assertEquals(3, parsed.size)
        assertEquals(3, parsed.map { it.id }.distinct().size)
        assertEquals(setOf("/a", "/b"), parsed.map { it.wsPath }.toSet())
    }

    @Test fun percentEncodedWebsocketPathIsDecodedExactlyOnce() {
        val server = VpnConfigParser.parseInputText(base + "%2Fws%3Fed%3D2048#Same").single()
        assertEquals("/ws?ed=2048", server.wsPath)
        val direct = CarrierTweak("direct", "Direct", "All", "", isDirectNoTweak = true)
        val root = JSONObject(VpnConfigParser.buildXrayJsonConfig(server, direct))
        val ws = root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings").getJSONObject("wsSettings")
        assertEquals("/ws?ed=2048", ws.getString("path"))
    }

    @Test fun externalHtmlErrorPageIsNotAConfig() {
        assertTrue(VpnConfigParser.parseInputText("<html>Service unavailable https://example.com</html>").isEmpty())
    }
}
