package com.example.h25vpn.data

/** Names are display labels, never tunnel identities. */
object TunnelRoutes {
    const val MIN_BYTES_PER_SECOND = 125_000L

    fun identity(s: VpnServer): String = listOf(
        s.protocol.name, s.host, s.port.toString(), s.uuid, s.username, s.password,
        s.alterId.toString(), s.security, s.networkType, s.tlsMode, s.sni,
        s.wsHost, s.wsPath, s.realityPubKey, s.realityShortId, s.fingerprint,
        s.flow, s.ssMethod, s.ovpnConfig
    ).joinToString("\u0000")

    fun candidates(selected: VpnServer, servers: List<VpnServer>): List<VpnServer> =
        (listOf(selected) + servers.filter {
            it.name.trim().equals(selected.name.trim(), ignoreCase = true)
        }).distinctBy(::identity)

    fun passes(bytes: Long, elapsedNanos: Long): Boolean =
        bytes >= 1024L * 1024 && elapsedNanos > 0 &&
            bytes.toDouble() * 1_000_000_000 / elapsedNanos > MIN_BYTES_PER_SECOND
}
