package com.avainfinity.android

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

/**
 * Pure chain-envelope logic. It never reads or writes storage and never mutates its inputs.
 * The encrypted file layer must authenticate each archive before this object receives it.
 */
object AvaBackupChain {
    const val FORMAT = "AVA-INFINITY-BACKUP-CHAIN-3"
    const val SCHEMA_VERSION = 3

    private val archiveIdPattern = Regex("[A-Fa-f0-9-]{16,64}")
    private val digestPattern = Regex("[a-f0-9]{64}")
    private val fullCheckpointKinds = setOf("initial", "daily", "weekly", "monthly", "recovery", "milestone")

    fun createFull(
        payload: JSONObject,
        checkpointKind: String,
        archiveId: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
        checkpointLabels: List<String> = listOf(checkpointKind)
    ): JSONObject {
        require(checkpointKind in fullCheckpointKinds)
        require(archiveIdPattern.matches(archiveId))
        require(createdAt > 0)
        require(checkpointLabels.isNotEmpty())
        require(checkpointLabels.all { it in fullCheckpointKinds })
        require(checkpointLabels.distinct().size == checkpointLabels.size)
        require(checkpointKind in checkpointLabels)
        val safeLabels = checkpointLabels.sorted()
        val copy = JSONObject(payload.toString())
        val archive = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("archiveId", archiveId)
            .put("archiveType", "full")
            .put("checkpointKind", checkpointKind)
            .put("checkpointLabels", JSONArray(safeLabels))
            .put("createdAt", createdAt)
            .put("parentArchiveId", JSONObject.NULL)
            .put("baseArchiveId", archiveId)
            .put("sequence", 0)
            .put("previousChainHash", JSONObject.NULL)
            .put("payloadSha256", sha256(copy))
            .put("payload", copy)
        archive.put("chainHash", calculateChainHash(archive))
        return archive
    }

    fun createDelta(
        parentArchive: JSONObject,
        parentPayload: JSONObject,
        currentPayload: JSONObject,
        checkpointKind: String = "event",
        archiveId: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis()
    ): JSONObject {
        require(checkpointKind == "event")
        require(archiveIdPattern.matches(archiveId))
        require(createdAt > 0)
        validateHeader(parentArchive)
        val parentId = parentArchive.getString("archiveId")
        require(parentId != archiveId) { "An archive cannot be its own parent." }
        require(createdAt >= parentArchive.optLong("createdAt")) {
            "A child archive cannot predate its parent."
        }
        val baseId = parentArchive.getString("baseArchiveId")
        val parentSequence = parentArchive.optInt("sequence", -1)
        require(parentSequence >= 0)
        val expectedParentDigest = if (parentArchive.optString("archiveType") == "full") {
            parentArchive.optString("payloadSha256")
        } else {
            parentArchive.optString("resultPayloadSha256")
        }
        require(expectedParentDigest == sha256(parentPayload)) {
            "The supplied parent payload does not match the parent archive."
        }

        val changes = JSONObject()
        val removed = JSONArray()
        val oldKeys = parentPayload.keys().asSequence().toSet()
        val newKeys = currentPayload.keys().asSequence().toSet()

        newKeys.sorted().forEach { key ->
            val next = currentPayload.get(key)
            if (!parentPayload.has(key) || canonical(parentPayload.get(key)) != canonical(next)) {
                changes.put(key, deepCopyValue(next))
            }
        }
        (oldKeys - newKeys).sorted().forEach { removed.put(it) }

        val archive = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("archiveId", archiveId)
            .put("archiveType", "delta")
            .put("checkpointKind", checkpointKind)
            .put("createdAt", createdAt)
            .put("parentArchiveId", parentId)
            .put("baseArchiveId", baseId)
            .put("sequence", parentSequence + 1)
            .put("previousChainHash", parentArchive.getString("chainHash"))
            .put("parentPayloadSha256", sha256(parentPayload))
            .put("resultPayloadSha256", sha256(currentPayload))
            .put("changes", changes)
            .put("removedKeys", removed)
        archive.put("chainHash", calculateChainHash(archive))
        return archive
    }

