package com.avainfinity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AacsAuditPolicyTest {
    @Test fun backupMaintenanceEventsDoNotCreateSelfReferentialDeltas() {
        assertFalse(AacsAuditPolicy.includeInPortableBackup("BACKUP_CHAIN_ARCHIVE_VERIFIED"))
        assertFalse(AacsAuditPolicy.includeInPortableBackup("BACKUP_EVENT_NO_CHANGE"))
        assertFalse(AacsAuditPolicy.includeInPortableBackup("BACKUP_HEALTH_OK"))
    }

    @Test fun securityAndRestoreEventsRemainInPortableAuditHistory() {
        assertTrue(AacsAuditPolicy.includeInPortableBackup("SESSION_START"))
        assertTrue(AacsAuditPolicy.includeInPortableBackup("ACCESS_DOWNGRADED"))
        assertTrue(AacsAuditPolicy.includeInPortableBackup("RESTORE_SUCCESS"))
        assertTrue(AacsAuditPolicy.includeInPortableBackup("PRIVATE_ACCESS_DENIED"))
    }
}
