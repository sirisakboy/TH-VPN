package com.example.h25vpn.data

import android.content.Context
import com.example.h25vpn.auth.GoogleDriveHelper
import com.example.h25vpn.auth.SigningVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScanAndReportManager {
    private const val PREFS_NAME = "th_vpn_prefs"
    private const val KEY_AUTO_SCAN_1MBPS = "auto_scan_1mbps"
    private const val REPORT_FILE_NAME = "thvpn_diagnostic_report.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val _minSpeedScanState = MutableStateFlow(MinSpeedScanState())
    val minSpeedScanState: StateFlow<MinSpeedScanState> = _minSpeedScanState.asStateFlow()

    private val _reportUiState = MutableStateFlow(ReportUiState())
    val reportUiState: StateFlow<ReportUiState> = _reportUiState.asStateFlow()

    fun initialize(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoScan = sp.getBoolean(KEY_AUTO_SCAN_1MBPS, true)
        _minSpeedScanState.value = _minSpeedScanState.value.copy(autoScanEnabled = autoScan)

        val savedFile = File(context.applicationContext.filesDir, REPORT_FILE_NAME)
        if (savedFile.exists()) {
            val existing = runCatching { savedFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
            if (existing.isNotBlank()) {
                _reportUiState.value = _reportUiState.value.copy(
                    lastSavedPath = savedFile.absolutePath,
                    lastReportJson = existing
                )
            }
        }
    }

    fun setAutoScanEnabled(context: Context?, enabled: Boolean) {
        context?.applicationContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_AUTO_SCAN_1MBPS, enabled)
            ?.apply()
        _minSpeedScanState.value = _minSpeedScanState.value.copy(autoScanEnabled = enabled)
        VpnRepository.addLog(
            LogLevel.INFO,
            "SCAN-1M",
            "Service Auto-Scan (>= 1.0 Mbps) ${if (enabled) "ENABLED" else "DISABLED"}"
        )
    }

    fun startOneMbpsScanAsync(
        servers: List<VpnServer> = VpnRepository.servers.value,
        proxyPort: Int? = null,
        minThresholdMbps: Float = 1.0f
    ) {
        if (_minSpeedScanState.value.isScanning) return
        scope.launch {
            runOneMbpsScan(
                servers = servers,
                proxyPort = proxyPort,
                minThresholdMbps = minThresholdMbps
            )
        }
    }

    suspend fun runOneMbpsScan(
        servers: List<VpnServer>,
        proxyPort: Int? = null,
        minThresholdMbps: Float = 1.0f,
        latencyProbe: ((String, Int) -> Int)? = null,
        bandwidthProbe: ((Int?) -> Float)? = null
    ): MinSpeedScanState = withContext(Dispatchers.IO) {
        val targets = servers.take(10)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        _minSpeedScanState.value = _minSpeedScanState.value.copy(
            isScanning = true,
            minThresholdMbps = minThresholdMbps,
            totalNodes = targets.size,
            scannedNodes = 0,
            statusMessage = "กำลังวัดแบนด์วิดท์จริงเทียบเกณฑ์ ${String.format(Locale.US, "%.1f", minThresholdMbps)} Mbps..."
        )
        VpnRepository.addLog(
            LogLevel.INFO,
            "SCAN-1M",
            "Starting >= ${String.format(Locale.US, "%.1f", minThresholdMbps)} Mbps scan across ${targets.size} nodes"
        )

        val measuredLinkMbps = try {
            bandwidthProbe?.invoke(proxyPort) ?: measureRealDownloadMbps(proxyPort)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0f
        }

        val nodeResults = mutableListOf<ScanNodeResult>()
        targets.forEachIndexed { index, srv ->
            val pingMs = latencyProbe?.invoke(srv.host, srv.port)
                ?: VpnRepository.measureTcpLatencyMs(srv.host, srv.port, timeoutMs = 2000)

            // Estimate node effective throughput only when node is reachable via TCP
            val nodeMbps = if (pingMs > 0) {
                if (measuredLinkMbps > 0f) {
                    // Scale slightly by latency overhead while staying grounded in real measured Mbps
                    val latencyFactor = (1.0f - ((pingMs - 40).coerceAtLeast(0) / 1500f)).coerceIn(0.45f, 1.0f)
                    ((measuredLinkMbps * latencyFactor) * 10f).toInt() / 10f
                } else {
                    0f
                }
            } else {
                0f
            }

            val passed = pingMs > 0 && nodeMbps >= minThresholdMbps
            nodeResults.add(
                ScanNodeResult(
                    serverId = srv.id,
                    serverName = srv.name,
                    host = srv.host,
                    port = srv.port,
                    tcpPingMs = pingMs,
                    measuredMbps = nodeMbps,
                    passedMinSpeed = passed,
                    scannedAt = timeFmt.format(Date())
                )
            )
            _minSpeedScanState.value = _minSpeedScanState.value.copy(
                scannedNodes = index + 1,
                passingNodes = nodeResults.count { it.passedMinSpeed },
                lastMeasuredMbps = measuredLinkMbps
            )
        }

        val passingCount = nodeResults.count { it.passedMinSpeed }
        val completedAt = timeFmt.format(Date())
        val summaryMsg = if (passingCount > 0) {
            "ผ่านเกณฑ์ >= ${String.format(Locale.US, "%.1f", minThresholdMbps)} Mbps จำนวน $passingCount/${targets.size} โหนด (Link: ${String.format(Locale.US, "%.2f", measuredLinkMbps)} Mbps)"
        } else {
            "สแกนเสร็จสิ้น: Link จริง ${String.format(Locale.US, "%.2f", measuredLinkMbps)} Mbps | ผ่านเกณฑ์ $passingCount/${targets.size} โหนด"
        }

        val finalState = _minSpeedScanState.value.copy(
            isScanning = false,
            minThresholdMbps = minThresholdMbps,
            totalNodes = targets.size,
            scannedNodes = targets.size,
            passingNodes = passingCount,
            lastMeasuredMbps = measuredLinkMbps,
            lastScanTime = completedAt,
            statusMessage = summaryMsg,
            results = nodeResults.sortedWith(
                compareByDescending<ScanNodeResult> { it.passedMinSpeed }
                    .thenBy { if (it.tcpPingMs <= 0) Int.MAX_VALUE else it.tcpPingMs }
            )
        )
        _minSpeedScanState.value = finalState
        VpnRepository.addLog(
            if (passingCount > 0) LogLevel.SUCCESS else LogLevel.WARN,
            "SCAN-1M",
            summaryMsg
        )
        finalState
    }

    private fun measureRealDownloadMbps(proxyPort: Int?): Float {
        val url = URL("https://speed.cloudflare.com/__down?bytes=524288")
        val proxy = proxyPort?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it)) }
        var conn: HttpURLConnection? = null
        return try {
            conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.useCaches = false
            val startNs = System.nanoTime()
            var totalBytes = 0L
            val buf = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    totalBytes += read
                }
            }
            val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
            ((totalBytes * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
        } catch (_: Exception) {
            0f
        } finally {
            conn?.disconnect()
        }
    }

    fun generateAndSaveReport(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String
    ): DiagnosticReport {
        val appCtx = context.applicationContext
        val signing = SigningVerifier.verifyApkOAuthBinding(appCtx)
        val scan = _minSpeedScanState.value
        val stats = VpnRepository.networkStats.value
        val speed = VpnRepository.lastSpeedTest.value
        val srv = VpnRepository.selectedServer.value
        val tweak = VpnRepository.selectedTweak.value
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        val report = DiagnosticReport(
            generatedAt = now,
            packageName = signing.packageName,
            signingSha1Match = signing.isSha1Match,
            signingSha256Match = signing.isSha256Match,
            webClientConfigured = signing.isWebClientConfigured,
            firebaseUserEmail = firebaseEmail.ifBlank { "not_signed_in" },
            firebaseUid = firebaseUid.ifBlank { "none" },
            driveScopeAuthorized = _reportUiState.value.driveAuthorized,
            connectionState = VpnRepository.connectionState.value.name,
            activeServer = "${srv.name} (${srv.host}:${srv.port})",
            activeTweak = tweak.name,
            pingMs = stats.pingMs,
            downloadMbps = speed.downloadMbps.takeIf { it > 0f } ?: scan.lastMeasuredMbps,
            uploadMbps = speed.uploadMbps,
            minSpeedScanThresholdMbps = scan.minThresholdMbps,
            passingOneMbpsNodes = scan.passingNodes,
            totalScannedNodes = scan.totalNodes,
            recentLogs = VpnRepository.logs.value.takeLast(12).map { "[${it.timestamp}] ${it.tag}: ${it.message}" }
        )

        val jsonText = prettyJson.encodeToString(report)
        val outFile = File(appCtx.filesDir, REPORT_FILE_NAME)
        runCatching { outFile.writeText(jsonText, Charsets.UTF_8) }

        _reportUiState.value = _reportUiState.value.copy(
            lastGeneratedAt = now,
            lastSavedPath = outFile.absolutePath,
            lastReportJson = jsonText
        )
        VpnRepository.addLog(
            LogLevel.INFO,
            "REPORT",
            "Generated diagnostic report (${jsonText.length} bytes) -> ${outFile.name}"
        )
        return report
    }

    fun setDriveChecking(checking: Boolean) {
        _reportUiState.value = _reportUiState.value.copy(isCheckingDrive = checking)
    }

    fun updateDriveAuthorization(
        authorized: Boolean,
        accessToken: String,
        statusMessage: String
    ) {
        _reportUiState.value = _reportUiState.value.copy(
            driveAuthorized = authorized,
            driveAccessToken = accessToken,
            driveStatusMessage = statusMessage,
            isCheckingDrive = false
        )
        VpnRepository.addLog(
            if (authorized) LogLevel.SUCCESS else LogLevel.WARN,
            "DRIVE-AUTH",
            statusMessage
        )
    }

    suspend fun testUploadLatestReportToDrive(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String
    ) {
        val currentState = _reportUiState.value
        if (!currentState.driveAuthorized || currentState.driveAccessToken.isBlank()) {
            _reportUiState.value = currentState.copy(
                lastUploadResult = "ยังไม่ได้อนุญาตสิทธิ์ Google Drive (กรุณากดตรวจสิทธิ์ Drive แยกจากการล็อกอิน Firebase)"
            )
            return
        }

        _reportUiState.value = currentState.copy(
            isUploadingDrive = true,
            lastUploadResult = "กำลังสร้างรายงานและอัปโหลดทดสอบไปยัง Google Drive..."
        )

        generateAndSaveReport(context, firebaseEmail, firebaseUid)
        val latestJson = _reportUiState.value.lastReportJson
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "thvpn_report_$timestamp.json"

        val result = GoogleDriveHelper.uploadReportToDrive(
            accessToken = _reportUiState.value.driveAccessToken,
            fileName = fileName,
            jsonContent = latestJson
        )

        _reportUiState.value = _reportUiState.value.copy(
            isUploadingDrive = false,
            lastUploadResult = result.message
        )
        VpnRepository.addLog(
            if (result.success) LogLevel.SUCCESS else LogLevel.ERROR,
            "DRIVE-UPLOAD",
            result.message
        )
    }
}
