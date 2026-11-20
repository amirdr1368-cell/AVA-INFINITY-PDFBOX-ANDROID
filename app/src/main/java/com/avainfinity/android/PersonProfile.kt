package com.avainfinity.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersonProfile(
    val id: String,
    val name: String,
    val level: Int,
    val relation: String,
    val occupation: String,
    val interests: String,
    val speakingStyle: String,
    val intimacy: Int,
    val photoUri: String?,
    val photoConsent: Boolean,
    val relationshipMemoryApproved: Boolean,
    val preferences: String = "",
    val privacyRestrictions: String = "",
    val relationshipHistory: String = "",
    val informationProvenance: String = "ثبت‌شده توسط مالک",
    val pendingRelationshipHistory: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("level", level)
        put("relation", relation); put("occupation", occupation)
        put("interests", interests); put("speakingStyle", speakingStyle)
        put("intimacy", intimacy); put("photoUri", if (photoConsent) photoUri ?: JSONObject.NULL else JSONObject.NULL)
        put("photoConsent", photoConsent)
        put("relationshipMemoryApproved", relationshipMemoryApproved)
        put("preferences", preferences)
        put("privacyRestrictions", privacyRestrictions)
        put("relationshipHistory", if (relationshipMemoryApproved) relationshipHistory else "")
        put("pendingRelationshipHistory", if (relationshipMemoryApproved) "" else pendingRelationshipHistory)
        put("informationProvenance", informationProvenance)
    }
    companion object {
        fun fromJson(o: JSONObject): PersonProfile {
            val photoConsent = o.optBoolean("photoConsent", false)
            val relationshipApproved = o.optBoolean("relationshipMemoryApproved", false)
            return PersonProfile(
                o.optString("id"), o.optString("name"), o.optInt("level", 3).coerceIn(2, 3),
                o.optString("relation"), o.optString("occupation"), o.optString("interests"),
                o.optString("speakingStyle"), o.optInt("intimacy", 0).coerceIn(0, 5),
                if (photoConsent) o.optString("photoUri").takeIf { it.isNotBlank() && it != "null" } else null,
                photoConsent, relationshipApproved,
                o.optString("preferences"), o.optString("privacyRestrictions"),
                if (relationshipApproved) o.optString("relationshipHistory") else "",
                o.optString("informationProvenance", "ثبت‌شده توسط مالک"),
                if (!relationshipApproved) o.optString("pendingRelationshipHistory") else ""
            )
        }
    }
}

class PersonProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AvaSecurePrefs(context, "ava_person_profiles")
    private val vault = AvaVaultStore(context)
    private fun requireOwnerProfileManagement() {
        val access = AacsController.get(appContext)
        if (access.isPublicOnlyModeActive() ||
            (access.hasActiveSession() &&
                (!access.isCurrentSessionApproved() ||
                    access.currentSessionLevel() != 1 || access.currentSessionPersonId() != "owner"))
        ) {
            throw SecurityException("Profile management is restricted to the owner context.")
        }
    }

    fun all(): List<PersonProfile> = synchronized(AvaBackupSnapshotLock.lock) {
        val access = AacsController.get(appContext)
        if (!AacsSessionPolicy.canExposeProfiles(
                access.isPublicOnlyModeActive(),
                access.hasActiveSession(),
                access.isCurrentSessionApproved(),
                access.currentSessionLevel(),
                access.currentSessionPersonId()
            )
        ) return emptyList()
        val raw = vault.readBucket("profiles") ?: prefs.getString("profiles", "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (error: Exception) {
            throw IllegalStateException("Stored AVA profiles are corrupted; existing data was not overwritten.", error)
        }
        val profiles = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { PersonProfile.fromJson(it) } }
        if (access.hasActiveSession()) {
            return profiles.mapNotNull { profile ->
                AacsProfilePolicy.visibleForSession(
                    profile,
                    access.currentSessionLevel(),
                    access.currentSessionPersonId(),
                    access.isCurrentSessionApproved()
                )
            }
        }
        return profiles
    }
    fun save(profile: PersonProfile) = synchronized(AvaBackupSnapshotLock.lock) {
        requireOwnerProfileManagement()
        val next = all().filterNot { it.id == profile.id }.toMutableList()
        next.add(profile)
        persist(next)
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
    }
    fun remove(id: String) = synchronized(AvaBackupSnapshotLock.lock) {
        requireOwnerProfileManagement()
        persist(all().filterNot { it.id == id })
        AvaBackupScheduler.enqueueEventSnapshot(appContext)
    }
    private fun verifyPersistedProfiles(expected: List<PersonProfile>) {
        val actual = all().associateBy { it.id }
        require(actual.keys == expected.map { it.id }.toSet()) {
            "Profile store read-back ID verification failed."
        }
        expected.forEach { profile ->
            val restored = actual[profile.id]
                ?: throw IllegalStateException("Restored profile is missing: ${profile.id}")
            require(restored.toJson().toString() == profile.toJson().toString()) {
                "Profile store read-back content verification failed: ${profile.id}"
            }
        }
    }

    private fun persist(items: List<PersonProfile>, forceCache: Boolean = false) =
        synchronized(AvaBackupSnapshotLock.lock) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val raw = array.toString()
        if (vault.isConfigured()) {
            if (!vault.writeBucket("profiles", raw)) {
                throw IllegalStateException("Primary vault and bounded recovery queue are unavailable; profile was not changed.")
            }
            if (forceCache || !vault.hasQueued("profiles")) prefs.edit().putString("profiles", raw).apply()
        } else {
            prefs.edit().putString("profiles", raw).apply()
        }
    }

    fun exportRawProfiles(): String {
        requireOwnerProfileManagement()
        val array = JSONArray()
        all().forEach { array.put(it.toJson()) }
        return array.toString()
    }

    fun refreshCacheFromVault() {
        val access = AacsController.get(appContext)
        if (access.isPublicOnlyModeActive() ||
            (access.hasActiveSession() &&
                (!access.isCurrentSessionApproved() ||
                    access.currentSessionLevel() != 1 || access.currentSessionPersonId() != "owner"))
        ) return
        if (!vault.isConfigured() || !vault.isAvailable()) return
        val raw = vault.readBucket("profiles") ?: return
        if (!vault.isDeleteMarker(raw)) prefs.edit().putString("profiles", raw).apply()
    }
    /**
     * Restores a trusted in-process owner snapshot during a failed restore rollback.
     * This is not a portable-import path and never grants relationship-memory consent.
     */
    fun restoreOwnerSnapshot(snapshot: List<PersonProfile>) =
        synchronized(AvaBackupSnapshotLock.lock) {
        requireOwnerProfileManagement()
        val ids = mutableSetOf<String>()
        snapshot.forEach { profile ->
            require(profile.id.matches(Regex("person-[A-Za-z0-9-]{1,80}")))
            require(ids.add(profile.id))
        }
        persist(snapshot, forceCache = true)
    }

    fun restoreFromBackup(array: JSONArray?) = synchronized(AvaBackupSnapshotLock.lock) {
        requireOwnerProfileManagement()
        if (array == null) return
        val current = all().associateBy { it.id }
        val profiles = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { incoming ->
                val restored = PersonProfile.fromJson(incoming)
                val existing = current[restored.id]
                if (existing == null) restored.copy(
                    photoConsent = false,
                    photoUri = null,
                    relationshipMemoryApproved = false,
                    pendingRelationshipHistory = if (restored.relationshipMemoryApproved)
                        restored.relationshipHistory else restored.pendingRelationshipHistory,
                    relationshipHistory = ""
                )
                else restored.copy(
                    photoConsent = existing.photoConsent,
                    photoUri = if (existing.photoConsent) existing.photoUri else null,
                    relationshipMemoryApproved = existing.relationshipMemoryApproved,
                    relationshipHistory = if (existing.relationshipMemoryApproved) restored.relationshipHistory else "",
                    pendingRelationshipHistory = if (existing.relationshipMemoryApproved) ""
                        else existing.pendingRelationshipHistory.ifBlank {
                            restored.relationshipHistory.ifBlank { restored.pendingRelationshipHistory }
                        }
                )
            }
        }
        persist(profiles, forceCache = true)
        verifyPersistedProfiles(profiles)
    }
    fun ensureArash() {
        requireOwnerProfileManagement()
        if (all().none { it.id == "person-arash-naderi" }) save(
            PersonProfile(
                id = "person-arash-naderi", name = "آرش نادری", level = 3,
                relation = "دوست نزدیک", occupation = "صاحب کافه و موزیسین",
                interests = "موسیقی، کافه", speakingStyle = "گرم، خودمانی و محترمانه",
                intimacy = 0, photoUri = null, photoConsent = false,
                relationshipMemoryApproved = false
            )
        )
        if (!prefs.getBoolean("arash_intimacy_migrated", false)) {
            all().firstOrNull { it.id == "person-arash-naderi" }?.let { existing ->
                save(existing.copy(intimacy = 0))
            }
            prefs.edit().putBoolean("arash_intimacy_migrated", true).apply()
        }
    }
}
