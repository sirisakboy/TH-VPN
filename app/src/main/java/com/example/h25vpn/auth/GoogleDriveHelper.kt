package com.example.h25vpn.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed interface DriveAuthCheckResult {
    data class Authorized(val accessToken: String) : DriveAuthCheckResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthCheckResult
    data class Error(val message: String) : DriveAuthCheckResult
}

data class DriveUploadResult(
    val success: Boolean,
    val fileId: String = "",
    val fileName: String = "",
    val message: String = ""
)

object GoogleDriveHelper {
    private const val TAG = "DriveAuth"
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks Google Drive OAuth permission separately from Firebase Auth sign-in.
     * Even if the user is logged into Firebase Auth, Drive requires explicit scope authorization.
     */
    suspend fun checkOrRequestDrivePermission(context: Context): DriveAuthCheckResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            when {
                result.hasResolution() && result.pendingIntent != null -> {
                    DriveAuthCheckResult.NeedsConsent(result.pendingIntent!!)
                }
                !result.accessToken.isNullOrBlank() -> {
                    DriveAuthCheckResult.Authorized(result.accessToken!!)
                }
                else -> {
                    DriveAuthCheckResult.Error("Google Drive authorization returned no access token.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization check failed", e)
            DriveAuthCheckResult.Error(e.localizedMessage ?: "Drive authorization failed")
        }
    }

    fun extractTokenFromActivityResult(context: Context, data: Intent?): DriveAuthCheckResult {
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                DriveAuthCheckResult.Authorized(token)
            } else {
                DriveAuthCheckResult.Error("ไม่ได้รับสิทธิ์ Google Drive Access Token")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Drive consent declined or failed: status=${e.statusCode}", e)
            DriveAuthCheckResult.Error("ยังไม่ได้รับสิทธิ์ Google Drive (code ${e.statusCode})")
        } catch (e: Exception) {
            DriveAuthCheckResult.Error(e.localizedMessage ?: "Drive consent failed")
        }
    }

    fun buildMultipartPayload(boundary: String, fileName: String, jsonContent: String): String {
        val metadataJson = """{"name":"${fileName.replace("\"", "")}","mimeType":"application/json"}"""
        return buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(jsonContent)
            append("\r\n--$boundary--\r\n")
        }
    }

    suspend fun uploadReportToDrive(
        accessToken: String,
        fileName: String,
        jsonContent: String
    ): DriveUploadResult = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext DriveUploadResult(
                success = false,
                message = "กรุณาอนุญาตสิทธิ์ Google Drive ก่อนทดสอบอัปโหลด (ล็อกอิน Firebase อย่างเดียวไม่พอ)"
            )
        }

        val boundary = "thvpn_drive_${System.currentTimeMillis()}"
        val body = buildMultipartPayload(boundary, fileName, jsonContent)

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(DRIVE_UPLOAD_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: ""

            if (code in 200..299) {
                val parsed = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
                val fileId = parsed?.get("id")?.jsonPrimitive?.content.orEmpty()
                val uploadedName = parsed?.get("name")?.jsonPrimitive?.content ?: fileName
                DriveUploadResult(
                    success = true,
                    fileId = fileId,
                    fileName = uploadedName,
                    message = "อัปโหลดขึ้น Google Drive สำเร็จ (ID: ${fileId.ifBlank { "OK" }})"
                )
            } else {
                DriveUploadResult(
                    success = false,
                    message = "Google Drive HTTP $code: ${responseText.take(180)}"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Drive upload failed", e)
            DriveUploadResult(
                success = false,
                message = "อัปโหลดไม่สำเร็จ: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            conn?.disconnect()
        }
    }
}