    fun validateHeader(archive: JSONObject) {
        require(archive.optString("format") == FORMAT) { "Unsupported backup chain format." }
        require(archive.optInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported backup chain schema." }
        require(archiveIdPattern.matches(archive.optString("archiveId"))) { "Invalid archive identity." }
        require(archive.optLong("createdAt") > 0) { "Invalid archive timestamp." }
        require(archive.optString("archiveType") in setOf("full", "delta")) { "Invalid archive type." }
        require(archiveIdPattern.matches(archive.optString("baseArchiveId"))) {
            "Invalid base archive identity."
        }
        require(archive.optInt("sequence", -1) >= 0) { "Invalid chain sequence." }
        require(digestPattern.matches(archive.optString("chainHash"))) { "Invalid chain digest." }

        when (archive.optString("archiveType")) {
            "full" -> {
                require(archive.optString("checkpointKind") in fullCheckpointKinds)
                val labels = archive.optJSONArray("checkpointLabels")
                    ?: throw IllegalArgumentException("Full archive checkpoint labels are missing.")
                val parsedLabels = mutableSetOf<String>()
                for (index in 0 until labels.length()) {
                    val label = labels.optString(index)
                    require(label in fullCheckpointKinds && parsedLabels.add(label)) {
                        "Full archive checkpoint labels are invalid or duplicated."
                    }
                }
                require(archive.optString("checkpointKind") in parsedLabels)
                require(archive.optString("baseArchiveId") == archive.optString("archiveId"))
                require(archive.optInt("sequence") == 0)
                require(archive.isNull("parentArchiveId"))
                require(archive.isNull("previousChainHash"))
                require(digestPattern.matches(archive.optString("payloadSha256")))
            }
            "delta" -> {
                require(archive.optString("checkpointKind") == "event")
                require(archiveIdPattern.matches(archive.optString("parentArchiveId")))
                require(digestPattern.matches(archive.optString("previousChainHash")))
                require(digestPattern.matches(archive.optString("parentPayloadSha256")))
                require(digestPattern.matches(archive.optString("resultPayloadSha256")))
                require(archive.optJSONObject("changes") != null)
                require(archive.optJSONArray("removedKeys") != null)
            }
        }
        require(archive.optString("chainHash") == calculateChainHash(archive)) {
            "Archive chain header digest mismatch."
        }
    }

    fun materializeFull(
        archive: JSONObject,
        validatePayload: (JSONObject) -> Boolean
    ): JSONObject {
        validateHeader(archive)
        require(archive.optString("archiveType") == "full") {
            "A restore chain must begin with a full archive."
        }
        val payload = JSONObject(archive.getJSONObject("payload").toString())
        require(sha256(payload) == archive.optString("payloadSha256")) {
            "Full archive payload digest mismatch."
        }
        require(validatePayload(payload)) { "Full archive payload schema is invalid." }
        return payload
    }

    fun applyDelta(
        parentArchive: JSONObject,
        parentPayload: JSONObject,
        delta: JSONObject,
        validatePayload: (JSONObject) -> Boolean
    ): JSONObject {
        validateHeader(parentArchive)
        validateHeader(delta)
        require(delta.optString("archiveType") == "delta") { "Expected a delta archive." }
        require(delta.optString("parentArchiveId") == parentArchive.optString("archiveId")) {
            "Delta parent identity mismatch."
        }
        require(delta.optString("baseArchiveId") == parentArchive.optString("baseArchiveId")) {
            "Delta base identity mismatch."
        }
        require(delta.optInt("sequence") == parentArchive.optInt("sequence") + 1) {
            "Delta sequence is not contiguous."
        }
        require(delta.optLong("createdAt") >= parentArchive.optLong("createdAt")) {
            "Delta timestamp predates its parent."
        }
        require(delta.optString("previousChainHash") == parentArchive.optString("chainHash")) {
            "Delta previous chain digest mismatch."
        }
        val expectedParentDigest = if (parentArchive.optString("archiveType") == "full") {
            parentArchive.optString("payloadSha256")
        } else {
            parentArchive.optString("resultPayloadSha256")
        }
        require(expectedParentDigest == sha256(parentPayload) &&
            delta.optString("parentPayloadSha256") == sha256(parentPayload)
        ) { "Delta parent payload digest mismatch." }

        val result = JSONObject(parentPayload.toString())
        val removed = delta.optJSONArray("removedKeys") ?: JSONArray()
        val removedKeys = mutableSetOf<String>()
        for (index in 0 until removed.length()) {
            val key = removed.optString(index)
            require(key.isNotBlank() && removedKeys.add(key)) {
                "Delta contains an empty or duplicate removal key."
            }
        }
        val changes = delta.optJSONObject("changes")
            ?: throw IllegalArgumentException("Delta change set is missing.")
        val changeKeys = changes.keys().asSequence().toSet()
        require((removedKeys intersect changeKeys).isEmpty()) {
            "A delta cannot remove and replace the same section."
        }
        removedKeys.forEach { result.remove(it) }
        val keys = changes.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result.put(key, deepCopyValue(changes.get(key)))
        }
        require(delta.optString("resultPayloadSha256") == sha256(result)) {
            "Delta result payload digest mismatch."
        }
        require(validatePayload(result)) { "Reconstructed payload schema is invalid." }
        return result
    }

