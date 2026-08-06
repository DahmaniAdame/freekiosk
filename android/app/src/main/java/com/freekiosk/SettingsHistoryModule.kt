package com.freekiosk

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class SettingsHistoryModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "SettingsHistoryModule"

    @ReactMethod
    fun writeSnapshot(json: String, reason: String, promise: Promise) {
        try {
            val metadata = SettingsHistoryStore.writeSnapshot(reactContext, json, reason)
            promise.resolve(metadata.toString())
        } catch (error: Exception) {
            promise.reject("SNAPSHOT_ERROR", error.message, error)
        }
    }

    @ReactMethod
    fun getPendingUpdateSnapshot(promise: Promise) {
        try {
            promise.resolve(SettingsHistoryStore.pendingSnapshot(reactContext))
        } catch (error: Exception) {
            promise.reject("RESTORE_ERROR", error.message, error)
        }
    }

    @ReactMethod
    fun completePendingRestore(promise: Promise) {
        try {
            SettingsHistoryStore.clearPendingRestore(reactContext)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject("RESTORE_ERROR", error.message, error)
        }
    }
}
