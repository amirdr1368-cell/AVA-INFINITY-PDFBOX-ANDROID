package com.avainfinity.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AvaLocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AvaSecurePrefs(context, "ava_local_data")
    private val vault = AvaVaultStore(context)

    private fun rawAll(bucket: String): List<JSONObject> {
        val vaultRaw = vault.readBucket(bucket)
        if (vault.isDeleteMarker(vaultRaw.orEmpty())) return emptyList()
        val raw = vaultRaw ?: prefs.getString(bucket, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (error: Exception) {
            throw IllegalStateException("Stored AVA data bucket is corrupted; existing data was not overwritten.", error)
        }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun ensureBucketAccess(bucket: String) {
        val access = AacsController.get(appContext)
        if (access.isPublicOnlyModeActive()) throw SecurityException("Public-only mode blocks local personal data.")
        if (!access.hasActiveSession()) return
        val personId = access.currentSessionPersonId()
        if (access.currentSessionLevel() == 1 && personId == "owner") return
        val expectedBucket = personId?.let { "memories_" + it }
        if (bucket == expectedBucket && personId?.startsWith("person-") == true &&
            access.canCurrentSessionAccess(AvaDataClass.PERSONAL)
        ) {
            val profile = PersonProfileStore(appContext).all().firstOrNull { it.id == personId }
            if (profile?.relationshipMemoryApproved == true) return
        }
        throw SecurityException("The active AACS session cannot access this data bucket.")
    }

    private fun visibleItems(bucket: String): List<JSONObject> {
        val now = System.currentTimeMillis()
        val sevenDays = 7L * 24L * 60L * 60L * 1000L
        return rawAll(bucket).filter { item ->
            if (item.optString("retention") != "temporary") true
            else {
                val createdAt = item.optLong("createdAt", now)
                val expiresAt = item.optLong("expiresAt", createdAt + sevenDays)
                expiresAt > now
            }
        }
    }

    fun all(bucket: String): List<JSONObject> {
        ensureBucketAccess(bucket)
        return visibleItems(bucket)
    }

    private fun saveBucket(bucket: String, array: JSONArray) = synchronized(AvaBackupSnapshotLock.lock) {
        val raw = array.toString()
        if (vault.isConfigured()) {
            if (!vault.writeBucket(bucket, raw)) {
                throw IllegalStateException("Primary vault and bounded recovery queue are unavailable; data was not changed.")
            }
            if (!vault.hasQueued(bucket)) prefs.edit().putString(bucket, raw).apply()
        } else {
            prefs.edit().putString(bucket, raw).apply()
        }
    }

    fun exportRawBuckets(): Map<String, String> {
        requireVocabularyOwnerContext()
        val queueKeys = vault.rawQueueSnapshot().keys().asSequence().toList()
        val keys = (prefs.allKeys + vault.listBucketNames("") + queueKeys)
            .filter { it != "profiles" && (it in setOf("memories", "memories_amir", "vocabulary", "tasks", "pending_relationship_memories") || it.startsWith("memories_person-")) }
            .toSet()
        return keys.associateWith { bucket ->
            val array = JSONArray()
            all(bucket).forEach { array.put(it) }
            array.toString()
        }
    }

    fun isVaultConfigured(): Boolean = vault.isConfigured()
    fun isVaultSafeMode(): Boolean = vault.isSafeMode()
    fun vaultPendingCount(): Int = vault.pendingCount()
    fun vaultStatus(): String = vault.status()

    fun flushVaultQueue(): Boolean {
        val pending = vault.rawQueueSnapshot()
        if (pending.length() == 0) return true
        if (!vault.flushPending()) return false
        val keys = pending.keys()
        while (keys.hasNext()) {
            val bucket = keys.next()
            val raw = pending.optString(bucket)
            if (bucket == "profiles") continue
            if (vault.isDeleteMarker(raw)) prefs.edit().remove(bucket).apply()
            else prefs.edit().putString(bucket, raw).apply()
        }
        return true
    }

    private fun deleteBucket(bucket: String) {
        if (vault.isConfigured() && !vault.deleteBucket(bucket)) {
            throw IllegalStateException("Primary vault recovery queue is full; deletion was not completed.")
        }
        prefs.edit().remove(bucket).apply()
    }

    fun quarantineRelationshipMemory(personId: String): Boolean {
        requireVocabularyOwnerContext()
        if (!personId.matches(Regex("person-[A-Za-z0-9-]{1,80}"))) return false
        return try {
            val activeBucket = "memories_" + personId
            val activeItems = visibleItems(activeBucket)
            if (activeItems.isEmpty()) return true
            val pending = visibleItems("pending_relationship_memories")
            val next = JSONArray()
            pending.filterNot { it.optString("personId") == personId }.forEach { next.put(it) }
            val items = JSONArray()
            val seen = mutableSetOf<String>()
            pending.firstOrNull { it.optString("personId") == personId }
                ?.optJSONArray("items")?.let { existingItems ->
                    for (index in 0 until existingItems.length()) {
                        val item = existingItems.optJSONObject(index) ?: continue
                        if (seen.add(item.toString())) items.put(item)
                    }
                }
            activeItems.forEach { if (seen.add(it.toString())) items.put(it) }
            next.put(JSONObject().put("personId", personId).put("items", items).put("quarantinedAt", System.currentTimeMillis()))
            saveBucket("pending_relationship_memories", next)
            saveBucket(activeBucket, JSONArray())
            AvaBackupScheduler.enqueueEventSnapshot(appContext)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun promotePendingRelationshipMemory(personId: String): Boolean {
        requireVocabularyOwnerContext()
        if (!personId.matches(Regex("person-[A-Za-z0-9-]{1,80}"))) return false
        return try {
            val pending = visibleItems("pending_relationship_memories")
            val entry = pending.firstOrNull { it.optString("personId") == personId } ?: return true
            val items = entry.optJSONArray("items") ?: JSONArray()
            saveBucket("memories_" + personId, items)
            val next = JSONArray()
            pending.filterNot { it.optString("personId") == personId }.forEach { next.put(it) }
            saveBucket("pending_relationship_memories", next)
            AvaBackupScheduler.enqueueEventSnapshot(appContext)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun add(bucket: String, fields: Map<String, String>) {
        ensureBucketAccess(bucket)
        val array = JSONArray()
        all(bucket).forEach { array.put(it) }
        val item = JSONObject()
        fields.forEach { (key, value) -> item.put(key, value) }
        item.put("createdAt", System.currentTimeMillis())
        array.put(item)
        saveBucket(bucket, array)
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
    }

    fun remove(bucket: String, index: Int) {
        ensureBucketAccess(bucket)
        val old = all(bucket)
        if (index !in old.indices) return
        val next = JSONArray()
        old.filterIndexed { i, _ -> i != index }.forEach { next.put(it) }
        saveBucket(bucket, next)
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
    }

    fun setDone(index: Int, done: Boolean) {
        ensureBucketAccess("tasks")
        val old = all("tasks")
        if (index !in old.indices) return
        old[index].put("done", done)
        val next = JSONArray()
        old.forEach { next.put(it) }
        saveBucket("tasks", next)
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
    }

    fun migrateLegacyVocabularyRecords(): Int {
        requireVocabularyOwnerContext()
        val old = all("vocabulary")
        var changed = 0
        old.forEach { item ->
            if (item.optString("id").isBlank()) {
                item.put("id", "vocab-" + UUID.randomUUID().toString())
                changed++
            }
            if (!item.has("status")) {
                item.put("status", "legacy_review_required")
                changed++
            }
            if (!item.has("language")) {
                item.put("language", "fa")
                changed++
            }
            if (!item.has("equivalents")) {
                item.put("equivalents", JSONObject())
                changed++
            }
            if (!item.has("pronunciation")) {
                item.put("pronunciation", "")
                changed++
            }
            if (!item.has("history")) {
                item.put("history", JSONArray())
                changed++
            }
        }
        if (changed > 0) saveVocabulary(old)
        return changed
    }

    private fun requireVocabularyOwnerContext() {
        val access = AacsController.get(appContext)
        if (!AvaVocabularyPolicy.canManage(
                access.isPublicOnlyModeActive(),
                access.hasActiveSession(),
                access.currentSessionLevel(),
                access.currentSessionPersonId(),
                access.isCurrentSessionApproved()
            )
        ) {
            throw SecurityException("AVA SECRET is locked outside the owner context.")
        }
    }

    private fun existingVocabularyTerms(
        items: List<JSONObject>,
        languageCode: String,
        excludingId: String? = null
    ): List<String> {
        return items.filter {
            it.optString("id") != excludingId &&
                it.optString("status", "legacy_review_required") != "archived"
        }.flatMap { item ->
            val terms = mutableListOf<String>()
            if (item.optString("language", "fa").equals(languageCode, true)) {
                terms.add(item.optString("word"))
            }
            val equivalent = item.optJSONObject("equivalents")?.optString(languageCode.lowercase()).orEmpty()
            if (equivalent.isNotBlank()) terms.add(equivalent)
            terms
        }
    }

    private fun vocabularySnapshot(item: JSONObject): JSONObject = JSONObject().apply {
        put("version", item.optInt("version", 1))
        put("word", item.optString("word"))
        put("meaning", item.optString("meaning"))
        put("category", item.optString("category", "عمومی"))
        put("language", item.optString("language", "fa"))
        put("equivalents", JSONObject(item.optJSONObject("equivalents")?.toString() ?: "{}"))
        put("pronunciation", item.optString("pronunciation"))
        put("status", item.optString("status", "legacy_review_required"))
        if (item.has("approvedAt")) put("approvedAt", item.optLong("approvedAt"))
        if (item.has("approvedBy")) put("approvedBy", item.optString("approvedBy"))
        put("archivedAt", System.currentTimeMillis())
    }

    private fun saveVocabulary(items: List<JSONObject>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        saveBucket("vocabulary", array)
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
    }

    fun addVocabulary(
        word: String,
        meaning: String,
        category: String,
        language: String = "fa",
        equivalents: Map<String, String> = emptyMap(),
        pronunciation: String = ""
    ): String {
        requireVocabularyOwnerContext()
        val cleanWord = word.trim()
        val cleanMeaning = meaning.trim()
        require(cleanWord.isNotBlank() && cleanWord.length <= 120) { "Vocabulary word is required and must be at most 120 characters." }
        require(cleanMeaning.isNotBlank() && cleanMeaning.length <= 2000) { "Vocabulary meaning is required and must be at most 2000 characters." }
        require(category.trim().length <= 80) { "Vocabulary category is too long." }
        require(AvaVocabularyPolicy.isValidLanguageCode(language)) { "Invalid language code." }
        val mapped = JSONObject()
        equivalents.forEach { (code, value) ->
            require(AvaVocabularyPolicy.isValidLanguageCode(code)) { "Invalid equivalent language code." }
            require(value.trim().length <= 500) { "Vocabulary equivalent is too long." }
            if (value.isNotBlank()) mapped.put(code.lowercase(), value.trim())
        }
        require(pronunciation.length <= 120) { "Pronunciation is too long." }
        val existing = all("vocabulary")
        require(!AvaVocabularyPolicy.hasTermConflict(existingVocabularyTerms(existing, language), cleanWord)) {
            "A vocabulary term already exists in this language."
        }
        require(equivalents.none { (code, value) ->
            AvaVocabularyPolicy.hasTermConflict(existingVocabularyTerms(existing, code), value)
        }) { "A translated equivalent conflicts with an existing vocabulary term." }
        val id = "vocab-" + UUID.randomUUID().toString()
        val entry = JSONObject().apply {
            put("id", id)
            put("word", cleanWord)
            put("meaning", cleanMeaning)
            put("category", category.trim().ifBlank { "عمومی" })
            put("language", language.lowercase())
            put("equivalents", mapped)
            put("pronunciation", pronunciation.trim())
            put("status", "draft")
            put("version", 1)
            put("history", JSONArray())
            }
        val next = all("vocabulary").toMutableList()
        next.add(entry)
        saveVocabulary(next)
        return id
    }

    fun updateVocabulary(
        entryId: String,
        word: String,
        meaning: String,
        category: String,
        equivalents: Map<String, String> = emptyMap(),
        pronunciation: String = ""
    ): Boolean {
        requireVocabularyOwnerContext()
        val cleanWord = word.trim()
        val cleanMeaning = meaning.trim()
        require(cleanWord.isNotBlank() && cleanWord.length <= 120)
        require(cleanMeaning.isNotBlank() && cleanMeaning.length <= 2000)
        require(category.trim().length <= 80 && pronunciation.length <= 120)
        val old = all("vocabulary")
        val item = old.firstOrNull { it.optString("id") == entryId } ?: return false
        if (item.optString("status", "legacy_review_required") == "archived") return false
        val language = item.optString("language", "fa")
        if (AvaVocabularyPolicy.hasTermConflict(existingVocabularyTerms(old, language, entryId), cleanWord) ||
            equivalents.any { (code, value) ->
                AvaVocabularyPolicy.hasTermConflict(existingVocabularyTerms(old, code, entryId), value)
            }
        ) return false
        val history = item.optJSONArray("history") ?: JSONArray()
        history.put(vocabularySnapshot(item))
        val mapped = JSONObject(item.optJSONObject("equivalents")?.toString() ?: "{}")
        equivalents.forEach { (code, value) ->
            require(code.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?")))
            if (value.isNotBlank()) mapped.put(code.lowercase(), value.trim().take(500))
            else mapped.remove(code.lowercase())
        }
        item.put("word", cleanWord)
        item.put("meaning", cleanMeaning)
        item.put("category", category.trim().ifBlank { "عمومی" })
        item.put("equivalents", mapped)
        item.put("pronunciation", pronunciation.trim())
        item.put("version", AvaVocabularyPolicy.nextVersion(item.optInt("version", 1)))
        item.put("status", "draft")
        item.remove("approvedAt")
        item.remove("approvedBy")
        item.put("updatedAt", System.currentTimeMillis())
        item.put("history", history)
        saveVocabulary(old)
        return true
    }

    fun approveVocabulary(entryId: String): Boolean {
        requireVocabularyOwnerContext()
        val access = AacsController.get(appContext)
        if (!access.consumeOwnerActionGrant("ava_secret_publish")) return false
        val old = all("vocabulary")
        val item = old.firstOrNull { it.optString("id") == entryId } ?: return false
        val status = item.optString("status", "legacy_review_required")
        if (!AvaVocabularyPolicy.canPublish(status, item.optString("word"), item.optString("meaning"))) return false
        val history = item.optJSONArray("history") ?: JSONArray()
        history.put(vocabularySnapshot(item))
        item.put("status", "approved")
        item.put("approvedBy", "owner")
        item.put("approvedAt", System.currentTimeMillis())
        item.put("version", item.optInt("version", 1) + 1)
        item.put("history", history)
        saveVocabulary(old)
        return true
    }

    fun rollbackVocabulary(entryId: String, targetVersion: Int): Boolean {
        requireVocabularyOwnerContext()
        val access = AacsController.get(appContext)
        if (!access.consumeOwnerActionGrant("ava_secret_rollback")) return false
        val old = all("vocabulary")
        val item = old.firstOrNull { it.optString("id") == entryId } ?: return false
        val history = item.optJSONArray("history") ?: JSONArray()
        val target = (0 until history.length()).mapNotNull { history.optJSONObject(it) }
            .firstOrNull { it.optInt("version") == targetVersion } ?: return false
        history.put(vocabularySnapshot(item))
        val nextVersion = item.optInt("version", 1) + 1
        listOf("word", "meaning", "category", "language", "pronunciation").forEach { key ->
            item.put(key, target.optString(key))
        }
        item.put("equivalents", JSONObject(target.optJSONObject("equivalents")?.toString() ?: "{}"))
        item.put("version", nextVersion)
        item.put("status", "draft")
        item.remove("approvedAt")
        item.remove("approvedBy")
        item.put("rolledBackFromVersion", targetVersion)
        item.put("updatedAt", System.currentTimeMillis())
        item.put("history", history)
        saveVocabulary(old)
        return true
    }

    fun archiveVocabulary(entryId: String): Boolean {
        requireVocabularyOwnerContext()
        val access = AacsController.get(appContext)
        if (!access.consumeOwnerActionGrant("ava_secret_archive")) return false
        val old = all("vocabulary")
        val item = old.firstOrNull { it.optString("id") == entryId } ?: return false
        if (item.optString("status") == "archived") return true
        val history = item.optJSONArray("history") ?: JSONArray()
        history.put(vocabularySnapshot(item))
        item.put("status", "archived")
        item.put("archivedAt", System.currentTimeMillis())
        item.put("version", item.optInt("version", 1) + 1)
        item.put("history", history)
        saveVocabulary(old)
        return true
    }

    fun cleanupExpiredTemporaryMemories(): Int {
        val queueKeys = vault.rawQueueSnapshot().keys().asSequence().toList()
        val buckets = (listOf("memories", "memories_amir") + prefs.allKeys +
            vault.listBucketNames("memories_person-") + queueKeys)
            .filter { it == "memories" || it == "memories_amir" || it.startsWith("memories_person-") }
            .toSet()
        val now = System.currentTimeMillis()
        val sevenDays = 7L * 24L * 60L * 60L * 1000L
        var removed = 0
        buckets.forEach { bucket ->
            val old = rawAll(bucket)
            val retained = old.filter { item ->
                if (item.optString("retention") != "temporary") true
                else {
                    val createdAt = item.optLong("createdAt", now)
                    val expiresAt = item.optLong("expiresAt", createdAt + sevenDays)
                    expiresAt > now
                }
            }
            if (retained.size != old.size) {
                val array = JSONArray()
                retained.forEach { array.put(it) }
                try {
                    saveBucket(bucket, array)
                    // Expiry is a deletion: scrub the internal cache even if the card is temporarily absent.
                    if (!vault.isConfigured() || !vault.hasQueued(bucket)) {
                        prefs.edit().putString(bucket, array.toString()).commit()
                    }
                    removed += old.size - retained.size
                } catch (_: Exception) {
                    // Expired items are already hidden by all(); retry physical deletion on the next resume.
                }
            }
        }
        val pendingOld = rawAll("pending_relationship_memories")
        if (pendingOld.isNotEmpty()) {
            val pendingNext = JSONArray()
            var pendingChanged = false
            pendingOld.forEach { entry ->
                val items = entry.optJSONArray("items") ?: JSONArray()
                val retained = JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val createdAt = item.optLong("createdAt", now)
                    val expiresAt = item.optLong("expiresAt", createdAt + sevenDays)
                    if (item.optString("retention") != "temporary" || expiresAt > now) retained.put(item)
                }
                if (retained.length() != items.length()) {
                    pendingChanged = true
                    removed += items.length() - retained.length()
                }
                pendingNext.put(JSONObject(entry.toString()).put("items", retained))
            }
            if (pendingChanged) {
                try {
                    saveBucket("pending_relationship_memories", pendingNext)
                    if (!vault.isConfigured() || !vault.hasQueued("pending_relationship_memories")) {
                        prefs.edit().putString("pending_relationship_memories", pendingNext.toString()).commit()
                    }
                } catch (_: Exception) {
                    // Expired nested items are excluded from all() and backup snapshots; retry physical cleanup later.
                }
            }
        }
        if (removed > 0) AvaBackupScheduler.enqueueEventSnapshot(appContext)
        return removed
    }

    fun extendTemporaryMemory(bucket: String, index: Int, reason: String): Boolean {
        ensureBucketAccess(bucket)
        if (reason.isBlank()) return false
        val old = all(bucket)
        if (index !in old.indices) return false
        val item = old[index]
        if (item.optString("retention") != "temporary") return false
        val now = System.currentTimeMillis()
        item.put("expiresAt", now + 7L * 24L * 60L * 60L * 1000L)
        item.put("extensionReason", reason.trim())
        item.put("extensionApprovedAt", now)
        val array = JSONArray()
        old.forEach { array.put(it) }
        saveBucket(bucket, array)
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
        return true
    }

    fun backupPayload(profiles: List<PersonProfile>): JSONObject = synchronized(AvaBackupSnapshotLock.lock) {
        requireVocabularyOwnerContext()
        JSONObject().apply {
        put("format", "AVA-INFINITY-MOBILE-BACKUP-1")
        // Archive timestamps live in the authenticated v3 envelope; keep payloads stable for no-op delta detection.
        listOf("memories", "memories_amir", "vocabulary", "tasks").forEach { bucket ->
            val array = JSONArray()
            all(bucket).filter { it.optString("retention") != "temporary" }.forEach { array.put(it) }
            put(bucket, array)
        }
        val people = JSONArray()
        profiles.forEach { people.put(it.toJson()) }
        put("profiles", people)
        val personMemories = JSONObject()
        profiles.forEach { profile ->
            val array = JSONArray()
            if (profile.relationshipMemoryApproved) {
                all("memories_" + profile.id)
                    .filter { it.optString("retention") != "temporary" }
                    .forEach { array.put(it) }
            }
            personMemories.put(profile.id, array)
        }
        put("personMemories", personMemories)
        val pendingRelationshipMemories = JSONArray()
        val pendingNow = System.currentTimeMillis()
        val temporaryLifetime = 7L * 24L * 60L * 60L * 1000L
        val pendingById = linkedMapOf<String, JSONObject>()
        all("pending_relationship_memories").forEach { entry ->
            val id = entry.optString("personId")
            if (id.matches(Regex("person-[A-Za-z0-9-]{1,80}"))) {
                val retained = JSONArray()
                val seenItems = mutableSetOf<String>()
                val items = entry.optJSONArray("items") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val createdAt = item.optLong("createdAt", 0L)
                    val expiresAt = item.optLong(
                        "expiresAt",
                        if (createdAt > 0L) createdAt + temporaryLifetime else 0L
                    )
                    if ((item.optString("retention") != "temporary" || expiresAt > pendingNow) && seenItems.add(item.toString())) {
                        retained.put(item)
                    }
                }
                pendingById[id] = JSONObject(entry.toString()).put("items", retained)
            }
        }
        // Legacy/unapproved profile buckets are exported as locked quarantine, never as active memory.
        profiles.filter { !it.relationshipMemoryApproved }.forEach { profile ->
            val legacyItems = all("memories_" + profile.id)
            if (legacyItems.isNotEmpty()) {
                val existing = pendingById[profile.id]
                val combined = JSONArray()
                val seenItems = mutableSetOf<String>()
                existing?.optJSONArray("items")?.let { oldItems ->
                    for (index in 0 until oldItems.length()) {
                        val item = oldItems.optJSONObject(index) ?: continue
                        if (seenItems.add(item.toString())) combined.put(item)
                    }
                }
                legacyItems.forEach { item ->
                    val createdAt = item.optLong("createdAt", 0L)
                    val expiresAt = item.optLong(
                        "expiresAt",
                        if (createdAt > 0L) createdAt + temporaryLifetime else 0L
                    )
                    if ((item.optString("retention") != "temporary" || expiresAt > pendingNow) &&
                        seenItems.add(item.toString())
                    ) combined.put(item)
                }
                if (combined.length() > 0) {
                    pendingById[profile.id] = JSONObject()
                        .put("personId", profile.id)
                        .put("items", combined)
                        .put(
                            "quarantinedAt",
                            existing?.optLong("quarantinedAt")
                                ?: legacyItems.mapNotNull { it.optLong("createdAt").takeIf { value -> value > 0L } }
                                    .minOrNull()
                                ?: 0L
                        )
                }
            }
        }
        pendingById.values.forEach { pendingRelationshipMemories.put(it) }
        // Portable AACS export includes historical audit and safe public/personal overrides only.
        // Active sessions, private grants, control grants, access tokens and Keystore keys never leave the device.
        put("aacsState", AacsController.get(appContext).portableStateSnapshot())

        val personality = AvaSecurePrefs(appContext, "ava_personality")
        val voice = AvaSecurePrefs(appContext, "ava_voice_config")
        val ui = AvaSecurePrefs(appContext, "ava_ui")
        val ai = AvaSecurePrefs(appContext, "ava_ai_config")
        val configuredEndpoint = ai.getString("endpoint", "") ?: ""
        val portableEndpoint = try {
            val parsed = java.net.URI(configuredEndpoint)
            if (parsed.scheme.equals("https", true) && parsed.userInfo == null &&
                parsed.query == null && parsed.fragment == null
            ) configuredEndpoint else ""
        } catch (_: Exception) {
            ""
        }
        put("settings", JSONObject().apply {
            put("personality", JSONObject()
                .put("tone", personality.getString("tone", "گرم و صمیمی"))
                .put("response_length", personality.getString("response_length", "متعادل"))
                .put("use_emojis", personality.getBoolean("use_emojis", true))
                .put("version", personality.getInt("version", 1))
                .put("history", personality.getString("history", "[]")))
            put("voice", JSONObject()
                .put("rate", voice.getInt("rate", 100))
                .put("pitch", voice.getInt("pitch", 106))
                .put("auto_speak", voice.getBoolean("auto_speak", false))
                .put("voice_name", voice.getString("voice_name", JSONObject.NULL.toString())))
            put("ui", JSONObject()
                .put("palette", ui.getInt("palette", 0))
                .put("font_scale", ui.getInt("font_scale", 100))
                .put("glass_alpha", ui.getInt("glass_alpha", 34))
                .put("avatar_size", ui.getInt("avatar_size", 148))
                .put("avatar_visible", ui.getBoolean("avatar_visible", true)))
            // Provider URL/model may migrate; API keys and external-send consent never do.
            put("aiProvider", JSONObject()
                .put("endpoint", portableEndpoint)
                .put("model", ai.getString("model", ""))
                .put("external_ai_enabled", false))
        })
        }
    }

    fun validateBackupPayload(payload: JSONObject): Boolean = try {
        require(payload.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1")
        listOf("memories", "memories_amir", "vocabulary", "tasks").forEach { bucket ->
            val items = payload.optJSONArray(bucket) ?: throw IllegalArgumentException("Bucket missing: $bucket")
            for (index in 0 until items.length()) require(items.optJSONObject(index) != null)
        }
        val profiles = payload.optJSONArray("profiles") ?: throw IllegalArgumentException("Profiles missing.")
        val profileIds = mutableSetOf<String>()
        for (index in 0 until profiles.length()) {
            val item = profiles.optJSONObject(index) ?: throw IllegalArgumentException("Invalid profile.")
            val profile = PersonProfile.fromJson(item)
            require(profile.id.matches(Regex("person-[A-Za-z0-9-]{1,80}")))
            require(profileIds.add(profile.id))
        }
        val pendingMemories = payload.optJSONArray("pendingRelationshipMemories") ?: JSONArray()
        val pendingIds = mutableSetOf<String>()
        for (index in 0 until pendingMemories.length()) {
            val entry = pendingMemories.optJSONObject(index) ?: throw IllegalArgumentException("Invalid quarantined memory entry.")
            val id = entry.optString("personId")
            require(id.matches(Regex("person-[A-Za-z0-9-]{1,80}")) && id in profileIds)
            require(pendingIds.add(id))
            val items = entry.optJSONArray("items") ?: throw IllegalArgumentException("Quarantined items missing.")
            for (itemIndex in 0 until items.length()) require(items.optJSONObject(itemIndex) != null)
        }
        val personMemories = payload.optJSONObject("personMemories") ?: throw IllegalArgumentException("Person memories missing.")
        val personKeys = personMemories.keys()
        while (personKeys.hasNext()) {
            val id = personKeys.next()
            require(id.matches(Regex("person-[A-Za-z0-9-]{1,80}")) && id in profileIds)
            val items = personMemories.optJSONArray(id) ?: throw IllegalArgumentException("Person memory items missing.")
            for (itemIndex in 0 until items.length()) require(items.optJSONObject(itemIndex) != null)
        }
        payload.optJSONObject("aacsState")?.let { aacs ->
            require(aacs.optString("format") == "AVA-AACS-PORTABLE-1")
            require(aacs.optJSONArray("auditHistory") != null)
            require(aacs.optJSONObject("permissions") != null)
        }
        payload.optJSONObject("settings")?.let { settings ->
            val history = settings.optJSONObject("personality")?.optString("history", "[]") ?: "[]"
            JSONArray(history)
            require(settings.optJSONObject("voice") != null)
            require(settings.optJSONObject("ui") != null)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun restorePayload(payload: JSONObject) = synchronized(AvaBackupSnapshotLock.lock) {
        require(payload.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1") { "Unsupported backup format." }
        listOf("memories", "memories_amir", "vocabulary", "tasks").forEach { bucket ->
            val restored = payload.optJSONArray(bucket) ?: JSONArray()
            saveBucket(bucket, restored)
            prefs.edit().putString(bucket, restored.toString()).apply()
        }
        // A restore is a replacement after explicit owner confirmation.
        val personMemories = payload.optJSONObject("personMemories") ?: JSONObject()
        val incomingIds = mutableSetOf<String>()
        val keys = personMemories.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            require(id.matches(Regex("person-[A-Za-z0-9-]{1,80}"))) { "Invalid person memory identifier." }
            incomingIds.add(id)
            val restored = personMemories.optJSONArray(id) ?: JSONArray()
            saveBucket("memories_" + id, restored)
            prefs.edit().putString("memories_" + id, restored.toString()).apply()
        }
        saveBucket("pending_relationship_memories", payload.optJSONArray("pendingRelationshipMemories") ?: JSONArray())
        val queuedKeys = vault.rawQueueSnapshot().keys().asSequence().toList()
        val oldIds = (prefs.allKeys + vault.listBucketNames("memories_person-") + queuedKeys)
            .filter { it.startsWith("memories_person-") }
            .map { it.removePrefix("memories_") }
            .toSet()
        (oldIds - incomingIds).forEach { id -> deleteBucket("memories_" + id) }
    }

    fun restoreSettings(payload: JSONObject): Boolean = synchronized(AvaBackupSnapshotLock.lock) {
        requireVocabularyOwnerContext()
        val settings = payload.optJSONObject("settings") ?: return true
        val personality = settings.optJSONObject("personality") ?: JSONObject()
        val voice = settings.optJSONObject("voice") ?: JSONObject()
        val ui = settings.optJSONObject("ui") ?: JSONObject()
        val ai = settings.optJSONObject("aiProvider") ?: JSONObject()

        val personalityStore = AvaSecurePrefs(appContext, "ava_personality")
        val history = personality.optString("history", "[]")
        JSONArray(history) // Validate history before replacing local settings.
        val personalitySaved = personalityStore.edit()
            .putString("tone", personality.optString("tone", "گرم و صمیمی"))
            .putString("response_length", personality.optString("response_length", "متعادل"))
            .putBoolean("use_emojis", personality.optBoolean("use_emojis", true))
            .putInt("version", personality.optInt("version", 1).coerceAtLeast(1))
            .putString("history", history)
            .commit()

        val voiceStore = AvaSecurePrefs(appContext, "ava_voice_config")
        val voiceEditor = voiceStore.edit()
            .putInt("rate", voice.optInt("rate", 100).coerceIn(70, 140))
            .putInt("pitch", voice.optInt("pitch", 106).coerceIn(70, 140))
            .putBoolean("auto_speak", voice.optBoolean("auto_speak", false))
        val voiceName = voice.optString("voice_name", "")
        if (voiceName.isBlank() || voiceName == "null") voiceEditor.remove("voice_name")
        else voiceEditor.putString("voice_name", voiceName)
        val voiceSaved = voiceEditor.commit()

        val uiStore = AvaSecurePrefs(appContext, "ava_ui")
        val uiSaved = uiStore.edit()
            .putInt("palette", ui.optInt("palette", 0).coerceIn(0, 3))
            .putInt("font_scale", ui.optInt("font_scale", 100).coerceIn(85, 125))
            .putInt("glass_alpha", ui.optInt("glass_alpha", 34).coerceIn(15, 90))
            .putInt("avatar_size", ui.optInt("avatar_size", 148).coerceIn(100, 200))
            .putBoolean("avatar_visible", ui.optBoolean("avatar_visible", true))
            .remove("avatar_uri") // A document URI belongs to the old device and must be reselected.
            .commit()

        val aiStore = AvaSecurePrefs(appContext, "ava_ai_config")
        val endpoint = ai.optString("endpoint", "")
        val safeEndpoint = try {
            val parsed = java.net.URI(endpoint)
            if (parsed.scheme.equals("https", true) && parsed.userInfo == null &&
                parsed.query == null && parsed.fragment == null
            ) endpoint else ""
        } catch (_: Exception) {
            ""
        }
        val aiSaved = aiStore.edit()
            .putString("endpoint", safeEndpoint)
            .putString("model", ai.optString("model", ""))
            .putBoolean("external_ai_enabled", false)
            .remove("api_key") // Secrets and external-send consent are never migrated.
            .commit()

        if (!personalitySaved || !voiceSaved || !uiSaved || !aiSaved) return false
        val expectedVoiceName = voice.optString("voice_name", "").takeUnless { it.isBlank() || it == "null" }
        return personalityStore.getString("tone", null) == personality.optString("tone", "گرم و صمیمی") &&
            personalityStore.getString("response_length", null) == personality.optString("response_length", "متعادل") &&
            personalityStore.getBoolean("use_emojis", true) == personality.optBoolean("use_emojis", true) &&
            personalityStore.getInt("version", 1) == personality.optInt("version", 1).coerceAtLeast(1) &&
            personalityStore.getString("history", "[]") == history &&
            voiceStore.getInt("rate", 100) == voice.optInt("rate", 100).coerceIn(70, 140) &&
            voiceStore.getInt("pitch", 106) == voice.optInt("pitch", 106).coerceIn(70, 140) &&
            voiceStore.getBoolean("auto_speak", false) == voice.optBoolean("auto_speak", false) &&
            voiceStore.getString("voice_name", null) == expectedVoiceName &&
            uiStore.getInt("palette", 0) == ui.optInt("palette", 0).coerceIn(0, 3) &&
            uiStore.getInt("font_scale", 100) == ui.optInt("font_scale", 100).coerceIn(85, 125) &&
            uiStore.getInt("glass_alpha", 34) == ui.optInt("glass_alpha", 34).coerceIn(15, 90) &&
            uiStore.getInt("avatar_size", 148) == ui.optInt("avatar_size", 148).coerceIn(100, 200) &&
            uiStore.getBoolean("avatar_visible", true) == ui.optBoolean("avatar_visible", true) &&
            uiStore.getString("avatar_uri", null) == null &&
            aiStore.getString("endpoint", null) == safeEndpoint &&
            aiStore.getString("model", null) == ai.optString("model", "") &&
            !aiStore.getBoolean("external_ai_enabled", false) &&
            aiStore.getString("api_key", null) == null
    }

}

