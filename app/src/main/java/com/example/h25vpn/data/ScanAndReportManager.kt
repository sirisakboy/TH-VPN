package com.example.h25vpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.h25vpn.auth.DriveAuthCheckResult
import com.example.h25vpn.auth.DriveUploadResult
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ScanAndReportManager {
    private const val PREFS_NAME = "th_vpn_prefs"
    private const val KEY_AUTO_SCAN_1MBPS = "auto_scan_1mbps"
    private const val REPORT_FILE_NAME = "thvpn_diagnostic_report.json"
    private const val PENDING_QUEUE_FILE_NAME = "pending_drive_uploads.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queueMutex = Mutex()
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val _minSpeedScanState = MutableStateFlow(MinSpeedScanState())
    val minSpeedScanState: StateFlow<MinSpeedScanState> = _minSpeedScanState.asStateFlow()

    private val _reportUiState = MutableStateFlow(ReportUiState())
    val reportUiState: StateFlow<ReportUiState> = _reportUiState.asStateFlow()

    @Volatile
    private var networkCallbackRegistered = false

    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoScan = sp.getBoolean(KEY_AUTO_SCAN_1MBPS, true)
        _minSpeedScanState.value = _minSpeedScanState.value.copy(autoScanEnabled = autoScan)

        val savedFile = File(appCtx.filesDir, REPORT_FILE_NAME)
        val existingReport = if (savedFile.exists()) {
            runCatching { savedFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        } else {
            ""
        }

        val queue = readQueueLocked(appCtx)
        _reportUiState.value = _reportUiState.value.copy(
            lastSavedPath = if (existingReport.isNotBlank()) savedFile.absolutePath else _reportUiState.value.lastSavedPath,
            lastReportJson = existingReport.ifBlank { _reportUiState.value.lastReportJson },
            pendingQueueCount = queue.size,
            pendingUploads = queue
        )

        registerConnectivityAutoFlush(appCtx)
    }

    private fun registerConnectivityAutoFlush(appCtx: Context) {
        if (networkCallbackRegistered) return
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                triggerAutoFlushIfPending(appCtx)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet) {
                    triggerAutoFlushIfPending(appCtx)
                }
            }
        }
        runCatching {
            cm.registerNetworkCallback(request, callback)
            networkCallbackRegistered = true
        }
    }

    private fun triggerAutoFlushIfPending(appCtx: Context) {
        if (_reportUiState.value.pendingQueueCount <= 0) return
        scope.launch {
            flushPendingDriveUploads(context = appCtx, onlyIfOnline = true)
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
        context: Context? = null,
        servers: List<VpnServer> = VpnRepository.servers.value,
        activeProxyPort: Int? = null,
        activeServerId: String? = null,
        minThresholdMbps: Float = 1.0f
    ) {
        if (_minSpeedScanState.value.isScanning) return
        scope.launch {
            runOneMbpsScan(
                context = context,
                servers = servers,
                activeProxyPort = activeProxyPort,
                activeServerId = activeServerId,
                minThresholdMbps = minThresholdMbps
            )
        }
    }

    suspend fun runOneMbpsScan(
        context: Context? = null,
        servers: List<VpnServer>,
        activeProxyPort: Int? = null,
        activeServerId: String? = null,
        tweak: CarrierTweak = VpnRepository.selectedTweak.value,
        dnsMode: DnsMode = VpnRepository.dnsMode.value,
        minThresholdMbps: Float = 1.0f,
        pathProbe: TunnelPathProbe = RealTunnelPathProbe
    ): MinSpeedScanState = withContext(Dispatchers.IO) {
        val targets = servers.take(6)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        _minSpeedScanState.value = _minSpeedScanState.value.copy(
            isScanning = true,
            minThresholdMbps = minThresholdMbps,
            totalNodes = targets.size,
            scannedNodes = 0,
            passingNodes = 0,
            statusMessage = "กำลังทดสอบผ่านอุโมงค์ทีละเส้นทาง (${targets.size} โหนด) เกณฑ์ >= ${String.format(Locale.US, "%.1f", minThresholdMbps)} Mbps..."
        )
        VpnRepository.addLog(
            LogLevel.INFO,
            "SCAN-1M",
            "Starting isolated per-path tunnel scan (>= ${String.format(Locale.US, "%.1f", minThresholdMbps)} Mbps) for ${targets.size} nodes"
        )

        val nodeResults = mutableListOf<ScanNodeResult>()
        targets.forEachIndexed { index, srv ->
            _minSpeedScanState.value = _minSpeedScanState.value.copy(
                statusMessage = "กำลังเปิดอุโมงค์ทดสอบ ${srv.name} (${index + 1}/${targets.size})..."
            )
            val outcome = try {
                pathProbe.probeNode(
                    context = context,
                    server = srv,
                    tweak = tweak,
                    dnsMode = dnsMode,
                    activeProxyPort = activeProxyPort,
                    activeServerId = activeServerId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TunnelProbeOutcome(
                    tcpPingMs = -1,
                    measuredMbps = 0f,
                    testedViaTunnel = false,
                    tunnelStatus = "ล้มเหลว: ${e.message?.take(48) ?: e.javaClass.simpleName}"
                )
            }

            val passed = outcome.testedViaTunnel &&
                outcome.tcpPingMs > 0 &&
                outcome.measuredMbps >= minThresholdMbps

            val nodeResult = ScanNodeResult(
                serverId = srv.id,
                serverName = srv.name,
                host = srv.host,
                port = srv.port,
                tcpPingMs = outcome.tcpPingMs,
                measuredMbps = outcome.measuredMbps,
                passedMinSpeed = passed,
                scannedAt = timeFmt.format(Date()),
                testedViaTunnel = outcome.testedViaTunnel,
                tunnelStatus = outcome.tunnelStatus
            )
            nodeResults.add(nodeResult)

            val bestSoFar = nodeResults.maxOfOrNull { it.measuredMbps } ?: 0f
            _minSpeedScanState.value = _minSpeedScanState.value.copy(
                scannedNodes = index + 1,
                passingNodes = nodeResults.count { it.passedMinSpeed },
                lastMeasuredMbps = if (outcome.measuredMbps > 0f) outcome.measuredMbps else bestSoFar,
                results = nodeResults.toList()
            )
        }

        val passingCount = nodeResults.count { it.passedMinSpeed }
        val tunneledCount = nodeResults.count { it.testedViaTunnel }
        val bestMbps = nodeResults.maxOfOrNull { it.measuredMbps } ?: 0f
        val completedAt = timeFmt.format(Date())
        val summaryMsg = if (passingCount > 0) {
            "ทดสอบผ่านอุโมงค์จริง $tunneledCount/${targets.size} โหนด • ผ่าน >= ${String.format(Locale.US, "%.1f", minThresholdMbps)} Mbps: $passingCount โหนด (สูงสุด ${String.format(Locale.US, "%.2f", bestMbps)} Mbps)"
        } else {
            "สแกนอุโมงค์จริงเสร็จสิ้น ($tunneledCount/${targets.size} เปิดอุโมงค์ได้) • ผ่าน >= 1.0 Mbps: 0 โหนด (สูงสุด ${String.format(Locale.US, "%.2f", bestMbps)} Mbps)"
        }

        val sortedResults = nodeResults.sortedWith(
            compareByDescending<ScanNodeResult> { it.passedMinSpeed }
                .thenByDescending { it.testedViaTunnel }
                .thenByDescending { it.measuredMbps }
                .thenBy { if (it.tcpPingMs <= 0) Int.MAX_VALUE else it.tcpPingMs }
        )

        val finalState = _minSpeedScanState.value.copy(
            isScanning = false,
            minThresholdMbps = minThresholdMbps,
            totalNodes = targets.size,
            scannedNodes = targets.size,
            passingNodes = passingCount,
            lastMeasuredMbps = bestMbps,
            lastScanTime = completedAt,
            statusMessage = summaryMsg,
            results = sortedResults
        )
        _minSpeedScanState.value = finalState
        VpnRepository.addLog(
            if (passingCount > 0) LogLevel.SUCCESS else LogLevel.WARN,
            "SCAN-1M",
            summaryMsg
        )
        finalState
    }

    fun buildDiagnosticReport(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String,
        includeAccountDetails: Boolean = false,
        includeServerEndpoints: Boolean = false
    ): DiagnosticReport {
        val appCtx = context.applicationContext
        val signing = SigningVerifier.verifyApkOAuthBinding(appCtx)
        val scan = _minSpeedScanState.value
        val stats = VpnRepository.networkStats.value
        val speed = VpnRepository.lastSpeedTest.value
        val srv = VpnRepository.selectedServer.value
        val tweak = VpnRepository.selectedTweak.value
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        val emailValue = when {
            firebaseEmail.isBlank() -> "not_signed_in"
            includeAccountDetails -> firebaseEmail
            else -> "sha256:${sha256Short(firebaseEmail)}"
        }
        val uidValue = when {
            firebaseUid.isBlank() -> "none"
            includeAccountDetails -> firebaseUid
            else -> "sha256:${sha256Short(firebaseUid)}"
        }

        val activeServerLabel = if (includeServerEndpoints) {
            "${srv.name} (${srv.host}:${srv.port})"
        } else {
            "${srv.name} (${maskHost(srv.host)}:***)"
        }

        val nodeResultsOut = if (includeServerEndpoints) {
            scan.results
        } else {
            scan.results.map { node ->
                node.copy(
                    host = maskHost(node.host),
                    port = 0
                )
            }
        }

        val logsOut = VpnRepository.logs.value.takeLast(12).map { entry ->
            val rawLine = "[${entry.timestamp}] ${entry.tag}: ${entry.message}"
            sanitizeLogLine(
                line = rawLine,
                maskEmails = !includeAccountDetails,
                maskEndpoints = !includeServerEndpoints
            )
        }

        return DiagnosticReport(
            generatedAt = now,
            redacted = !includeAccountDetails || !includeServerEndpoints,
            packageName = signing.packageName,
            signingSha1Match = signing.isSha1Match,
            signingSha256Match = signing.isSha256Match,
            webClientConfigured = signing.isWebClientConfigured,
            firebaseUserEmail = emailValue,
            firebaseUid = uidValue,
            driveScopeAuthorized = _reportUiState.value.driveAuthorized,
            connectionState = VpnRepository.connectionState.value.name,
            activeServer = activeServerLabel,
            activeTweak = tweak.name,
            pingMs = stats.pingMs,
            downloadMbps = speed.downloadMbps.takeIf { it > 0f } ?: scan.lastMeasuredMbps,
            uploadMbps = speed.uploadMbps,
            minSpeedScanThresholdMbps = scan.minThresholdMbps,
            passingOneMbpsNodes = scan.passingNodes,
            totalScannedNodes = scan.totalNodes,
            nodeTunnelResults = nodeResultsOut,
            recentLogs = logsOut
        )
    }

    fun buildPreviewReportJson(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String,
        includeAccountDetails: Boolean = false,
        includeServerEndpoints: Boolean = false
    ): String {
        val report = buildDiagnosticReport(
            context = context,
            firebaseEmail = firebaseEmail,
            firebaseUid = firebaseUid,
            includeAccountDetails = includeAccountDetails,
            includeServerEndpoints = includeServerEndpoints
        )
        return prettyJson.encodeToString(report)
    }

    fun generateAndSaveReport(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String,
        includeAccountDetails: Boolean = false,
        includeServerEndpoints: Boolean = false
    ): DiagnosticReport {
        val appCtx = context.applicationContext
        val report = buildDiagnosticReport(
            context = appCtx,
            firebaseEmail = firebaseEmail,
            firebaseUid = firebaseUid,
            includeAccountDetails = includeAccountDetails,
            includeServerEndpoints = includeServerEndpoints
        )
        val jsonText = prettyJson.encodeToString(report)
        val outFile = File(appCtx.filesDir, REPORT_FILE_NAME)
        runCatching { outFile.writeText(jsonText, Charsets.UTF_8) }

        _reportUiState.value = _reportUiState.value.copy(
            lastGeneratedAt = report.generatedAt,
            lastSavedPath = outFile.absolutePath,
            lastReportJson = jsonText
        )
        VpnRepository.addLog(
            LogLevel.INFO,
            "REPORT",
            "Generated ${if (report.redacted) "redacted" else "full"} report (${jsonText.length} bytes) -> ${outFile.name}"
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

    suspend fun enqueueOrUploadReport(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String,
        includeAccountDetails: Boolean = false,
        includeServerEndpoints: Boolean = false,
        isOnlineOverride: Boolean? = null,
        uploader: suspend (accessToken: String, fileName: String, jsonContent: String) -> DriveUploadResult =
            { token, name, body -> GoogleDriveHelper.uploadReportToDrive(token, name, body) }
    ) {
        val appCtx = context.applicationContext
        generateAndSaveReport(
            context = appCtx,
            firebaseEmail = firebaseEmail,
            firebaseUid = firebaseUid,
            includeAccountDetails = includeAccountDetails,
            includeServerEndpoints = includeServerEndpoints
        )
        val latestJson = _reportUiState.value.lastReportJson
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "thvpn_report_$timestamp.json"
        val isOnline = isOnlineOverride ?: isNetworkAvailable(appCtx)

        if (!isOnline) {
            val queued = enqueuePendingUpload(
                context = appCtx,
                fileName = fileName,
                jsonContent = latestJson,
                error = "ไม่มีการเชื่อมต่ออินเทอร์เน็ต — รอส่งอัตโนมัติเมื่อออนไลน์"
            )
            _reportUiState.value = _reportUiState.value.copy(
                isUploadingDrive = false,
                lastUploadResult = "ออฟไลน์: เก็บรายงานเข้าคิวอัตโนมัติแล้ว (ค้างส่ง ${queued.size} ไฟล์)"
            )
            return
        }

        var token = _reportUiState.value.driveAccessToken
        if ((!_reportUiState.value.driveAuthorized || token.isBlank())) {
            val silentCheck = runCatching { GoogleDriveHelper.checkOrRequestDrivePermission(appCtx) }.getOrNull()
            if (silentCheck is DriveAuthCheckResult.Authorized) {
                token = silentCheck.accessToken
                updateDriveAuthorization(
                    authorized = true,
                    accessToken = token,
                    statusMessage = "อนุญาตสิทธิ์ Google Drive (drive.file) พร้อมใช้งาน"
                )
            }
        }

        if (!_reportUiState.value.driveAuthorized || token.isBlank()) {
            _reportUiState.value = _reportUiState.value.copy(
                isUploadingDrive = false,
                lastUploadResult = "ยังไม่ได้อนุญาตสิทธิ์ Google Drive (กรุณากดตรวจสิทธิ์ Drive แยกจากการล็อกอิน Firebase)"
            )
            return
        }

        _reportUiState.value = _reportUiState.value.copy(
            isUploadingDrive = true,
            lastUploadResult = "กำลังอัปโหลดรายงานไปยัง Google Drive..."
        )

        val result = try {
            uploader(token, fileName, latestJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DriveUploadResult(
                success = false,
                message = "อัปโหลดล้มเหลว: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }

        if (result.success) {
            _reportUiState.value = _reportUiState.value.copy(
                isUploadingDrive = false,
                lastUploadResult = result.message
            )
            VpnRepository.addLog(LogLevel.SUCCESS, "DRIVE-UPLOAD", result.message)
        } else {
            val queued = enqueuePendingUpload(
                context = appCtx,
                fileName = fileName,
                jsonContent = latestJson,
                error = result.message
            )
            _reportUiState.value = _reportUiState.value.copy(
                isUploadingDrive = false,
                lastUploadResult = "${result.message} • เก็บเข้าคิวรอส่งซ้ำแล้ว (${queued.size} ไฟล์)"
            )
            VpnRepository.addLog(LogLevel.WARN, "DRIVE-QUEUE", "Queued failed upload: ${result.message}")
        }
    }

    suspend fun testUploadLatestReportToDrive(
        context: Context,
        firebaseEmail: String,
        firebaseUid: String
    ) {
        enqueueOrUploadReport(
            context = context,
            firebaseEmail = firebaseEmail,
            firebaseUid = firebaseUid,
            includeAccountDetails = false,
            includeServerEndpoints = false
        )
    }

    suspend fun enqueuePendingUpload(
        context: Context,
        fileName: String,
        jsonContent: String,
        error: String = ""
    ): List<PendingDriveUpload> = queueMutex.withLock {
        val appCtx = context.applicationContext
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val item = PendingDriveUpload(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            jsonContent = jsonContent,
            createdAt = now,
            retryCount = 0,
            lastError = error
        )
        val current = readQueueLocked(appCtx).toMutableList()
        current.add(item)
        val trimmed = current.takeLast(25)
        writeQueueLocked(appCtx, trimmed)
        _reportUiState.value = _reportUiState.value.copy(
            pendingQueueCount = trimmed.size,
            pendingUploads = trimmed
        )
        trimmed
    }

    suspend fun flushPendingDriveUploads(
        context: Context,
        onlyIfOnline: Boolean = true,
        driveAuthChecker: suspend (Context) -> DriveAuthCheckResult = { ctx ->
            GoogleDriveHelper.checkOrRequestDrivePermission(ctx)
        },
        uploader: suspend (accessToken: String, fileName: String, jsonContent: String) -> DriveUploadResult =
            { token, name, body -> GoogleDriveHelper.uploadReportToDrive(token, name, body) }
    ): Int = queueMutex.withLock {
        val appCtx = context.applicationContext
        val queue = readQueueLocked(appCtx)
        if (queue.isEmpty()) {
            _reportUiState.value = _reportUiState.value.copy(
                pendingQueueCount = 0,
                pendingUploads = emptyList()
            )
            return 0
        }
        if (onlyIfOnline && !isNetworkAvailable(appCtx)) {
            _reportUiState.value = _reportUiState.value.copy(
                lastQueueFlushMessage = "ยังไม่เชื่อมต่ออินเทอร์เน็ต (คิวค้าง ${queue.size} ไฟล์)"
            )
            return 0
        }

        var token = _reportUiState.value.driveAccessToken
        if (token.isBlank()) {
            val check = runCatching { driveAuthChecker(appCtx) }.getOrNull()
            if (check is DriveAuthCheckResult.Authorized && check.accessToken.isNotBlank()) {
                token = check.accessToken
                updateDriveAuthorization(
                    authorized = true,
                    accessToken = token,
                    statusMessage = "อนุญาตสิทธิ์ Google Drive (drive.file) พร้อมใช้งาน"
                )
            }
        }

        if (token.isBlank()) {
            _reportUiState.value = _reportUiState.value.copy(
                lastQueueFlushMessage = "มีรายงานค้างส่ง ${queue.size} ไฟล์ (รอตรวจสิทธิ์ Google Drive)"
            )
            return 0
        }

        _reportUiState.value = _reportUiState.value.copy(isUploadingDrive = true)
        var uploadedCount = 0
        val remaining = mutableListOf<PendingDriveUpload>()

        for (item in queue) {
            val res = try {
                uploader(token, item.fileName, item.jsonContent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DriveUploadResult(
                    success = false,
                    message = e.localizedMessage ?: e.javaClass.simpleName
                )
            }

            if (res.success) {
                uploadedCount++
                VpnRepository.addLog(
                    LogLevel.SUCCESS,
                    "DRIVE-QUEUE",
                    "Auto-flushed queued report ${item.fileName} -> ${res.fileId.ifBlank { "OK" }}"
                )
            } else {
                remaining.add(
                    item.copy(
                        retryCount = item.retryCount + 1,
                        lastError = res.message
                    )
                )
            }
        }

        writeQueueLocked(appCtx, remaining)
        val flushMsg = when {
            uploadedCount > 0 && remaining.isEmpty() ->
                "ส่งรายงานที่ค้างทั้งหมด $uploadedCount ไฟล์ขึ้น Drive สำเร็จ"
            uploadedCount > 0 ->
                "ส่งรายงานที่ค้างสำเร็จ $uploadedCount ไฟล์ (ยังเหลือ ${remaining.size} ไฟล์)"
            else ->
                "ส่งคิวค้างไม่สำเร็จ (${remaining.size} ไฟล์ยังรอส่งซ้ำ)"
        }
        _reportUiState.value = _reportUiState.value.copy(
            isUploadingDrive = false,
            pendingQueueCount = remaining.size,
            pendingUploads = remaining,
            lastQueueFlushMessage = flushMsg,
            lastUploadResult = if (uploadedCount > 0) flushMsg else _reportUiState.value.lastUploadResult
        )
        uploadedCount
    }

    @Synchronized
    private fun readQueueLocked(appCtx: Context): List<PendingDriveUpload> {
        val file = File(appCtx.filesDir, PENDING_QUEUE_FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) emptyList()
            else prettyJson.decodeFromString<List<PendingDriveUpload>>(text)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun writeQueueLocked(appCtx: Context, items: List<PendingDriveUpload>) {
        val file = File(appCtx.filesDir, PENDING_QUEUE_FILE_NAME)
        runCatching {
            if (items.isEmpty()) {
                file.writeText("[]", Charsets.UTF_8)
            } else {
                file.writeText(prettyJson.encodeToString(items), Charsets.UTF_8)
            }
        }
    }

    private fun sha256Short(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun maskHost(host: String): String {
        val trimmed = host.trim()
        if (trimmed.isBlank()) return "***"
        val ipParts = trimmed.split(".")
        return if (ipParts.size == 4 && ipParts.all { it.toIntOrNull() != null }) {
            "${ipParts[0]}.${ipParts[1]}.*.*"
        } else if (trimmed.length > 6) {
            "${trimmed.take(3)}***${trimmed.takeLast(3)}"
        } else {
            "***"
        }
    }

    private fun sanitizeLogLine(line: String, maskEmails: Boolean, maskEndpoints: Boolean): String {
        var out = line
        if (maskEmails) {
            out = out.replace(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[masked-email]")
        }
        if (maskEndpoints) {
            out = out.replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b"""), "[masked-endpoint]")
        }
        return out
    }
}
