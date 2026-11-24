package com.example.h25vpn.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.DarkSurfaceVariant
import com.example.h25vpn.ui.theme.EmeraldGreen
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary
import com.example.h25vpn.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@Composable
fun SpeedTestDialog(
    onDismiss: () -> Unit
) {
    var phase by remember { mutableStateOf("IDLE") } // IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR
    var downloadMbps by remember { mutableFloatStateOf(0f) }
    var uploadMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorDetail by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(250),
        label = "speedProgress"
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Speed Test",
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ทดสอบความเร็วเน็ตจริง",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "วัดความเร็วผ่านเครือข่ายปัจจุบัน / อุโมงค์ VPN (Cloudflare Edge)",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(135.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .border(
                        width = 3.dp,
                        color = when (phase) {
                            "DOWNLOAD" -> CyanAccent
                            "UPLOAD" -> EmeraldGreen
                            "DONE" -> EmeraldGreen
                            else -> DarkBorder
                        },
                        shape = CircleShape
                    )
            ) {
                if (phase == "PING" || phase == "DOWNLOAD" || phase == "UPLOAD") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(125.dp),
                        color = if (phase == "UPLOAD") EmeraldGreen else CyanAccent,
                        strokeWidth = 3.dp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val currentVal = when (phase) {
                        "UPLOAD" -> uploadMbps
                        else -> downloadMbps
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f", currentVal),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Mbps",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (phase) {
                            "IDLE" -> "พร้อมทดสอบ"
                            "PING" -> "กำลังวัด Ping..."
                            "DOWNLOAD" -> "กำลังดาวน์โหลด..."
                            "UPLOAD" -> "กำลังอัปโหลด..."
                            "ERROR" -> "เชื่อมต่อไม่สำเร็จ"
                            else -> "เสร็จสมบูรณ์"
                        },
                        fontSize = 11.sp,
                        color = CyanAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (phase == "PING" || phase == "DOWNLOAD" || phase == "UPLOAD") {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanAccent,
                    trackColor = DarkSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (errorDetail.isNotEmpty()) {
                Text(errorDetail, fontSize = 11.sp, color = Color(0xFFFF6B6B))
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DOWNLOAD", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f Mbps", downloadMbps),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("UPLOAD", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f Mbps", uploadMbps),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PING", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (pingMs > 0) "$pingMs ms" else "-- ms",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) {
                    Text("ปิดหน้าต่าง", color = TextMuted)
                }

                Button(
                    onClick = {
                        if (phase == "IDLE" || phase == "DONE" || phase == "ERROR") {
                            scope.launch {
                                errorDetail = ""
                                downloadMbps = 0f
                                uploadMbps = 0f
                                pingMs = 0
                                progress = 0.05f
                                phase = "PING"

                                try {
                                    // 1. Real HTTP HEAD Latency
                                    val measuredPing = withContext(Dispatchers.IO) {
                                        val t0 = System.nanoTime()
                                        val conn = (URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection).apply {
                                            connectTimeout = 6000
                                            readTimeout = 6000
                                            requestMethod = "GET"
                                        }
                                        conn.inputStream.use { it.read() }
                                        conn.disconnect()
                                        ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                                    }
                                    pingMs = measuredPing
                                    progress = 0.2f

                                    // 2. Real HTTP Stream Download (5 MB payload or up to 5 seconds)
                                    phase = "DOWNLOAD"
                                    withContext(Dispatchers.IO) {
                                        val targetBytes = 5_000_000
                                        val conn = (URL("https://speed.cloudflare.com/__down?bytes=$targetBytes").openConnection() as HttpURLConnection).apply {
                                            connectTimeout = 8000
                                            readTimeout = 8000
                                        }
                                        val startNs = System.nanoTime()
                                        var totalRead = 0L
                                        val buf = ByteArray(16_384)
                                        conn.inputStream.use { input ->
                                            while (true) {
                                                val r = input.read(buf)
                                                if (r <= 0) break
                                                totalRead += r
                                                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                                                downloadMbps = ((totalRead * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
                                                progress = (0.2f + 0.45f * (totalRead.toFloat() / targetBytes).coerceIn(0f, 1f))
                                                if (elapsedSec > 6.0) break
                                            }
                                        }
                                        conn.disconnect()
                                    }

                                    // 3. Real HTTP POST Upload (1.5 MB payload)
                                    phase = "UPLOAD"
                                    withContext(Dispatchers.IO) {
                                        val uploadBytesTotal = 1_500_000
                                        val conn = (URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection).apply {
                                            connectTimeout = 8000
                                            readTimeout = 8000
                                            requestMethod = "POST"
                                            doOutput = true
                                            setChunkedStreamingMode(16_384)
                                        }
                                        val chunk = ByteArray(16_384) { 0x41 }
                                        val startNs = System.nanoTime()
                                        var sent = 0L
                                        conn.outputStream.use { out ->
                                            while (sent < uploadBytesTotal) {
                                                out.write(chunk)
                                                out.flush()
                                                sent += chunk.size
                                                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                                                uploadMbps = ((sent * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
                                                progress = (0.65f + 0.35f * (sent.toFloat() / uploadBytesTotal).coerceIn(0f, 1f))
                                                if (elapsedSec > 5.5) break
                                            }
                                        }
                                        runCatching { conn.responseCode }
                                        conn.disconnect()
                                    }

                                    progress = 1f
                                    phase = "DONE"
                                } catch (e: Exception) {
                                    errorDetail = "ทดสอบไม่สำเร็จ: ${e.message ?: "ไม่มีอินเทอร์เน็ต"}"
                                    phase = "ERROR"
                                }
                            }
                        }
                    },
                    enabled = phase == "IDLE" || phase == "DONE" || phase == "ERROR",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (phase == "DONE" || phase == "ERROR") "ทดสอบอีกครั้ง" else "เริ่มทดสอบ",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
