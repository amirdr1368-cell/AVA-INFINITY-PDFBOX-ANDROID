package com.avainfinity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaBackupDestinationPolicyTest {
    @Test fun requiresASeparateRemovableLocalVolume() {
        assertTrue(AvaBackupDestinationPolicy.isIndependentTarget(
            null, "PRIMARY", AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY, "ABCD-1234", true
        ))
        assertFalse(AvaBackupDestinationPolicy.isIndependentTarget(
            AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY, "ABCD-1234", AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY, "ABCD-1234", true
        ))
        assertFalse(AvaBackupDestinationPolicy.isIndependentTarget(
            AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY, "ABCD-1234", AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY, "ABCD-1234", false
        ))
        assertFalse(AvaBackupDestinationPolicy.isIndependentTarget(
            null, null, AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY, "primary", false
        ))
    }

    @Test fun knownRemoteProvidersAreIndependentTargets() {
        assertTrue(AvaBackupDestinationPolicy.isIndependentTarget(
            null, "ABCD-1234", "com.google.android.apps.docs.storage", null, false
        ))
        assertTrue(AvaBackupDestinationPolicy.isIndependentTarget(
            null, "ABCD-1234", "com.dropbox.android", null, false
        ))
    }

    @Test fun sameCloudProviderIsNotAnIndependentReplica() {
        assertFalse(AvaBackupDestinationPolicy.isIndependentTarget(
            "com.google.android.apps.docs.storage", null,
            "com.google.android.apps.docs.storage", null, false
        ))
    }

    @Test fun unknownProvidersAreNotAssumedToBeRemote() {
        assertFalse(AvaBackupDestinationPolicy.isIndependentTarget(
            null, "ABCD-1234", "com.example.local.documents", null, false
        ))
    }
}
