package com.example.h25vpn.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Allowlisted diagnostics only. Never serialize VpnServer, raw logs, or share URIs here. */
object ConnectionReports {
    private lateinit var app: Context
    private val _status = MutableStateFlow("ยังไม่ได้เปิดบันทึก")
    val status = _status.asStateFlow()
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()
    private val _sync = MutableStateFlow(false)
    val sync = _sync.asStateFlow()
    private val prefs get() = app.getSharedPreferences("connection_reports", Context.MODE_PRIVATE)
    private val directory get() = File(app.noBackupFilesDir, "connection-reports").apply { mkdirs() }

    fun initialize(context: Context) {
        app = context.applicationContext
        _enabled.value = prefs.getBoolean("enabled", false)
        _sync.value = prefs.getBoolean("drive_sync", false)
        _status.value = "คิวรายงาน ${pending().size} รายการ"
        if (_sync.value) schedule()
    }

    @Synchronized fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("enabled", value).apply()
        if (!value) setSync(false)
    }

    @Synchronized fun setSync(value: Boolean) {
        _sync.value = value && _enabled.value
        prefs.edit().putBoolean("drive_sync", _sync.value).apply()
        if (_sync.value) schedule() else {
            WorkManager.getInstance(app).cancelUniqueWork("report-upload")
            WorkManager.getInstance(app).cancelUniqueWork("report-periodic")
        }
    }

    fun updateStatus(value: String) { _status.value = value }

    @Synchronized fun record(event: String, server: VpnServer? = null, mbps: Double? = null) {
        if (!::app.isInitialized || !_enabled.value) return
        runCatching {
            val salt = prefs.getString("salt", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("salt", it).apply()
            }
            val report = ReportPayload.create(event, server, mbps, salt,
                System.currentTimeMillis(), android.os.Build.VERSION.SDK_INT)
            // Private storage; one bounded, immutable file per report; no device/account identifiers.
            File(directory, "${report.getString("reportId")}.json").writeText(report.toString(2))
            pending().sortedBy { it.lastModified() }.dropLast(200).forEach { it.delete() }
            _status.value = "คิวรายงาน ${pending().size} รายการ"
            if (_sync.value) enqueue()
        }.onFailure { _status.value = "บันทึกรายงานไม่สำเร็จ" }
    }

    @Synchronized fun pending(): List<File> = directory.listFiles()?.filter { it.extension == "json" } ?: emptyList()
    @Synchronized fun clear() {
        pending().forEach { it.delete() }
        _status.value = "ลบคิวในเครื่องแล้ว (ไม่ลบไฟล์ที่ส่งขึ้น Drive ไปแล้ว)"
    }
    @Synchronized fun acknowledge(file: File) { file.delete() }
    @Synchronized fun preview(): String = pending().takeLast(5).joinToString("\n\n") { it.readText() }
        .ifBlank { "ยังไม่มีรายงาน" }

    private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue() {
        if (!_sync.value) return
        WorkManager.getInstance(app).enqueueUniqueWork("report-upload", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DriveReportWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    private fun schedule() {
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("report-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveReportWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        enqueue()
    }
}
