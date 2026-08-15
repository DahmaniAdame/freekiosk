package com.freekiosk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Applies an OEM-compatible full-system immersive policy while child-facing UI is active.
 * MainActivity can hide its own bars with WindowInsets, but external apps own their
 * windows. The global policy is therefore used only when WRITE_SECURE_SETTINGS was
 * provisioned on the kiosk device, and the previous value is restored in admin UI or on exit.
 */
object KioskSystemUiPolicy {
    private const val TAG = "KioskSystemUiPolicy"
    private const val PREFS = "freekiosk_system_ui_policy"
    private const val KEY_APPLIED = "navigation_immersive_applied"
    private const val KEY_HAD_PREVIOUS = "had_previous_policy"
    private const val KEY_PREVIOUS = "previous_policy_control"
    private const val KEY_HAD_PREVIOUS_HEADS_UP = "had_previous_heads_up"
    private const val KEY_PREVIOUS_HEADS_UP = "previous_heads_up"
    private const val KEY_WINDOW_MENU_CAPTURED = "fullscreen_window_menu_captured"
    private const val KEY_HAD_PREVIOUS_WINDOW_MENU = "had_previous_fullscreen_window_menu"
    private const val KEY_PREVIOUS_WINDOW_MENU = "previous_fullscreen_window_menu"
    private const val KEY_NAV_GESTURE_POLICY_CAPTURED = "nav_gesture_policy_captured"
    private const val KEY_HAD_PREVIOUS_NAV_GESTURE_POLICY = "had_previous_nav_gesture_policy"
    private const val KEY_PREVIOUS_NAV_GESTURE_POLICY = "previous_nav_gesture_policy"
    private const val POLICY_CONTROL = "policy_control"
    private const val HEADS_UP_NOTIFICATIONS = "heads_up_notifications_enabled"
    // Samsung's desktop-windowing handle/menu shown above otherwise fullscreen apps.
    // Its maximize/fullscreen action can move a locked task through desktop-mode shell code,
    // exposing SystemUI even while ActivityManager still reports LOCK_TASK_MODE_LOCKED.
    private const val FULLSCREEN_WINDOW_MENU = "multi_window_menu_in_full_screen"
    // Samsung policy hook that prevents a swipe from resurrecting the hidden navigation
    // button bar. Standard immersive mode alone deliberately permits transient bars.
    private const val NAV_GESTURE_DISABLED_BY_POLICY =
        "navigation_bar_gesture_disabled_by_policy"
    private const val KIOSK_POLICY = "immersive.full=*"

