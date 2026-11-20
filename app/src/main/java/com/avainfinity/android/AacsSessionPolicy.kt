package com.avainfinity.android

/**
 * Pure identity/level checks shared by AACS session transitions and tests.
 */
object AacsSessionPolicy {
    fun isValidIdentity(personId: String, level: Int): Boolean = when (level) {
        1 -> personId == "owner"
        2, 3 -> personId.matches(Regex("person-[A-Za-z0-9-]{1,80}"))
        4 -> personId.matches(Regex("guest-[A-Za-z0-9-]{1,80}"))
        else -> false
    }

    fun isPrivilegeDowngrade(currentLevel: Int, requestedLevel: Int): Boolean =
        currentLevel in 2..3 && requestedLevel in 2..4 && requestedLevel > currentLevel
    
    fun canTransmitExternalData(
        publicOnlyMode: Boolean,
        hasActiveSession: Boolean,
        sessionApproved: Boolean,
        sessionLevel: Int,
        sessionPersonId: String?,
        currentEpoch: Long,
        expectedEpoch: Long
    ): Boolean =
        currentEpoch == expectedEpoch && !publicOnlyMode &&
            (!hasActiveSession || (sessionApproved && sessionLevel == 1 && sessionPersonId == "owner"))

    fun canExposeProfiles(
        publicOnlyMode: Boolean,
        hasActiveSession: Boolean,
        sessionApproved: Boolean,
        sessionLevel: Int,
        sessionPersonId: String?
    ): Boolean {
        if (publicOnlyMode) return false
        if (!hasActiveSession) return true
        if (!sessionApproved) return false
        return when (sessionLevel) {
            1 -> sessionPersonId == "owner"
            2, 3 -> sessionPersonId?.matches(Regex("person-[A-Za-z0-9-]{1,80}")) == true
            else -> false
        }
    }

}
