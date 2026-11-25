package com.example.h25vpn

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.h25vpn.auth.DriveAuthCheckResult
import com.example.h25vpn.auth.DriveUploadResult
import com.example.h25vpn.auth.GoogleDriveHelper
import com.example.h25vpn.auth.SigningVerifier
import com.example.h25vpn.data.CarrierTweak
import com.example.h25vpn.data.DnsMode
import com.example.h25vpn.data.ScanAndReportManager
import com.example.h25vpn.data.TunnelPathProbe
import com.example.h25vpn.data.TunnelProbeOutcome
import com.example.h25vpn.data.TunnelProtocol
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.data.VpnServer
import com.example.h25vpn.ui.screens.SettingsScreen
import com.example.h25vpn.ui.theme.H25VPNTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanReportAndOAuthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `package name and google services oauth config match permanent values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(SigningVerifier.EXPECTED_PACKAGE_NAME, context.packageName)

        val webClientId = context.getString(R.string.default_web_client_id)
        assertTrue(
            "Expected default_web_client_id to end with .apps.googleusercontent.com",
            webClientId.endsWith(".apps.googleusercontent.com")
        )

        assertTrue(SigningVerifier.EXPECTED_SHA1.matches(Regex("^([0-9A-F]{2}:){19}[0-9A-F]{2}$")))
        assertTrue(SigningVerifier.EXPECTED_SHA256.matches(Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")))
        assertEquals(
            "0E:80:C6:7B:78:D2:A5:AB:A4:3C:61:7D:65:5C:BD:D4:89:9F:0F:22",
            SigningVerifier.EXPECTED_SHA1
        )
    }

    @Test
    fun `per-path tunnel scanner qualifies only nodes passing via tunnel and blocks unproxied nodes`() = runTest {
        val servers = listOf(
            VpnServer(
                id = "s-fast",
                name = "Xray TH Fast",
                country = "Thailand",
                countryCode = "th",
                city = "Bangkok",
                host = "203.0.113.10",
                port = 443,
                protocol = TunnelProtocol.V2RAY_VLESS
            ),
            VpnServer(
                id = "s-slow",
                name = "SSH SG Slow",
                country = "Singapore",
                countryCode = "sg",
                city = "SG",
                host = "203.0.113.20",
                port = 443,
                protocol = TunnelProtocol.SSH_SSL
            ),
            VpnServer(
                id = "s-ovpn",
                name = "VPNGate OVPN",
                country = "Japan",
                countryCode = "jp",
                city = "Tokyo",
                host = "203.0.113.30",
                port = 1194,
                protocol = TunnelProtocol.OPENVPN_UDP
            )
        )

        val fakeProbe = object : TunnelPathProbe {
            override suspend fun probeNode(
                context: Context?,
                server: VpnServer,
                tweak: CarrierTweak,
                dnsMode: DnsMode,
                activeProxyPort: Int?,
                activeServerId: String?
            ): TunnelProbeOutcome {
                return when (server.id) {
                    "s-fast" -> TunnelProbeOutcome(
                        tcpPingMs = 28,
                        measuredMbps = 3.6f,
                        testedViaTunnel = true,
                        tunnelStatus = "ผ่านอุโมงค์ VLESS จริง"
                    )
                    "s-slow" -> TunnelProbeOutcome(
                        tcpPingMs = 45,
                        measuredMbps = 0.7f,
                        testedViaTunnel = true,
                        tunnelStatus = "ผ่านอุโมงค์ SSH/SOCKS5 จริง"
                    )
                    else -> TunnelProbeOutcome(
                        tcpPingMs = 22,
                        measuredMbps = 5.0f, // Even if ping is good, testedViaTunnel=false must NOT pass!
                        testedViaTunnel = false,
                        tunnelStatus = "OpenVPN ต้องสลับเชื่อมต่อ TUN หลักก่อน"
                    )
                }
            }
        }

        val result = ScanAndReportManager.runOneMbpsScan(
            servers = servers,
            minThresholdMbps = 1.0f,
            pathProbe = fakeProbe
        )

        assertEquals(3, result.totalNodes)
        assertEquals(1, result.passingNodes)

        val fast = result.results.first { it.serverId == "s-fast" }
        val slow = result.results.first { it.serverId == "s-slow" }
        val ovpn = result.results.first { it.serverId == "s-ovpn" }

        assertTrue(fast.testedViaTunnel)
        assertTrue(fast.passedMinSpeed)
        assertTrue(slow.testedViaTunnel)
        assertFalse(slow.passedMinSpeed)
        assertFalse(ovpn.testedViaTunnel)
        assertFalse(ovpn.passedMinSpeed)
    }

    @Test
    fun `diagnostic report redacts PII and server endpoints by default unless explicitly consented`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ScanAndReportManager.updateDriveAuthorization(
            authorized = false,
            accessToken = "",
            statusMessage = "Drive not yet authorized"
        )

        val redactedReport = ScanAndReportManager.generateAndSaveReport(
            context = context,
            firebaseEmail = "sirisakkhacha@gmail.com",
            firebaseUid = "uid-secret-999"
        )

        assertTrue(redactedReport.redacted)
        assertTrue(redactedReport.firebaseUserEmail.startsWith("sha256:"))
        assertTrue(redactedReport.firebaseUid.startsWith("sha256:"))
        assertFalse(redactedReport.firebaseUserEmail.contains("sirisakkhacha@gmail.com"))
        assertFalse(redactedReport.firebaseUid.contains("uid-secret-999"))
        assertFalse("Firebase login must NOT imply Drive authorization", redactedReport.driveScopeAuthorized)
        assertTrue(redactedReport.webClientConfigured)

        val fullReport = ScanAndReportManager.generateAndSaveReport(
            context = context,
            firebaseEmail = "sirisakkhacha@gmail.com",
            firebaseUid = "uid-secret-999",
            includeAccountDetails = true,
            includeServerEndpoints = true
        )
        assertFalse(fullReport.redacted)
        assertEquals("sirisakkhacha@gmail.com", fullReport.firebaseUserEmail)
        assertEquals("uid-secret-999", fullReport.firebaseUid)

        val reportFile = File(context.filesDir, "thvpn_diagnostic_report.json")
        assertTrue(reportFile.exists())

        val uploadBlocked = GoogleDriveHelper.uploadReportToDrive(
            accessToken = "",
            fileName = "test.json",
            jsonContent = "{}"
        )
        assertFalse(uploadBlocked.success)
    }

    @Test
    fun `offline upload enqueues report persistently and flushes when connectivity and Drive token return`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queueFile = File(context.filesDir, "pending_drive_uploads.json")
        if (queueFile.exists()) queueFile.delete()
        ScanAndReportManager.initialize(context)
        ScanAndReportManager.updateDriveAuthorization(
            authorized = false,
            accessToken = "",
            statusMessage = "Offline"
        )

        ScanAndReportManager.enqueueOrUploadReport(
            context = context,
            firebaseEmail = "sirisakkhacha@gmail.com",
            firebaseUid = "uid-123",
            includeAccountDetails = false,
            includeServerEndpoints = false,
            isOnlineOverride = false
        )

        assertEquals(1, ScanAndReportManager.reportUiState.value.pendingQueueCount)
        assertTrue(queueFile.exists())
        assertTrue(queueFile.readText().contains("thvpn_report_"))

        val uploadedNames = mutableListOf<String>()
        val flushed = ScanAndReportManager.flushPendingDriveUploads(
            context = context,
            onlyIfOnline = false,
            driveAuthChecker = { DriveAuthCheckResult.Authorized("fake-valid-token") },
            uploader = { token, name, _ ->
                assertEquals("fake-valid-token", token)
                uploadedNames.add(name)
                DriveUploadResult(success = true, fileId = "drive-id-1", fileName = name, message = "อัปโหลดขึ้น Google Drive สำเร็จ (ID: drive-id-1)")
            }
        )

        assertEquals(1, flushed)
        assertEquals(1, uploadedNames.size)
        assertEquals(0, ScanAndReportManager.reportUiState.value.pendingQueueCount)
        assertTrue(ScanAndReportManager.reportUiState.value.driveAuthorized)
    }

    @Test
    fun `settings screen shows queue status and consent preview dialog before uploading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { com.google.firebase.FirebaseApp.initializeApp(context) }
        VpnRepository.initialize(context)

        composeTestRule.setContent {
            H25VPNTheme {
                SettingsScreen(onNavigateBack = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("oauth_signing_status_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("drive_auth_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("drive_queue_status").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("check_drive_permission_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("one_mbps_scan_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("run_1mbps_scan_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("generate_report_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val savedJson = ScanAndReportManager.reportUiState.value.lastReportJson
        assertTrue(savedJson.contains(SigningVerifier.EXPECTED_PACKAGE_NAME))
        assertTrue(savedJson.contains("\"redacted\": true"))

        composeTestRule.onNodeWithTag("test_drive_upload_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("report_consent_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("consent_submit_upload_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("consent_confirm_checkbox").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("consent_submit_upload_button").assertIsEnabled()
    }
}
