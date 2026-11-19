package com.avainfinity.android

object AacsProfilePolicy {
    fun visibleForSession(
        profile: PersonProfile,
        sessionLevel: Int,
        sessionPersonId: String?,
        sessionApproved: Boolean
    ): PersonProfile? {
        if (!sessionApproved) return null
        if (sessionLevel == 1 && sessionPersonId == "owner") return profile
        if (sessionLevel !in 2..3 ||
            sessionPersonId != profile.id ||
            profile.level != sessionLevel
        ) return null

        return profile.copy(
            pendingRelationshipHistory = "",
            relationshipHistory = if (profile.relationshipMemoryApproved) profile.relationshipHistory else "",
            intimacy = 0,
            privacyRestrictions = "",
            informationProvenance = "",
            photoUri = if (profile.photoConsent) profile.photoUri else null
        )
    }
}
