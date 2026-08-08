package com.freekiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Lifecycle-scoped control of Samsung WAF's signed side-menu service.
 *
 * The handles are TYPE_SYSTEM_ALERT windows owned by com.xbh.navisetting, so FreeKiosk's
 * TYPE_APPLICATION_OVERLAY touch regions are always below them and cannot consume their taps.
 * The OEM package exposes a controller ContentProvider specifically for showing/hiding all of
 * its navigation windows. Its own service also reads two Global settings at startup; preserving
 * and temporarily disabling those settings keeps the menus hidden if the service is recreated.
 *
 * Do not hide com.xbh.navisetting. WAF's launcher has a broken PACKAGE_CHANGED receiver which
 * crashes when that package is hidden. Do not hide com.android.launcher3 either; it supplies
 * Quickstep/Recents and changing its package state can destabilize the display.
 */
object WafSideMenuPolicy {
    private const val TAG = "WafSideMenuPolicy"
    private const val SIDE_MENU_PACKAGE = "com.xbh.navisetting"
    private const val SERVICE_NAME =
        "com.xbh.navisetting.appstart.service.NavigationBarService"
    private const val CONTROLLER_AUTHORITY = "com.xbh.navisetting.controller"
    private const val CONTROLLER_BASE_URI = "content://$CONTROLLER_AUTHORITY"
    private const val ACTION_HIDE = "xbh.intent.action.XBH_RECEIVER_CLOSE_NAVI_BAR"
    private const val ACTION_SHOW = "com.xbh.showNBView"

    private const val PREFS_NAME = "FreeKioskLifecyclePolicies"
    private const val SIDE_MENU_HIDDEN_BY_KIOSK_KEY = "waf_side_menu_hidden_by_kiosk"
    private const val SIDE_MENU_DISABLED_BY_KIOSK_KEY = "waf_side_menu_disabled_by_kiosk"
    private const val ORIGINAL_SHOW_SETTING_KEY = "waf_side_menu_original_show_setting"
    private const val ORIGINAL_ENABLE_SETTING_KEY = "waf_side_menu_original_enable_setting"
    private const val UNSET_SETTING = "\u0000"

    // Observed by NavigationBarService; zero removes every open/closed side-menu window.
    private const val NAVI_SHOW_SETTING = "NAVI_ENABLE_SHOW_KEY"

    // Read by NavigationBarConfig when the persistent service starts.
    private const val NAVI_ENABLE_SETTING = "persist.vendor.xbh.navigation.enable"

    fun hideForKiosk(
        context: Context,
        @Suppress("UNUSED_PARAMETER") policyManager: DevicePolicyManager,
        @Suppress("UNUSED_PARAMETER") admin: ComponentName,
    ): Boolean {
        if (!isWafSideMenuAvailable(context)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SIDE_MENU_DISABLED_BY_KIOSK_KEY, false)) {
            val resolver = context.contentResolver
            val saved = prefs.edit()
                .putString(
                    ORIGINAL_SHOW_SETTING_KEY,
                    Settings.Global.getString(resolver, NAVI_SHOW_SETTING) ?: UNSET_SETTING,
                )
                .putString(
                    ORIGINAL_ENABLE_SETTING_KEY,
                    Settings.Global.getString(resolver, NAVI_ENABLE_SETTING) ?: UNSET_SETTING,
                )
                .putBoolean(SIDE_MENU_DISABLED_BY_KIOSK_KEY, true)
                .commit()
            check(saved) { "Could not remember the WAF side-menu state" }
        }

        val resolver = context.contentResolver
        val showSettingDisabled = Settings.Global.putString(resolver, NAVI_SHOW_SETTING, "0")
        val enableSettingDisabled = Settings.Global.putString(resolver, NAVI_ENABLE_SETTING, "0")
        val hiddenImmediately = requestVisibility(context, show = false)

