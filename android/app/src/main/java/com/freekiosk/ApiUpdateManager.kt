package com.freekiosk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class UpdateValidationException(message: String) : IllegalArgumentException(message)

/** Validates an uploaded APK, stages settings recovery, then updates FreeKiosk. */
class ApiUpdateManager(private val context: Context) {
    companion object {
        private const val MIN_APK_SIZE = 50_000L
        private const val MAX_APK_SIZE = 200L * 1024L * 1024L
    }

    @Synchronized
    fun prepareAndSchedule(uploadedFile: File): JSONObject {
        if (!BuildConfig.ENABLE_SELF_UPDATE) {
            throw UpdateValidationException("Self-update is disabled in this build")
        }
        if (!uploadedFile.isFile) throw UpdateValidationException("APK upload is missing")
        if (uploadedFile.length() !in MIN_APK_SIZE..MAX_APK_SIZE) {
            throw UpdateValidationException("APK size must be between 50 KB and 200 MB")
        }
        if (context.filesDir.usableSpace < uploadedFile.length() * 2) {
            throw UpdateValidationException("Not enough free storage to stage the update")
        }

        val candidate = archiveInfo(uploadedFile)
            ?: throw UpdateValidationException("Uploaded file is not a valid APK")
        if (candidate.packageName != context.packageName) {
            throw UpdateValidationException("APK package must be ${context.packageName}")
        }

        val current = installedInfo()
        val candidateVersionCode = versionCode(candidate)
        val currentVersionCode = versionCode(current)
        if (candidateVersionCode <= currentVersionCode) {
            throw UpdateValidationException(
                "APK versionCode $candidateVersionCode must be newer than installed versionCode $currentVersionCode"
            )
        }
        if (!hasMatchingSigningCertificate(current, candidate)) {
            throw UpdateValidationException("APK signing certificate does not match the installed app")
        }

        val updatesDirectory = File(context.filesDir, "updates").apply { mkdirs() }
        updatesDirectory.listFiles()?.forEach { it.delete() }
        val stagedApk = File(updatesDirectory, "freekiosk-$candidateVersionCode.apk")
        uploadedFile.inputStream().use { input ->
            stagedApk.outputStream().use { output -> input.copyTo(output) }
        }
        val apkHash = sha256(stagedApk)
        val recovery = SettingsHistoryStore.stageLatestForUpdate(context, candidateVersionCode)

        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { install(stagedApk, candidateVersionCode) }
                .onFailure { Log.e("ApiUpdateManager", "Update install failed", it) }
        }, 1_200)

        return JSONObject().apply {
            put("accepted", true)
            put("packageName", candidate.packageName)
            put("versionName", candidate.versionName ?: "")
            put("versionCode", candidateVersionCode)
            put("sha256", apkHash)
            put("size", stagedApk.length())
            put("settingsSnapshot", recovery.optString("snapshot"))
            put("message", "Update validated; installation will start shortly")
        }
    }

    private fun install(apk: File, versionCode: Long) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }

            val resultIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                putExtra("source", "api")
                putExtra("targetVersionCode", versionCode)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                flags,
            )
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun archiveInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    private fun installedInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageInfo(context.packageName, flags)
    }

    private fun hasMatchingSigningCertificate(current: PackageInfo, candidate: PackageInfo): Boolean {
        val currentCertificates = signatures(current, includeHistory = true).map { sha256(it.toByteArray()) }.toSet()
        val candidateCertificates = signatures(candidate, includeHistory = false).map { sha256(it.toByteArray()) }.toSet()
        return currentCertificates.intersect(candidateCertificates).isNotEmpty()
    }

    private fun signatures(info: PackageInfo, includeHistory: Boolean): Array<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyArray()
            if (includeHistory && !signingInfo.hasMultipleSigners()) {
                signingInfo.signingCertificateHistory ?: emptyArray()
            } else {
                signingInfo.apkContentsSigners ?: emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
