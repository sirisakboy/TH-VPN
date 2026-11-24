package com.example.h25vpn.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.h25vpn.BuildConfig
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
    val EXPECTED_SHA1 = BuildConfig.OAUTH_SIGNING_SHA1
    val EXPECTED_SHA256 = BuildConfig.OAUTH_SIGNING_SHA256

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
            val clientId = GoogleAuthHelper.webClientId(context)
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
            isSha1Match = EXPECTED_SHA1.isNotBlank() && sha1.equals(EXPECTED_SHA1, ignoreCase = true),
            isSha256Match = EXPECTED_SHA256.isNotBlank() && sha256.equals(EXPECTED_SHA256, ignoreCase = true),
            isWebClientConfigured = webClientConfigured
        )
    }

    fun digestHex(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
