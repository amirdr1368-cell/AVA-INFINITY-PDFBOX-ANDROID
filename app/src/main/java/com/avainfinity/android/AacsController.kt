package com.avainfinity.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AvaAccessLevel(val value: Int) { OWNER(1), PARTNER(2), TRUSTED(3), PUBLIC(4) }
enum class AvaDataClass { PUBLIC, PERSONAL, PRIVATE, CONTROL }

class AacsController private constructor(context: Context) {
    companion object {
        @Volatile private var sharedInstance: AacsController? = null
        private val processToken: String = UUID.randomUUID().toString()

        fun get(context: Context): AacsController {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: AacsController(context.applicationContext).also { sharedInstance = it }
            }
        }
    }

    private val prefs = AvaSecurePrefs(context, "ava_aacs")
    private var activePersonId: String? = null
    private var activeLevel: Int = 4
    private var sessionApproved = false
    private var sessionEpoch = 0L
    private var privateModeAllowed = false
    private var privateGrantExpiresAt = 0L
    private var sessionOwnerApprovalExpiresAt = 0L
    private var privateOwnerApprovalExpiresAt = 0L
    private var publicOnlyExitExpiresAt = 0L
    // Short-lived, single-use grants issued only after Android device-credential success.
    private val ownerActionGrants = mutableMapOf<String, Long>()
    private var publicOnlyModeActive = try {
        prefs.getBoolean("public_only_mode", false)
    } catch (_: Exception) {
        true
    }

    init {
        val interrupted = try {
            prefs.getBoolean("session_active", false) &&
                prefs.getString("session_process_token", null) != processToken
        } catch (_: Exception) {
            true
        }
        if (interrupted) {
            // A process restart interrupts approval; additional instances in this process share this singleton.
            publicOnlyModeActive = true
            sessionApproved = false
            privateModeAllowed = false
            privateGrantExpiresAt = 0L
            try {
                prefs.edit()
                    .putBoolean("public_only_mode", true)
                    .putBoolean("session_active", false)
                    .remove("session_process_token")
                    .commit()
            } catch (_: Exception) { }
            log("SESSION_INTERRUPTED", "owner", "app process restarted or session state could not be verified")
        }
    }

    @Synchronized
    fun beginSession(personId: String, level: Int): Boolean {
        if (activePersonId != null || publicOnlyModeActive) return false
        if (!AacsSessionPolicy.isValidIdentity(personId, level)) return false
        val safeLevel = level
        if (!log("SESSION_START", personId, "level=$safeLevel")) return false
        val sessionMarkerSaved = try {
            prefs.edit()
                .putBoolean("session_active", true)
                .putString("session_process_token", processToken)
                .commit()
        } catch (_: Exception) { false }
        if (!sessionMarkerSaved) {
            log("SESSION_START_FAILED", personId, "session marker could not be persisted")
            return false
        }
        ownerActionGrants.clear()
        sessionOwnerApprovalExpiresAt = 0L
        activePersonId = personId
        activeLevel = safeLevel
        sessionEpoch++
        // Even an owner-level session must be explicitly authenticated; level alone is not approval.
        sessionApproved = false
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        sessionOwnerApprovalExpiresAt = 0L
        privateOwnerApprovalExpiresAt = 0L
        publicOnlyExitExpiresAt = 0L
        return true
    }

    // The Activity calls this only after Android reports RESULT_OK from its device-credential screen.
    @Synchronized
    fun grantCurrentSessionApprovalAfterDeviceCredential(): Boolean {
        val id = activePersonId ?: return false
        if (activeLevel >= 4 || publicOnlyModeActive) return false
        sessionApproved = false
        sessionEpoch++
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        sessionOwnerApprovalExpiresAt = 0L
        privateOwnerApprovalExpiresAt = 0L
        if (!log("OWNER_APPROVAL_AUTHENTICATED", id, "Android device credential succeeded")) return false
        sessionOwnerApprovalExpiresAt = System.currentTimeMillis() + 30_000L
        return true
    }

    @Synchronized
    fun approveCurrentSessionByOwner(): Boolean {
        val id = activePersonId ?: return false
        if (sessionOwnerApprovalExpiresAt < System.currentTimeMillis()) return false
        sessionOwnerApprovalExpiresAt = 0L
        if (!log("OWNER_APPROVAL", id, "single-use authenticated session approval")) return false
        sessionApproved = true
        sessionEpoch++
        return true
    }

    @Synchronized
    fun grantOwnerActionAfterDeviceCredential(action: String): Boolean {
        val allowedActions = setOf("ava_secret_publish", "ava_secret_rollback", "ava_secret_archive")
        if (action !in allowedActions || publicOnlyModeActive) return false
        if (activePersonId != null && (activeLevel != 1 || activePersonId != "owner")) return false
        ownerActionGrants.remove(action)
        if (!log("OWNER_ACTION_AUTHENTICATED", activePersonId ?: "owner", action)) return false
        ownerActionGrants[action] = System.currentTimeMillis() + 30_000L
        return true
    }

    @Synchronized
    fun consumeOwnerActionGrant(action: String): Boolean {
        val expiresAt = ownerActionGrants.remove(action) ?: return false
        if (expiresAt < System.currentTimeMillis() || publicOnlyModeActive) return false
        if (activePersonId != null && (activeLevel != 1 || activePersonId != "owner")) return false
        return log("OWNER_ACTION_CONSUMED", activePersonId ?: "owner", action)
    }

    @Synchronized
    fun grantPrivateInformationAfterDeviceCredential(): Boolean {
        val id = activePersonId ?: return false
        if (publicOnlyModeActive || activeLevel !in 2..3 || !sessionApproved) return false
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        privateOwnerApprovalExpiresAt = 0L
        if (!log("PRIVATE_ACCESS_AUTHENTICATED", id, "separate Android device-credential confirmation")) return false
        privateOwnerApprovalExpiresAt = System.currentTimeMillis() + 30_000L
        return true
    }

    @Synchronized
    fun allowPrivateInformation(): Boolean {
        val id = activePersonId ?: return false
        if (publicOnlyModeActive || activeLevel !in 2..3 || !sessionApproved) return false
        if (privateOwnerApprovalExpiresAt < System.currentTimeMillis()) {
            privateOwnerApprovalExpiresAt = 0L
            log("PRIVATE_ACCESS_DENIED", id, "separate owner approval missing or expired")
            return false
        }
        privateOwnerApprovalExpiresAt = 0L
        if (!log("PRIVATE_ACCESS_GRANTED", id, "one-time explicit owner approval")) return false
        privateModeAllowed = true
        privateGrantExpiresAt = System.currentTimeMillis() + 60_000L
        return true
    }

    @Synchronized
    fun canReadPrivateInformation(): Boolean {
        // Public-only mode overrides even owner-level convenience paths.
        if (publicOnlyModeActive) {
            privateModeAllowed = false
            privateGrantExpiresAt = 0L
            return false
        }
        if (activeLevel == 1) return true
        if (activeLevel >= 4) return false
        val id = activePersonId ?: return false
        if (!sessionApproved || !privateModeAllowed ||
            privateGrantExpiresAt < System.currentTimeMillis()
        ) {
            privateModeAllowed = false
            privateGrantExpiresAt = 0L
            return false
        }
        if (!log("PRIVATE_ACCESS_CONSUMED", id, "one-time private access consumed")) {
            privateModeAllowed = false
            privateGrantExpiresAt = 0L
            return false
        }
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        return true
    }

    fun hasActiveSession(): Boolean = activePersonId != null

    fun currentSessionPersonId(): String? = activePersonId

    fun currentSessionLevel(): Int = activeLevel

    @Synchronized
    fun isCurrentSessionApproved(): Boolean =
        activePersonId != null && sessionApproved && !publicOnlyModeActive

    @Synchronized
    fun currentSessionEpoch(): Long = sessionEpoch

    @Synchronized
    fun isApprovedOwnerContext(): Boolean =
        !publicOnlyModeActive &&
            (activePersonId == null ||
                (activeLevel == 1 && activePersonId == "owner" && sessionApproved))

    @Synchronized
    fun canTransmitExternalData(expectedSessionEpoch: Long): Boolean =
        AacsSessionPolicy.canTransmitExternalData(
            publicOnlyModeActive, activePersonId != null, sessionApproved,
            activeLevel, activePersonId, sessionEpoch, expectedSessionEpoch
        )

    fun canCurrentSessionAccess(dataClass: AvaDataClass): Boolean {
        return try {
            val id = activePersonId
            when {
                id == null -> false
                publicOnlyModeActive && dataClass != AvaDataClass.PUBLIC -> false
                dataClass != AvaDataClass.PUBLIC && !sessionApproved -> false
                dataClass == AvaDataClass.PRIVATE -> canReadPrivateInformation()
                else -> canAccess(id, activeLevel, dataClass)
            }
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun setPersonPermission(personId: String, dataClass: AvaDataClass, allowed: Boolean, expiresAtMillis: Long? = null): Boolean {
        return try {
            if (publicOnlyModeActive ||
                (activePersonId != null &&
                    (activeLevel != 1 || activePersonId != "owner" || !sessionApproved)) ||
                !personId.matches(Regex("person-[A-Za-z0-9-]{1,80}"))
            ) return false
            if (dataClass == AvaDataClass.CONTROL && allowed) return false
            if (expiresAtMillis != null && expiresAtMillis <= System.currentTimeMillis()) return false
            val key = "permission_" + personId + "_" + dataClass.name
            val previous = prefs.getString(key, null)
            val detail = dataClass.name + "=" + allowed
            if (!log("PERMISSION_CHANGE_REQUESTED", personId, detail)) return false
            val record = JSONObject().apply {
                put("allowed", allowed)
                if (expiresAtMillis == null) put("expiresAt", JSONObject.NULL) else put("expiresAt", expiresAtMillis)
                put("updatedAt", System.currentTimeMillis())
            }
            if (!prefs.edit().putString(key, record.toString()).commit()) return false
            if (!log("PERMISSION_CHANGED", personId, detail)) {
                val rollback = prefs.edit()
                if (previous == null) rollback.remove(key) else rollback.putString(key, previous)
                rollback.commit()
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun canAccess(personId: String, level: Int, dataClass: AvaDataClass): Boolean = try {
        canAccessChecked(personId, level, dataClass)
    } catch (_: Exception) {
        false
    }

    private fun canAccessChecked(personId: String, level: Int, dataClass: AvaDataClass): Boolean {
        if (!AacsSessionPolicy.isValidIdentity(personId, level)) return false
        if (publicOnlyModeActive && dataClass != AvaDataClass.PUBLIC) return false
        if (level == 1) {
            return activePersonId == personId && activeLevel == 1 && sessionApproved
        }
        // Private and control scopes are never granted by persistent per-person settings.
        if (dataClass == AvaDataClass.PRIVATE) {
            return activePersonId == personId && activeLevel == level &&
                sessionApproved && canReadPrivateInformation()
        }
        if (dataClass == AvaDataClass.CONTROL) return false
        if (dataClass == AvaDataClass.PUBLIC) {
            val publicRaw = prefs.getString("permission_" + personId + "_" + dataClass.name, null)
                ?: return true
            val publicRecord = try { JSONObject(publicRaw) } catch (_: Exception) { return false }
            val publicExpiry = if (publicRecord.isNull("expiresAt")) null else publicRecord.optLong("expiresAt")
            if (publicExpiry != null && publicExpiry <= System.currentTimeMillis()) return false
            return publicRecord.optBoolean("allowed", false)
        }
        // Personal data requires an active, owner-approved session for this exact person.
        if (activePersonId != personId || activeLevel != level || !sessionApproved) return false
        val raw = prefs.getString("permission_" + personId + "_" + dataClass.name, null)
            ?: return level <= 3
        val record = try { JSONObject(raw) } catch (_: Exception) { return false }
        val expiry = if (record.isNull("expiresAt")) null else record.optLong("expiresAt")
        if (expiry != null && expiry <= System.currentTimeMillis()) return false
        return record.optBoolean("allowed", false)
    }

    @Synchronized
    fun clearAllPersonPermissions(personId: String): Boolean {
        return try {
            if (publicOnlyModeActive ||
                (activePersonId != null && (activeLevel != 1 || activePersonId != "owner")) ||
                !personId.matches(Regex("person-[A-Za-z0-9-]{1,80}"))
            ) return false
            val editor = prefs.edit()
            AvaDataClass.values().forEach { editor.remove("permission_" + personId + "_" + it.name) }
            if (!editor.commit()) {
                log("PERMISSION_REVOCATION_FAILED", personId, "permission set could not be cleared")
                false
            } else {
                log("ALL_CUSTOM_PERMISSIONS_REVOKED", personId, "permission set cleared")
            }
        } catch (_: Exception) {
            log("PERMISSION_REVOCATION_FAILED", personId, "encrypted permission storage unavailable")
            false
        }
    }

    @Synchronized
    fun endSession() {
        val id = activePersonId
        if (id != null) log("SESSION_END", id, "approval revoked")
        try {
            prefs.edit().putBoolean("session_active", false).remove("session_process_token").commit()
        } catch (_: Exception) { }
        activePersonId = null
        activeLevel = 4
        sessionEpoch++
        sessionApproved = false
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        ownerActionGrants.clear()
        sessionOwnerApprovalExpiresAt = 0L
        privateOwnerApprovalExpiresAt = 0L
        publicOnlyExitExpiresAt = 0L
    }

    @Synchronized
    fun onAccessDowngraded(personId: String, newLevel: Int): Boolean {
        if (publicOnlyModeActive ||
            (activePersonId != null &&
                (activeLevel != 1 || activePersonId != "owner" || !sessionApproved)) ||
            !personId.matches(Regex("person-[A-Za-z0-9-]{1,80}")) ||
            newLevel !in 2..4 ||
            (activePersonId == personId && !AacsSessionPolicy.isPrivilegeDowngrade(activeLevel, newLevel))
        ) return false
        val cleared = clearAllPersonPermissions(personId)
        sessionEpoch++
        ownerActionGrants.clear()
        sessionOwnerApprovalExpiresAt = 0L
        privateOwnerApprovalExpiresAt = 0L
        publicOnlyExitExpiresAt = 0L
        if (activePersonId == personId) {
            // Reduce the live session immediately, even if the audit store is unhealthy.
            activeLevel = newLevel
            sessionApproved = false
            privateModeAllowed = false
            privateGrantExpiresAt = 0L
        }
        val audited = log("ACCESS_DOWNGRADED", personId, "custom permissions revoked")
        if (!cleared || !audited) {
            // A failed revocation or audit must not allow a later session to inherit stale grants.
            publicOnlyMode("access downgrade revocation or audit failed; owner review required")
            return false
        }
        return true
    }

    fun isPublicOnlyModeActive(): Boolean = publicOnlyModeActive

    @Synchronized
    fun grantPublicOnlyExitAfterDeviceCredential(): Boolean {
        if (!publicOnlyModeActive || activePersonId != null) return false
        publicOnlyExitExpiresAt = 0L
        if (!log("PUBLIC_ONLY_EXIT_AUTHENTICATED", "owner", "Android device credential succeeded")) return false
        publicOnlyExitExpiresAt = System.currentTimeMillis() + 30_000L
        return true
    }

    @Synchronized
    fun clearPublicOnlyModeAfterOwnerApproval(): Boolean {
        if (publicOnlyExitExpiresAt < System.currentTimeMillis()) return false
        publicOnlyExitExpiresAt = 0L
        if (activePersonId != null) {
            log("PUBLIC_ONLY_EXIT_DENIED", activePersonId ?: "session", "active session must end before leaving public-only mode")
            return false
        }
        val persisted = try { prefs.edit().putBoolean("public_only_mode", false).commit() } catch (_: Exception) { false }
        if (!persisted) return false
        if (!log("PUBLIC_ONLY_MODE_DISABLED", activePersonId ?: "owner", "fresh Android owner authentication")) {
            try { prefs.edit().putBoolean("public_only_mode", true).commit() } catch (_: Exception) { }
            publicOnlyModeActive = true
            return false
        }
        publicOnlyModeActive = false
        sessionEpoch++
        // Exiting public-only mode never resumes a previous session.
        sessionApproved = false
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        return true
    }

    @Synchronized
    fun publicOnlyMode(reason: String): Boolean {
        privateModeAllowed = false
        privateGrantExpiresAt = 0L
        ownerActionGrants.clear()
        sessionOwnerApprovalExpiresAt = 0L
        privateOwnerApprovalExpiresAt = 0L
        publicOnlyExitExpiresAt = 0L
        sessionApproved = false
        sessionEpoch++
        publicOnlyModeActive = true
        val persisted = try { prefs.edit().putBoolean("public_only_mode", true).commit() } catch (_: Exception) { false }
        val audited = log("PUBLIC_ONLY_MODE", activePersonId ?: "unknown", reason)
        return persisted && audited
    }

    @Synchronized
    fun portableStateSnapshot(): JSONObject {
        if (publicOnlyModeActive ||
            (activePersonId != null &&
                (activeLevel != 1 || activePersonId != "owner" || !sessionApproved))
        ) throw SecurityException("AACS state export is restricted to an approved owner context.")
        val permissions = JSONObject()
        prefs.allKeys.filter { it.startsWith("permission_") }.forEach { key ->
            val dataClass = key.substringAfterLast('_')
            if (dataClass == AvaDataClass.PUBLIC.name || dataClass == AvaDataClass.PERSONAL.name) {
                val value = prefs.getString(key, null)
                if (!value.isNullOrBlank()) permissions.put(key, JSONObject(value))
            }
        }
        val localAudit = auditSnapshot()
            ?: throw IllegalStateException("AACS audit history is unreadable; backup was stopped.")
        val portableAudit = JSONArray()
        for (index in 0 until localAudit.length()) {
            val entry = localAudit.optJSONObject(index) ?: continue
            if (AacsAuditPolicy.includeInPortableBackup(entry.optString("event"))) {
                portableAudit.put(JSONObject(entry.toString()))
            }
        }
        return JSONObject()
            .put("format", "AVA-AACS-PORTABLE-1")
            .put("permissions", permissions)
            .put("auditHistory", portableAudit)
    }

    @Synchronized
    fun restorePortableState(state: JSONObject?, allowedPersonIds: Set<String>): Boolean {
        if (publicOnlyModeActive ||
            (activePersonId != null &&
                (activeLevel != 1 || activePersonId != "owner" || !sessionApproved))
        ) return false
        if (state == null) return true
        return try {
            if (state.optString("format") != "AVA-AACS-PORTABLE-1") return false
            val importedAudit = state.optJSONArray("auditHistory") ?: return false
            if (!log("PORTABLE_AACS_IMPORT_REQUESTED", "owner", "portable access history and safe permissions")) return false
            val currentAudit = auditSnapshot() ?: return false

            val mergedAudit = org.json.JSONArray()
            val seen = mutableSetOf<String>()
            fun appendAudit(source: JSONObject, imported: Boolean) {
                val identity = listOf(
                    source.optString("event"),
                    source.optString("personId"),
                    source.optLong("time").toString(),
                    source.optString("detail")
                ).joinToString("|")
                if (seen.add(identity)) {
                    val copy = JSONObject(source.toString())
                    if (imported) copy.put("importedFromBackup", true)
                    mergedAudit.put(copy)
                }
            }
            // Imported history is older; append it first so recent local audit events survive the cap.
            for (index in 0 until importedAudit.length()) {
                importedAudit.optJSONObject(index)?.let { appendAudit(it, true) }
            }
            for (index in 0 until currentAudit.length()) {
                currentAudit.optJSONObject(index)?.let { appendAudit(it, false) }
            }
            mergedAudit.put(JSONObject().apply {
                put("event", "PORTABLE_AACS_STATE_IMPORTED")
                put("personId", "owner")
                put("detail", "historical audit and eligible public/personal permission overrides imported; sessions and private grants excluded")
                put("time", System.currentTimeMillis())
            })
            while (mergedAudit.length() > 2000) mergedAudit.remove(0)

            val importedPermissions = state.optJSONObject("permissions") ?: JSONObject()
            val editor = prefs.edit()
            val writtenPermissions = linkedMapOf<String, String>()
            val permissionKeys = importedPermissions.keys()
            while (permissionKeys.hasNext()) {
                val key = permissionKeys.next()
                if (!key.startsWith("permission_")) continue
                val className = key.substringAfterLast('_')
                if (className != AvaDataClass.PUBLIC.name && className != AvaDataClass.PERSONAL.name) continue
                val personId = key.removePrefix("permission_").removeSuffix("_" + className)
                if (!personId.matches(Regex("person-[A-Za-z0-9-]{1,80}")) || personId !in allowedPersonIds) continue
                // Never overwrite a newer local decision, especially a local denial/revocation.
                if (prefs.getString(key, null) != null) continue
                val record = importedPermissions.optJSONObject(key) ?: continue
                if (!record.has("allowed")) continue
                val serialized = record.toString()
                writtenPermissions[key] = serialized
                editor.putString(key, serialized)
            }
            if (!editor.putString("audit", mergedAudit.toString()).commit()) return false
            if (prefs.getString("audit", null) != mergedAudit.toString()) return false
            writtenPermissions.forEach { (key, expected) ->
                if (prefs.getString(key, null) != expected) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun restorePortableSnapshotForRollback(state: JSONObject?, allowedPersonIds: Set<String>): Boolean {
        if (publicOnlyModeActive ||
            (activePersonId != null &&
                (activeLevel != 1 || activePersonId != "owner" || !sessionApproved))
        ) return false
        val snapshotPermissions = state?.optJSONObject("permissions") ?: return false
        return try {
            val currentAudit = auditSnapshot() ?: return false
            val nextAudit = JSONObject().let { org.json.JSONArray(currentAudit.toString()) }
            nextAudit.put(JSONObject().apply {
                put("event", "PORTABLE_AACS_SNAPSHOT_ROLLED_BACK")
                put("personId", "owner")
                put("detail", "restored pre-restore permission snapshot; audit history retained")
                put("time", System.currentTimeMillis())
            })
            while (nextAudit.length() > 2000) nextAudit.remove(0)

            val editor = prefs.edit()
            prefs.allKeys.filter { it.startsWith("permission_") }.forEach { editor.remove(it) }
            val expectedKeys = mutableSetOf<String>()
            val keys = snapshotPermissions.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!key.startsWith("permission_")) continue
                val className = key.substringAfterLast('_')
                if (className != AvaDataClass.PUBLIC.name && className != AvaDataClass.PERSONAL.name) continue
                val personId = key.removePrefix("permission_").removeSuffix("_" + className)
                if (!personId.matches(Regex("person-[A-Za-z0-9-]{1,80}")) || personId !in allowedPersonIds) continue
                val record = snapshotPermissions.optJSONObject(key) ?: continue
                if (!record.has("allowed")) continue
                expectedKeys.add(key)
                editor.putString(key, record.toString())
            }
            if (!editor.putString("audit", nextAudit.toString()).commit()) return false

            val actualKeys = prefs.allKeys.filter { it.startsWith("permission_") }.toSet()
            if (actualKeys != expectedKeys) return false
            expectedKeys.forEach { key ->
                val expected = snapshotPermissions.optJSONObject(key) ?: return false
                if (prefs.getString(key, null) != expected.toString()) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun recordOwnerEvent(event: String, detail: String): Boolean =
        log(event, "owner", detail)

    @Synchronized
    fun auditSnapshot(): org.json.JSONArray? = try {
        org.json.JSONArray(prefs.getString("audit", "[]") ?: "[]")
    } catch (_: Exception) {
        null
    }

    @Synchronized
    private fun log(event: String, personId: String, detail: String): Boolean {
        return try {
            val key = "audit"
            val old = prefs.getString(key, null)
            val array = if (old.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(old)
            array.put(JSONObject().apply {
                put("event", event); put("personId", personId)
                put("detail", detail); put("time", System.currentTimeMillis())
            })
            while (array.length() > 2000) array.remove(0)
            prefs.edit().putString(key, array.toString()).commit()
        } catch (_: Exception) {
            // Unreadable or unwritable audit data must never be silently reset.
            false
        }
    }
}
