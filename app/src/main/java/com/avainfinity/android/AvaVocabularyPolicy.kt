package com.avainfinity.android

/**
 * Pure policy rules shared by AVA SECRET storage and JVM unit tests.
 * Authentication itself is performed by Android device credentials and AACS.
 */
object AvaVocabularyPolicy {
    private val languageCode = Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?")

    fun isValidLanguageCode(value: String): Boolean = languageCode.matches(value)

    fun canManage(
        publicOnlyMode: Boolean,
        hasActiveSession: Boolean,
        sessionLevel: Int,
        sessionPersonId: String?,
        sessionApproved: Boolean
    ): Boolean {
        if (publicOnlyMode) return false
        return !hasActiveSession ||
            (sessionApproved && sessionLevel == 1 && sessionPersonId == "owner")
    }

    fun canPublish(status: String, word: String, meaning: String): Boolean {
        return (status == "draft" || status == "legacy_review_required") &&
            word.isNotBlank() && meaning.isNotBlank()
    }

    fun normalizeTerm(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    fun hasTermConflict(existingTerms: Iterable<String>, candidate: String): Boolean {
        val normalized = normalizeTerm(candidate)
        if (normalized.isBlank()) return false
        return existingTerms.any { normalizeTerm(it) == normalized }
    }

    fun nextVersion(currentVersion: Int): Int = currentVersion.coerceAtLeast(1) + 1
}
