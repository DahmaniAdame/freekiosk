package com.freekiosk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Persists import-compatible settings snapshots outside AsyncStorage.
 *
 * Files under filesDir survive a normal package replacement. A separate staged
 * copy under noBackupFilesDir is created immediately before an APK update so the
 * next app version can recover configuration if its AsyncStorage data is absent.
 */
object SettingsHistoryStore {
    private const val HISTORY_DIRECTORY = "settings-history"
    private const val PENDING_SNAPSHOT = "pending-update-settings.json"
    private const val PENDING_MARKER = "pending-update-settings-marker.json"
    private const val MAX_SNAPSHOTS = 30

    @Synchronized
    fun writeSnapshot(context: Context, json: String, reason: String): JSONObject {
        val parsed = JSONObject(json)
        require(parsed.optString("version").isNotBlank()) { "Backup version is missing" }
        require(parsed.optJSONObject("settings") != null) { "Backup settings are missing" }

        val contentHash = contentHash(parsed)
        val latest = latestFile(context)
        if (latest != null) {
            val latestJson = runCatching { JSONObject(latest.readText()) }.getOrNull()
            if (latestJson?.optJSONObject("history")?.optString("contentHash") == contentHash) {
                return metadata(latest, latestJson, deduplicated = true)
            }
        }

        val createdAt = System.currentTimeMillis()
        parsed.put("history", JSONObject().apply {
            put("createdAt", createdAt)
            put("reason", reason.take(160))
            put("contentHash", contentHash)
        })

        val directory = historyDirectory(context)
        val target = File(directory, "settings-$createdAt-${contentHash.take(10)}.json")
        writeAtomically(target, parsed.toString(2))
        prune(directory)
        return metadata(target, parsed, deduplicated = false)
    }

    @Synchronized
    fun listSnapshots(context: Context): JSONArray {
        val result = JSONArray()
        snapshotFiles(context).forEach { file ->
            val parsed = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (parsed != null) result.put(metadata(file, parsed, deduplicated = false))
        }
        return result
    }

    fun latestFile(context: Context): File? = snapshotFiles(context).firstOrNull()

    fun snapshotFile(context: Context, id: String): File? {
        if (!id.matches(Regex("settings-[0-9]+-[a-f0-9]{10}\\.json"))) return null
        val file = File(historyDirectory(context), id)
        return file.takeIf { it.isFile }
    }

    @Synchronized
    fun stageLatestForUpdate(context: Context, targetVersionCode: Long): JSONObject {
        val latest = latestFile(context)
            ?: throw IllegalStateException("No settings snapshot is ready yet")
        val pending = File(context.noBackupFilesDir, PENDING_SNAPSHOT)
        writeAtomically(pending, latest.readText())

        val currentVersion = packageVersionCode(context)
        val marker = JSONObject().apply {
            put("createdAt", System.currentTimeMillis())
            put("sourceVersionCode", currentVersion)
            put("targetVersionCode", targetVersionCode)
            put("snapshot", latest.name)
        }
        writeAtomically(File(context.noBackupFilesDir, PENDING_MARKER), marker.toString(2))
        return marker
    }

    @Synchronized
    fun pendingSnapshot(context: Context): String? {
        val marker = File(context.noBackupFilesDir, PENDING_MARKER)
        val pending = File(context.noBackupFilesDir, PENDING_SNAPSHOT)
        if (!marker.isFile || !pending.isFile) return null

        val markerJson = runCatching { JSONObject(marker.readText()) }.getOrNull() ?: return null
        val currentVersion = packageVersionCode(context)
        if (currentVersion < markerJson.optLong("targetVersionCode", Long.MAX_VALUE)) return null
        return runCatching { pending.readText() }.getOrNull()
    }

    @Synchronized
    fun clearPendingRestore(context: Context) {
        File(context.noBackupFilesDir, PENDING_SNAPSHOT).delete()
        File(context.noBackupFilesDir, PENDING_MARKER).delete()
    }

    private fun historyDirectory(context: Context): File =
        File(context.filesDir, HISTORY_DIRECTORY).apply { mkdirs() }

    private fun snapshotFiles(context: Context): List<File> =
        historyDirectory(context).listFiles()
            ?.filter { it.isFile && it.name.matches(Regex("settings-[0-9]+-[a-f0-9]{10}\\.json")) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    private fun prune(directory: File) {
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("settings-") && it.extension == "json" }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_SNAPSHOTS)
            ?.forEach { it.delete() }
    }

    private fun contentHash(json: JSONObject): String {
        val content = JSONObject().apply {
            put("settings", json.optJSONObject("settings") ?: JSONObject())
            put("hasPinConfigured", json.optBoolean("hasPinConfigured", false))
        }.toString()
        return sha256(content.toByteArray())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun metadata(file: File, json: JSONObject, deduplicated: Boolean): JSONObject {
        val history = json.optJSONObject("history") ?: JSONObject()
        return JSONObject().apply {
            put("id", file.name)
            put("createdAt", history.optLong("createdAt", file.lastModified()))
            put("reason", history.optString("reason", "settings change"))
            put("contentHash", history.optString("contentHash", ""))
            put("appVersion", json.optString("appVersion", ""))
            put("size", file.length())
            put("deduplicated", deduplicated)
        }
    }

    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            target.writeText(content)
            temporary.delete()
        }
    }

    private fun packageVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }
}
