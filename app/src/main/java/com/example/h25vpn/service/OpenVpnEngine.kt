package com.example.h25vpn.service

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.h25vpn.data.CarrierTweak
import com.example.h25vpn.data.DnsMode
import com.example.h25vpn.data.VpnConfigParser
import com.example.h25vpn.data.VpnServer
import net.openvpn.openvpn.ClientAPI_Config
import net.openvpn.openvpn.ClientAPI_EvalConfig
import net.openvpn.openvpn.ClientAPI_Event
import net.openvpn.openvpn.ClientAPI_LogInfo
import net.openvpn.openvpn.ClientAPI_OpenVPNClient
import net.openvpn.openvpn.ClientAPI_ProvideCreds
import net.openvpn.openvpn.ClientAPI_Status

/**
 * Real OpenVPN3 C++ Native Engine (`libovpncli.so`) subclassing [ClientAPI_OpenVPNClient].
 * Handles `.ovpn` profiles (VPNGate, custom imported `.ovpn`, TCP/UDP), TUN establishment,
 * Split Tunneling (`addDisallowedApplication`), and `VpnService.protect(fd)`.
 */
class OpenVpnEngine(
    private val vpnService: VpnService,
    private val splitTunnelEnabled: Boolean,
    private val bypassedPackages: Set<String>,
    private val dnsMode: DnsMode,
    private val onConnected: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onLog: (String) -> Unit
) : ClientAPI_OpenVPNClient() {

    private var workerThread: Thread? = null
    private var pendingBuilder: VpnService.Builder? = null
    private var tunPfd: ParcelFileDescriptor? = null
    @Volatile
    private var assignedVpnIp: String = "10.8.0.2"

    fun startOpenVpn(server: VpnServer, tweak: CarrierTweak) {
        if (server.ovpnConfig.isBlank()) {
            onError("Server does not contain an OpenVPN (.ovpn) profile.")
            return
        }

        val profileContent = VpnConfigParser.applyCarrierTweakToOvpn(server.ovpnConfig, tweak)
        val cfg = ClientAPI_Config().apply {
            content = profileContent
            guiVersion = "TH-VPN 1.0"
            connTimeout = 20
            tunPersist = false
            googleDnsFallback = true
        }

        val eval: ClientAPI_EvalConfig = eval_config(cfg)
        if (eval.error) {
            onError("OpenVPN config error: ${eval.message}")
            return
        }

        if (!eval.autologin && (server.username.isNotBlank() || server.password.isNotBlank())) {
            val creds = ClientAPI_ProvideCreds().apply {
                username = server.username
                password = server.password
                replacePasswordWithSessionID = true
            }
            val credStatus = provide_creds(creds)
            if (credStatus.error) {
                onError("OpenVPN credentials error: ${credStatus.message}")
                return
            }
        }

        workerThread = Thread({
            try {
                onLog("Starting OpenVPN3 native client (${server.host}:${server.port})...")
                val status: ClientAPI_Status = connect()
                if (status.error) {
                    onError("OpenVPN terminated: ${status.status} ${status.message}")
                }
            } catch (t: Throwable) {
                onError("OpenVPN runtime error: ${t.message}")
            } finally {
                closeTunPfd()
            }
        }, "OpenVPN3-Worker").apply {
            isDaemon = true
            start()
        }
    }

    fun stopOpenVpn() {
        try {
            stop()
        } catch (_: Throwable) {
        }
        closeTunPfd()
    }

    private fun closeTunPfd() {
        try {
            tunPfd?.close()
        } catch (_: Exception) {
        }
        tunPfd = null
    }

    // --- SWIG Director Callbacks from C++ libovpncli.so ---

    override fun socket_protect(socket: Int): Boolean {
        return vpnService.protect(socket)
    }

    override fun pause_on_connection_timeout(): Boolean = false

    override fun tun_builder_new(): Boolean {
        val builder = vpnService.Builder()
            .setSession("TH-VPN OpenVPN")
            .addDnsServer(dnsMode.primary)
            .addDnsServer(dnsMode.secondary)

        if (splitTunnelEnabled) {
            for (pkg in bypassedPackages) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: Exception) {
                }
            }
        }
        // Always prevent VPN from routing our own app if needed? Wait: socket_protect protects C++ sockets!
        pendingBuilder = builder
        return true
    }

    override fun tun_builder_add_address(
        address: String,
        prefix_length: Int,
        gateway: String?,
        ipv6: Boolean,
        net30: Boolean
    ): Boolean {
        return try {
            if (!ipv6 && address.isNotBlank()) {
                assignedVpnIp = address
            }
            pendingBuilder?.addAddress(address, prefix_length)
            true
        } catch (e: Exception) {
            onLog("tun_builder_add_address warning: ${e.message}")
            false
        }
    }

    override fun tun_builder_add_route(
        address: String,
        prefix_length: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        return try {
            pendingBuilder?.addRoute(address, prefix_length)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun tun_builder_reroute_gw(ipv4: Boolean, ipv6: Boolean, flags: Long): Boolean {
        return try {
            if (ipv4) pendingBuilder?.addRoute("0.0.0.0", 0)
            if (ipv6) pendingBuilder?.addRoute("::", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun tun_builder_add_dns_server(address: String, ipv6: Boolean): Boolean {
        return try {
            pendingBuilder?.addDnsServer(address)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun tun_builder_set_mtu(mtu: Int): Boolean {
        return try {
            pendingBuilder?.setMtu(mtu.coerceIn(1280, 1500))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun tun_builder_set_session_name(name: String): Boolean {
        pendingBuilder?.setSession("TH-VPN ($name)")
        return true
    }

    override fun tun_builder_establish(): Int {
        val builder = pendingBuilder ?: return -1
        val pfd = builder.establish() ?: return -1
        // detachFd() hands ownership of the raw fd to C++ OpenVPN3 engine!
        return pfd.detachFd()
    }

    override fun tun_builder_teardown(disconnect: Boolean) {
        closeTunPfd()
    }

    override fun event(ev: ClientAPI_Event) {
        val name = ev.name ?: ""
        val info = ev.info ?: ""
        onLog("OpenVPN Event [$name] $info")
        if (ev.error || name == "AUTH_FAILED" || name == "CONNECTION_TIMEOUT") {
            if (ev.fatal) {
                onError("OpenVPN error ($name): $info")
            }
        } else if (name == "CONNECTED") {
            onConnected(assignedVpnIp)
        }
    }

    override fun log(logInfo: ClientAPI_LogInfo) {
        val txt = logInfo.text?.trim() ?: return
        if (txt.isNotEmpty() && (txt.contains("Peer Connection Initiated") || txt.contains("WARNING") || txt.contains("ERROR"))) {
            onLog(txt.take(140))
        }
    }
}
