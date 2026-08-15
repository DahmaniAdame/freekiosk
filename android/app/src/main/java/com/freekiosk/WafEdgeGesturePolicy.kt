package com.freekiosk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Disables the Samsung WAF firmware's top and bottom edge-reveal gestures.
 *
 * AOSP immersive mode intentionally allows transient system bars to be revealed by swiping
 * from an edge. WAF exposes two additional global policy switches which are checked before
 * SystemUI begins those gestures. Preserve their original values so an explicit kiosk exit can
 * restore the display exactly, then continuously enforce both switches while child-facing.
 */
object WafEdgeGesturePolicy {
    private const val TAG = "WafEdgeGesturePolicy"
    private const val PREFS = "freekiosk_waf_edge_gesture_policy"
    private const val KEY_APPLIED = "applied"
    private const val KEY_HAD_SKIP_SWIPE = "had_skip_swipe_bottom_top"
    private const val KEY_PREVIOUS_SKIP_SWIPE = "previous_skip_swipe_bottom_top"
    private const val KEY_HAD_SKIP_GESTURE = "had_skip_gesture"
    private const val KEY_PREVIOUS_SKIP_GESTURE = "previous_skip_gesture"

    // OEM WAF SystemUI reads these Settings.Global values before accepting an edge swipe.
    private const val SKIP_SWIPE_BOTTOM_TOP = "skip_swipe_bottom_top"
    private const val SKIP_GESTURE = "skip_gesture"

    @Synchronized
    fun blockForKiosk(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED) {
            DebugLog.d(TAG, "WRITE_SECURE_SETTINGS unavailable; cannot block WAF edge gestures")
            return false
        }

        return try {
            val resolver = context.contentResolver
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_APPLIED, false)) {
                val previousSkipSwipe = Settings.Global.getString(
                    resolver,
                    SKIP_SWIPE_BOTTOM_TOP,
                )
                val previousSkipGesture = Settings.Global.getString(resolver, SKIP_GESTURE)
                check(
                    prefs.edit()
                        .putBoolean(KEY_APPLIED, true)
                        .putBoolean(KEY_HAD_SKIP_SWIPE, previousSkipSwipe != null)
                        .putString(KEY_PREVIOUS_SKIP_SWIPE, previousSkipSwipe.orEmpty())
                        .putBoolean(KEY_HAD_SKIP_GESTURE, previousSkipGesture != null)
                        .putString(KEY_PREVIOUS_SKIP_GESTURE, previousSkipGesture.orEmpty())
                        .commit(),
                ) { "Could not preserve WAF edge-gesture settings" }
            }

            Settings.Global.putInt(resolver, SKIP_SWIPE_BOTTOM_TOP, 1)
            Settings.Global.putInt(resolver, SKIP_GESTURE, 1)
            val blocked =
                Settings.Global.getString(resolver, SKIP_SWIPE_BOTTOM_TOP) == "1" &&
                    Settings.Global.getString(resolver, SKIP_GESTURE) == "1"
            DebugLog.d(TAG, "WAF top/bottom edge gestures blocked=$blocked")
            blocked
        } catch (error: Exception) {
            DebugLog.errorProduction(TAG, "Could not block WAF edge gestures: ${error.message}")
            false
        }
    }

    @Synchronized
    fun restoreAfterKiosk(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APPLIED, false)) return true

        return try {
            val resolver = context.contentResolver
            restoreSetting(
                resolver = resolver,
                setting = SKIP_SWIPE_BOTTOM_TOP,
                hadPrevious = prefs.getBoolean(KEY_HAD_SKIP_SWIPE, false),
                previous = prefs.getString(KEY_PREVIOUS_SKIP_SWIPE, "").orEmpty(),
            )
            restoreSetting(
                resolver = resolver,
                setting = SKIP_GESTURE,
                hadPrevious = prefs.getBoolean(KEY_HAD_SKIP_GESTURE, false),
                previous = prefs.getString(KEY_PREVIOUS_SKIP_GESTURE, "").orEmpty(),
            )
            check(prefs.edit().clear().commit()) {
                "Could not clear preserved WAF edge-gesture settings"
            }
            DebugLog.d(TAG, "Previous WAF edge-gesture settings restored")
            true
        } catch (error: Exception) {
            DebugLog.errorProduction(TAG, "Could not restore WAF edge gestures: ${error.message}")
            false
        }
    }

    private fun restoreSetting(
        resolver: android.content.ContentResolver,
        setting: String,
        hadPrevious: Boolean,
        previous: String,
    ) {
        if (hadPrevious) {
            Settings.Global.putString(resolver, setting, previous)
        } else {
            Settings.Global.putString(resolver, setting, null)
        }
    }
}
