package com.freekiosk

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log

/** Native emergency exit: Volume Up x3 followed by Volume Down x3. */
object EmergencyVolumeSequence {
    enum class Direction { UP, DOWN }

    private const val TAG = "EmergencyVolumeSequence"
    private const val MAX_GAP_MS = 1_800L
    private const val TOTAL_TIMEOUT_MS = 8_000L
    private const val DUPLICATE_EVENT_WINDOW_MS = 180L

    private var upCount = 0
    private var downCount = 0
    private var startedAt = 0L
    private var lastAcceptedAt = 0L
    private var lastDirection: Direction? = null

    /**
     * Records one initial hardware-button press. Returns true only for the completing press.
     * Duplicate Activity/broadcast observations of the same physical press are de-duplicated.
     */
    @Synchronized
    fun record(context: Context, direction: Direction): Boolean {
        if (!isKioskActive(context)) {
            reset()
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (direction == lastDirection && now - lastAcceptedAt < DUPLICATE_EVENT_WINDOW_MS) {
            return false
        }
        if ((lastAcceptedAt > 0 && now - lastAcceptedAt > MAX_GAP_MS) ||
            (startedAt > 0 && now - startedAt > TOTAL_TIMEOUT_MS)) {
            reset()
        }

        if (startedAt == 0L) startedAt = now
        lastAcceptedAt = now
        lastDirection = direction

        when (direction) {
            Direction.UP -> {
                if (downCount > 0) {
                    // A new UP after the DOWN phase starts a fresh candidate sequence.
                    upCount = 1
                    downCount = 0
                    startedAt = now
                } else {
                    upCount = (upCount + 1).coerceAtMost(3)
                }
            }
            Direction.DOWN -> {
                if (upCount == 3) {
                    downCount++
                } else {
                    reset()
                    return false
                }
            }
        }

        Log.d(TAG, "Emergency sequence progress: up=$upCount/3 down=$downCount/3")
        if (upCount == 3 && downCount == 3) {
            reset()
            Log.w(TAG, "Emergency volume sequence accepted; performing complete kiosk exit")
            KioskExitManager.scheduleExit(
                context = context,
                activity = MainActivity.currentInstance,
                terminateProcess = true,
            )
            return true
        }
        return false
    }

    /** True while a valid sequence is being entered and has not timed out. */
    @Synchronized
    fun isInProgress(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if ((lastAcceptedAt > 0 && now - lastAcceptedAt > MAX_GAP_MS) ||
            (startedAt > 0 && now - startedAt > TOTAL_TIMEOUT_MS)) {
            reset()
        }
        return startedAt > 0 && upCount > 0
    }

    private fun isKioskActive(context: Context): Boolean {
        val locked = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        }.getOrDefault(false)
        if (locked) return true

        return runCatching {
            val path = context.getDatabasePath("RKStorage").absolutePath
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery(
                    "SELECT value FROM catalystLocalStorage WHERE key = ?",
                    arrayOf("@kiosk_enabled"),
                ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "true" }
            }
        }.getOrDefault(false)
    }

    private fun reset() {
        upCount = 0
        downCount = 0
        startedAt = 0L
        lastAcceptedAt = 0L
        lastDirection = null
    }
}
