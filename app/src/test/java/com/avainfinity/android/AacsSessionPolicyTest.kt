package com.avainfinity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AacsSessionPolicyTest {
    @Test fun acceptsOnlyIdentityShapesForTheirExactLevels() {
        assertTrue(AacsSessionPolicy.isValidIdentity("owner", 1))
        assertTrue(AacsSessionPolicy.isValidIdentity("person-arash-naderi", 2))
        assertTrue(AacsSessionPolicy.isValidIdentity("person-amir-friend", 3))
        assertTrue(AacsSessionPolicy.isValidIdentity("guest-123456", 4))
        assertFalse(AacsSessionPolicy.isValidIdentity("guest-123456", 1))
        assertFalse(AacsSessionPolicy.isValidIdentity("person-amir", 4))
        assertFalse(AacsSessionPolicy.isValidIdentity("owner", 2))
    }

    @Test fun rejectsOutOfRangeLevelsInsteadOfCoercingThem() {
        assertFalse(AacsSessionPolicy.isValidIdentity("owner", 0))
        assertFalse(AacsSessionPolicy.isValidIdentity("owner", 5))
        assertFalse(AacsSessionPolicy.isValidIdentity("guest-1", 7))
    }

    @Test fun profilesStayHiddenUntilTheActiveSessionIsApproved() {
        assertFalse(AacsSessionPolicy.canExposeProfiles(false, true, false, 1, "owner"))
        assertFalse(AacsSessionPolicy.canExposeProfiles(false, true, false, 3, "person-arash-naderi"))
        assertTrue(AacsSessionPolicy.canExposeProfiles(false, true, true, 1, "owner"))
        assertTrue(AacsSessionPolicy.canExposeProfiles(false, true, true, 3, "person-arash-naderi"))
        assertFalse(AacsSessionPolicy.canExposeProfiles(false, true, true, 4, "guest-123"))
        assertFalse(AacsSessionPolicy.canExposeProfiles(true, false, false, 1, "owner"))
        assertTrue(AacsSessionPolicy.canExposeProfiles(false, false, false, 4, null))
    }

    @Test fun externalTransmissionRequiresCurrentApprovedOwnerContext() {
        assertTrue(AacsSessionPolicy.canTransmitExternalData(false, false, false, 4, null, 7, 7))
        assertTrue(AacsSessionPolicy.canTransmitExternalData(false, true, true, 1, "owner", 7, 7))
        assertFalse(AacsSessionPolicy.canTransmitExternalData(false, true, false, 1, "owner", 7, 7))
        assertFalse(AacsSessionPolicy.canTransmitExternalData(false, true, true, 3, "person-arash-naderi", 7, 7))
        assertFalse(AacsSessionPolicy.canTransmitExternalData(true, false, false, 1, "owner", 7, 7))
        assertFalse(AacsSessionPolicy.canTransmitExternalData(false, false, false, 4, null, 8, 7))
    }

    @Test fun privilegeDowngradeMustMoveTowardLessPrivilege() {
        assertTrue(AacsSessionPolicy.isPrivilegeDowngrade(2, 3))
        assertTrue(AacsSessionPolicy.isPrivilegeDowngrade(3, 4))
        assertFalse(AacsSessionPolicy.isPrivilegeDowngrade(3, 2))
        assertFalse(AacsSessionPolicy.isPrivilegeDowngrade(2, 1))
        assertFalse(AacsSessionPolicy.isPrivilegeDowngrade(1, 2))
    }
}
