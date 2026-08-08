package com.freekiosk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Applies an OEM-compatible full-system immersive policy while kiosk mode is active.
 * MainActivity can hide its own bars with WindowInsets, but external apps own their
 * windows. The global policy is therefore used only when WRITE_SECURE_SETTINGS was
 * provisioned on the kiosk device, and the previous value is restored on admin exit.
 */
object KioskSystemUiPolicy {
    private const val TAG = "KioskSystemUiPolicy"
    private const val PREFS = "freekiosk_system_ui_policy"
    private const val KEY_APPLIED = "navigation_immersive_applied"
    private const val KEY_HAD_PREVIOUS = "had_previous_policy"
    private const val KEY_PREVIOUS = "previous_policy_control"
    private const val KEY_HAD_PREVIOUS_HEADS_UP = "had_previous_heads_up"
    private const val KEY_PREVIOUS_HEADS_UP = "previous_heads_up"
    private const val POLICY_CONTROL = "policy_control"
    private const val HEADS_UP_NOTIFICATIONS = "heads_up_notifications_enabled"
    private const val KIOSK_POLICY = "immersive.full=*"

    fun enable(context: Context): Boolean {
        val wafTaskbarSuppressed = WafTaskbarPolicy.hideForKiosk(context)
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED) {
            DebugLog.d(TAG, "WRITE_SECURE_SETTINGS unavailable; relying on lock task for navigation UI")
            return wafTaskbarSuppressed
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_APPLIED, false)) {
                val previous = Settings.Global.getString(context.contentResolver, POLICY_CONTROL)
                val previousHeadsUp = Settings.Global.getString(
                    context.contentResolver,
                    HEADS_UP_NOTIFICATIONS
                )
                prefs.edit()
                    .putBoolean(KEY_APPLIED, true)
                    .putBoolean(KEY_HAD_PREVIOUS, previous != null)
                    .putString(KEY_PREVIOUS, previous.orEmpty())
                    .putBoolean(KEY_HAD_PREVIOUS_HEADS_UP, previousHeadsUp != null)
                    .putString(KEY_PREVIOUS_HEADS_UP, previousHeadsUp.orEmpty())
                    .apply()
            }
            Settings.Global.putString(context.contentResolver, POLICY_CONTROL, KIOSK_POLICY)
            Settings.Global.putInt(context.contentResolver, HEADS_UP_NOTIFICATIONS, 0)
            DebugLog.d(TAG, "Global full immersive and heads-up suppression enabled")
            true
        } catch (e: Exception) {
            DebugLog.d(TAG, "Could not enable global navigation immersive policy: ${e.message}")
            wafTaskbarSuppressed
        }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APPLIED, false)) {
            WafTaskbarPolicy.restoreAfterKiosk(context)
            return
        }

        try {
            if (prefs.getBoolean(KEY_HAD_PREVIOUS, false)) {
                Settings.Global.putString(
                    context.contentResolver,
                    POLICY_CONTROL,
                    prefs.getString(KEY_PREVIOUS, "").orEmpty()
                )
            } else {
                Settings.Global.putString(context.contentResolver, POLICY_CONTROL, null)
            }
            if (prefs.getBoolean(KEY_HAD_PREVIOUS_HEADS_UP, false)) {
                Settings.Global.putString(
                    context.contentResolver,
                    HEADS_UP_NOTIFICATIONS,
                    prefs.getString(KEY_PREVIOUS_HEADS_UP, "").orEmpty()
                )
            } else {
                Settings.Global.putString(context.contentResolver, HEADS_UP_NOTIFICATIONS, null)
            }
            DebugLog.d(TAG, "Previous global system UI policy restored")
        } catch (e: Exception) {
            DebugLog.d(TAG, "Could not restore global navigation policy: ${e.message}")
        } finally {
            prefs.edit().clear().apply()
            WafTaskbarPolicy.restoreAfterKiosk(context)
        }
    }
}
