package com.example.h25vpn.ui.screens

import android.app.Activity
import android.net.ConnectivityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.h25vpn.data.*
import com.example.h25vpn.service.TunnelSpeedProbe
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionReportsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by ConnectionReports.enabled.collectAsState()
    val sync by ConnectionReports.sync.collectAsState()
    val status by ConnectionReports.status.collectAsState()
    var consent by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var externalResult by remember { mutableStateOf("") }
    val probe = remember { TunnelSpeedProbe() }
    DisposableEffect(Unit) { onDispose { probe.cancel() } }
    val recovery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && ConnectionReports.enabled.value) {
            ConnectionReports.setSync(true)
        } else ConnectionReports.updateStatus("ยังไม่ได้อนุญาตส่งรายงาน เก็บข้อมูลในเครื่องเท่านั้น")
    }
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { signedIn ->
                scope.launch {
                    try {
                        val account = signedIn.account ?: error("No Google account")
                        withContext(Dispatchers.IO) {
                            // Request interactive recovery if Play Services needs extra consent; discard the token.
                            GoogleAuthUtil.getToken(context, account, "oauth2:${DriveReportWorker.SCOPE}")
                            Unit
                        }
                        if (ConnectionReports.enabled.value) {
                            ConnectionReports.setSync(true)
                            ConnectionReports.updateStatus("อนุญาตแล้ว กำลังเตรียมส่งรายงาน")
                        }
                    } catch (e: UserRecoverableAuthException) {
                        recovery.launch(e.intent)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ConnectionReports.updateStatus("อนุญาต Drive ไม่สำเร็จ ตรวจอินเทอร์เน็ตและการตั้งค่า OAuth")
                    }
                }
            }
            .addOnFailureListener {
                ConnectionReports.updateStatus("ลงชื่อเข้าใช้ไม่สำเร็จ/ยกเลิก ตรวจ OAuth package และ SHA-1 ของ APK")
            }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("รายงานการเชื่อมต่อ • Google Drive", style = MaterialTheme.typography.titleMedium)
        Text("บันทึกผลสแกน/ความเร็วและรหัสเส้นทางแบบแฮช ไม่เก็บบัญชี VPN, UUID, Host/path จริง หรือเว็บที่เข้า เก็บในเครื่องสูงสุด 200 รายงาน")
        Row {
            Checkbox(checked = enabled, onCheckedChange = { if (it) consent = true else ConnectionReports.setEnabled(false) })
            Text("บันทึกรายงานในเครื่อง", modifier = Modifier.padding(top = 12.dp))
        }
        if (enabled) {
            Button(onClick = {
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestScopes(Scope(DriveReportWorker.SCOPE)).build()
                signIn.launch(GoogleSignIn.getClient(context, options).signInIntent)
            }) { Text("เชื่อม Google Drive และอนุญาตส่งอัตโนมัติ") }
            Text("ส่งไป Drive ของบัญชีที่เลือกแบบส่วนตัว เฉพาะไฟล์ที่แอปสร้าง ต้องตั้งค่า OAuth ก่อนใช้งานจริง")
            Row {
                Checkbox(checked = sync, onCheckedChange = {
                    if (!it) ConnectionReports.setSync(false)
                    else if (GoogleSignIn.getLastSignedInAccount(context) != null) ConnectionReports.setSync(true)
                    else ConnectionReports.updateStatus("กดเชื่อม Google Drive ก่อน")
                })
                Text("ส่งคิวเมื่อออนไลน์ (ระบบอาจหน่วงเวลา)", modifier = Modifier.padding(top = 12.dp))
            }
            Text(status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { preview = ConnectionReports.preview() }) { Text("ดูข้อมูลที่จะส่ง") }
                OutlinedButton(onClick = { ConnectionReports.clear() }) { Text("ลบคิวในเครื่อง") }
            }
        }
        Button(enabled = !testing, onClick = {
            if (VpnRepository.connectionState.value !in listOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR)) {
                externalResult = "กรุณาหยุด VPN/สแกนของเราก่อน แล้วเปิด VPN แอปอื่น"
            } else {
                testing = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            ConnectionReports.record("external_vpn_probe_started")
                            probe.measure(null, context.getSystemService(ConnectivityManager::class.java))
                        }
                        externalResult = "VPN ที่มีอยู่: %.2f Mbps • ไม่ทราบไฟล์/เส้นทางภายใน".format(result.mbps)
                        ConnectionReports.record(if (result.passed) "external_vpn_probe_passed" else "external_vpn_probe_slow", mbps = result.mbps)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        externalResult = "ทดสอบไม่ได้: ไม่มี VPN ที่แอปเข้าถึงได้ หรือปลายทางทดสอบไม่ตอบสนอง"
                        ConnectionReports.record("external_vpn_probe_failed")
                    } finally { testing = false }
                }
            }
        }) { Text(if (testing) "กำลังทดสอบ…" else "ทดสอบ VPN แอปอื่น (ใช้ดาต้า ≤1 MiB + overhead)") }
        if (testing) TextButton(onClick = { probe.cancel() }) { Text("ยกเลิกทดสอบ") }
        if (externalResult.isNotBlank()) Text(externalResult)
        Text("โหมดนี้ไม่เปิด VPN ของเรา ไม่ดึงไฟล์จากแอปอื่น และไม่พิสูจน์ว่าโปรซิมฟรี เมื่อส่งรายงานแล้ว แจ้งชื่อโฟลเดอร์ในแชตให้ช่วยอ่านได้")
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("อนุญาตบันทึกข้อมูลวิเคราะห์?") },
        text = { Text("บันทึกเวลา ผลเชื่อมต่อ/สแกน ความเร็ว ping TCP ประเภทอุโมงค์ พอร์ต และ fingerprint ที่แยกเส้นทางได้ ไม่บันทึกรหัสผ่าน UUID IP/Host/path จริง หรือประวัติเข้าเว็บ การส่ง Drive ต้องอนุญาตอีกขั้น หยุดส่งและลบคิวได้ในหน้านี้ ไฟล์ที่อัปโหลดแล้วต้องลบใน Drive ของคุณ") },
        confirmButton = { TextButton(onClick = { ConnectionReports.setEnabled(true); consent = false }) { Text("อนุญาตบันทึก") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("ยกเลิก") } })
    preview?.let { text -> AlertDialog(onDismissRequest = { preview = null }, title = { Text("ตัวอย่างคิวล่าสุด 5 รายงาน") },
        text = { Text(text.take(6000)) }, confirmButton = { TextButton(onClick = { preview = null }) { Text("ปิด") } }) }
}
