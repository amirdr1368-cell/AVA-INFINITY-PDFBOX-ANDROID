package com.avainfinity.android

import java.io.File

/**
 * Strict access and storage-path policy for owner-only wardrobe media.
 */
object AvaWardrobeMediaPolicy {
    private val generatedImageName = Regex("wardrobe_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\\.(jpg|png|gif|webp)")

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

    /**
     * Accept only regular, bounded files with app-generated names whose canonical
     * location is a direct child of the canonical private wardrobe directory.
     */
    fun isSafeImageFile(root: File, candidate: File): Boolean = try {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        canonicalCandidate.parentFile == canonicalRoot &&
            canonicalCandidate.isFile &&
            canonicalCandidate.length() in 1L..(15L * 1024L * 1024L) &&
            generatedImageName.matches(canonicalCandidate.name)
    } catch (_: Exception) {
        false
    }
}
