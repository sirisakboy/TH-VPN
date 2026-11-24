package com.example.h25vpn.service

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the native `badvpn-tun2socks` (`libtun2socks.so` / `libh1.so`) and `pdnsd` (`libpdnsd.so`)
 * child processes and sends the Android TUN file descriptor via Unix Domain Socket SCM_RIGHTS (`sendfd_path`).
 */
class Tun2SocksBridge(
    private val context: Context,
    private val onLog: (String) -> Unit
) {
    private var tun2SocksProcess: Process? = null
    private var pdnsdProcess: Process? = null

    fun start(
        tunInterface: ParcelFileDescriptor,
        socksPort: Int = 10808,
        dnsPrimary: String = "1.1.1.1",
        dnsSecondary: String = "8.8.8.8",
        enableUdpGw: Boolean = true
    ): Boolean {
        stop()

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val tunBinary = sequenceOf(
            File(nativeDir, "libtun2socks.so"),
            File(nativeDir, "libh1.so")
        ).firstOrNull { it.exists() }
            ?: throw IllegalStateException("Native tun2socks binary not found in $nativeDir")

        // Ensure geoip.dat and geosite.dat exist in filesDir for Xray-core
        ensureXrayAssetsExtracted(context)

        // Start local pdnsd TCP-DNS forwarder if available (converts UDP DNS -> TCP DNS so DNS works even without UDP relay)
        val pdnsdPort = 8091
        val pdnsdStarted = startPdnsdIfAvailable(nativeDir, pdnsdPort, dnsPrimary, dnsSecondary)

        val sockFile = File(context.filesDir, "sock_path")
        if (sockFile.exists()) sockFile.delete()

        val cmd = mutableListOf(
            tunBinary.absolutePath,
            "--netif-ipaddr", "26.26.26.2",
            "--netif-netmask", "255.255.255.252",
            "--socks-server-addr", "127.0.0.1:$socksPort",
            "--tunmtu", "1500",
            "--sock-path", sockFile.absolutePath,
            "--loglevel", "warning"
        )

        if (pdnsdStarted) {
            cmd.add("--dnsgw")
            cmd.add("127.0.0.1:$pdnsdPort")
        } else if (enableUdpGw) {
            cmd.add("--enable-udprelay")
        }

        onLog("Launching tun2socks bridge -> 127.0.0.1:$socksPort")
        val pb = ProcessBuilder(cmd)
            .directory(context.filesDir)
            .redirectErrorStream(true)

        val proc = pb.start()
        tun2SocksProcess = proc

        // Drain stdout/stderr asynchronously
        Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank() && (line.contains("ERROR", true) || line.contains("warn", true))) {
                            onLog("tun2socks: ${line.take(120)}")
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }, "Tun2Socks-Logger").apply {
            isDaemon = true
            start()
        }

        // Send TUN fd to badvpn-tun2socks over Unix domain socket
        val sent = sendTunFd(tunInterface, sockFile)
        if (!sent) {
            onLog("Failed to pass TUN fd to tun2socks socket!")
            stop()
            return false
        }
        onLog("TUN file descriptor attached to tun2socks successfully.")
        return true
    }

    private fun sendTunFd(tunPfd: ParcelFileDescriptor, sockFile: File): Boolean {
        repeat(25) { attempt ->
            try {
                Thread.sleep(if (attempt == 0) 80L else 120L)
                LocalSocket().use { localSocket ->
                    localSocket.connect(
                        LocalSocketAddress(
                            sockFile.absolutePath,
                            LocalSocketAddress.Namespace.FILESYSTEM
                        )
                    )
                    localSocket.setFileDescriptorsForSend(arrayOf(tunPfd.fileDescriptor))
                    localSocket.outputStream.write(42)
                    localSocket.outputStream.flush()
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun startPdnsdIfAvailable(
        nativeDir: String,
        listenPort: Int,
        dnsPrimary: String,
        dnsSecondary: String
    ): Boolean {
        val pdnsdBin = File(nativeDir, "libpdnsd.so")
        if (!pdnsdBin.exists()) return false
        return try {
            val cacheDir = File(context.filesDir, "pdnsd_cache").apply { mkdirs() }
            val confFile = File(context.filesDir, "pdnsd.conf")
            val conf = """
                global {
                    perm_cache = 2048;
                    cache_dir = "${cacheDir.absolutePath}";
                    server_ip = 127.0.0.1;
                    server_port = $listenPort;
                    query_method = tcp_only;
                    min_ttl = 15m;
                    max_ttl = 1w;
                    timeout = 10;
                    daemon = off;
                }
                server {
                    label = "upstream";
                    ip = $dnsPrimary, $dnsSecondary;
                    port = 53;
                    uptest = none;
                    proxy_only = on;
                }
            """.trimIndent()
            confFile.writeText(conf)

            pdnsdProcess = ProcessBuilder(
                pdnsdBin.absolutePath,
                "-c", confFile.absolutePath
            ).directory(context.filesDir).redirectErrorStream(true).start()
            true
        } catch (e: Exception) {
            onLog("pdnsd skipped (${e.message}), using SOCKS5 UDP relay.")
            false
        }
    }

    fun stop() {
        try {
            tun2SocksProcess?.destroy()
        } catch (_: Exception) {
        }
        tun2SocksProcess = null

        try {
            pdnsdProcess?.destroy()
        } catch (_: Exception) {
        }
        pdnsdProcess = null
    }

    companion object {
        fun ensureXrayAssetsExtracted(context: Context) {
            val assetNames = listOf("geoip.dat", "geosite.dat")
            for (name in assetNames) {
                val target = File(context.filesDir, name)
                if (!target.exists() || target.length() < 1024L) {
                    try {
                        context.assets.open(name).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
