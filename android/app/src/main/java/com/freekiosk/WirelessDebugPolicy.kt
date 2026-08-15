package com.freekiosk

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Keeps Android debugging available independently of the kiosk lifecycle.
 *
 * A Device Owner is allowed to control Settings.Global.ADB_ENABLED. Wireless debugging is a
 * separate secure setting on Android 11+, so that part additionally needs WRITE_SECURE_SETTINGS
 * (FreeKiosk's managed-device provisioning grants it once). The preference is stored in device
 * encrypted storage so it can be re-applied at LOCKED_BOOT_COMPLETED, before React Native and
 * credential-encrypted AsyncStorage are available.
 *
 * Stock Android does not expose an app/DevicePolicyManager API for setting adbd's legacy TCP
 * listener port. `adb tcpip 5555` remains the provisioning command for that listener. This policy
 * keeps ADB and Android wireless debugging enabled and preserves the host's pairing authorization;
 * it never claims that an unavailable fixed port was enabled.
 */
object WirelessDebugPolicy {
    private const val PREFS_NAME = "wireless_debug_policy"
    private const val KEY_ENABLED = "keep_wireless_debug_enabled"
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val ADB_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"

    data class Status(
        val configured: Boolean,
        val deviceOwner: Boolean,
        val writeSecureSettings: Boolean,
        val adbEnabled: Boolean,
        val wirelessDebugEnabled: Boolean,
        val legacyPort5555Active: Boolean,
        val error: String? = null,
    )

    /** Enabled by default for managed FreeKiosk deployments; the admin setting can opt out. */
    fun isEnabled(context: Context): Boolean = try {
        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    } catch (_: Exception) {
        true
    }

    fun setEnabled(context: Context, enabled: Boolean): Status {
        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
        return apply(context, enabled)
    }

    fun reapply(context: Context): Status = apply(context, isEnabled(context))

    fun currentStatus(context: Context): Status = buildStatus(context, isEnabled(context), null)

    private fun apply(context: Context, enabled: Boolean): Status {
        var error: String? = null
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isOwner = dpm.isDeviceOwnerApp(context.packageName)
        val hasWriteSecure = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

        if (!isOwner) {
            error = "Device Owner is required"
        } else {
            try {
                val admin = ComponentName(context, DeviceAdminReceiver::class.java)
                dpm.setGlobalSetting(admin, Settings.Global.ADB_ENABLED, if (enabled) "1" else "0")
            } catch (e: Exception) {
                error = "Unable to set ADB policy: ${e.message ?: e.javaClass.simpleName}"
            }

            if (hasWriteSecure) {
                try {
                    // Zero is Android's documented sentinel for non-expiring authorizations.
                    // Without this, Android can remove an inactive host key and unexpectedly
                    // require a new pairing even though wireless debugging remains enabled.
                    Settings.Global.putLong(
                        context.contentResolver,
                        ADB_ALLOWED_CONNECTION_TIME,
                        if (enabled) 0L else Settings.Global.getLong(
                            context.contentResolver,
                            ADB_ALLOWED_CONNECTION_TIME,
                            0L,
                        ),
                    )
                    Settings.Global.putInt(
                        context.contentResolver,
                        ADB_WIFI_ENABLED,
                        if (enabled) 1 else 0,
                    )
                } catch (e: Exception) {
                    if (error == null) {
                        error = "Unable to set wireless debugging: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            } else if (enabled && error == null) {
                error = "WRITE_SECURE_SETTINGS is required for wireless debugging"
            }
        }

        val status = buildStatus(context, enabled, error)
        if (status.error == null) {
            DebugLog.d(
                "WirelessDebugPolicy",
                "Applied enabled=$enabled adb=${status.adbEnabled} wifi=${status.wirelessDebugEnabled} port5555=${status.legacyPort5555Active}",
            )
        } else {
            DebugLog.errorProduction("WirelessDebugPolicy", status.error)
        }
        return status
    }

    private fun buildStatus(context: Context, configured: Boolean, error: String?): Status {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return Status(
            configured = configured,
            deviceOwner = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false),
            writeSecureSettings = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED,
            adbEnabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0,
            ) == 1,
            wirelessDebugEnabled = Settings.Global.getInt(
                context.contentResolver,
                ADB_WIFI_ENABLED,
                0,
            ) == 1,
            legacyPort5555Active = isLocalPortOpen(5555),
            error = error,
        )
    }

    private fun isLocalPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 150)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
