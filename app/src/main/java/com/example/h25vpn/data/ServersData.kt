package com.example.h25vpn.data

object ServersData {
    /**
     * Initial bootstrap server list before live Free Cloud Auto-Sync completes.
     * Note: VpnRepository automatically fetches fresh working VLESS/VMess/Trojan/OpenVPN
     * nodes from live GitHub cloud feeds & VPNGate on app startup and whenever the user taps Sync.
     */
    val defaultServers = listOf(
        VpnServer(
            id = "th-ais-ws-80",
            name = "Thailand Free-Net WS #1 (Auto-BugHost)",
            country = "Thailand",
            countryCode = "th",
            city = "Bangkok",
            host = "45.141.27.2",
            port = 80,
            protocol = TunnelProtocol.V2RAY_VLESS,
            latencyMs = 28,
            loadPercent = 32,
            isVip = true,
            uuid = "1d829099-0b8d-4940-8b20-0745d8b8a427",
            security = "none",
            networkType = "ws",
            tlsMode = "",
            wsHost = "ais.speedtest.net",
            wsPath = "/ws",
            rawUri = "vless://1d829099-0b8d-4940-8b20-0745d8b8a427@45.141.27.2:80?encryption=none&security=none&type=ws&host=ais.speedtest.net&path=%2Fws#TH-Bangkok-WS",
            sourceTag = "TH Free-Net"
        ),
        VpnServer(
            id = "th-bkk-ws-2",
            name = "Thailand Free-Net WS #2 (Multi-SIM)",
            country = "Thailand",
            countryCode = "th",
            city = "Bangkok",
            host = "45.91.170.119",
            port = 80,
            protocol = TunnelProtocol.V2RAY_VLESS,
            latencyMs = 31,
            loadPercent = 29,
            isVip = true,
            uuid = "1d829099-0b8d-4940-8b20-0745d8b8a427",
            security = "none",
            networkType = "ws",
            tlsMode = "",
            wsHost = "ais.speedtest.net",
            wsPath = "/ws",
            rawUri = "vless://1d829099-0b8d-4940-8b20-0745d8b8a427@45.91.170.119:80?encryption=none&security=none&type=ws&host=ais.speedtest.net&path=%2Fws#TH-Bangkok-WS-2",
            sourceTag = "TH Free-Net"
        ),
        VpnServer(
            id = "th-bkk-ws-3",
            name = "Thailand Free-Net WS #3 (NoPro/Promo)",
            country = "Thailand",
            countryCode = "th",
            city = "Bangkok",
            host = "45.91.170.123",
            port = 80,
            protocol = TunnelProtocol.V2RAY_VLESS,
            latencyMs = 34,
            loadPercent = 35,
            isVip = true,
            uuid = "1d829099-0b8d-4940-8b20-0745d8b8a427",
            security = "none",
            networkType = "ws",
            tlsMode = "",
            wsHost = "ais.speedtest.net",
            wsPath = "/ws",
            rawUri = "vless://1d829099-0b8d-4940-8b20-0745d8b8a427@45.91.170.123:80?encryption=none&security=none&type=ws&host=ais.speedtest.net&path=%2Fws#TH-Bangkok-WS-3",
            sourceTag = "TH Free-Net"
        ),
        VpnServer(
            id = "th-bkk-ws-4",
            name = "Thailand Free-Net WS #4 (Direct/SNI)",
            country = "Thailand",
            countryCode = "th",
            city = "Bangkok",
            host = "45.141.27.38",
            port = 80,
            protocol = TunnelProtocol.V2RAY_VLESS,
            latencyMs = 36,
            loadPercent = 40,
            isVip = false,
            uuid = "1d829099-0b8d-4940-8b20-0745d8b8a427",
            security = "none",
            networkType = "ws",
            tlsMode = "",
            wsHost = "ais.speedtest.net",
            wsPath = "/ws",
            rawUri = "vless://1d829099-0b8d-4940-8b20-0745d8b8a427@45.141.27.38:80?encryption=none&security=none&type=ws&host=ais.speedtest.net&path=%2Fws#TH-Bangkok-WS-4",
            sourceTag = "TH Free-Net"
        )
    )
}
