package com.freekiosk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Samsung's "auto_disabler" moves unused apps to DISABLED_UNTIL_USED. A kiosk grid tap is
 * not "use" in its eyes, and FreeKiosk cannot set enabled state (signature permission).
 * An in-place reinstall of the app's own installed APK resets the state and keeps its data,
 * and a Device Owner may install silently. Callers must be Device Owner.
 */
object AutoDisabledAppRepair {
    private const val TAG = "AutoDisabledAppRepair"
    private const val RESULT_ACTION = "com.freekiosk.AUTO_DISABLED_REPAIR_RESULT"
    private const val REPAIR_TIMEOUT_MS = 60_000L
    private const val RETRY_INTERVAL_MS = 10 * 60_000L
    private val inFlight = ConcurrentHashMap<String, CountDownLatch>()
    private val lastAttempt = ConcurrentHashMap<String, Long>()

    fun isAutoDisabled(pm: PackageManager, packageName: String): Boolean = try {
        pm.getApplicationEnabledSetting(packageName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
    } catch (_: IllegalArgumentException) {
        false
    }

    /** Starts a background reinstall unless one is already running for this package. */
    fun repairAsync(context: Context, info: ApplicationInfo) {
        val packageName = info.packageName
        // The watchdog asks every second; a reinstall that keeps failing must not loop.
        val now = System.currentTimeMillis()
        if (now - (lastAttempt[packageName] ?: 0L) < RETRY_INTERVAL_MS) return
        val latch = CountDownLatch(1)
        if (inFlight.putIfAbsent(packageName, latch) != null) return
        lastAttempt[packageName] = now
        Thread({
            try {
                reinstall(context.applicationContext, info)
                waitUntilEnabled(context.packageManager, packageName)
            } catch (error: Exception) {
                Log.e(TAG, "Could not re-enable $packageName: ${error.message}")
            } finally {
                inFlight.remove(packageName)
                latch.countDown()
            }
        }, "repair-$packageName").start()
    }

    /** Blocks (off the UI thread) until a running repair for [packageName] finishes. */
    fun awaitRepair(packageName: String, timeoutMs: Long = 20_000L) {
        inFlight[packageName]?.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun reinstall(context: Context, info: ApplicationInfo) {
        Log.w(TAG, "${info.packageName} is DISABLED_UNTIL_USED; reinstalling in place")
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(info.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            val apks = listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
            apks.forEachIndexed { index, path ->
                val file = File(path)
                session.openWrite("apk$index", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
            }
            // Nobody listens for the result; completion is observed through the enabled state.
            val result = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(RESULT_ACTION).setPackage(context.packageName),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
            )
            session.commit(result.intentSender)
        }
    }

    private fun waitUntilEnabled(pm: PackageManager, packageName: String) {
        val deadline = System.currentTimeMillis() + REPAIR_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            // The enabled state flips before the replaced package's providers resolve again;
            // a micro-app launch needs MicroAppStation's catalog provider.
            if (!isAutoDisabled(pm, packageName) && providersResolve(pm, packageName)) {
                Log.w(TAG, "Re-enabled $packageName")
                return
            }
            Thread.sleep(250)
        }
        Log.e(TAG, "$packageName still DISABLED_UNTIL_USED after reinstall")
    }

    private fun providersResolve(pm: PackageManager, packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
            .flatMap { it.authority.orEmpty().split(';') }
            .filter { it.isNotBlank() }
            .all { pm.resolveContentProvider(it, 0) != null }
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
