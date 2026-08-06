package com.freekiosk

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.media.AudioManager
import android.util.Log

/** Enforces the saved media-volume ceiling only while Android lock task is active. */
object VolumeLimitManager {
    private const val TAG = "VolumeLimitManager"
    private const val STORAGE_KEY = "@kiosk_max_volume_percent"
    private const val DEFAULT_LIMIT_PERCENT = 100

    fun clampRequestedPercent(context: Context, requestedPercent: Int): Int {
        val requested = requestedPercent.coerceIn(0, 100)
        return if (isKioskActive(context)) {
            minOf(requested, getConfiguredLimitPercent(context))
        } else {
            requested
        }
    }

    /** Returns the effective raw media volume after applying the ceiling. */
    fun enforce(context: Context, audioManager: AudioManager? = null): Int {
        val manager = audioManager
            ?: context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (!isKioskActive(context)) return currentVolume

        val systemMaxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val limitPercent = getConfiguredLimitPercent(context)
        val maximumAllowedVolume = (systemMaxVolume * limitPercent / 100)
            .coerceIn(0, systemMaxVolume)

        if (currentVolume > maximumAllowedVolume) {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, maximumAllowedVolume, 0)
            Log.d(
                TAG,
                "Media volume limited to $limitPercent% " +
                    "(raw: $maximumAllowedVolume/$systemMaxVolume)"
            )
            return maximumAllowedVolume
        }

        return currentVolume
    }

    fun isKioskActive(context: Context): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check lock task mode: ${e.message}")
            false
        }
    }

    private fun getConfiguredLimitPercent(context: Context): Int {
        var database: SQLiteDatabase? = null
        return try {
            val databasePath = context.getDatabasePath("RKStorage").absolutePath
            database = SQLiteDatabase.openDatabase(
                databasePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            database.rawQuery(
                "SELECT value FROM catalystLocalStorage WHERE key = ?",
                arrayOf(STORAGE_KEY)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.toIntOrNull()?.coerceIn(0, 100)
                        ?: DEFAULT_LIMIT_PERCENT
                } else {
                    DEFAULT_LIMIT_PERCENT
                }
            }
        } catch (_: Exception) {
            DEFAULT_LIMIT_PERCENT
        } finally {
            database?.close()
        }
    }
}
