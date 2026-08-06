package com.freekiosk

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build

/** Launches an allowed kiosk app as a full-screen task, including on freeform OEM boards. */
object KioskTaskLauncher {
    private const val TAG = "KioskTaskLauncher"
    private const val WINDOWING_MODE_FULLSCREEN = 1

    fun launch(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            options.launchBounds = null
        }

        // setLaunchWindowingMode is a platform API used by Android's task launcher but
        // hidden from normal SDK apps on some releases. OEM kiosk boards commonly leave
        // apps in freeform mode (showing a title strip with three window buttons), so use
        // it when exposed and safely fall back to normal full-screen task launch.
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            method.invoke(options, WINDOWING_MODE_FULLSCREEN)
        } catch (e: Exception) {
            DebugLog.d(TAG, "Explicit full-screen windowing mode unavailable: ${e.message}")
        }

        context.startActivity(intent, options.toBundle())
    }
}
