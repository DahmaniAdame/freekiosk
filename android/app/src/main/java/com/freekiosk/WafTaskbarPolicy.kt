package com.freekiosk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Process
import android.util.Log

/**
 * Removes the AOSP Launcher3 tablet taskbar while child-facing UI is active on Samsung WAF.
 *
 * This firmware implements the bottom taskbar as a privileged NAVIGATION_BAR_PANEL window.
 * It remains above application overlays and ignores immersive policy. Launcher3 only creates
 * that window for configurations whose smallest width is at least 600dp. The smallest safe
 * density override (577dpi on the WAF's 3840x2160/480dpi panel) selects the phone navigation
 * profile without changing the physical resolution. Android's normal navigation window then
 * obeys FreeKiosk's immersive/lock-task policy.
 *
 * The previous density override is preserved and restored when Lock Mode exits. This policy is
 * deliberately gated on the WAF side-menu provider so it cannot affect ordinary Android devices.
 */
object WafTaskbarPolicy {
    private const val TAG = "WafTaskbarPolicy"
    private const val WAF_CONTROLLER_AUTHORITY = "com.xbh.navisetting.controller"
    private const val PREFS_NAME = "FreeKioskLifecyclePolicies"
    private const val KEY_APPLIED = "waf_taskbar_density_applied"
    private const val KEY_HAD_OVERRIDE = "waf_taskbar_had_density_override"
    private const val KEY_ORIGINAL_OVERRIDE = "waf_taskbar_original_density_override"
    private const val TARGET_DENSITY = 577
    private const val DISPLAY_ID = 0
    private const val WINDOW_MANAGER_DESCRIPTOR = "android.view.IWindowManager"

    // Verified against this WAF's Android 14 framework IWindowManager Stub.
    private const val TRANSACTION_GET_INITIAL_DISPLAY_DENSITY = 10
    private const val TRANSACTION_GET_BASE_DISPLAY_DENSITY = 11
    private const val TRANSACTION_SET_FORCED_DISPLAY_DENSITY_FOR_USER = 13
    private const val TRANSACTION_CLEAR_FORCED_DISPLAY_DENSITY_FOR_USER = 14

    private data class DensityState(
        val physical: Int,
        val override: Int?,
    )

    fun hideForKiosk(context: Context): Boolean {
        if (!isSamsungWaf(context)) return false
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS unavailable; cannot suppress WAF taskbar")
            return false
        }

        val windowManager = getWindowManagerBinder(context)
        if (windowManager == null) {
            Log.w(TAG, "Could not access the WAF window manager binder")
            return false
        }

