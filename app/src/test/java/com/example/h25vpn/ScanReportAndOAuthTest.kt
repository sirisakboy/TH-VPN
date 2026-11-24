package com.example.h25vpn

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.h25vpn.auth.GoogleDriveHelper
import com.example.h25vpn.auth.SigningVerifier
import com.example.h25vpn.data.ScanAndReportManager
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
    fun `one mbps scanner qualifies reachable nodes above 1 Mbps and rejects slow or offline nodes`() = runTest {
        val servers = listOf(
            VpnServer(id = "s1", name = "Fast TH", country = "Thailand", countryCode = "th", city = "Bangkok", host = "1.1.1.1", port = 443),
            VpnServer(id = "s2", name = "Offline Node", country = "Singapore", countryCode = "sg", city = "SG", host = "10.0.0.1", port = 443)
        )

        val fastScan = ScanAndReportManager.runOneMbpsScan(
            servers = servers,
            minThresholdMbps = 1.0f,
            latencyProbe = { host, _ -> if (host == "1.1.1.1") 25 else -1 },
            bandwidthProbe = { 4.2f }
        )
        assertEquals(2, fastScan.totalNodes)
        assertEquals(1, fastScan.passingNodes)
        assertTrue(fastScan.results.first { it.serverId == "s1" }.passedMinSpeed)
        assertFalse(fastScan.results.first { it.serverId == "s2" }.passedMinSpeed)

        val slowScan = ScanAndReportManager.runOneMbpsScan(
            servers = servers,
            minThresholdMbps = 1.0f,
            latencyProbe = { _, _ -> 30 },
            bandwidthProbe = { 0.6f }
        )
        assertEquals(0, slowScan.passingNodes)
    }

    @Test
    fun `diagnostic report persists locally and keeps Drive permission separate from Firebase login`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ScanAndReportManager.updateDriveAuthorization(
            authorized = false,
            accessToken = "",
            statusMessage = "Drive not yet authorized"
        )

        val report = ScanAndReportManager.generateAndSaveReport(
            context = context,
            firebaseEmail = "sirisakkhacha@gmail.com",
            firebaseUid = "uid-test-123"
        )

        assertEquals(SigningVerifier.EXPECTED_PACKAGE_NAME, report.packageName)
        assertEquals("sirisakkhacha@gmail.com", report.firebaseUserEmail)
        assertFalse("Firebase login must NOT imply Drive authorization", report.driveScopeAuthorized)
        assertTrue(report.webClientConfigured)

        val reportFile = File(context.filesDir, "thvpn_diagnostic_report.json")
        assertTrue(reportFile.exists())

        val uploadBlocked = GoogleDriveHelper.uploadReportToDrive(
            accessToken = "",
            fileName = "test.json",
            jsonContent = "{}"
        )
        assertFalse(uploadBlocked.success)

        val multipart = GoogleDriveHelper.buildMultipartPayload("b123", "report.json", """{"ok":true}""")
        assertTrue(multipart.contains("report.json"))
        assertTrue(multipart.contains("""{"ok":true}"""))
    }

    @Test
    fun `settings screen renders OAuth, Drive permission, 1 Mbps scan, and generates report on click`() {
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
        composeTestRule.onNodeWithTag("check_drive_permission_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_drive_upload_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("one_mbps_scan_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_scan_1mbps_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("run_1mbps_scan_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("generate_report_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(ScanAndReportManager.reportUiState.value.lastReportJson.contains(SigningVerifier.EXPECTED_PACKAGE_NAME))
    }
}