    /**
     * Reconstructs one target archive. Forks, cycles, missing parents and invalid sequence
     * or digest links fail closed instead of guessing.
     */
    fun reconstruct(
        archives: List<JSONObject>,
        targetArchiveId: String,
        validatePayload: (JSONObject) -> Boolean
    ): JSONObject {
        val byId = linkedMapOf<String, JSONObject>()
        archives.forEach { archive ->
            validateHeader(archive)
            val id = archive.getString("archiveId")
            require(!byId.containsKey(id)) { "Duplicate archive identity." }
            byId[id] = JSONObject(archive.toString())
        }
        val target = byId[targetArchiveId]
            ?: throw IllegalArgumentException("Target archive is missing.")
        val children = mutableMapOf<String, MutableList<String>>()
        byId.values.filter { it.optString("archiveType") == "delta" }.forEach { delta ->
            val parent = delta.optString("parentArchiveId")
            children.getOrPut(parent) { mutableListOf() }.add(delta.getString("archiveId"))
        }

        var cursor = target
        val reverse = mutableListOf<JSONObject>()
        val visited = mutableSetOf<String>()
        while (cursor.optString("archiveType") == "delta") {
            val id = cursor.getString("archiveId")
            require(visited.add(id)) { "Backup chain cycle detected." }
            val parentId = cursor.optString("parentArchiveId")
            require(archiveIdPattern.matches(parentId)) { "Delta parent is missing." }
            require(children[parentId]?.size == 1) { "Backup chain fork or missing parent detected." }
            reverse.add(cursor)
            cursor = byId[parentId] ?: throw IllegalArgumentException("Backup chain parent is missing.")
        }
        require(visited.add(cursor.getString("archiveId"))) { "Backup chain cycle detected." }
        require(cursor.optString("archiveType") == "full") {
            "Backup chain root is not a full archive."
        }

        val full = cursor
        var payload = materializeFull(full, validatePayload)
        var parent = full
        reverse.asReversed().forEach { delta ->
            payload = applyDelta(parent, payload, delta, validatePayload)
            parent = delta
        }
        require(target.optString("baseArchiveId") == full.optString("archiveId")) {
            "Backup chain root does not match target base identity."
        }
        return payload
    }

    fun sha256(value: JSONObject): String {
        val bytes = canonical(value).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun calculateChainHash(archive: JSONObject): String {
        val header = JSONObject()
            .put("format", archive.optString("format"))
            .put("schemaVersion", archive.optInt("schemaVersion"))
            .put("archiveId", archive.optString("archiveId"))
            .put("archiveType", archive.optString("archiveType"))
            .put("checkpointKind", archive.optString("checkpointKind"))
            .put("createdAt", archive.optLong("createdAt"))
            .put("parentArchiveId", if (archive.isNull("parentArchiveId")) JSONObject.NULL else archive.optString("parentArchiveId"))
            .put("baseArchiveId", archive.optString("baseArchiveId"))
            .put("sequence", archive.optInt("sequence"))
            .put("previousChainHash", if (archive.isNull("previousChainHash")) JSONObject.NULL else archive.optString("previousChainHash"))
        if (archive.optString("archiveType") == "full") {
            header.put("checkpointLabels", archive.optJSONArray("checkpointLabels") ?: JSONArray())
            header.put("payloadSha256", archive.optString("payloadSha256"))
        } else {
            header.put("parentPayloadSha256", archive.optString("parentPayloadSha256"))
            header.put("resultPayloadSha256", archive.optString("resultPayloadSha256"))
            header.put("changes", archive.optJSONObject("changes") ?: JSONObject())
            header.put("removedKeys", archive.optJSONArray("removedKeys") ?: JSONArray())
        }
        return sha256(header)
    }

    private fun deepCopyValue(value: Any): Any = when (value) {
        is JSONObject -> JSONObject(value.toString())
        is JSONArray -> JSONArray(value.toString())
        else -> value
    }

    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            keys.joinToString(prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + canonical(value.get(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") {
            canonical(value.get(it))
        }
        is String -> JSONObject.quote(value)
        is Number -> {
            val number = BigDecimal(value.toString()).stripTrailingZeros()
            if (number.compareTo(BigDecimal.ZERO) == 0) "0" else number.toPlainString()
        }
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
