package com.example.h25vpn.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.UUID

object VpnConfigParser {

    val DEFAULT_SUBSCRIPTION_URLS = listOf(
        "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Thailand.txt",
        "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Singapore.txt",
        "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Japan.txt",
        "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Hong_Kong.txt"
    )

    const val VPNGATE_CSV_URL = "http://www.vpngate.net/api/iphone/"

    /**
     * Parses mixed subscription text (plain lines or Base64-encoded subscription body,
     * single URI, or full `.ovpn` profile).
     */
    fun parseInputText(rawText: String, sourceTag: String = "Imported"): List<VpnServer> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Check if entire text is an OpenVPN .ovpn config
        if (trimmed.contains("client") && (trimmed.contains("<ca>") || trimmed.contains("remote "))) {
            parseOvpnConfig(trimmed, "Imported OpenVPN", "Unknown", "sg", sourceTag)?.let {
                return listOf(it)
            }
        }

        // Try decoding Base64 if it doesn't contain URI schemes directly
        val decodedText = if (!trimmed.contains("://") && !trimmed.contains("\n")) {
            decodeBase64Safe(trimmed).ifBlank { trimmed }
        } else if (!trimmed.contains("://")) {
            decodeBase64Safe(trimmed.replace("\n", "").replace("\r", "")).ifBlank { trimmed }
        } else {
            trimmed
        }

        val results = mutableListOf<VpnServer>()
        val seenKeys = HashSet<String>()

        decodedText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .forEach { line ->
                val parsed = when {
                    line.startsWith("vless://", ignoreCase = true) -> parseVlessUri(line, sourceTag)
                    line.startsWith("vmess://", ignoreCase = true) -> parseVmessUri(line, sourceTag)
                    line.startsWith("trojan://", ignoreCase = true) -> parseTrojanUri(line, sourceTag)
                    line.startsWith("ss://", ignoreCase = true) -> parseShadowsocksUri(line, sourceTag)
                    line.startsWith("ssh://", ignoreCase = true) -> parseSshUri(line, sourceTag)
                    else -> null
                }
                if (parsed != null && parsed.host.isNotBlank() && parsed.port in 1..65535) {
                    val dedupKey = "${parsed.protocol.name}:${parsed.host}:${parsed.port}:${parsed.wsPath}"
                    if (seenKeys.add(dedupKey)) {
                        results.add(parsed)
                    }
                }
            }

