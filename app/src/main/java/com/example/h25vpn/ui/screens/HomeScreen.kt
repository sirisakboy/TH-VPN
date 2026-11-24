package com.example.h25vpn.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.h25vpn.R
import com.example.h25vpn.data.ConnectionState
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.ui.components.ConnectButton
import com.example.h25vpn.ui.components.ServerCard
import com.example.h25vpn.ui.components.SpeedGauge
import com.example.h25vpn.ui.components.SpeedTestCard
import com.example.h25vpn.ui.components.TweakCard
import com.example.h25vpn.ui.theme.AmberWarning
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBg
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.EmeraldGreen
import com.example.h25vpn.ui.theme.GoldAccent
import com.example.h25vpn.ui.theme.PurpleAccent
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary
import com.example.h25vpn.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToServers: () -> Unit,
    onNavigateToTweaks: () -> Unit,
    onNavigateToPromoHub: () -> Unit,
    onNavigateToProxyShare: () -> Unit,
    onNavigateToSplitTunnel: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val connectionState by VpnRepository.connectionState.collectAsState()
    val selectedServer by VpnRepository.selectedServer.collectAsState()
    val selectedTweak by VpnRepository.selectedTweak.collectAsState()
    val networkStats by VpnRepository.networkStats.collectAsState()
    val sessionSeconds by VpnRepository.sessionDurationSec.collectAsState()
    val isSyncing by VpnRepository.isSyncingServers.collectAsState()
    val lastSpeedTest by VpnRepository.lastSpeedTest.collectAsState()

    var showSpeedTestDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnRepository.connect(context)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("กรุณากดอนุญาตสิทธิ์ VPN เพื่อเชื่อมต่อ")
            }
        }
    }

    fun handleConnectClick() {
        when (connectionState) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> {
                VpnRepository.disconnect(context)
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnPermissionLauncher.launch(vpnIntent)
                } else {
                    VpnRepository.connect(context)
                }
            }
            ConnectionState.DISCONNECTING -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top Brand Header featuring Arena VPN MotorBoy Mascot Logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0E1D34), Color(0xFF16324F))
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(CyanAccent, GoldAccent)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.main_icon),
                            contentDescription = "Arena VPN Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Arena VPN",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(GoldAccent.copy(alpha = 0.18f))
                                    .border(0.8.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TH FREE-NET",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldAccent
                                )
                            }
                        }
                        Text(
                            text = "SECURE • FAST • PRIVATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = CyanAccent
                        )
                    }
                }

                // Header Actions: Speed Test pill + Logs console
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GoldAccent.copy(alpha = 0.15f))
                            .border(1.dp, GoldAccent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable { showSpeedTestDialog = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("action_speed_test_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed Test",
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "เช็คสปีด",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToLogs,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .testTag("action_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Logs",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Highlight Banner: Thai SIM Promo Hub & Auto Free Server Sync
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigateToPromoHub)
                ) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = "Thai SIM Promo Hub",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "โปรซิม & เน็ตฟรีไทย (สมัครในแอป)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "AIS Play • True เรียนฟรี/Viber • DTAC • My NT",
                            fontSize = 10.sp,
                            color = CyanAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(EmeraldGreen.copy(alpha = 0.16f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                        .clickable(enabled = !isSyncing) {
                            VpnRepository.syncFreeCloudServers(silent = false)
                            scope.launch {
                                snackbarHostState.showSnackbar("กำลังดึงเซิร์ฟเวอร์ฟรีล่าสุดอัตโนมัติ...")
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync Free Servers",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSyncing) "ซิงค์..." else "อัปเดตโหนดฟรี",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Big Animated Connect Button
            ConnectButton(
                connectionState = connectionState,
                sessionTimerText = VpnRepository.formatDuration(sessionSeconds),
                onClick = { handleConnectClick() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Server Selector Card
            SectionLabel(text = "เซิร์ฟเวอร์ที่เชื่อมต่อ (SERVER NODE)")
            ServerCard(
                server = selectedServer,
                onClick = onNavigateToServers
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tweak / Payload Selector Card
            SectionLabel(text = "โปรเสริม / เน็ตฟรีค่ายมือถือไทย (CARRIER SNI & PAYLOAD)")
            TweakCard(
                tweak = selectedTweak,
                onClick = onNavigateToTweaks
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Network Stats & Speed Gauge
            SectionLabel(text = "ความเร็วรับ-ส่งข้อมูลจริง (LIVE TRAFFIC)")
            SpeedGauge(
                stats = networkStats,
                connectionState = connectionState
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated Speed Test Card + Start Button!
            SectionLabel(text = "ทดสอบความเร็วอินเทอร์เน็ต (SPEED TEST)")
            SpeedTestCard(
                summary = lastSpeedTest,
                onRunSpeedTest = { showSpeedTestDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Tool Grid (2x2 + Speed Test Action) — Spacious & easy to tap on all screen sizes
            SectionLabel(text = "เครื่องมือด่วน (QUICK TOOLS)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Default.Speed,
                    title = "Speed Test",
                    subtitle = "วัดสปีดเน็ตจริง",
                    tint = GoldAccent,
                    onClick = { showSpeedTestDialog = true },
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Default.SimCard,
                    title = "สมัครโปรซิม",
                    subtitle = "AIS/True/DTAC/NT",
                    tint = AmberWarning,
                    onClick = onNavigateToPromoHub,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Default.Tune,
                    title = "แยกแอปธนาคาร",
                    subtitle = "Split Tunnel",
                    tint = CyanAccent,
                    onClick = onNavigateToSplitTunnel,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Default.Share,
                    title = "ปล่อยฮอตสปอต",
                    subtitle = "แชร์ VPN ไม่รูท",
                    tint = EmeraldGreen,
                    onClick = onNavigateToProxyShare,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Default.Assessment,
                    title = "เช็คค่าปิง",
                    subtitle = "Ping โหนดทั้งหมด",
                    tint = PurpleAccent,
                    onClick = {
                        VpnRepository.pingAllServers()
                        scope.launch {
                            snackbarHostState.showSnackbar("กำลังทดสอบค่าปิงทุกเซิร์ฟเวอร์...")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showSpeedTestDialog) {
        SpeedTestDialog(onDismiss = { showSpeedTestDialog = false })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun QuickActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}
