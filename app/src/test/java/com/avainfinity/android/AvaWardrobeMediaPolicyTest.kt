package com.avainfinity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaWardrobeMediaPolicyTest {
    @Test fun allowsOnlyAnApprovedLevelOneOwnerSession() {
        assertTrue(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 1, "owner"))
    }

    @Test fun rejectsMissingOrUnapprovedSessions() {
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, false, true, 1, "owner"))
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, false, 1, "owner"))
    }

    @Test fun rejectsPublicOnlyModeEvenForOwner() {
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(true, true, true, 1, "owner"))
    }

    @Test fun rejectsOtherIdentitiesAndPrivilegeLevels() {
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 2, "owner"))
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 1, "person-amir"))
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 4, "guest-123"))
    }
}