        return results
    }

    fun fetchCloudFreeServers(
        customSubUrls: List<String> = emptyList(),
        includeVpnGate: Boolean = true,
        maxPerSource: Int = 18
    ): List<VpnServer> {
        val allServers = mutableListOf<VpnServer>()
        val seenKeys = HashSet<String>()

        val urls = (customSubUrls + DEFAULT_SUBSCRIPTION_URLS).distinct()
        for (feedUrl in urls) {
            val countryHint = when {
                feedUrl.contains("Thailand", ignoreCase = true) -> "Thailand" to "th"
                feedUrl.contains("Singapore", ignoreCase = true) -> "Singapore" to "sg"
                feedUrl.contains("Japan", ignoreCase = true) -> "Japan" to "jp"
                feedUrl.contains("Hong_Kong", ignoreCase = true) -> "Hong Kong" to "hk"
                feedUrl.contains("Taiwan", ignoreCase = true) -> "Taiwan" to "tw"
                else -> null
            }

            val body = httpGetText(feedUrl, timeoutMs = 8000)
            if (body.isNotBlank()) {
                val parsedList = parseInputText(body, sourceTag = "Cloud Free")
                // Prefer WS / TLS / port 80 / 443 / 8080 / 2052 / 2082 / 8443 nodes for Thai Carrier SNI/Host bypass!
                val prioritized = parsedList.sortedByDescending { node ->
                    var score = 0
                    if (node.networkType == "ws") score += 50
                    if (node.tlsMode == "tls" || node.tlsMode == "reality") score += 30
                    if (node.port in listOf(80, 443, 8080, 8443, 2052, 2053, 2082, 2083, 2095, 2096)) score += 20
                    if (node.protocol == TunnelProtocol.V2RAY_VLESS) score += 15
                    score
                }.take(maxPerSource)

                for (srv in prioritized) {
                    val enriched = if (countryHint != null && srv.country == "Auto") {
                        srv.copy(
                            country = countryHint.first,
                            countryCode = countryHint.second,
                            city = countryHint.first
                        )
                    } else {
                        srv
                    }
                    val key = "${enriched.protocol}:${enriched.host}:${enriched.port}"
                    if (seenKeys.add(key)) {
                        allServers.add(enriched)
                    }
                }
            }
        }

        if (includeVpnGate) {
            val ovpnNodes = fetchVpnGateServers(maxCount = 8)
            for (node in ovpnNodes) {
                val key = "OVPN:${node.host}:${node.port}"
                if (seenKeys.add(key)) {
                    allServers.add(node)
                }
            }
        }

        return allServers
    }

    fun fetchVpnGateServers(maxCount: Int = 8): List<VpnServer> {
        val csv = httpGetText(VPNGATE_CSV_URL, timeoutMs = 9000)
        if (csv.isBlank()) return emptyList()

        val result = mutableListOf<VpnServer>()
        val lines = csv.lineSequence().drop(2).filter { !it.startsWith("*") && it.contains(",") }.toList()

        // Prefer Thailand, Japan, Korea, Singapore, Taiwan, VietNam
        val preferredCountries = setOf("TH", "SG", "JP", "KR", "TW", "VN", "HK", "US")

        for (line in lines) {
            val cols = line.split(",")
            if (cols.size < 15) continue
            val hostName = cols[0].trim()
            val ip = cols[1].trim()
            val pingStr = cols[3].trim()
            val countryLong = cols[5].trim()
            val countryShort = cols[6].trim().lowercase()
            val b64Ovpn = cols[14].trim()

            if (countryShort.uppercase() !in preferredCountries && result.size >= maxCount / 2) continue

            val ovpnText = decodeBase64Safe(b64Ovpn)
            if (ovpnText.isBlank() || !ovpnText.contains("remote ")) continue

            val parsed = parseOvpnConfig(
                ovpnContent = ovpnText,
                displayName = "VPNGate $hostName",
                country = countryLong.ifBlank { "Japan" },
                countryCode = mapSupportedFlag(countryShort),
                sourceTag = "VPNGate Free"
            )
            if (parsed != null) {
                val pingVal = pingStr.toIntOrNull() ?: -1
                result.add(parsed.copy(host = ip.ifBlank { parsed.host }, latencyMs = pingVal))
                if (result.size >= maxCount) break
            }
        }
        return result
    }

    fun parseOvpnConfig(
        ovpnContent: String,
        displayName: String,
        country: String,
        countryCode: String,
        sourceTag: String
    ): VpnServer? {
        var host = ""
        var port = 443
        var isUdp = false

        ovpnContent.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("remote ")) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) host = parts[1]
                if (parts.size >= 3) port = parts[2].toIntOrNull() ?: 443
            } else if (line.startsWith("proto ")) {
                isUdp = line.contains("udp", ignoreCase = true)
            }
        }
        if (host.isBlank()) return null

        return VpnServer(
            id = "ovpn-${UUID.nameUUIDFromBytes("$host:$port".toByteArray())}",
            name = displayName,
            country = country,
            countryCode = mapSupportedFlag(countryCode),
            city = country,
            host = host,
            port = port,
            protocol = if (isUdp) TunnelProtocol.OPENVPN_UDP else TunnelProtocol.OPENVPN_TCP,
            ovpnConfig = ovpnContent,
            sourceTag = sourceTag
        )
    }

    /**
     * Generates full Xray-core JSON config with automatic Thai Carrier Bug-Host / SNI injection!
     */
    fun buildXrayJsonConfig(
        server: VpnServer,
        tweak: CarrierTweak,
        socksPort: Int = 10808,
        httpPort: Int = 10809,
        dnsMode: DnsMode = DnsMode.CLOUDFLARE
    ): String {
        val useCarrierSpoof = !tweak.isDirectNoTweak &&
            (tweak.bugHostSni.isNotBlank() || tweak.wsHostHeader.isNotBlank())

        val spoofHost = tweak.wsHostHeader.ifBlank { tweak.bugHostSni }
        val spoofSni = tweak.bugHostSni.ifBlank { tweak.wsHostHeader }

        // Outbound protocol configuration
        val outbound = JSONObject()
        outbound.put("tag", "proxy")

        when (server.protocol) {
            TunnelProtocol.V2RAY_VLESS, TunnelProtocol.WEBSOCKET -> {
                outbound.put("protocol", "vless")
                val user = JSONObject().apply {
                    put("id", server.uuid.ifBlank { "1d829099-0b8d-4940-8b20-0745d8b8a427" })
                    put("encryption", "none")
                    put("level", 8)
                    if (server.flow.isNotBlank() && server.networkType == "tcp") {
                        put("flow", server.flow)
                    }
                }
                val vnext = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    put("users", JSONArray().put(user))
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            }

            TunnelProtocol.V2RAY_VMESS -> {
                outbound.put("protocol", "vmess")
                val user = JSONObject().apply {
                    put("id", server.uuid)
                    put("alterId", server.alterId)
                    put("security", server.security.ifBlank { "auto" })
                    put("level", 8)
                }
                val vnext = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    put("users", JSONArray().put(user))
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            }

            TunnelProtocol.V2RAY_TROJAN -> {
                outbound.put("protocol", "trojan")
                val srvObj = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    put("password", server.password.ifBlank { server.uuid })
                    put("level", 8)
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srvObj)))
            }

            TunnelProtocol.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                val srvObj = JSONObject().apply {
                    put("address", server.host)
                    put("port", server.port)
                    put("method", server.ssMethod.ifBlank { "chacha20-ietf-poly1305" })
                    put("password", server.password)
                    put("level", 8)
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(srvObj)))
            }

            else -> {
                outbound.put("protocol", "vless")
            }
        }

        // Stream Settings (Transport + Thai Carrier Bug Host / TLS SNI injection)
        val streamSettings = JSONObject()
        val netType = when (server.networkType.lowercase()) {
            "ws", "websocket" -> "ws"
            "grpc" -> "grpc"
            "h2", "http" -> "http"
            else -> "tcp"
        }
        streamSettings.put("network", netType)

        // Security (none / tls / reality)
        val secMode = server.tlsMode.lowercase()
        if (secMode == "reality") {
            streamSettings.put("security", "reality")
            // Note: REALITY cryptographically authenticates the server's real SNI, so keep server's SNI for REALITY
            val realityObj = JSONObject().apply {
                put("show", false)
                put("fingerprint", server.fingerprint.ifBlank { "chrome" })
                put("serverName", server.sni.ifBlank { spoofSni })
                put("publicKey", server.realityPubKey)
                put("shortId", server.realityShortId)
                put("spiderX", "/")
            }
            streamSettings.put("realitySettings", realityObj)
        } else if (secMode == "tls" || server.protocol == TunnelProtocol.V2RAY_TROJAN) {
            streamSettings.put("security", "tls")
            val effectiveSni = if (useCarrierSpoof && spoofSni.isNotBlank()) {
                spoofSni
            } else {
                server.sni.ifBlank { server.wsHost.ifBlank { server.host } }
            }
            val tlsObj = JSONObject().apply {
                put("allowInsecure", true) // Required when spoofing SNI to Thai carrier bug host!
                put("serverName", effectiveSni)
                put("fingerprint", server.fingerprint.ifBlank { "chrome" })
            }
            streamSettings.put("tlsSettings", tlsObj)
        } else {
            streamSettings.put("security", "none")
        }

        // Transport Specific Headers (Inject Thai Carrier Bug Host into WS / HTTP TCP headers!)
        when (netType) {
            "ws" -> {
                val effectiveWsHost = if (useCarrierSpoof && spoofHost.isNotBlank()) {
                    spoofHost
                } else {
                    server.wsHost.ifBlank { server.sni.ifBlank { server.host } }
                }
                val headers = JSONObject().apply {
                    put("Host", effectiveWsHost)
                    put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
                }
                val wsObj = JSONObject().apply {
                    put("path", server.wsPath.ifBlank { "/" })
                    put("headers", headers)
                }
                streamSettings.put("wsSettings", wsObj)
            }

            "grpc" -> {
                val grpcObj = JSONObject().apply {
                    put("serviceName", server.wsPath.removePrefix("/"))
                    put("multiMode", false)
                }
                streamSettings.put("grpcSettings", grpcObj)
            }

            "tcp" -> {
                // If user is on port 80 / non-TLS TCP and has a Thai Carrier Bug Host selected,
                // wrap HTTP header host if server expects HTTP obfs, otherwise leave raw TCP for TLS/REALITY
                if (secMode.isBlank() && server.security == "http" && spoofHost.isNotBlank()) {
                    val reqHeaders = JSONObject().apply {
                        put("Host", JSONArray().put(spoofHost))
                        put("Connection", JSONArray().put("keep-alive"))
                    }
                    val headerObj = JSONObject().apply {
                        put("type", "http")
                        put("request", JSONObject().apply {
                            put("version", "1.1")
                            put("method", "GET")
                            put("path", JSONArray().put("/"))
                            put("headers", reqHeaders)
                        })
                    }
                    streamSettings.put("tcpSettings", JSONObject().put("header", headerObj))
                }
            }
        }

        outbound.put("streamSettings", streamSettings)
        outbound.put("mux", JSONObject().put("enabled", false))

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().put("domainStrategy", "UseIP"))
        }

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        // Inbounds: Local SOCKS5 (with UDP) + Local HTTP Proxy
        val socksInbound = JSONObject().apply {
            put("tag", "socks")
            put("port", socksPort)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
                put("userLevel", 8)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls"))
            })
        }

        val httpInbound = JSONObject().apply {
            put("tag", "http")
            put("port", httpPort)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject().put("userLevel", 8))
        }

        val root = JSONObject().apply {
            put("stats", JSONObject())
            put("log", JSONObject().put("loglevel", "warning"))
            put("policy", JSONObject().apply {
                put("levels", JSONObject().put("8", JSONObject().apply {
                    put("handshake", 4)
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                }))
                put("system", JSONObject().apply {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                })
            })
            put("inbounds", JSONArray().put(socksInbound).put(httpInbound))
            put("outbounds", JSONArray().put(outbound).put(directOutbound).put(blockOutbound))
            put("dns", JSONObject().apply {
                put("servers", JSONArray().put(dnsMode.primary).put(dnsMode.secondary))
            })
            put("routing", JSONObject().apply {
                put("domainStrategy", "AsIs")
                put("rules", JSONArray().put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                }))
            })
        }

        return root.toString(2)
    }

    /**
     * Injects Thai Carrier HTTP/SNI headers into an OpenVPN `.ovpn` config when applicable.
     */
    fun applyCarrierTweakToOvpn(ovpnConfig: String, tweak: CarrierTweak): String {
        if (tweak.isDirectNoTweak || tweak.bugHostSni.isBlank()) return ovpnConfig
        val sb = StringBuilder(ovpnConfig)
        if (!ovpnConfig.contains("verify-x509-name") && tweak.bugHostSni.isNotBlank()) {
            sb.append("\n# TH-VPN Carrier Host Header Hint\n")
            sb.append("setenv UV_SNI ").append(tweak.bugHostSni).append("\n")
        }
        return sb.toString()
    }

    private fun parseVlessUri(uriStr: String, sourceTag: String): VpnServer? {
        return try {
            val clean = uriStr.trim()
            val hashIdx = clean.indexOf('#')
            val remark = if (hashIdx >= 0) decodeUrl(clean.substring(hashIdx + 1)) else "VLESS Node"
            val withoutFrag = if (hashIdx >= 0) clean.substring(0, hashIdx) else clean
            val parsedUri = URI(withoutFrag)
            val uuid = parsedUri.userInfo ?: return null
            val host = parsedUri.host?.removeSurrounding("[", "]") ?: return null
            val port = if (parsedUri.port > 0) parsedUri.port else 443
            val params = parseQueryParams(parsedUri.rawQuery ?: "")

            val netType = (params["type"] ?: "tcp").lowercase()
            val sec = (params["security"] ?: "none").lowercase()
            val sni = params["sni"] ?: params["peer"] ?: ""
            val wsHost = params["host"] ?: ""
            val wsPath = params["path"] ?: params["serviceName"] ?: "/"
            val pbk = params["pbk"] ?: ""
            val sid = params["sid"] ?: ""
            val fp = params["fp"] ?: "chrome"
            val flow = params["flow"] ?: ""

            val (country, code) = detectCountryFromRemark(remark, host)
            VpnServer(
                id = "vless-${UUID.nameUUIDFromBytes("$host:$port:$uuid:$wsPath".toByteArray())}",
                name = cleanDisplayName(remark, "VLESS $host"),
                country = country,
                countryCode = code,
                city = country,
                host = host,
                port = port,
                protocol = if (netType == "ws") TunnelProtocol.WEBSOCKET else TunnelProtocol.V2RAY_VLESS,
                uuid = uuid,
                security = params["headerType"] ?: "none",
                networkType = netType,
                tlsMode = if (sec == "none") "" else sec,
                sni = sni,
                wsHost = wsHost,
                wsPath = wsPath,
                realityPubKey = pbk,
                realityShortId = sid,
                fingerprint = fp,
                flow = flow,
                rawUri = clean,
                sourceTag = sourceTag
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVmessUri(uriStr: String, sourceTag: String): VpnServer? {
        return try {
            val b64 = uriStr.removePrefix("vmess://").removePrefix("VMESS://").trim()
            val jsonStr = decodeBase64Safe(b64)
            if (jsonStr.isBlank()) return null
            val obj = JSONObject(jsonStr)
            val host = obj.optString("add").trim()
            val port = obj.optString("port").toIntOrNull() ?: obj.optInt("port", 443)
            val uuid = obj.optString("id").trim()
            if (host.isBlank() || uuid.isBlank()) return null

            val remark = obj.optString("ps", "VMess $host")
            val netType = obj.optString("net", "tcp").lowercase()
            val tls = obj.optString("tls", "").lowercase()
            val sni = obj.optString("sni", "")
            val wsHost = obj.optString("host", "")
            val wsPath = obj.optString("path", "/")
            val aid = obj.optString("aid").toIntOrNull() ?: 0
            val scy = obj.optString("scy", "auto")
            val typeHdr = obj.optString("type", "none")

            val (country, code) = detectCountryFromRemark(remark, host)
            VpnServer(
                id = "vmess-${UUID.nameUUIDFromBytes("$host:$port:$uuid:$wsPath".toByteArray())}",
                name = cleanDisplayName(remark, "VMess $host"),
                country = country,
                countryCode = code,
                city = country,
                host = host,
                port = port,
                protocol = TunnelProtocol.V2RAY_VMESS,
                uuid = uuid,
                alterId = aid,
                security = if (typeHdr == "http") "http" else scy,
                networkType = netType,
                tlsMode = if (tls == "tls") "tls" else "",
                sni = sni,
                wsHost = wsHost,
                wsPath = wsPath,
                rawUri = uriStr.trim(),
                sourceTag = sourceTag
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTrojanUri(uriStr: String, sourceTag: String): VpnServer? {
        return try {
            val clean = uriStr.trim()
            val hashIdx = clean.indexOf('#')
            val remark = if (hashIdx >= 0) decodeUrl(clean.substring(hashIdx + 1)) else "Trojan Node"
            val withoutFrag = if (hashIdx >= 0) clean.substring(0, hashIdx) else clean
            val parsedUri = URI(withoutFrag)
            val password = decodeUrl(parsedUri.rawUserInfo ?: return null)
            val host = parsedUri.host?.removeSurrounding("[", "]") ?: return null
            val port = if (parsedUri.port > 0) parsedUri.port else 443
            val params = parseQueryParams(parsedUri.rawQuery ?: "")

            val netType = (params["type"] ?: "tcp").lowercase()
            val sec = (params["security"] ?: "tls").lowercase()
            val sni = params["sni"] ?: params["peer"] ?: host
            val wsHost = params["host"] ?: sni
            val wsPath = params["path"] ?: "/"

            val (country, code) = detectCountryFromRemark(remark, host)
            VpnServer(
                id = "trojan-${UUID.nameUUIDFromBytes("$host:$port:$password".toByteArray())}",
                name = cleanDisplayName(remark, "Trojan $host"),
                country = country,
                countryCode = code,
                city = country,
                host = host,
                port = port,
                protocol = TunnelProtocol.V2RAY_TROJAN,
                password = password,
                networkType = netType,
                tlsMode = if (sec == "none") "" else sec,
                sni = sni,
                wsHost = wsHost,
                wsPath = wsPath,
                rawUri = clean,
                sourceTag = sourceTag
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocksUri(uriStr: String, sourceTag: String): VpnServer? {
        return try {
            val clean = uriStr.trim()
            val hashIdx = clean.indexOf('#')
            val remark = if (hashIdx >= 0) decodeUrl(clean.substring(hashIdx + 1)) else "Shadowsocks"
            val body = (if (hashIdx >= 0) clean.substring(0, hashIdx) else clean)
                .removePrefix("ss://").removePrefix("SS://")
                .substringBefore("?")

            val method: String
            val password: String
            val host: String
            val port: Int

            if (body.contains("@")) {
                val userPart = body.substringBefore("@")
                val hostPart = body.substringAfter("@")
                val decodedUser = if (userPart.contains(":")) userPart else decodeBase64Safe(userPart)
                if (!decodedUser.contains(":")) return null
                method = decodedUser.substringBefore(":")
                password = decodedUser.substringAfter(":")
                host = hostPart.substringBeforeLast(":").removeSurrounding("[", "]")
                port = hostPart.substringAfterLast(":").toIntOrNull() ?: return null
            } else {
                val decoded = decodeBase64Safe(body)
                if (!decoded.contains("@") || !decoded.contains(":")) return null
                val userPart = decoded.substringBeforeLast("@")
                val hostPart = decoded.substringAfterLast("@")
                method = userPart.substringBefore(":")
                password = userPart.substringAfter(":")
                host = hostPart.substringBeforeLast(":")
                port = hostPart.substringAfterLast(":").toIntOrNull() ?: return null
            }

            val (country, code) = detectCountryFromRemark(remark, host)
            VpnServer(
                id = "ss-${UUID.nameUUIDFromBytes("$host:$port:$method".toByteArray())}",
                name = cleanDisplayName(remark, "SS $host"),
                country = country,
                countryCode = code,
                city = country,
                host = host,
                port = port,
                protocol = TunnelProtocol.SHADOWSOCKS,
                password = password,
                ssMethod = method,
                rawUri = clean,
                sourceTag = sourceTag
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `ssh://user:pass@host:port?mode=ssl#Remark`
     */
    private fun parseSshUri(uriStr: String, sourceTag: String): VpnServer? {
        return try {
            val clean = uriStr.trim()
            val hashIdx = clean.indexOf('#')
            val remark = if (hashIdx >= 0) decodeUrl(clean.substring(hashIdx + 1)) else "SSH Tunnel"
            val withoutFrag = if (hashIdx >= 0) clean.substring(0, hashIdx) else clean
            val parsed = URI(withoutFrag)
            val userInfo = parsed.rawUserInfo ?: ""
            val user = decodeUrl(userInfo.substringBefore(":"))
            val pass = if (userInfo.contains(":")) decodeUrl(userInfo.substringAfter(":")) else ""
            val host = parsed.host ?: return null
            val port = if (parsed.port > 0) parsed.port else 443
            val params = parseQueryParams(parsed.rawQuery ?: "")
            val isSsl = params["mode"]?.equals("ssl", ignoreCase = true) == true || port == 443

            val (country, code) = detectCountryFromRemark(remark, host)
            VpnServer(
                id = "ssh-${UUID.nameUUIDFromBytes("$host:$port:$user".toByteArray())}",
                name = cleanDisplayName(remark, "SSH $host"),
                country = country,
                countryCode = code,
                city = country,
                host = host,
                port = port,
                protocol = if (isSsl) TunnelProtocol.SSH_SSL else TunnelProtocol.SSH_DIRECT,
                username = user,
                password = pass,
                sni = params["sni"] ?: "",
                rawUri = clean,
                sourceTag = sourceTag
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun detectCountryFromRemark(remark: String, host: String): Pair<String, String> {
        val text = "$remark $host".uppercase()
        return when {
            text.contains("🇹🇭") || text.contains("THAILAND") || text.contains("BANGKOK") || Regex("\\bTH\\b").containsMatchIn(text) -> "Thailand" to "th"
            text.contains("🇸🇬") || text.contains("SINGAPORE") || Regex("\\bSG\\b").containsMatchIn(text) -> "Singapore" to "sg"
            text.contains("🇯🇵") || text.contains("JAPAN") || text.contains("TOKYO") || Regex("\\bJP\\b").containsMatchIn(text) -> "Japan" to "jp"
            text.contains("🇭🇰") || text.contains("HONG KONG") || text.contains("HONGKONG") || Regex("\\bHK\\b").containsMatchIn(text) -> "Hong Kong" to "hk"
            text.contains("🇹🇼") || text.contains("TAIWAN") || Regex("\\bTW\\b").containsMatchIn(text) -> "Taiwan" to "sg"
            text.contains("🇰🇷") || text.contains("KOREA") || Regex("\\bKR\\b").containsMatchIn(text) -> "South Korea" to "jp"
            text.contains("🇺🇸") || text.contains("UNITED STATES") || text.contains("USA") || Regex("\\bUS\\b").containsMatchIn(text) -> "United States" to "us"
            text.contains("🇬🇧") || text.contains("UNITED KINGDOM") || Regex("\\bUK\\b").containsMatchIn(text) -> "United Kingdom" to "uk"
            text.contains("🇩🇪") || text.contains("GERMANY") || Regex("\\bDE\\b").containsMatchIn(text) -> "Germany" to "de"
            text.contains("🇳🇱") || text.contains("NETHERLANDS") || Regex("\\bNL\\b").containsMatchIn(text) -> "Netherlands" to "nl"
            text.contains("🇮🇳") || text.contains("INDIA") || Regex("\\bIN\\b").containsMatchIn(text) -> "India" to "in"
            text.contains("🇮🇩") || text.contains("INDONESIA") || Regex("\\bID\\b").containsMatchIn(text) -> "Indonesia" to "id"
            text.contains("🇵🇭") || text.contains("PHILIPPINES") || Regex("\\bPH\\b").containsMatchIn(text) -> "Philippines" to "ph"
            else -> "Auto" to "sg"
        }
    }

    private fun mapSupportedFlag(code: String): String {
        val lower = code.lowercase()
        val available = setOf("de", "hk", "id", "in", "jp", "nl", "ph", "sg", "th", "uk", "us")
        return if (lower in available) lower else "sg"
    }

    private fun cleanDisplayName(raw: String, fallback: String): String {
        val cleaned = raw
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("@[A-Za-z0-9_]+"), "")
            .trim()
        return if (cleaned.length >= 2) cleaned.take(44) else fallback
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else decodeUrl(pair.substring(0, idx)) to decodeUrl(pair.substring(idx + 1))
        }.toMap()
    }

    private fun decodeUrl(s: String): String {
        return try {
            URLDecoder.decode(s, "UTF-8")
        } catch (_: Exception) {
            s
        }
    }

    private fun decodeBase64Safe(input: String): String {
        val sanitized = input.trim().replace('-', '+').replace('_', '/')
        val padded = when (sanitized.length % 4) {
            2 -> "$sanitized=="
            3 -> "$sanitized="
            else -> sanitized
        }
        return try {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun httpGetText(urlString: String, timeoutMs: Int): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "TH-VPN/1.0 (Android; FreeNet)")
            }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        } finally {
            conn?.disconnect()
        }
    }
}
