package com.avainfinity.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Non-destructive, idempotent enrichment of legacy owner wardrobe image records.
 * Existing fields and records are preserved; only missing technical metadata is added
 * when the referenced file is a bounded, app-generated image in the private directory.
 */
object AvaWardrobeMediaMigration {
    data class Outcome(
        val json: String,
        val changed: Boolean,
        val migratedRecords: Int
    )

    fun migrateLegacyIndex(raw: String, root: File): Outcome {
        val original = JSONArray(raw)
        val migrated = JSONArray()
        var migratedCount = 0

        for (index in 0 until original.length()) {
            val originalItem = original.optJSONObject(index)
            if (originalItem == null) {
                migrated.put(original.opt(index))
                continue
            }

            val item = JSONObject(originalItem.toString())
            val hasMetadata = item.optString("itemId").isNotBlank() &&
                item.optString("mimeType").isNotBlank() &&
                item.optLong("byteLength", -1L) > 0L &&
                item.optString("sha256").matches(Regex("[0-9a-f]{64}"))

            if (!hasMetadata) {
                val storedPath = item.optString("file", "")
                val file = try {
                    File(storedPath).canonicalFile
                } catch (_: Exception) {
                    null
                }

                if (file != null && AvaWardrobeMediaPolicy.isSafeImageFile(root, file)) {
                    val calculated = inspect(file)
                    if (calculated != null) {
                        val id = file.name.removePrefix("wardrobe_").substringBeforeLast(".")
                        item.put("itemId", item.optString("itemId").ifBlank { id })
                        item.put("mimeType", calculated.first)
                        item.put("byteLength", calculated.second)
                        item.put("sha256", calculated.third)
                        migratedCount++
                    }
                }
            }
            migrated.put(item)
        }

        val changed = migratedCount > 0
        return Outcome(if (changed) migrated.toString() else raw, changed, migratedCount)
    }

    private fun inspect(file: File): Triple<String, Long, String>? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val header = ByteArray(12)
            var headerLength = 0
            var total = 0L
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    if (total > 15L * 1024L * 1024L) return null
                    digest.update(buffer, 0, count)
                    if (headerLength < header.size) {
                        val copy = minOf(count, header.size - headerLength)
                        System.arraycopy(buffer, 0, header, headerLength, copy)
                        headerLength += copy
                    }
                }
            }
            if (total != file.length()) return null
            val format = AvaWardrobeImageFormat.detect(header.copyOf(headerLength)) ?: return null
            if (!file.name.endsWith(format.extension, ignoreCase = true)) return null
            val sha256 = digest.digest().joinToString("") {
                "%02x".format(Locale.ROOT, it.toInt() and 0xff)
            }
            Triple(format.mimeType, total, sha256)
        } catch (_: Exception) {
            null
        }
    }
}
