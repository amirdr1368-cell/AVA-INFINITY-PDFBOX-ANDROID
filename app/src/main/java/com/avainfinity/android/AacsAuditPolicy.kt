package com.avainfinity.android

object AacsAuditPolicy {
    /** Backup-worker maintenance events stay in the local audit log but are excluded from portable snapshots. */
    fun includeInPortableBackup(eventType: String): Boolean =
        !eventType.startsWith("BACKUP_")
}
