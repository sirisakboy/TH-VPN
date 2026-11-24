package com.example.h25vpn.data

import org.junit.Assert.*
import org.junit.Test

class TunnelRoutesTest {
    private val selected = VpnServer("1", "Shared profile", "Thailand", "th", "Bangkok", "example.com", 443,
        uuid = "account-a", networkType = "ws", tlsMode = "tls", wsPath = "/a")

    @Test fun keepsDistinctPathsAndCredentialsWithSameName() {
        val path = selected.copy(id = "2", wsPath = "/b")
        val account = selected.copy(id = "3", uuid = "account-b")
        val tls = selected.copy(id = "4", sni = "other.example.com")
        val host = selected.copy(id = "5", wsHost = "route.example.com")
        val reality = selected.copy(id = "6", realityPubKey = "different")
        assertEquals(listOf(selected, path, account, tls, host, reality),
            TunnelRoutes.candidates(selected, listOf(path, account, tls, host, reality)))
    }

    @Test fun removesExactRouteDuplicatesButNeverGroupsUnrelatedNames() {
        val duplicate = selected.copy(id = "other", name = " SHARED PROFILE ")
        val unrelated = selected.copy(id = "unrelated", name = "Different profile", wsPath = "/b")
        assertEquals(listOf(selected), TunnelRoutes.candidates(selected, listOf(duplicate, unrelated)))
    }

    @Test fun canTryDifferentProtocolsUnderSameProfileName() {
        val ssh = selected.copy(id = "ssh", protocol = TunnelProtocol.SSH_SSL)
        assertEquals(listOf(selected, ssh), TunnelRoutes.candidates(selected, listOf(ssh)))
    }

    @Test fun identityIncludesRoutingAndSecurityNotDisplayMetadata() {
        assertEquals(TunnelRoutes.identity(selected), TunnelRoutes.identity(selected.copy(id = "new", name = "Renamed", latencyMs = 10)))
        assertNotEquals(TunnelRoutes.identity(selected), TunnelRoutes.identity(selected.copy(flow = "different")))
        assertNotEquals(TunnelRoutes.identity(selected), TunnelRoutes.identity(selected.copy(port = 8443)))
    }

    @Test fun usesStrictOneMegabitThresholdNotMegabyte() {
        assertFalse(TunnelRoutes.passes(1_048_576, 8_388_608_000)) // exactly 1 Mbps
        assertTrue(TunnelRoutes.passes(1_048_576, 8_000_000_000)) // 1.048576 Mbps
        assertFalse(TunnelRoutes.passes(1_048_576, 9_000_000_000))
    }

    @Test fun rejectsEmptyShortAndInvalidMeasurements() {
        assertFalse(TunnelRoutes.passes(0, 1))
        assertFalse(TunnelRoutes.passes(1024, 1))
        assertFalse(TunnelRoutes.passes(1_048_576, 0))
        assertFalse(TunnelRoutes.passes(1_048_576, -1))
    }
}