    fun enable(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED) {
            DebugLog.d(TAG, "WRITE_SECURE_SETTINGS unavailable; relying on lock task for navigation UI")
            return false
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (!prefs.getBoolean(KEY_APPLIED, false)) {
                val previous = Settings.Global.getString(context.contentResolver, POLICY_CONTROL)
                val previousHeadsUp = Settings.Global.getString(
                    context.contentResolver,
                    HEADS_UP_NOTIFICATIONS
                )
                // Recover an in-place upgrade from an older rapid route transition that
                // could clear the lifecycle marker after reapplying FreeKiosk's exact pair.
                val staleKioskState = previous == KIOSK_POLICY && previousHeadsUp == "0"
                editor
                    .putBoolean(KEY_APPLIED, true)
                    .putBoolean(KEY_HAD_PREVIOUS, previous != null && !staleKioskState)
                    .putString(KEY_PREVIOUS, previous.takeUnless { staleKioskState }.orEmpty())
                    .putBoolean(
                        KEY_HAD_PREVIOUS_HEADS_UP,
                        previousHeadsUp != null && !staleKioskState,
                    )
                    .putString(
                        KEY_PREVIOUS_HEADS_UP,
                        previousHeadsUp.takeUnless { staleKioskState }.orEmpty(),
                    )
            }

            // Keep this migration marker independent from KEY_APPLIED. Existing kiosk installs
            // already have KEY_APPLIED=true, but still need to capture this OEM setting exactly
            // once before the first version that manages it changes the value.
            if (isSamsung() && !prefs.getBoolean(KEY_WINDOW_MENU_CAPTURED, false)) {
                val previousWindowMenu = Settings.Global.getString(
                    context.contentResolver,
                    FULLSCREEN_WINDOW_MENU,
                )
                editor
                    .putBoolean(KEY_WINDOW_MENU_CAPTURED, true)
                    .putBoolean(
                        KEY_HAD_PREVIOUS_WINDOW_MENU,
                        previousWindowMenu != null,
                    )
                    .putString(
                        KEY_PREVIOUS_WINDOW_MENU,
                        previousWindowMenu.orEmpty(),
                    )
            }

            if (isSamsung() && !prefs.getBoolean(KEY_NAV_GESTURE_POLICY_CAPTURED, false)) {
                val previousNavGesturePolicy = Settings.Global.getString(
                    context.contentResolver,
                    NAV_GESTURE_DISABLED_BY_POLICY,
                )
                editor
                    .putBoolean(KEY_NAV_GESTURE_POLICY_CAPTURED, true)
                    .putBoolean(
                        KEY_HAD_PREVIOUS_NAV_GESTURE_POLICY,
                        previousNavGesturePolicy != null,
                    )
                    .putString(
                        KEY_PREVIOUS_NAV_GESTURE_POLICY,
                        previousNavGesturePolicy.orEmpty(),
                    )
            }

            check(editor.commit()) { "Could not remember the previous system UI policy" }
            Settings.Global.putString(context.contentResolver, POLICY_CONTROL, KIOSK_POLICY)
            Settings.Global.putInt(context.contentResolver, HEADS_UP_NOTIFICATIONS, 0)
            if (isSamsung()) {
                Settings.Global.putInt(context.contentResolver, FULLSCREEN_WINDOW_MENU, 0)
                Settings.Global.putInt(
                    context.contentResolver,
                    NAV_GESTURE_DISABLED_BY_POLICY,
                    1,
                )
            }
            DebugLog.d(
                TAG,
                "Global full immersive, heads-up suppression, and fullscreen window-menu suppression enabled",
            )
            true
        } catch (e: Exception) {
            DebugLog.d(TAG, "Could not enable global navigation immersive policy: ${e.message}")
            false
        }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APPLIED, false)) return

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
            if (prefs.getBoolean(KEY_WINDOW_MENU_CAPTURED, false)) {
                if (prefs.getBoolean(KEY_HAD_PREVIOUS_WINDOW_MENU, false)) {
                    Settings.Global.putString(
                        context.contentResolver,
                        FULLSCREEN_WINDOW_MENU,
                        prefs.getString(KEY_PREVIOUS_WINDOW_MENU, "").orEmpty(),
                    )
                } else {
                    Settings.Global.putString(
                        context.contentResolver,
                        FULLSCREEN_WINDOW_MENU,
                        null,
                    )
                }
            }
            if (prefs.getBoolean(KEY_NAV_GESTURE_POLICY_CAPTURED, false)) {
                if (prefs.getBoolean(KEY_HAD_PREVIOUS_NAV_GESTURE_POLICY, false)) {
                    Settings.Global.putString(
                        context.contentResolver,
                        NAV_GESTURE_DISABLED_BY_POLICY,
                        prefs.getString(KEY_PREVIOUS_NAV_GESTURE_POLICY, "").orEmpty(),
                    )
                } else {
                    Settings.Global.putString(
                        context.contentResolver,
                        NAV_GESTURE_DISABLED_BY_POLICY,
                        null,
                    )
                }
            }
            DebugLog.d(TAG, "Previous global system UI policy restored")
        } catch (e: Exception) {
            DebugLog.d(TAG, "Could not restore global navigation policy: ${e.message}")
        } finally {
            check(prefs.edit().clear().commit()) {
                "Could not clear the remembered system UI policy"
            }
        }
    }

    private fun isSamsung(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)
}
