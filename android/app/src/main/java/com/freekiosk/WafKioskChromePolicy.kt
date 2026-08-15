package com.freekiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Keeps both pieces of Samsung WAF chrome in one lifecycle.
 *
 * The OEM side menus and the Launcher3 taskbar are controlled by different systems. Applying
 * them from unrelated callbacks allowed one policy to succeed while the other was not ready yet,
 * leaving a half-hidden kiosk. Child-facing kiosk UI uses a single, periodically reconciled
 * policy. PIN and authenticated FreeKiosk Settings retain it; only an explicit Lock Mode exit
 * restores Android/OEM chrome.
 */
object WafKioskChromePolicy {
    private const val TAG = "WafKioskChromePolicy"
    private const val RECONCILE_INTERVAL_MS = 2_000L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var childFacingActive = false

    @Volatile
    private var applicationContext: Context? = null

    private val reconcileRunnable = object : Runnable {
        override fun run() {
            reconcileIfActive()
        }
    }

    /**
     * Switch the complete WAF chrome policy for the current FreeKiosk route.
     *
     * `true` means the kiosk itself or an allowed external app is child-facing. `false` means
     * Lock Mode has explicitly exited.
     */
    @Synchronized
    fun setChildFacingActive(context: Context, active: Boolean): Boolean {
        val appContext = context.applicationContext
        handler.removeCallbacks(reconcileRunnable)

        if (!SamsungWafDevice.isWaf(appContext)) {
            childFacingActive = false
            applicationContext = null
            return false
        }

        childFacingActive = active
        applicationContext = appContext.takeIf { active }

        return if (active) {
            val applied = enforceOnce(appContext)
            handler.postDelayed(reconcileRunnable, RECONCILE_INTERVAL_MS)
            applied
        } else {
            restoreOnce(appContext)
        }
    }

    /** Reapply both policies immediately and keep reconciling them while child-facing. */
    fun enforceForKiosk(context: Context): Boolean = setChildFacingActive(context, true)

    /** Stop reconciliation and restore both OEM surfaces for administrator use or kiosk exit. */
    fun restoreForAdmin(context: Context): Boolean = setChildFacingActive(context, false)

    @Synchronized
    private fun reconcileIfActive() {
        val context = applicationContext
        if (!childFacingActive || context == null) return

        enforceOnce(context)
        if (childFacingActive) {
            handler.postDelayed(reconcileRunnable, RECONCILE_INTERVAL_MS)
        }
    }

    private fun enforceOnce(context: Context): Boolean {
        val policyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)

        val sideMenusHidden = runCatching {
            WafSideMenuPolicy.hideForKiosk(context, policyManager, admin)
        }.onFailure { error ->
            Log.w(TAG, "Could not enforce WAF side-menu policy: ${error.message}")
        }.getOrDefault(false)

        val taskbarHidden = runCatching {
            WafTaskbarPolicy.hideForKiosk(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not enforce WAF taskbar policy: ${error.message}")
        }.getOrDefault(false)

        val edgeGesturesBlocked = runCatching {
            WafEdgeGesturePolicy.blockForKiosk(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not enforce WAF edge-gesture policy: ${error.message}")
        }.getOrDefault(false)

        val desktopWindowingBlocked = runCatching {
            WafWindowingPolicy.blockForKiosk(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not enforce WAF desktop-windowing policy: ${error.message}")
        }.getOrDefault(false)

        val accessibilitySafetyReady = runCatching {
            FreeKioskAccessibilityService.isRunning() ||
                FreeKioskAccessibilityService.ensureEnabledForDeviceOwner(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not enable WAF accessibility safety service: ${error.message}")
        }.getOrDefault(false)

        return sideMenusHidden &&
            taskbarHidden &&
            edgeGesturesBlocked &&
            desktopWindowingBlocked &&
            accessibilitySafetyReady
    }

    private fun restoreOnce(context: Context): Boolean {
        val policyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)

        val sideMenusRestored = runCatching {
            WafSideMenuPolicy.restoreAfterKiosk(context, policyManager, admin)
        }.onFailure { error ->
            Log.w(TAG, "Could not restore WAF side-menu policy: ${error.message}")
        }.getOrDefault(false)

        val taskbarRestored = runCatching {
            WafTaskbarPolicy.restoreAfterKiosk(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not restore WAF taskbar policy: ${error.message}")
        }.getOrDefault(false)

        val edgeGesturesRestored = runCatching {
            WafEdgeGesturePolicy.restoreAfterKiosk(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not restore WAF edge-gesture policy: ${error.message}")
        }.getOrDefault(false)

        val desktopWindowingRestored = runCatching {
            WafWindowingPolicy.restoreAfterKiosk(context)
        }.onFailure { error ->
            Log.w(TAG, "Could not restore WAF desktop-windowing policy: ${error.message}")
        }.getOrDefault(false)

        return sideMenusRestored ||
            taskbarRestored ||
            edgeGesturesRestored ||
            desktopWindowingRestored
    }

}
