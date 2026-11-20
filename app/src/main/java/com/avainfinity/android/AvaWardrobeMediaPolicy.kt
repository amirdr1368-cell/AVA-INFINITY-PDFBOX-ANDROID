package com.avainfinity.android

/**
 * Strict access policy for owner-only wardrobe media.
 * Keep this separate from general storage/backup access rules.
 */
object AvaWardrobeMediaPolicy {
    fun canAccessOwnerMedia(
        publicOnlyMode: Boolean,
        hasActiveSession: Boolean,
        sessionApproved: Boolean,
        sessionLevel: Int,
        sessionPersonId: String?
    ): Boolean =
        !publicOnlyMode &&
            hasActiveSession &&
            sessionApproved &&
            sessionLevel == 1 &&
            sessionPersonId == "owner"
}
