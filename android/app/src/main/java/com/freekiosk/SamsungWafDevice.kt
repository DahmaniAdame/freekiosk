package com.freekiosk

import android.content.Context
import android.os.Build

/** Stable WAF detection that does not depend solely on Android package visibility. */
object SamsungWafDevice {
    private const val CONTROLLER_AUTHORITY = "com.xbh.navisetting.controller"

    fun isWaf(context: Context): Boolean {
        val firmwareIdentity =
            Build.MODEL.equals("WAF", ignoreCase = true) &&
                Build.BRAND.equals("Samsung", ignoreCase = true)
        return firmwareIdentity ||
            context.packageManager.resolveContentProvider(CONTROLLER_AUTHORITY, 0) != null
    }
}
