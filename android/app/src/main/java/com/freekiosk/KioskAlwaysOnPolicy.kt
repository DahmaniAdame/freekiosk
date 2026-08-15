package com.freekiosk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.PowerManager
import android.view.WindowManager

/** Keeps the physical display awake for the complete saved Lock Mode lifecycle. */
object KioskAlwaysOnPolicy {
    private const val TAG = "KioskAlwaysOn"
    private const val WAKE_PULSE_TIMEOUT_MS = 10_000L
    private var persistentWakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun enforce(context: Context) {
        if (persistentWakeLock?.isHeld != true) {
            val powerManager =
                context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            persistentWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "FreeKiosk:KioskAlwaysOn",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            DebugLog.d(TAG, "Persistent kiosk display WakeLock acquired")
        }
        MainActivity.currentInstance?.runOnUiThread {
            MainActivity.currentInstance?.window?.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    fun wakeNow(context: Context) {
        enforce(context)
        val powerManager =
            context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) return
        @Suppress("DEPRECATION")
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "FreeKiosk:KioskWakePulse",
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_PULSE_TIMEOUT_MS)
        }
        DebugLog.d(TAG, "Kiosk screen wake pulse acquired")
    }

    @Synchronized
    fun release() {
        runCatching { persistentWakeLock?.takeIf { it.isHeld }?.release() }
            .onFailure { DebugLog.d(TAG, "Could not release display WakeLock: ${it.message}") }
        persistentWakeLock = null
    }

    fun isKioskEnabled(context: Context): Boolean {
        return try {
            SQLiteDatabase.openDatabase(
                context.getDatabasePath("RKStorage").absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                database.rawQuery(
                    "SELECT value FROM catalystLocalStorage WHERE key = ?",
                    arrayOf("@kiosk_enabled"),
                ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "true" }
            }
        } catch (error: Exception) {
            DebugLog.d(TAG, "Could not read kiosk state: ${error.message}")
            false
        }
    }
}
