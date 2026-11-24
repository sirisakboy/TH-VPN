package com.example.h25vpn.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.h25vpn.data.CarrierConfigLink
import com.example.h25vpn.data.PublicConfigSources
import com.example.h25vpn.data.PromoHubData
import com.example.h25vpn.data.SimPromoItem
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.ui.components.CarrierBadgeIcon
import com.example.h25vpn.ui.theme.AmberWarning
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBackground
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.DarkSurfaceVariant
import com.example.h25vpn.ui.theme.EmeraldGreen
import com.example.h25vpn.ui.theme.PurpleAccent
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary
import com.example.h25vpn.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun PromoHubScreen(
    onPromoApplied: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncing by VpnRepository.isSyncingServers.collectAsState()
    val selectedTweak by VpnRepository.selectedTweak.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    var selectedCarrier by remember { mutableStateOf("All") }
    var bugHostInput by remember { mutableStateOf(selectedTweak.bugHostSni.ifBlank { "aisplay.ais.co.th" }) }
    var bugHostResult by remember { mutableStateOf("") }
    var isTestingHost by remember { mutableStateOf(false) }

    val carriers = listOf("All", "AIS", "TrueMove", "DTAC", "My NT")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "โปรเสริม & เน็ตฟรีไทย",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "รวมโปรซิมยอดฮิต กดสมัคร USSD และตั้งค่าทะลุโปรอัตโนมัติ",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyanAccent.copy(alpha = 0.15f))
                    .border(1.dp, CyanAccent, RoundedCornerShape(10.dp))
                    .clickable {
                        openExternalUrl(context, PromoHubData.PORTAL_HOME_URL)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Portal",
                        tint = CyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("เว็บหลัก", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = activeTab,
            containerColor = DarkSurface,
            contentColor = CyanAccent,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("โปรซิม & โนโปร", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("เช็ค Bug Host", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("คลังไฟล์ค่าย", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeTab == 0 || activeTab == 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                carriers.forEach { carrier ->
                    val isSelected = selectedCarrier == carrier
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CyanAccent else DarkSurface)
                            .border(
                                1.dp,
                                if (isSelected) CyanAccent else DarkBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedCarrier = carrier }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = carrier,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.Black else TextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (activeTab) {
            0 -> {
                val filteredPromos = PromoHubData.simPromos.filter {
                    selectedCarrier == "All" || it.carrier.equals(selectedCarrier, ignoreCase = true)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredPromos, key = { it.id }) { promo ->
                        SimPromoCard(
                            promo = promo,
                            isActiveTweak = selectedTweak.id == promo.tweakIdToApply,
                            onDialUssd = { code -> dialUssdCode(context, code) },
                            onApplyTweak = {
                                if (VpnRepository.selectTweakById(promo.tweakIdToApply)) {
                                    Toast.makeText(
                                        context,
                                        "ตั้งค่าโปรไฟล์ ${promo.title} เรียบร้อย! กดเชื่อมต่อได้เลย",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onPromoApplied()
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ตัวตรวจสอบ Bug Host / SNI บนซิมของคุณ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ปิด Wi-Fi แล้วเปิดเน็ตมือถือ (4G/5G) จากนั้นกดทดสอบว่าโดเมนไหนตอบสนองผ่านซิมของคุณได้ฟรี (Port 80/443)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bugHostInput,
                        onValueChange = { bugHostInput = it },
                        label = { Text("โดเมน SNI / Bug Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Bug Host chips
                    val quickHosts = listOf(
                        "aisplay.ais.co.th",
                        "ais.speedtest.net",
                        "www.trueplookpanya.com",
                        "www.viber.com",
                        "dl.kgvn.garenanow.com",
                        "www.mybynt.com"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickHosts.forEach { h ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable { bugHostInput = h }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(h, fontSize = 11.sp, color = CyanAccent)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            isTestingHost = true
                            bugHostResult = "กำลังทดสอบ TCP/TLS ไปยัง $bugHostInput..."
                            scope.launch {
                                bugHostResult = VpnRepository.testCarrierBugHost(bugHostInput)
                                isTestingHost = false
                            }
                        },
                        enabled = !isTestingHost,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingHost) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("ทดสอบ Bug Host ทันที", fontWeight = FontWeight.Bold)
                    }

                    if (bugHostResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = bugHostResult,
                                fontSize = 13.sp,
                                color = if (bugHostResult.startsWith("✅")) EmeraldGreen else AmberWarning
                            )
                        }
                    }
                }
            }

            2 -> {
                val filteredFiles = PromoHubData.carrierConfigFiles.filter {
                    selectedCarrier == "All" || it.carrier.equals(selectedCarrier, ignoreCase = true)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PurpleAccent.copy(alpha = 0.12f))
                                .border(1.dp, PurpleAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "ฟีดสาธารณะด้านล่างเป็น VPN ทั่วไป ไม่รับรองว่าใช้กับโปรซิมได้ทุกค่าย ไฟล์ .ehi/.npvs เดิมยังไม่ยืนยันวันหมดอายุ กรุณาตรวจสอบจากผู้แจกก่อนใช้งาน",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { VpnRepository.syncFreeCloudServers() },
                            enabled = !syncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (syncing) "กำลังอัปเดตฟีดสาธารณะ…" else "อัปเดตเซิร์ฟเวอร์จากฟีดสาธารณะ")
                        }
                    }
                    items(PublicConfigSources.all, key = { it.id }) { source ->
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(source.title, color = CyanAccent, fontWeight = FontWeight.Bold)
                            Text("VPN ทั่วไป • ยังไม่ทดสอบกับโปรซิม • ${source.path}", color = TextSecondary, fontSize = 12.sp)
                            OutlinedButton(onClick = { openExternalUrl(context, source.pageUrl) }) {
                                Text("ดูแหล่งแจกสาธารณะ", color = TextPrimary)
                            }
                        }
                    }
                    items(filteredFiles, key = { it.id }) { cfg ->
                        CarrierConfigLinkCard(
                            item = cfg,
                            onUseBuiltIn = {
                                if (VpnRepository.selectTweakById(cfg.matchingTweakId)) {
                                    Toast.makeText(context, "เปิดใช้โปรไฟล์ในแอป TH-VPN แล้ว!", Toast.LENGTH_SHORT).show()
                                    onPromoApplied()
                                }
                            },
                            onDownloadExternal = {
                                openExternalUrl(context, cfg.pageUrl)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SimPromoCard(
    promo: SimPromoItem,
    isActiveTweak: Boolean,
    onDialUssd: (String) -> Unit,
    onApplyTweak: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(
                width = if (isActiveTweak) 1.5.dp else 1.dp,
                color = if (isActiveTweak) EmeraldGreen else DarkBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarrierBadgeIcon(carrier = promo.carrier, size = 26.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = promo.carrier,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = promo.priceText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = promo.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = "⚡ ${promo.speedText}",
            fontSize = 12.sp,
            color = AmberWarning
        )

        Spacer(modifier = Modifier.height(8.dp))

        promo.steps.forEach { step ->
            Text(
                text = step,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }

        if (promo.note.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📌 ${promo.note}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (promo.ussdCode.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onDialUssd(promo.ussdCode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("กด ${promo.ussdCode}", color = CyanAccent, fontSize = 12.sp)
                }
            }

            Button(
                onClick = onApplyTweak,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActiveTweak) EmeraldGreen else CyanAccent,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (isActiveTweak) Icons.Default.CheckCircle else Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isActiveTweak) "กำลังใช้งาน" else "ใช้โปรไฟล์นี้",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CarrierConfigLinkCard(
    item: CarrierConfigLink,
    onUseBuiltIn: () -> Unit,
    onDownloadExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item.carrier, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
            Text("${item.platform} • ${item.appRequired}", fontSize = 11.sp, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(item.subtitle, fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onUseBuiltIn,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
            ) {
                Text("ใช้ใน TH-VPN เลย", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDownloadExternal,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ตรวจสอบกับผู้แจก", fontSize = 12.sp, color = TextPrimary)
            }
        }
    }
}

private fun dialUssdCode(context: android.content.Context, ussd: String) {
    try {
        val encoded = Uri.encode(ussd)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encoded")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "ไม่สามารถเปิดแป้นโทรศัพท์: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "ไม่สามารถเปิดลิงก์: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
