package com.avainfinity.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Additive, recoverable application of an authenticated media preview.
 *
 * Existing media is never replaced or deleted. Same ID + same digest is a no-op;
 * same ID + different digest is a hard conflict. New files are promoted first,
 * then the encrypted preference index is committed and read back. A small
 * app-private journal allows startup recovery after process death.
 */
object AvaWardrobeMediaRestoreTransaction {
    data class Result(val added: Int, val unchanged: Int, val newIndexJson: String)

    private const val JOURNAL_NAME = "ava-wardrobe-media-restore-journal.json"
    private const val JOURNAL_VERSION = 1

    fun apply(
        root: File,
        journalDirectory: File,
        preview: AvaWardrobeMediaBundleCodec.StagedPreview,
        currentIndexJson: String,
        readIndex: () -> String?,
        writeIndex: (String) -> Boolean,
        isOwnerAuthorized: () -> Boolean,
        audit: (String, String) -> Boolean
    ): Result {
        require(isOwnerAuthorized()) { "Owner session is not authorized." }
        require(audit("WARDROBE_MEDIA_RESTORE_APPLY_REQUESTED", "bundle=${preview.bundleId};items=${preview.entries.size}")) {
            "Security audit authorization failed."
        }
        val canonicalRoot = root.canonicalFile
        require(canonicalRoot.exists() || canonicalRoot.mkdirs()) { "Unable to create wardrobe directory." }
        require(canonicalRoot.isDirectory)
        require(journalDirectory.exists() || journalDirectory.mkdirs())
        val journalFile = File(journalDirectory.canonicalFile, JOURNAL_NAME)
        require(!journalFile.exists()) { "A previous restore transaction requires recovery." }

        val current = JSONArray(currentIndexJson)
        require(current.length() <= AvaWardrobeMediaBundleCodec.MAX_ITEMS)
        val byId = linkedMapOf<String, JSONObject>()
        for (i in 0 until current.length()) {
            val record = current.optJSONObject(i) ?: error("Current wardrobe index contains an invalid record.")
            val id = record.optString("itemId")
            require(id.isNotBlank() && record.optString("sha256").matches(Regex("[0-9a-fA-F]{64}"))) {
                "Current wardrobe index requires completed media metadata migration before restore."
            }
            require(byId.put(id, record) == null) { "Current wardrobe index has duplicate item IDs." }
        }

        val next = JSONArray(currentIndexJson)
        val additions = mutableListOf<AvaWardrobeMediaBundleCodec.PreviewEntry>()
        var unchanged = 0
        preview.entries.forEach { entry ->
            require(isOwnerAuthorized()) { "Owner session changed during restore preparation." }
            val existing = byId[entry.itemId]
            if (existing != null) {
                require(existing.optString("sha256").equals(entry.sha256, ignoreCase = true)) {
                    "An item ID already exists with different image content."
                }
                val existingFile = File(existing.optString("file", "")).canonicalFile
                require(AvaWardrobeMediaPolicy.isSafeImageFile(canonicalRoot, existingFile) &&
                    sha256(existingFile) == entry.sha256) {
                    "An indexed image failed path or digest verification."
                }
                unchanged++
            } else {
                val finalFile = File(canonicalRoot, entry.fileName)
                require(!finalFile.exists()) { "A target filename already exists outside the current index." }
                additions.add(entry)
                val categoryLabel = when (entry.category) {
                    "private" -> "مجموعه خصوصی"
                    "sporty" -> "اسپرت"
                    "everyday" -> "روزمره"
                    "formal" -> "رسمی و شب"
                    "homewear" -> "لباس راحتی"
                    "hairstyle" -> "مدل مو"
                    else -> "سایر"
                }
                next.put(JSONObject()
                    .put("itemId", entry.itemId)
                    .put("name", entry.displayName)
                    .put("category", entry.category)
                    .put("categoryLabel", categoryLabel)
                    .put("addedAt", entry.addedAt)
                    .put("file", finalFile.absolutePath)
                    .put("mimeType", entry.mimeType)
                    .put("byteLength", entry.byteLength)
                    .put("sha256", entry.sha256))
            }
        }
        require(next.length() <= AvaWardrobeMediaBundleCodec.MAX_ITEMS) { "Restore would exceed the wardrobe item limit." }
        require(additions.isNotEmpty() || unchanged == preview.entries.size) { "Nothing can be safely applied." }

        val oldDigest = sha256(currentIndexJson)
        val nextJson = next.toString()
        val nextDigest = sha256(nextJson)
        val transactionId = UUID.randomUUID().toString()
        val stage = File(journalDirectory.canonicalFile, "ava-wardrobe-restore-$transactionId.stage")
        require(stage.mkdirs()) { "Unable to create restore staging directory." }
        val promoted = mutableListOf<File>()
        var journalWritten = false
        try {
            additions.forEach { entry ->
                require(isOwnerAuthorized()) { "Owner session changed while staging restore." }
                require(entry.stagedFile.isFile && entry.stagedFile.length() == entry.byteLength)
                val stagedCopy = File(stage, entry.fileName)
                FileInputStream(entry.stagedFile).use { input ->
                    FileOutputStream(stagedCopy).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                require(stagedCopy.length() == entry.byteLength && sha256(stagedCopy) == entry.sha256)
            }

            val journal = JSONObject()
                .put("version", JOURNAL_VERSION)
                .put("transactionId", transactionId)
                .put("oldIndexSha256", oldDigest)
                .put("nextIndexSha256", nextDigest)
                .put("promotedFiles", JSONArray(additions.map { it.fileName }))
                .put("stageDirectory", stage.name)
            writeJournalAtomically(journalFile, journal)
            journalWritten = true

            additions.forEach { entry ->
                require(isOwnerAuthorized()) { "Owner session changed before file promotion." }
                val source = File(stage, entry.fileName)
                val destination = File(canonicalRoot, entry.fileName)
                require(!destination.exists()) { "Target filename appeared during restore." }
                require(source.renameTo(destination)) { "Unable to promote a staged wardrobe image." }
                promoted.add(destination)
            }

            require(isOwnerAuthorized()) { "Owner session changed before index commit." }
            require(audit("WARDROBE_MEDIA_RESTORE_FILES_STAGED", "bundle=${preview.bundleId};added=${additions.size}")) {
                "Security audit failed before index commit."
            }
            require(writeIndex(nextJson)) { "Encrypted wardrobe index write failed." }
            require(readIndex() == nextJson) { "Wardrobe index read-back verification failed." }
            require(isOwnerAuthorized()) { "Owner session changed after index commit." }
            require(audit("WARDROBE_MEDIA_RESTORE_COMMITTED", "bundle=${preview.bundleId};added=${additions.size};unchanged=$unchanged")) {
                "Security audit commit record failed."
            }

            journalFile.delete()
            stage.deleteRecursively()
            return Result(additions.size, unchanged, nextJson)
        } catch (failure: Exception) {
            val currentAfterFailure = runCatching { readIndex() }.getOrNull()
            if (currentAfterFailure == nextJson) {
                // The preference commit may have succeeded before a later audit/readback error.
                // Keep the promoted files and journal so recovery can finish the commit safely.
                throw failure
            }
            if (currentAfterFailure == currentIndexJson || currentAfterFailure == null) {
                runCatching { writeIndex(currentIndexJson) }
                promoted.forEach { runCatching { it.delete() } }
                if (journalWritten) runCatching { journalFile.delete() }
                stage.deleteRecursively()
            }
            // If the index is neither old nor new, preserve files and journal for manual recovery.
            throw failure
        }
    }

    /**
     * Call only inside an approved owner session. If the current encrypted index
     * matches the journal's target digest, the transaction committed and only
     * temporary state is removed. If it matches the old digest, promoted files
     * are rolled back. Any third state fails closed and preserves the journal.
     */
    fun recover(
        root: File,
        journalDirectory: File,
        readIndex: () -> String?,
        isOwnerAuthorized: () -> Boolean,
        audit: (String, String) -> Boolean
    ): Boolean {
        if (!isOwnerAuthorized()) return false
        val journalFile = File(journalDirectory.canonicalFile, JOURNAL_NAME)
        if (!journalFile.exists()) return true
        if (!audit("WARDROBE_MEDIA_RESTORE_RECOVERY_REQUESTED", "pending restore journal found")) return false
        return try {
            val journal = JSONObject(journalFile.readText(Charsets.UTF_8))
            require(journal.optInt("version", -1) == JOURNAL_VERSION)
            val current = readIndex() ?: return false
            val currentDigest = sha256(current)
            val oldDigest = journal.getString("oldIndexSha256")
            val nextDigest = journal.getString("nextIndexSha256")
            val stageName = journal.getString("stageDirectory")
            require(stageName.matches(Regex("ava-wardrobe-restore-[0-9a-fA-F-]{36}\\.stage")))
            val stage = File(journalDirectory.canonicalFile, stageName)
            val files = journal.getJSONArray("promotedFiles")
            if (currentDigest == nextDigest) {
                // New index is live. Keep all promoted media.
            } else if (currentDigest == oldDigest) {
                for (i in 0 until files.length()) {
                    val name = files.getString(i)
                    require(name.matches(Regex("wardrobe_[0-9a-fA-F-]{36}\\.(jpg|png|gif|webp)")))
                    File(root.canonicalFile, name).delete()
                }
            } else {
                return false
            }
            stage.deleteRecursively()
            require(journalFile.delete() || !journalFile.exists())
            audit("WARDROBE_MEDIA_RESTORE_RECOVERY_COMPLETED", "transaction=${journal.optString("transactionId")}")
        } catch (_: Exception) {
            false
        }
    }

    private fun writeJournalAtomically(target: File, journal: JSONObject) {
        val temporary = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(journal.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (target.exists()) require(target.delete())
        require(temporary.renameTo(target)) { "Unable to persist restore recovery journal." }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHex()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
