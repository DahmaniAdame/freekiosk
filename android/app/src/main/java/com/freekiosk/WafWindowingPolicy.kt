package com.freekiosk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Disables the WAF desktop/freeform affordances for the saved kiosk lifecycle.
 *
 * This firmware can reveal a desktop caption from a horizontal edge swipe even for an
 * activity that declares itself non-resizable. Its next-window/minimize button then moves the
 * kiosk task away and exposes the OEM launcher. Preserve the three firmware switches exactly,
 * force them off while child-facing, and restore them only on an explicit Lock Mode exit.
 */
object WafWindowingPolicy {
    private const val TAG = "WafWindowingPolicy"
    private const val PREFS = "freekiosk_waf_windowing_policy"
    private const val CAPTURED = "captured"
    private const val UNSET = "\u0000"

    private val settings = listOf(
        "enable_freeform_support",
        "multi_cb",
        "side_button",
    )

    @Synchronized
    fun blockForKiosk(context: Context): Boolean {
        if (!SamsungWafDevice.isWaf(context)) return false
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
            PackageManager.PERMISSION_GRANTED) return false

        return try {
            val resolver = context.contentResolver
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(CAPTURED, false)) {
                val editor = prefs.edit().putBoolean(CAPTURED, true)
                settings.forEach { name ->
                    editor.putString(name, Settings.Global.getString(resolver, name) ?: UNSET)
                }
                check(editor.commit()) { "Could not preserve WAF windowing settings" }
            }

            settings.forEach { name -> Settings.Global.putInt(resolver, name, 0) }
            val blocked = settings.all { name ->
                Settings.Global.getString(resolver, name) == "0"
            }
            DebugLog.d(TAG, "WAF side-swipe/freeform windowing blocked=$blocked")
            blocked
        } catch (error: Exception) {
            DebugLog.errorProduction(TAG, "Could not block WAF windowing: ${error.message}")
            false
        }
    }

    @Synchronized
    fun restoreAfterKiosk(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(CAPTURED, false)) return true

        return try {
            val resolver = context.contentResolver
            settings.forEach { name ->
                Settings.Global.putString(
                    resolver,
                    name,
                    prefs.getString(name, UNSET).orEmpty().takeUnless { it == UNSET },
                )
            }
            check(prefs.edit().clear().commit()) {
                "Could not clear preserved WAF windowing settings"
            }
            DebugLog.d(TAG, "Previous WAF windowing settings restored")
            true
        } catch (error: Exception) {
            DebugLog.errorProduction(TAG, "Could not restore WAF windowing: ${error.message}")
            false
        }
    }
}
