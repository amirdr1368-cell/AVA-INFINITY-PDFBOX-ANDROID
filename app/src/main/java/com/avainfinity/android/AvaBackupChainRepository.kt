package com.avainfinity.android

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject

class AvaBackupChainRepository(
    context: Context,
    private val root: DocumentFile,
    private val passphrase: String,
    private val localStore: AvaLocalStore
) {
    data class Stored(val file: DocumentFile, val archive: JSONObject)
    data class Head(val stored: Stored, val payload: JSONObject)
    data class RestorePoint(val stored: Stored, val payload: JSONObject)
    data class Scan(
        val archives: List<Stored>,
        val latest: Head?,
        val hasCorruption: Boolean
    )

    private val app = context.applicationContext

    fun scan(): Scan = synchronized(AvaBackupChainLock.lock) { scanLocked() }

    private fun scanLocked(): Scan {
        val files = try {
            root.listFiles()
        } catch (_: Exception) {
            return Scan(emptyList(), null, true)
        }
        val candidates = files.filter { file ->
            val name = file.name ?: ""
            file.isFile && name.endsWith(".avaibk", ignoreCase = true) &&
                (name.startsWith("AVA_full_") || name.startsWith("AVA_delta_") ||
                    name.startsWith("AVA_milestone_"))
        }
        val stored = mutableListOf<Stored>()
        var corrupt = false
        candidates.forEach { file ->
            try {
                val preview = AvaEncryptedBackup(app).previewEncrypted(file.uri, passphrase.toCharArray())
                val name = file.name ?: ""
                val archive = preview.optJSONObject("archive")
                if (archive == null && preview.optString("archiveType") == "legacy_full" &&
                    name.startsWith("AVA_milestone_")
                ) return@forEach
                require(archive != null) { "Chain archive metadata is missing." }
                AvaBackupChain.validateHeader(archive)
                require(
                    (name.startsWith("AVA_full_") || name.startsWith("AVA_milestone_")) &&
                        archive.optString("archiveType") == "full" ||
                    name.startsWith("AVA_delta_") && archive.optString("archiveType") == "delta"
                ) { "Archive filename/type mismatch." }
                stored.add(Stored(file, JSONObject(archive.toString())))
            } catch (_: Exception) {
                corrupt = true
            }
        }

        val ids = mutableSetOf<String>()
        stored.forEach { if (!ids.add(it.archive.optString("archiveId"))) corrupt = true }
        val byId = stored.associateBy { it.archive.optString("archiveId") }
        val children = stored.filter { it.archive.optString("archiveType") == "delta" }
            .groupBy { it.archive.optString("parentArchiveId") }
        if (children.values.any { it.size > 1 }) corrupt = true
        stored.filter { it.archive.optString("archiveType") == "delta" }.forEach { item ->
            if (!byId.containsKey(item.archive.optString("parentArchiveId"))) corrupt = true
        }

        val validHeads = mutableListOf<Head>()
        stored.sortedByDescending { it.archive.optLong("createdAt") }.forEach { item ->
            try {
                val payload = AvaBackupChain.reconstruct(
                    stored.map { it.archive },
                    item.archive.getString("archiveId")
                ) { localStore.validateBackupPayload(it) }
                validHeads.add(Head(item, payload))
            } catch (_: Exception) {
                // Any invalid full or delta prevents safe retention decisions.
                corrupt = true
            }
        }
        val latest = if (corrupt) null else validHeads.maxByOrNull {
            it.stored.archive.optLong("createdAt")
        }
        return Scan(stored, latest, corrupt)
    }

    fun writeManifest(scan: Scan): Boolean =
        synchronized(AvaBackupChainLock.lock) { writeManifestLocked(scan) }

    fun ensureManifest(scan: Scan): Boolean =
        synchronized(AvaBackupChainLock.lock) {
            if (scan.hasCorruption) return@synchronized false
            if (scan.archives.isEmpty()) return@synchronized true
            val currentIndex = readLatestManifestLocked()
            if (currentIndex != null && manifestMatches(scan, currentIndex)) {
                true
            } else {
                writeManifestLocked(scan)
            }
        }

    private fun readLatestManifestLocked(): JSONObject? {
        val indexes = try {
            root.listFiles()
                .filter { it.isFile && it.name?.startsWith("AVA_chain_index_") == true &&
                    it.name?.endsWith(".avacidx", ignoreCase = true) == true
                }
                .sortedByDescending { it.name ?: "" }
        } catch (_: Exception) {
            return null
        }
        val latest = indexes.firstOrNull() ?: return null
        return try {
            val preview = AvaEncryptedBackup(app).previewEncrypted(latest.uri, passphrase.toCharArray())
            if (preview.optString("health") != "authenticated") null
            else preview.optJSONObject("index")?.takeIf {
                it.optString("format") == "AVA-INFINITY-BACKUP-INDEX-1" &&
                    it.optInt("schemaVersion") == 1
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun manifestMatches(scan: Scan, index: JSONObject): Boolean {
        val entries = index.optJSONArray("entries") ?: return false
        if (entries.length() != scan.archives.size ||
            index.optInt("entryCount", -1) != scan.archives.size
        ) return false
        val byName = scan.archives.associateBy { it.file.name ?: "" }
        for (position in 0 until entries.length()) {
            val entry = entries.optJSONObject(position) ?: return false
            val stored = byName[entry.optString("fileName")] ?: return false
            val archive = stored.archive
            val digest = if (archive.optString("archiveType") == "full")
                archive.optString("payloadSha256")
            else archive.optString("resultPayloadSha256")
            if (entry.optString("archiveId") != archive.optString("archiveId") ||
                entry.optString("archiveType") != archive.optString("archiveType") ||
                entry.optString("baseArchiveId") != archive.optString("baseArchiveId") ||
                entry.optString("chainHash") != archive.optString("chainHash") ||
                entry.optInt("sequence", -1) != archive.optInt("sequence") ||
                entry.optLong("createdAt") != archive.optLong("createdAt") ||
                entry.optLong("sizeBytes", -1L) != stored.file.length() ||
                entry.optString("contentDigest") != digest
            ) return false
            val parent = archive.optString("parentArchiveId")
            if (entry.optString("parentArchiveId") != parent &&
                !(archive.isNull("parentArchiveId") && entry.isNull("parentArchiveId"))
            ) return false
        }
        val head = scan.latest?.stored?.archive
        return index.optString("headArchiveId") == (head?.optString("archiveId") ?: "null") &&
            index.optString("headBaseArchiveId") == (head?.optString("baseArchiveId") ?: "null") &&
            index.optInt("headSequence", -1) == (head?.optInt("sequence") ?: 0) &&
            index.optString("headChainHash") == (head?.optString("chainHash") ?: "null")
    }

    private fun writeManifestLocked(scan: Scan): Boolean {
        if (scan.hasCorruption) return false
        var temporary: DocumentFile? = null
        return try {
            val entries = org.json.JSONArray()
            scan.archives.sortedBy { it.archive.optLong("createdAt") }.forEach { stored ->
                val archive = stored.archive
                val digest = if (archive.optString("archiveType") == "full")
                    archive.optString("payloadSha256")
                else archive.optString("resultPayloadSha256")
                entries.put(JSONObject()
                    .put("archiveId", archive.optString("archiveId"))
                    .put("fileName", stored.file.name ?: "")
                    .put("archiveType", archive.optString("archiveType"))
                    .put("checkpointKind", archive.optString("checkpointKind"))
                    .put("parentArchiveId", archive.opt("parentArchiveId"))
                    .put("baseArchiveId", archive.optString("baseArchiveId"))
                    .put("sequence", archive.optInt("sequence"))
                    .put("checkpointLabels", archive.optJSONArray("checkpointLabels") ?: org.json.JSONArray())
                    .put("previousChainHash", archive.opt("previousChainHash"))
                    .put("chainHash", archive.optString("chainHash"))
                    .put("createdAt", archive.optLong("createdAt"))
                    .put("contentDigest", digest)
                    .put("sizeBytes", stored.file.length()))
            }
            val head = scan.latest?.stored?.archive
            val existingGeneration = root.listFiles()
                .mapNotNull { file ->
                    val name = file.name ?: return@mapNotNull null
                    Regex("AVA_chain_index_v3_(\\d{12})_").find(name)
                        ?.groupValues?.getOrNull(1)?.toLongOrNull()
                }.maxOrNull() ?: 0L
            val generation = existingGeneration + 1L
            val index = JSONObject()
                .put("format", "AVA-INFINITY-BACKUP-INDEX-1")
                .put("schemaVersion", 1)
                .put("generation", generation)
                .put("createdAt", System.currentTimeMillis())
                .put("entryCount", entries.length())
                .put("headArchiveId", head?.optString("archiveId") ?: JSONObject.NULL)
                .put("headBaseArchiveId", head?.optString("baseArchiveId") ?: JSONObject.NULL)
                .put("headSequence", head?.optInt("sequence") ?: 0)
                .put("headChainHash", head?.optString("chainHash") ?: JSONObject.NULL)
                .put("entries", entries)
            val token = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            temporary = root.createFile(
                "application/octet-stream",
                "AVA_pending_index_" + token + ".tmp.avacidx"
            ) ?: throw IllegalStateException("Backup index temporary file could not be created.")
            AvaEncryptedBackup(app).exportEncrypted(
                temporary!!.uri, index, passphrase.toCharArray()
            )
            val preview = AvaEncryptedBackup(app).previewEncrypted(
                temporary!!.uri, passphrase.toCharArray()
            )
            val verifiedIndex = preview.getJSONObject("index")
            require(preview.optString("health") == "authenticated")
            require(AvaBackupChain.sha256(index) == AvaBackupChain.sha256(verifiedIndex))
            val generationName = generation.toString().padStart(12, '0')
            val finalName = "AVA_chain_index_v3_" + generationName + "_" + token + ".avacidx"
            require(temporary!!.renameTo(finalName)) { "Verified backup index could not be promoted." }
            temporary = null
            val publishedIndex = root.findFile(finalName)
                ?: throw IllegalStateException("Published backup index is missing.")
            val publishedPreview = AvaEncryptedBackup(app).previewEncrypted(
                publishedIndex.uri, passphrase.toCharArray()
            )
            val publishedPayload = publishedPreview.getJSONObject("index")
            require(publishedPreview.optString("health") == "authenticated")
            require(AvaBackupChain.sha256(index) == AvaBackupChain.sha256(publishedPayload)) {
                "Published backup index read-back mismatch."
            }

            val indexes = root.listFiles()
                .filter { it.isFile && it.name?.startsWith("AVA_chain_index_") == true }
                .sortedByDescending { it.name ?: "" }
            indexes.drop(2).forEach { it.delete() }
            true
        } catch (_: Exception) {
            temporary?.delete()
            false
        }
    }

    fun restorePoints(scan: Scan): List<RestorePoint> {
        val archives = scan.archives.map { it.archive }
        return scan.archives.mapNotNull { stored ->
            try {
                val payload = AvaBackupChain.reconstruct(
                    archives,
                    stored.archive.getString("archiveId")
                ) { localStore.validateBackupPayload(it) }
                RestorePoint(stored, payload)
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.stored.archive.optLong("createdAt") }
    }

    /**
     * Dependency-aware retention. It refuses to prune if the scanned graph is ambiguous or damaged.
     */
    fun prune(scan: Scan, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(AvaBackupChainLock.lock) { pruneLocked(scan, now) }

    private fun pruneLocked(scan: Scan, now: Long): Boolean {
        if (scan.hasCorruption) return false
        val stored = scan.archives
        val byId = stored.associateBy { it.archive.optString("archiveId") }
        val fulls = stored.filter { it.archive.optString("archiveType") == "full" }
        val keepFullIds = mutableSetOf<String>()
        val keepDeltaIds = mutableSetOf<String>()

        fun hasLabel(archive: JSONObject, label: String): Boolean {
            val labels = archive.optJSONArray("checkpointLabels")
            if (labels != null) {
                for (index in 0 until labels.length()) if (labels.optString(index) == label) return true
            }
            return archive.optString("checkpointKind") == label
        }

        fulls.filter { hasLabel(it.archive, "daily") }
            .sortedByDescending { it.archive.optLong("createdAt") }
            .take(30).forEach { keepFullIds.add(it.archive.optString("archiveId")) }
        fulls.filter { hasLabel(it.archive, "weekly") }
            .sortedByDescending { it.archive.optLong("createdAt") }
            .take(12).forEach { keepFullIds.add(it.archive.optString("archiveId")) }
        fulls.filter { hasLabel(it.archive, "monthly") }
            .sortedByDescending { it.archive.optLong("createdAt") }
            .take(12).forEach { keepFullIds.add(it.archive.optString("archiveId")) }
        fulls.filter { hasLabel(it.archive, "milestone") ||
            (it.file.name?.startsWith("AVA_milestone_") == true)
        }.forEach { keepFullIds.add(it.archive.optString("archiveId")) }

        val recentCutoff = now - 30L * 24L * 60L * 60L * 1000L
        val retainedDeltas = stored.filter {
            it.archive.optString("archiveType") == "delta" &&
                it.archive.optLong("createdAt") >= recentCutoff
        }
        retainedDeltas.forEach { target ->
            var cursor = target.archive
            val visited = mutableSetOf<String>()
            while (cursor.optString("archiveType") == "delta") {
                val id = cursor.optString("archiveId")
                if (!visited.add(id)) return false
                keepDeltaIds.add(id)
                val parentId = cursor.optString("parentArchiveId")
                val parent = byId[parentId]?.archive ?: return false
                cursor = parent
            }
            if (cursor.optString("archiveType") != "full") return false
            keepFullIds.add(cursor.optString("archiveId"))
        }

        fulls.filter { hasLabel(it.archive, "initial") || hasLabel(it.archive, "recovery") }
            .maxByOrNull { it.archive.optLong("createdAt") }
            ?.let { keepFullIds.add(it.archive.optString("archiveId")) }

        var success = true
        stored.forEach { item ->
            val id = item.archive.optString("archiveId")
            val keep = if (item.archive.optString("archiveType") == "full") {
                id in keepFullIds
            } else {
                id in keepDeltaIds
            }
            if (!keep && !item.file.delete()) success = false
        }
        return success
    }
}
