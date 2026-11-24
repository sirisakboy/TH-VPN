package com.example.h25vpn.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.h25vpn.R
import com.example.h25vpn.auth.GoogleAuthHelper
import com.example.h25vpn.data.DnsMode
import com.example.h25vpn.data.VpnRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.h25vpn.ui.theme.CoralRed
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBg
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.DarkSurfaceVariant
import com.example.h25vpn.ui.theme.EmeraldGreen
import com.example.h25vpn.ui.theme.PurpleAccent
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary
import com.example.h25vpn.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by remember { GoogleAuthHelper.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val selectedDns by VpnRepository.dnsMode.collectAsState()
    val mtu by VpnRepository.mtuSetting.collectAsState()
    val autoReconnect by VpnRepository.autoReconnect.collectAsState()
    val udpForwarding by VpnRepository.udpForwarding.collectAsState()

    var showDnsDialog by remember { mutableStateOf(false) }
    var showMtuDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSpeedTestDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(36.dp).testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ตั้งค่าระบบ & โปรโตคอล",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "ปรับแต่ง DNS, MTU, แบตเตอรี่ และการส่งข้อมูล",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Arena VPN Brand Hero Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, CyanAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.main_icon),
                contentDescription = "Arena VPN Logo",
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Arena VPN • MotorBoy Edition",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "TH-VPN Free-Net & SIM Promo Engine v1.0.0",
                    fontSize = 12.sp,
                    color = CyanAccent
                )
                Text(
                    text = "Xray VLESS/Reality • SSH SSL • OpenVPN3",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section: Google Account & OAuth
        SettingsSectionHeader(title = "บัญชี GOOGLE & OAUTH (GOOGLE ACCOUNT)")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
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
                            ?: if (currentUser != null) "เชื่อมต่อบัญชี Google แล้ว" else "ยังไม่ได้เชื่อมต่อบัญชี Google",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = currentUser?.email?.takeIf { it.isNotBlank() }
                            ?: "Package: ${context.packageName}",
                        fontSize = 12.sp,
                        color = if (currentUser != null) EmeraldGreen else TextSecondary
                    )
                }
            }

            if (authError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = authError ?: "",
                    fontSize = 12.sp,
                    color = CoralRed
                )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = DarkBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBg,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign in with Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        GoogleAuthHelper.signOut(
                            credentialManager = credentialManager,
                            scope = scope
                        )
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
                    Text(
                        text = "ออกจากระบบบัญชี Google",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section: Network & DNS
        SettingsSectionHeader(title = "เครือข่าย & DNS (NETWORK & DNS)")

        SettingsClickableItem(
            icon = Icons.Default.Dns,
            title = "เซิร์ฟเวอร์ DNS (DNS Resolver)",
            subtitle = selectedDns.title,
            onClick = { showDnsDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsClickableItem(
            icon = Icons.Default.Router,
            title = "ขนาดแพ็กเก็ต MTU",
            subtitle = "$mtu bytes",
            onClick = { showMtuDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsSwitchItem(
            icon = Icons.Default.Refresh,
            title = "เชื่อมต่อใหม่อัตโนมัติ (Auto-Reconnect)",
            subtitle = "ต่ออุโมงค์ VPN ใหม่อัตโนมัติเมื่อสลับสัญญาณซิม/Wi-Fi",
            checked = autoReconnect,
            onCheckedChange = { VpnRepository.setAutoReconnect(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsSwitchItem(
            icon = Icons.Default.Security,
            title = "ส่งแพ็กเก็ต UDP (UDP Forwarding)",
            subtitle = "เพิ่มความเสถียรสำหรับเล่นเกม RoV / FreeFire และโทรไลน์",
            checked = udpForwarding,
            onCheckedChange = { VpnRepository.setUdpForwarding(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Diagnostics & Battery
        SettingsSectionHeader(title = "เครื่องมือวิเคราะห์ & ระบบ (DIAGNOSTICS)")

        SettingsClickableItem(
            icon = Icons.Default.NetworkCheck,
            title = "ทดสอบความเร็วเน็ต (Speed Test)",
            subtitle = "วัดความเร็ว Download / Upload และค่า Ping จริง",
            onClick = { showSpeedTestDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsClickableItem(
            icon = Icons.Default.BatteryChargingFull,
            title = "ปลดล็อกประหยัดแบตเตอรี่ (กันแอปหลุด)",
            subtitle = "ป้องกันระบบ Android ปิด VPN เองขณะปิดหน้าจอ",
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(fallback)
                    } catch (ignored: Exception) {}
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section: About & Privacy
        SettingsSectionHeader(title = "ABOUT & PRIVACY")

        SettingsClickableItem(
            icon = Icons.Default.Info,
            title = "About TH-VPN",
            subtitle = "Version 1.0.0 (Xray + SSH/SSL + OpenVPN3)",
            onClick = { showAboutDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsClickableItem(
            icon = Icons.Default.Policy,
            title = "Zero-Logs Privacy Policy",
            subtitle = "Encryption and data retention commitments",
            onClick = { showPrivacyDialog = true }
        )

        Spacer(modifier = Modifier.height(30.dp))
    }

    // DNS Picker Dialog
    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("Select Tunnel DNS", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    DnsMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    VpnRepository.setDnsMode(mode)
                                    showDnsDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDns == mode,
                                onClick = {
                                    VpnRepository.setDnsMode(mode)
                                    showDnsDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                if (mode.primary.isNotEmpty()) {
                                    Text("${mode.primary}, ${mode.secondary}", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Close", color = CyanAccent)
                }
            },
            containerColor = DarkSurface
        )
    }

    // MTU Picker Dialog
    if (showMtuDialog) {
        val mtuOptions = listOf(1500, 1420, 1400, 1280)
        AlertDialog(
            onDismissRequest = { showMtuDialog = false },
            title = { Text("Select MTU Size", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    mtuOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    VpnRepository.setMtuSetting(option)
                                    showMtuDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mtu == option,
                                onClick = {
                                    VpnRepository.setMtuSetting(option)
                                    showMtuDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$option bytes", color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMtuDialog = false }) {
                    Text("Cancel", color = CyanAccent)
                }
            },
            containerColor = DarkSurface
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About TH-VPN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "TH-VPN Free-Net & Promo Edition v1.0.0",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "แอป VPN สายฟรีเน็ตและโปรเสริมค่ายมือถือไทย (AIS, TrueMove H, DTAC, My by NT) ขับเคลื่อนด้วย Xray-core (VLESS/VMess/Trojan/Reality), SSH+SSL/WS Payload Injector, OpenVPN3 C++ และ Hotspot Proxy Share แท้",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = CyanAccent)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Strict Zero-Logs Guarantee",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "H25 VPN does not store, monitor, or log your browsing history, DNS queries, traffic destination, or packet contents. All connection sessions are fully encrypted using AES-256-GCM cipher standards.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("I Understand", color = CyanAccent)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showSpeedTestDialog) {
        SpeedTestDialog(onDismiss = { showSpeedTestDialog = false })
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
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
                Icon(imageVector = icon, contentDescription = title, tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
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
                Icon(imageVector = icon, contentDescription = title, tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBg,
                checkedTrackColor = CyanAccent,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}