        if (!showSettingDisabled || !enableSettingDisabled) {
            Log.w(TAG, "Could not persist every WAF side-menu setting; controller result=$hiddenImmediately")
        } else {
            Log.d(TAG, "Samsung WAF side menus disabled for Lock Mode")
        }
        return showSettingDisabled || enableSettingDisabled || hiddenImmediately
    }

    fun restoreAfterKiosk(
        context: Context,
        policyManager: DevicePolicyManager,
        admin: ComponentName,
    ): Boolean {
        val legacyPackageRestored = restoreManagedPackage(
            context,
            policyManager,
            admin,
            SIDE_MENU_PACKAGE,
            SIDE_MENU_HIDDEN_BY_KIOSK_KEY,
            "Samsung WAF side menus",
        )
        if (legacyPackageRestored) restartNavigationService(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SIDE_MENU_DISABLED_BY_KIOSK_KEY, false)) {
            return legacyPackageRestored
        }

        val originalShow = prefs.getString(ORIGINAL_SHOW_SETTING_KEY, UNSET_SETTING)
            ?: UNSET_SETTING
        val originalEnable = prefs.getString(ORIGINAL_ENABLE_SETTING_KEY, UNSET_SETTING)
            ?: UNSET_SETTING
        val resolver = context.contentResolver
        val showRestored = restoreGlobalSetting(resolver, NAVI_SHOW_SETTING, originalShow)
        val enableRestored = restoreGlobalSetting(resolver, NAVI_ENABLE_SETTING, originalEnable)

        // A missing original value means the OEM default was enabled. Explicitly ask the
        // controller to restore its windows because deleting NAVI_ENABLE_SHOW_KEY makes its
        // observer use a different default than its startup path.
        val shouldShow = originalShow != "0" && originalEnable != "0"
        val visibilityRestored = requestVisibility(context, show = shouldShow)

        if (showRestored && enableRestored) {
            check(
                prefs.edit()
                    .remove(SIDE_MENU_DISABLED_BY_KIOSK_KEY)
                    .remove(ORIGINAL_SHOW_SETTING_KEY)
                    .remove(ORIGINAL_ENABLE_SETTING_KEY)
                    .commit()
            ) { "Could not clear remembered WAF side-menu state" }
            Log.d(TAG, "Samsung WAF side menus restored after Lock Mode")
        } else {
            Log.w(TAG, "WAF side-menu settings could not be fully restored")
        }

        return legacyPackageRestored || showRestored || enableRestored || visibilityRestored
    }

    private fun isWafSideMenuAvailable(context: Context): Boolean =
        context.packageManager.resolveContentProvider(CONTROLLER_AUTHORITY, 0) != null

    private fun requestVisibility(context: Context, show: Boolean): Boolean {
        val action = if (show) "show" else "hide"
        val providerSucceeded = runCatching {
            context.contentResolver.update(
                Uri.parse("$CONTROLLER_BASE_URI/$action"),
                ContentValues(),
                null,
                null,
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "WAF side-menu controller $action failed: ${error.message}")
        }.getOrDefault(false)

        if (!providerSucceeded) {
            runCatching {
                context.sendBroadcast(Intent(if (show) ACTION_SHOW else ACTION_HIDE))
            }.onFailure { error ->
                Log.w(TAG, "WAF side-menu broadcast $action failed: ${error.message}")
            }
        }
        return providerSucceeded
    }

    private fun restoreGlobalSetting(
        resolver: android.content.ContentResolver,
        name: String,
        originalValue: String,
    ): Boolean = Settings.Global.putString(
        resolver,
        name,
        originalValue.takeUnless { it == UNSET_SETTING },
    )

    private fun restoreManagedPackage(
        context: Context,
        policyManager: DevicePolicyManager,
        admin: ComponentName,
        packageName: String,
        stateKey: String,
        label: String,
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(stateKey, false)) return false

        return runCatching {
            policyManager.setApplicationHidden(admin, packageName, false)
            if (!policyManager.isApplicationHidden(admin, packageName)) {
                check(prefs.edit().remove(stateKey).commit()) {
                    "Could not clear remembered $label state"
                }
                Log.d(TAG, "$label restored after Lock Mode")
                true
            } else {
                false
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not restore $label: ${error.message}")
        }.getOrDefault(false)
    }

    private fun restartNavigationService(context: Context) {
        runCatching {
            val intent = Intent().setComponent(ComponentName(SIDE_MENU_PACKAGE, SERVICE_NAME))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { error ->
            Log.d(TAG, "WAF navigation service will be restored by firmware: ${error.message}")
        }
    }
}
