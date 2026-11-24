package com.example.h25vpn.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** OAuth tokens stay in memory and are never embedded in the APK, logs, or reports. */
class DriveReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private val uploadMutex = Mutex()
    }

    override suspend fun doWork(): Result = uploadMutex.withLock { uploadReports() }

    private suspend fun uploadReports(): Result {
        ConnectionReports.initialize(applicationContext, scheduleWork = false)
        if (!ConnectionReports.sync.value || !ConnectionReports.enabled.value) return Result.success()
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)?.account
        if (account == null) {
            ConnectionReports.updateStatus("กรุณาลงชื่อเข้าใช้ Google เพื่อส่งรายงาน")
            return Result.failure()
        }
        return try {
            val token = GoogleAuthUtil.getToken(applicationContext, account, "oauth2:$SCOPE")
            val folderQuery = "trashed = false and mimeType = 'application/vnd.google-apps.folder' and appProperties has { key='thVpnReports' and value='1' }"
            val folders = request("GET", "https://www.googleapis.com/drive/v3/files?q=${encode(folderQuery)}&fields=files(id)", token)
                .getJSONArray("files")
            val folder = if (folders.length() > 0) folders.getJSONObject(0).getString("id") else {
                request("POST", "https://www.googleapis.com/drive/v3/files", token,
                    JSONObject().put("name", "TH-VPN Connection Reports")
                        .put("mimeType", "application/vnd.google-apps.folder")
                        .put("appProperties", JSONObject().put("thVpnReports", "1")).toString()).getString("id")
            }
            for (file in ConnectionReports.pending()) {
                currentCoroutineContext().ensureActive()
                if (!ConnectionReports.sync.value || !ConnectionReports.enabled.value) return Result.success()
                val content = synchronized(ConnectionReports) { if (file.exists()) file.readText() else null } ?: continue
                val id = JSONObject(content).getString("reportId")
                // Recover from an upload whose response was lost without creating duplicate reports.
                val query = "trashed = false and '$folder' in parents and appProperties has { key='reportId' and value='$id' }"
                val exists = request("GET", "https://www.googleapis.com/drive/v3/files?q=${encode(query)}&fields=files(id)", token)
                    .getJSONArray("files").length() > 0
                if (!exists) {
                    val boundary = "thvpn_$id"
                    val metadata = JSONObject().put("name", "thvpn-report-$id.json")
                        .put("parents", org.json.JSONArray().put(folder))
                        .put("appProperties", JSONObject().put("reportId", id))
                    val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                        "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$content\r\n--$boundary--\r\n"
                    request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", token,
                        body, "multipart/related; boundary=$boundary")
                }
                currentCoroutineContext().ensureActive()
                ConnectionReports.acknowledge(file)
            }
            ConnectionReports.updateStatus("ส่งรายงานแล้ว • โฟลเดอร์ TH-VPN Connection Reports")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserRecoverableAuthException) {
            ConnectionReports.updateStatus("ต้องอนุญาต Google อีกครั้ง กดเชื่อม Google Drive")
            Result.failure()
        } catch (e: Exception) {
            ConnectionReports.updateStatus("ส่งไม่สำเร็จ เก็บคิวไว้แล้ว จะลองใหม่เมื่อออนไลน์ / ตรวจสิทธิ์ Google")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private suspend fun request(method: String, url: String, token: String, body: String? = null,
                                contentType: String = "application/json; charset=UTF-8"): JSONObject {
        currentCoroutineContext().ensureActive()
        check(ConnectionReports.sync.value && ConnectionReports.enabled.value) { "Consent revoked" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (conn.responseCode == 401) GoogleAuthUtil.clearToken(applicationContext, token)
            check(conn.responseCode in 200..299) { "Drive HTTP ${conn.responseCode}" }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
