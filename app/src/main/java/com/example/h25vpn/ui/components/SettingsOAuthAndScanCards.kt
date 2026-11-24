package com.example.h25vpn.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.h25vpn.auth.DriveAuthCheckResult
import com.example.h25vpn.auth.GoogleAuthHelper
import com.example.h25vpn.auth.GoogleDriveHelper
import com.example.h25vpn.auth.SigningVerifier
import com.example.h25vpn.data.ConnectionState
import com.example.h25vpn.data.ScanAndReportManager
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.service.H25VpnService
import com.example.h25vpn.ui.theme.AmberWarning
import com.example.h25vpn.ui.theme.CoralRed
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBg
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.DarkSurfaceVariant
import com.example.h25vpn.ui.theme.EmeraldGreen
import com.example.h25vpn.ui.theme.GoldAccent
import com.example.h25vpn.ui.theme.PurpleAccent
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary
import com.example.h25vpn.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsOAuthAndScanSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by remember(context) { GoogleAuthHelper.authStateFlow(context) }
        .collectAsStateWithLifecycle(initialValue = GoogleAuthHelper.currentUserOrNull(context))
    val scanState by ScanAndReportManager.minSpeedScanState.collectAsStateWithLifecycle()
    val reportState by ScanAndReportManager.reportUiState.collectAsStateWithLifecycle()
    val connectionState by VpnRepository.connectionState.collectAsStateWithLifecycle()
    val signingInfo = remember(context) { SigningVerifier.verifyApkOAuthBinding(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showConsentDialog by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        when (val extracted = GoogleDriveHelper.extractTokenFromActivityResult(context, activityResult.data)) {
            is DriveAuthCheckResult.Authorized -> {
                ScanAndReportManager.updateDriveAuthorization(
                    authorized = true,
                    accessToken = extracted.accessToken,
                    statusMessage = "อนุญาตสิทธิ์ Google Drive (drive.file) สำเร็จ"
                )
                if (ScanAndReportManager.reportUiState.value.pendingQueueCount > 0) {
                    scope.launch {
                        ScanAndReportManager.flushPendingDriveUploads(context)
                    }
                }
            }
            is DriveAuthCheckResult.Error -> {
                ScanAndReportManager.updateDriveAuthorization(
                    authorized = false,
                    accessToken = "",
                    statusMessage = extracted.message
                )
            }
            is DriveAuthCheckResult.NeedsConsent -> Unit
        }
    }

    // 1. Google Account + Permanent Keystore/OAuth Binding
    Text(
        text = "บัญชี GOOGLE, คีย์เซ็นถาวร & OAUTH",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("oauth_signing_status_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (currentUser != null) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                    contentDescription = "Google Account",
                    tint = if (currentUser != null) EmeraldGreen else CyanAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentUser?.displayName?.takeIf { it.isNotBlank() }
                        ?: if (currentUser != null) "เชื่อมต่อบัญชี Google (Firebase Auth) แล้ว" else "ยังไม่ได้เชื่อมต่อบัญชี Google",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = currentUser?.email?.takeIf { it.isNotBlank() }
                        ?: "Package: ${signingInfo.packageName}",
                    fontSize = 12.sp,
                    color = if (currentUser != null) EmeraldGreen else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkBg)
                .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Signing Key Verification",
                    tint = if (signingInfo.isFullyVerified) EmeraldGreen else GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Package: ${signingInfo.packageName} (${if (signingInfo.isPackageMatch) "ตรง" else "ไม่ตรง"})",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "SHA-1 APK: ${signingInfo.actualSha1.ifBlank { signingInfo.expectedSha1 }}",
                fontSize = 10.sp,
                color = if (signingInfo.isSha1Match) EmeraldGreen else TextSecondary
            )
            Text(
                text = "OAuth Web Client: ${if (signingInfo.isWebClientConfigured) "พร้อมใช้งาน (google-services.json)" else "ยังไม่พบ"}",
                fontSize = 10.sp,
                color = if (signingInfo.isWebClientConfigured) CyanAccent else CoralRed
            )
        }

        if (authError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = authError ?: "", fontSize = 12.sp, color = CoralRed)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (currentUser == null) {
            Button(
                onClick = {
                    authError = null
                    isSigningIn = true
                    GoogleAuthHelper.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = {
                            isSigningIn = false
                            authError = null
                        },
                        onAuthError = { msg ->
                            isSigningIn = false
                            authError = msg
                        },
                        onAuthCancelled = {
                            isSigningIn = false
                        }
                    )
                },
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = DarkBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBg, strokeWidth = 2.dp)
                } else {
                    Text(text = "Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    GoogleAuthHelper.signOut(credentialManager = credentialManager, scope = scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "ออกจากระบบบัญชี Google", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // 2. Google Drive Separate Authorization, Offline Queue & Consent Upload
    Text(
        text = "สิทธิ์ GOOGLE DRIVE, คิวออฟไลน์ & ส่งรายงาน",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("drive_auth_section")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (reportState.driveAuthorized) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                    contentDescription = "Google Drive Permission",
                    tint = if (reportState.driveAuthorized) EmeraldGreen else AmberWarning,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "แยกสิทธิ์ Google Drive (drive.file)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = reportState.driveStatusMessage,
                    fontSize = 12.sp,
                    color = if (reportState.driveAuthorized) EmeraldGreen else TextSecondary
                )
            }
        }

        if (reportState.lastUploadResult.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reportState.lastUploadResult,
                fontSize = 12.sp,
                color = if (reportState.lastUploadResult.contains("สำเร็จ")) EmeraldGreen else GoldAccent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkBg)
                .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("drive_queue_status"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "คิวรอส่งอัตโนมัติเมื่อออนไลน์: ${reportState.pendingQueueCount} ไฟล์",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reportState.pendingQueueCount > 0) AmberWarning else TextSecondary
                )
                if (reportState.lastQueueFlushMessage.isNotBlank()) {
                    Text(
                        text = reportState.lastQueueFlushMessage,
                        fontSize = 10.sp,
                        color = CyanAccent
                    )
                }
            }
            if (reportState.pendingQueueCount > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            ScanAndReportManager.flushPendingDriveUploads(context, onlyIfOnline = false)
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("flush_drive_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Flush Pending Queue",
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ส่งคิวที่ค้างทันที",
                        fontSize = 11.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    ScanAndReportManager.setDriveChecking(true)
                    scope.launch {
                        when (val res = GoogleDriveHelper.checkOrRequestDrivePermission(context)) {
                            is DriveAuthCheckResult.Authorized -> {
                                ScanAndReportManager.updateDriveAuthorization(
                                    authorized = true,
                                    accessToken = res.accessToken,
                                    statusMessage = "อนุญาตสิทธิ์ Google Drive (drive.file) พร้อมใช้งาน"
                                )
                                if (ScanAndReportManager.reportUiState.value.pendingQueueCount > 0) {
                                    ScanAndReportManager.flushPendingDriveUploads(context)
                                }
                            }
                            is DriveAuthCheckResult.NeedsConsent -> {
                                ScanAndReportManager.setDriveChecking(false)
                                driveConsentLauncher.launch(
                                    IntentSenderRequest.Builder(res.pendingIntent.intentSender).build()
                                )
                            }
                            is DriveAuthCheckResult.Error -> {
                                ScanAndReportManager.updateDriveAuthorization(
                                    authorized = false,
                                    accessToken = "",
                                    statusMessage = res.message
                                )
                            }
                        }
                    }
                },
                enabled = !reportState.isCheckingDrive,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("check_drive_permission_button")
            ) {
                if (reportState.isCheckingDrive) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyanAccent, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "ตรวจสิทธิ์ Drive",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }
            }

            Button(
                onClick = { showConsentDialog = true },
                enabled = !reportState.isUploadingDrive,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = TextPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("test_drive_upload_button")
            ) {
                if (reportState.isUploadingDrive) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextPrimary, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "ตรวจข้อมูล & อัปโหลด",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // 3. Per-Path 1 Mbps Scan & Redacted Reporting System
    Text(
        text = "ระบบสแกนอุโมงค์ 1 MBPS ทีละโหนด & รายงาน",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("one_mbps_scan_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "1 Mbps Auto Scan",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "สแกนผ่านอุโมงค์จริง (>= 1.0 Mbps)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "วัดทีละโหนดผ่าน Xray/SSH proxy (ไม่ใช้เน็ตตรง)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = scanState.autoScanEnabled,
                onCheckedChange = { ScanAndReportManager.setAutoScanEnabled(context, it) },
                modifier = Modifier.testTag("auto_scan_1mbps_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkBg,
                    checkedTrackColor = CyanAccent,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = scanState.statusMessage,
            fontSize = 12.sp,
            color = if (scanState.passingNodes > 0) EmeraldGreen else TextSecondary
        )
        if (reportState.lastSavedPath.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "รายงานล่าสุด (ค่าเริ่มต้นปิดบัง PII): ${reportState.lastGeneratedAt.ifBlank { "บันทึกแล้ว" }}",
                fontSize = 11.sp,
                color = CyanAccent
            )
        }

        if (scanState.results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkBg)
                    .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("per_node_tunnel_results"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scanState.results.forEach { node ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.serverName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = node.tunnelStatus.ifBlank {
                                    if (node.testedViaTunnel) "ผ่านอุโมงค์จริง" else "ไม่ได้ผ่านอุโมงค์"
                                },
                                fontSize = 10.sp,
                                color = if (node.testedViaTunnel) CyanAccent else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (node.testedViaTunnel) {
                                    String.format(Locale.US, "%.2f Mbps", node.measuredMbps)
                                } else {
                                    "0.00 Mbps"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    node.passedMinSpeed -> EmeraldGreen
                                    node.testedViaTunnel -> AmberWarning
                                    else -> CoralRed
                                }
                            )
                            Text(
                                text = if (node.tcpPingMs > 0) "${node.tcpPingMs} ms" else "Offline",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (connectionState == ConnectionState.CONNECTED) {
                        val intent = Intent(context, H25VpnService::class.java).apply {
                            action = H25VpnService.ACTION_RUN_1MBPS_SCAN
                        }
                        context.startService(intent)
                    } else {
                        ScanAndReportManager.startOneMbpsScanAsync(context = context)
                    }
                },
                enabled = !scanState.isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = DarkBg),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("run_1mbps_scan_button")
            ) {
                if (scanState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBg, strokeWidth = 2.dp)
                } else {
                    Text(text = "สแกน 1 Mbps", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = {
                    ScanAndReportManager.generateAndSaveReport(
                        context = context,
                        firebaseEmail = currentUser?.email.orEmpty(),
                        firebaseUid = currentUser?.uid.orEmpty(),
                        includeAccountDetails = false,
                        includeServerEndpoints = false
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("generate_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Generate Redacted Report",
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "บันทึกรายงาน", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent)
            }
        }
    }

    if (showConsentDialog) {
        ReportConsentPreviewDialog(
            firebaseEmail = currentUser?.email.orEmpty(),
            firebaseUid = currentUser?.uid.orEmpty(),
            isUploading = reportState.isUploadingDrive,
            onDismiss = { showConsentDialog = false },
            onConfirmUpload = { includeAccount, includeEndpoints ->
                showConsentDialog = false
                scope.launch {
                    ScanAndReportManager.enqueueOrUploadReport(
                        context = context,
                        firebaseEmail = currentUser?.email.orEmpty(),
                        firebaseUid = currentUser?.uid.orEmpty(),
                        includeAccountDetails = includeAccount,
                        includeServerEndpoints = includeEndpoints
                    )
                }
            }
        )
    }
}

@Composable
private fun ReportConsentPreviewDialog(
    firebaseEmail: String,
    firebaseUid: String,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onConfirmUpload: (includeAccountDetails: Boolean, includeServerEndpoints: Boolean) -> Unit
) {
    val context = LocalContext.current
    var includeAccountDetails by remember { mutableStateOf(false) }
    var includeServerEndpoints by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf(false) }

    val previewJson = remember(firebaseEmail, firebaseUid, includeAccountDetails, includeServerEndpoints) {
        ScanAndReportManager.buildPreviewReportJson(
            context = context,
            firebaseEmail = firebaseEmail,
            firebaseUid = firebaseUid,
            includeAccountDetails = includeAccountDetails,
            includeServerEndpoints = includeServerEndpoints
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        modifier = Modifier.testTag("report_consent_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.PrivacyTip,
                contentDescription = "Report Privacy Review",
                tint = CyanAccent
            )
        },
        title = {
            Text(
                text = "ตรวจสอบข้อมูลและความยินยอมก่อนส่ง",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ค่าเริ่มต้นจะปิดบังอีเมล, UID และ Host/Port ด้วยแฮช SHA-256 สามารถเลือกเปิดเผยเฉพาะส่วนที่ต้องการได้ด้านล่าง:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "แนบอีเมลและ UID จริง",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = includeAccountDetails,
                        onCheckedChange = { includeAccountDetails = it },
                        modifier = Modifier.testTag("include_account_details_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBg,
                            checkedTrackColor = CyanAccent
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "แนบ Host / Port เซิร์ฟเวอร์จริง",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = includeServerEndpoints,
                        onCheckedChange = { includeServerEndpoints = it },
                        modifier = Modifier.testTag("include_server_endpoints_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBg,
                            checkedTrackColor = CyanAccent
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkBg)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = previewJson,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { consentChecked = !consentChecked }
                        .testTag("consent_confirm_checkbox"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyanAccent,
                            checkmarkColor = DarkBg
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ฉันได้ตรวจสอบตัวอย่างรายงาน JSON และยินยอมให้อัปโหลด",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmUpload(includeAccountDetails, includeServerEndpoints) },
                enabled = consentChecked && !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = TextPrimary),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("consent_submit_upload_button")
            ) {
                Text("ยืนยันส่งเข้า Drive / คิว")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("ยกเลิก", color = TextSecondary)
            }
        }
    )
}
