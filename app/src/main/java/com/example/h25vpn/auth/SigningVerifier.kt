package com.example.h25vpn.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.h25vpn.R
import java.security.MessageDigest

data class SigningVerificationResult(
    val packageName: String,
    val expectedPackageName: String,
    val actualSha1: String,
    val actualSha256: String,
    val expectedSha1: String,
    val expectedSha256: String,
    val isPackageMatch: Boolean,
    val isSha1Match: Boolean,
    val isSha256Match: Boolean,
    val isWebClientConfigured: Boolean
) {
    val isFullyVerified: Boolean
        get() = isPackageMatch && isSha1Match && isSha256Match && isWebClientConfigured
}

object SigningVerifier {
    const val EXPECTED_PACKAGE_NAME = "com.aistudio.h25vpn.bxtkp"
    const val EXPECTED_SHA1 = "0E:80:C6:7B:78:D2:A5:AB:A4:3C:61:7D:65:5C:BD:D4:89:9F:0F:22"
    const val EXPECTED_SHA256 =
        "A7:E5:7A:B1:E4:47:87:41:9C:7E:06:F8:BF:FA:17:DC:E9:50:E4:76:4D:3D:21:1C:42:D6:E4:94:D8:C5:10:44"

    @Suppress("DEPRECATION")
    fun verifyApkOAuthBinding(context: Context): SigningVerificationResult {
        val pkgName = context.packageName
        var sha1 = ""
        var sha256 = ""

        try {
            val pm = context.packageManager
            val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                val signers = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
                signers?.firstOrNull()?.toByteArray()
            } else {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
            }

            if (certBytes != null) {
                sha1 = digestHex(certBytes, "SHA-1")
                sha256 = digestHex(certBytes, "SHA-256")
            }
        } catch (_: Exception) {
        }

        val webClientConfigured = try {
            val clientId = context.getString(R.string.default_web_client_id).trim()
            clientId.isNotBlank() && clientId.contains("-")
        } catch (_: Exception) {
            false
        }

        return SigningVerificationResult(
            packageName = pkgName,
            expectedPackageName = EXPECTED_PACKAGE_NAME,
            actualSha1 = sha1,
            actualSha256 = sha256,
            expectedSha1 = EXPECTED_SHA1,
            expectedSha256 = EXPECTED_SHA256,
            isPackageMatch = pkgName == EXPECTED_PACKAGE_NAME,
            isSha1Match = sha1.equals(EXPECTED_SHA1, ignoreCase = true),
            isSha256Match = sha256.equals(EXPECTED_SHA256, ignoreCase = true),
            isWebClientConfigured = webClientConfigured
        )
    }

    fun digestHex(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