        val current = readDensityState(windowManager)
        if (current == null) {
            Log.w(TAG, "Could not read WAF display density")
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APPLIED, false)) {
            // During an in-place upgrade the diagnostic override may already be active. Treat
            // our exact target on the stock 480dpi panel as temporary, not as an admin override.
            val originalOverride = current.override?.takeUnless {
                it == TARGET_DENSITY && current.physical == 480
            }
            val saved = prefs.edit()
                .putBoolean(KEY_APPLIED, true)
                .putBoolean(KEY_HAD_OVERRIDE, originalOverride != null)
                .putInt(KEY_ORIGINAL_OVERRIDE, originalOverride ?: current.physical)
                .commit()
            if (!saved) {
                Log.w(TAG, "Could not remember the original WAF density")
                return false
            }
        }

        if (current.override == TARGET_DENSITY) return true

        val commandSucceeded = setForcedDensity(windowManager, TARGET_DENSITY)
        val applied = commandSucceeded &&
            readDensityState(windowManager)?.override == TARGET_DENSITY
        if (applied) {
            Log.d(TAG, "Samsung WAF Launcher3 taskbar suppressed for child-facing kiosk UI")
        } else {
            Log.w(TAG, "Could not suppress the WAF Launcher3 taskbar")
        }
        return applied
    }

    fun restoreAfterKiosk(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APPLIED, false)) return false

        val hadOverride = prefs.getBoolean(KEY_HAD_OVERRIDE, false)
        val originalOverride = prefs.getInt(KEY_ORIGINAL_OVERRIDE, 480)
        val windowManager = getWindowManagerBinder(context)
        if (windowManager == null) {
            Log.w(TAG, "Could not access the WAF window manager binder during restore")
            return false
        }
        val commandSucceeded = if (hadOverride) {
            setForcedDensity(windowManager, originalOverride)
        } else {
            clearForcedDensity(windowManager)
        }
        val restoredState = readDensityState(windowManager)
        val restored = commandSucceeded && if (hadOverride) {
            restoredState?.override == originalOverride
        } else {
            restoredState?.override == null
        }

        if (restored) {
            check(
                prefs.edit()
                    .remove(KEY_APPLIED)
                    .remove(KEY_HAD_OVERRIDE)
                    .remove(KEY_ORIGINAL_OVERRIDE)
                    .commit()
            ) { "Could not clear remembered WAF density state" }
            Log.d(TAG, "Previous WAF display density restored for admin UI or kiosk exit")
        } else {
            Log.w(TAG, "Could not restore WAF display density")
        }
        return restored
    }

    private fun isSamsungWaf(context: Context): Boolean =
        context.packageManager.resolveContentProvider(WAF_CONTROLLER_AUTHORITY, 0) != null

    private fun getWindowManagerBinder(context: Context): IBinder? = runCatching {
        // Apps cannot discover WindowManagerService through the command-line ServiceManager
        // client on this firmware. WindowManagerImpl already holds the authorized binder used
        // to add application windows; these two fields are UNSUPPORTED (greylist), not BLOCKED.
        val manager = context.getSystemService(Context.WINDOW_SERVICE)
            ?: error("WindowManager service is unavailable")
        val globalField = manager.javaClass.getDeclaredField("mGlobal").apply {
            isAccessible = true
        }
        val global = globalField.get(manager)
            ?: error("WindowManagerGlobal is unavailable")
        val serviceField = global.javaClass.getDeclaredField("sWindowManagerService").apply {
            isAccessible = true
        }
        (serviceField.get(null) as? IInterface)?.asBinder()
    }.onFailure { error ->
        Log.w(TAG, "Window manager binder lookup failed: ${error.message}")
    }.getOrNull()

    private fun readDensityState(windowManager: IBinder): DensityState? {
        val physical = transactForInt(
            windowManager,
            TRANSACTION_GET_INITIAL_DISPLAY_DENSITY,
            DISPLAY_ID,
        ) ?: return null
        val base = transactForInt(
            windowManager,
            TRANSACTION_GET_BASE_DISPLAY_DENSITY,
            DISPLAY_ID,
        ) ?: return null
        return DensityState(physical, base.takeUnless { it == physical })
    }

    private fun setForcedDensity(windowManager: IBinder, density: Int): Boolean = transactForUnit(
        windowManager,
        TRANSACTION_SET_FORCED_DISPLAY_DENSITY_FOR_USER,
        DISPLAY_ID,
        density,
        Process.myUid() / 100000,
    )

    private fun clearForcedDensity(windowManager: IBinder): Boolean = transactForUnit(
        windowManager,
        TRANSACTION_CLEAR_FORCED_DISPLAY_DENSITY_FOR_USER,
        DISPLAY_ID,
        Process.myUid() / 100000,
    )

    private fun transactForInt(
        windowManager: IBinder,
        transactionCode: Int,
        vararg values: Int,
    ): Int? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(WINDOW_MANAGER_DESCRIPTOR)
            values.forEach(data::writeInt)
            check(windowManager.transact(transactionCode, data, reply, 0)) {
                "Window manager rejected transaction $transactionCode"
            }
            reply.readException()
            reply.readInt()
        } catch (error: Exception) {
            Log.w(TAG, "Window manager read transaction failed: ${error.message}")
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactForUnit(
        windowManager: IBinder,
        transactionCode: Int,
        vararg values: Int,
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(WINDOW_MANAGER_DESCRIPTOR)
            values.forEach(data::writeInt)
            check(windowManager.transact(transactionCode, data, reply, 0)) {
                "Window manager rejected transaction $transactionCode"
            }
            reply.readException()
            true
        } catch (error: Exception) {
            Log.w(TAG, "Window manager write transaction failed: ${error.message}")
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
