package com.freekiosk

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the visible task limited to FreeKiosk and the user-facing kiosk apps.
 *
 * Lock task is the primary security boundary. This guard is a second layer for
 * vendor/system activities that some Android builds allow above a locked task.
 */
object KioskForegroundGuard {
    private const val TAG = "KioskForegroundGuard"
    private const val RECOVERY_COOLDOWN_MS = 500L
    private const val ACTIVE_PREFS = "freekiosk_active_app_guard"
    private const val ACTIVE_PACKAGE_KEY = "active_package"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val temporaryAllowedPackages = ConcurrentHashMap<String, Long>()

    @Volatile
    private var lastUserFacingPackage: String? = null

    @Volatile
    private var lastRecoveryTime = 0L

    fun isProtectionActive(context: Context): Boolean {
        val lockTaskActive = try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: Exception) {
            Log.w(TAG, "Could not read lock task state: ${e.message}")
            false
        }
        if (lockTaskActive) return true

        // Non-Device-Owner devices must leave Android screen pinning to open an
        // allowed second app. Keep the accessibility guard active from the saved
        // kiosk state during that transition.
        return readStorageValue(context, "@kiosk_enabled", "false") == "true"
    }

    fun isAllowedForeground(
        context: Context,
        packageName: String,
        className: String? = null
    ): Boolean {
        val activePackage = getActiveKioskPackage(context)
        if (packageName == context.packageName) return activePackage == null

        val temporaryAllowedUntil = temporaryAllowedPackages[packageName]
        if (temporaryAllowedUntil != null) {
            if (temporaryAllowedUntil >= System.currentTimeMillis()) return true
            temporaryAllowedPackages.remove(packageName)
        }

        val normalizedPackage = packageName.lowercase()
        val normalizedClass = className?.lowercase().orEmpty()

        // Settings packages/activities are never part of the child-facing task.
        if (normalizedPackage.contains("settings") ||
            normalizedClass.contains("settings") ||
            normalizedClass.contains("controlcenter") ||
            normalizedClass.contains("quicksettings")) {
            return false
        }

        // Lock-task packages are candidates shown on the kiosk home, not concurrent
        // foreground apps. Only the app explicitly selected by the user may own the
        // visible task. This prevents a whitelisted app's alarm/background process
        // (for example Netflix) from surfacing over another running kiosk app.
        if (packageName == activePackage) return true

        // Permit only tightly-scoped setup surfaces. SystemUI and the generic
        // android package are deliberately excluded: on non-Device-Owner OEM
        // builds they host the notification shade, navigation UI, power menu,
        // and other windows that must never replace the active child-facing app.
        if (packageName == "com.android.permissioncontroller" ||
            packageName == "com.google.android.permissioncontroller" ||
            packageName == "com.android.packageinstaller" ||
            packageName == "com.google.android.packageinstaller") {
            return true
        }

        val defaultInputMethod = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )?.substringBefore('/')
        } catch (_: Exception) {
            null
        }

        return packageName == defaultInputMethod &&
            (normalizedClass.contains("inputmethod") ||
                normalizedClass.contains("softinput") ||
                normalizedClass.contains("keyboard"))
    }

    fun noteAllowedForeground(context: Context, packageName: String) {
        if (packageName == getActiveKioskPackage(context)) {
            lastUserFacingPackage = packageName
        }
    }

    /**
     * Authorize an app selected by FreeKiosk's own grid for the launch transition.
     * The time limit only bridges native/AsyncStorage and OEM window-order races;
     * the persistent lock-task allowlist remains the long-term policy boundary.
     */
    fun authorizeKioskLaunch(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        temporaryAllowedPackages[packageName] = System.currentTimeMillis() + 15_000L
        lastUserFacingPackage = packageName
        context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_PACKAGE_KEY, packageName)
            .apply()
    }

    fun clearActiveKioskPackage(context: Context) {
        getActiveKioskPackage(context)?.let(temporaryAllowedPackages::remove)
        lastUserFacingPackage = null
        context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(ACTIVE_PACKAGE_KEY)
            .apply()
    }

    fun getActiveKioskPackage(context: Context): String? {
        return context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_PACKAGE_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    /** Permit a tightly-scoped native system workflow, such as Wi-Fi confirmation. */
    fun temporarilyAllowPackages(packageNames: Collection<String>, durationMs: Long = 120_000L) {
        val expiresAt = System.currentTimeMillis() + durationMs.coerceIn(1_000L, 300_000L)
        packageNames.filter(String::isNotBlank).forEach {
            temporaryAllowedPackages[it] = expiresAt
        }
    }

    fun revokeTemporaryPackages(packageNames: Collection<String>) {
        packageNames.forEach(temporaryAllowedPackages::remove)
    }

    fun handleAccessibilityWindow(
        service: AccessibilityService,
        event: AccessibilityEvent,
        packageName: String
    ) {
        if (!isProtectionActive(service)) return

        val className = event.className?.toString()
        if (isAllowedForeground(service, packageName, className)) {
            noteAllowedForeground(service, packageName)
            return
        }

        Log.w(TAG, "Blocked window: $packageName/${className.orEmpty()}")

        // Pop the unauthorized activity first so the active app's state remains intact.
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        } catch (_: Exception) {
        }

        // Then restore the previous allowed app as a reliable OEM-independent fallback.
        mainHandler.postDelayed({
            recoverAllowedForeground(service, "accessibility:$packageName")
        }, 120L)
    }

    fun recoverAllowedForeground(context: Context, reason: String): Boolean {
        if (!isProtectionActive(context)) return false

        val now = System.currentTimeMillis()
        if (now - lastRecoveryTime < RECOVERY_COOLDOWN_MS) return false
        lastRecoveryTime = now

        val activePackage = getActiveKioskPackage(context)
        val targetPackage = activePackage
            ?.takeIf { context.packageManager.getLaunchIntentForPackage(it) != null }
            ?: context.packageName

        return try {
            val intent = if (targetPackage == context.packageName) {
                Intent(context, MainActivity::class.java)
            } else {
                context.packageManager.getLaunchIntentForPackage(targetPackage)
                    ?: Intent(context, MainActivity::class.java)
            }.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }

            KioskTaskLauncher.launch(context, intent)
            Log.w(TAG, "Restored ${intent.component?.packageName ?: targetPackage} ($reason)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not restore kiosk foreground: ${e.message}")
            false
        }
    }

    fun getUserFacingPackages(context: Context): Set<String> {
        val packages = linkedSetOf(context.packageName)
        val displayMode = readStorageValue(context, "@kiosk_display_mode", "webview")
        if (displayMode != "external_app") return packages

        val externalMode = readStorageValue(context, "@kiosk_external_app_mode", "single")
        if (externalMode == "single") {
            readStorageValue(context, "@kiosk_external_app_package", "")
                .takeIf { it.isNotBlank() }
                ?.let(packages::add)
        } else {
            packages.addAll(readHomeScreenManagedApps(context))
        }
        return packages
    }

    private fun getPreferredKioskPackage(
        context: Context,
        userFacingPackages: Set<String>
    ): String {
        val displayMode = readStorageValue(context, "@kiosk_display_mode", "webview")
        val externalMode = readStorageValue(context, "@kiosk_external_app_mode", "single")
        if (displayMode == "external_app" && externalMode == "single") {
            val externalPackage =
                readStorageValue(context, "@kiosk_external_app_package", "")
            if (userFacingPackages.contains(externalPackage)) return externalPackage
        }
        return context.packageName
    }

    private fun getLockTaskPackages(context: Context): Set<String> {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val admin = ComponentName(context, DeviceAdminReceiver::class.java)
                dpm.getLockTaskPackages(admin).toSet()
            } else {
                getUserFacingPackages(context)
            }
        } catch (_: Exception) {
            getUserFacingPackages(context)
        }
    }

    private fun readHomeScreenManagedApps(context: Context): Set<String> {
        return try {
            val apps = JSONArray(readStorageValue(context, "@kiosk_managed_apps", "[]"))
            buildSet {
                for (index in 0 until apps.length()) {
                    val app = apps.getJSONObject(index)
                    if (app.optBoolean("showOnHomeScreen", false)) {
                        app.optString("packageName")
                            .takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun readStorageValue(context: Context, key: String, defaultValue: String): String {
        var database: SQLiteDatabase? = null
        return try {
            database = SQLiteDatabase.openDatabase(
                context.getDatabasePath("RKStorage").absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            database.rawQuery(
                "SELECT value FROM catalystLocalStorage WHERE key = ?",
                arrayOf(key)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: defaultValue else defaultValue
            }
        } catch (_: Exception) {
            defaultValue
        } finally {
            database?.close()
        }
    }
}
