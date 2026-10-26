package com.example.h25vpn.data

object TweaksData {
    val defaultTweaks = listOf(
        CarrierTweak(
            id = "direct-ssl",
            name = "Direct SSL / Cloudflare SNI",
            carrier = "Direct",
            description = "High-speed encrypted TLS direct tunnel, works globally",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "cloudflare.com"
        ),
        CarrierTweak(
            id = "ais-5g",
            name = "AIS Unlimited 5G / Edu Bypass",
            carrier = "AIS",
            description = "Optimized SNI bug host for AIS Thailand network",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "m.ais.co.th"
        ),
        CarrierTweak(
            id = "ais-play",
            name = "AIS Play & Disney+ Unlock",
            carrier = "AIS",
            description = "Zero-rated streaming packet injection for AIS",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "aisplay.ais.co.th"
        ),
        CarrierTweak(
            id = "dtac-free",
            name = "DTAC Free Social / Line Tweak",
            carrier = "DTAC",
            description = "SSL Payload bypass for DTAC unlimited messaging",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "line.me"
        ),
        CarrierTweak(
            id = "dtac-udp",
            name = "DTAC Fast Gaming UDP",
            carrier = "DTAC",
            description = "Direct UDP OpenVPN protocol for lowest ping",
            protocol = TunnelProtocol.OPENVPN_UDP,
            bugHostSni = "dtac.co.th"
        ),
        CarrierTweak(
            id = "true-id",
            name = "TrueMove H TrueID Bypass",
            carrier = "TrueMove",
            description = "Injects TrueID SNI headers for free streaming bandwidth",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "trueid.net"
        ),
        CarrierTweak(
            id = "true-5g",
            name = "TrueMove 5G Max Speed",
            carrier = "TrueMove",
            description = "WebSocket CDN reverse proxy for TrueMove users",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "service.truemoveh.com"
        ),
        CarrierTweak(
            id = "smart-giga",
            name = "Smart Giga Video / Stories",
            carrier = "Smart",
            description = "Consumes Giga Video/Stories promo balance first",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "v.whatsapp.net"
        ),
        CarrierTweak(
            id = "globe-tm",
            name = "Globe & TM FunALIW / No Load",
            carrier = "Globe",
            description = "Payload header injector for Globe Telecom",
            protocol = TunnelProtocol.SSH_DIRECT,
            bugHostSni = "m.facebook.com",
            customPayload = "CONNECT [host_port] [protocol][crlf]Host: m.facebook.com[crlf]X-Online-Host: m.facebook.com[crlf]Connection: Keep-Alive[crlf][crlf]"
        ),
        CarrierTweak(
            id = "tnt-tiktok",
            name = "TNT Free TikTok 2025",
            carrier = "TNT",
            description = "TikTok SNI payload with SSL TLS 1.3 handshake",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "m.tiktok.com"
        ),
        CarrierTweak(
            id = "stream-netflix",
            name = "Streaming Unlock (Netflix & YouTube)",
            carrier = "Streaming",
            description = "Dedicated residential CDN routing for video streaming",
            protocol = TunnelProtocol.OPENVPN_TCP,
            bugHostSni = "fast.netflix.com"
        ),
        CarrierTweak(
            id = "gaming-udp",
            name = "Mobile Legends / PUBG Gaming UDP",
            carrier = "Gaming",
            description = "Ultra low latency tunnel with packet loss protection",
            protocol = TunnelProtocol.OPENVPN_UDP,
            bugHostSni = "game.garena.sg"
        )
    )
}
