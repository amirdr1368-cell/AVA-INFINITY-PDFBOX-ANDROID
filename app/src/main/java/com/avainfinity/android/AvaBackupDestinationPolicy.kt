package com.avainfinity.android

/**
 * Pure policy for accepting a physically independent backup target.
 * Unknown SAF providers are not assumed to be remote/cloud.
 */
object AvaBackupDestinationPolicy {
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    private val knownRemoteAuthorities = setOf(
        "com.google.android.apps.docs.storage",
        "com.google.android.apps.docs.storage.legacy",
        "com.microsoft.skydrive.content.StorageAccessProvider",
        "com.dropbox.android",
        "com.box.android"
    )

    fun isKnownRemoteProvider(authority: String?): Boolean =
        authority != null && authority in knownRemoteAuthorities

    fun isIndependentRemovableVolume(
        primaryVolumeId: String?,
        backupVolumeId: String?,
        backupIsRemovableAndMounted: Boolean
    ): Boolean {
        if (!backupIsRemovableAndMounted || backupVolumeId.isNullOrBlank()) return false
        return primaryVolumeId.isNullOrBlank() ||
            !primaryVolumeId.equals(backupVolumeId, ignoreCase = true)
    }

    fun isIndependentTarget(
        primaryAuthority: String?,
        primaryVolumeId: String?,
        backupAuthority: String?,
        backupVolumeId: String?,
        backupIsRemovableAndMounted: Boolean
    ): Boolean {
        if (isKnownRemoteProvider(backupAuthority)) {
            // A second folder in the same cloud provider is not an independent replica.
            return primaryAuthority.isNullOrBlank() || primaryAuthority != backupAuthority
        }
        return backupAuthority == EXTERNAL_STORAGE_AUTHORITY &&
            isIndependentRemovableVolume(primaryVolumeId, backupVolumeId, backupIsRemovableAndMounted)
    }
}
