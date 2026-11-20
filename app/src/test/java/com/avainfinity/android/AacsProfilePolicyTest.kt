package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AacsProfilePolicyTest {
    private fun profile(
        approved: Boolean = false,
        photoConsent: Boolean = false
    ) = PersonProfile(
        id = "person-arash-naderi",
        name = "آرش",
        level = 3,
        relation = "دوست",
        occupation = "کافه‌دار",
        interests = "موسیقی",
        speakingStyle = "خودمانی",
        intimacy = 5,
        photoUri = "content://private/photo",
        photoConsent = photoConsent,
        relationshipMemoryApproved = approved,
        preferences = "پاسخ کوتاه",
        privacyRestrictions = "یادداشت مالک",
        relationshipHistory = "تاریخچه تأییدشده",
        informationProvenance = "ثبت‌شده توسط مالک",
        pendingRelationshipHistory = "حافظه در انتظار"
    )

    @Test fun unapprovedOrMismatchedSessionCannotReadProfile() {
        assertNull(AacsProfilePolicy.visibleForSession(profile(), 3, "person-arash-naderi", false))
        assertNull(AacsProfilePolicy.visibleForSession(profile(), 2, "person-arash-naderi", true))
        assertNull(AacsProfilePolicy.visibleForSession(profile(), 3, "person-other", true))
        assertNull(AacsProfilePolicy.visibleForSession(profile(), 4, "guest-123", true))
    }

    @Test fun guestProfileIsMinimizedAndPendingOwnerNotesAreHidden() {
        val visible = AacsProfilePolicy.visibleForSession(
            profile(approved = false, photoConsent = false), 3, "person-arash-naderi", true
        ) ?: throw AssertionError("Approved matching profile should be visible.")
        assertEquals("", visible.pendingRelationshipHistory)
        assertEquals("", visible.relationshipHistory)
        assertEquals(0, visible.intimacy)
        assertEquals("", visible.privacyRestrictions)
        assertEquals("", visible.informationProvenance)
        assertNull(visible.photoUri)
    }

    @Test fun approvedRelationshipHistoryAndConsentedPhotoMayBeVisible() {
        val visible = AacsProfilePolicy.visibleForSession(
            profile(approved = true, photoConsent = true), 3, "person-arash-naderi", true
        ) ?: throw AssertionError("Approved matching profile should be visible.")
        assertEquals("تاریخچه تأییدشده", visible.relationshipHistory)
        assertEquals("", visible.pendingRelationshipHistory)
        assertEquals("content://private/photo", visible.photoUri)
    }

    @Test fun approvedOwnerSessionCanReadFullProfiles() {
        val source = profile(approved = false)
        val visible = AacsProfilePolicy.visibleForSession(source, 1, "owner", true)
        assertNotNull(visible)
        assertEquals(source.toJson().toString(), visible?.toJson()?.toString())
    }
}
