package com.avainfinity.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AvaBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val app = context.applicationContext

    override fun doWork(): Result = synchronized(AvaBackupChainLock.lock) { doWorkLocked() }

    private fun doWorkLocked(): Result {
        val access = AacsController.get(app)
        val requestedKind = inputData.getString("kind") ?: "daily"
        if (access.isPublicOnlyModeActive() || access.hasActiveSession()) {
            saveStatus("پشتیبان‌گیری تا پایان نشست و خروج از حالت عمومی متوقف است؛ داده‌های نشست مهمان وارد نسخه پشتیبان نمی‌شوند.")
            recordFailure(requestedKind, "restricted_session_or_public_only")
            return retryOrFail()
        }

        val localStore = AvaLocalStore(app)
        if (localStore.isVaultSafeMode()) {
            saveStatus("حافظه اصلی در حالت محافظت‌شده یا صف همگام‌سازی است؛ پشتیبان تا تأیید سلامت حافظه متوقف شد.")
            recordFailure(requestedKind, "primary_vault_not_healthy")
            return retryOrFail()
        }

        val config = AvaSecurePrefs(app, "ava_backup_config")
        val treeValue = config.getString("backup_tree_uri", null)
        val storedPassphrase = config.getString("backup_passphrase", null)
        if (treeValue.isNullOrBlank() || storedPassphrase.isNullOrBlank()) {
            saveStatus("تنظیم پشتیبان خودکار کامل نیست.")
            recordFailure(requestedKind, "backup_destination_not_configured")
            return Result.failure()
        }

        val treeUri = Uri.parse(treeValue)
        val primaryVaultUri = AvaSecurePrefs(app, "ava_vault_config").getString("tree_uri", null)
        val primaryUri = primaryVaultUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (!AvaVaultStore(app).isIndependentBackupUri(primaryUri, Uri.parse(treeValue))) {
            saveStatus("مقصد پشتیبان باید از نظر فیزیکی مستقل از مخزن اصلی باشد.")
            recordFailure(requestedKind, "backup_destination_not_independent")
            return Result.failure()
        }

        val root = DocumentFile.fromTreeUri(app, treeUri)
        if (root == null || !root.canRead() || !root.canWrite()) {
            saveStatus("مقصد مستقل پشتیبان در دسترس یا قابل نوشتن نیست؛ نسخه‌های قبلی حفظ شدند.")
            recordFailure(requestedKind, "backup_destination_unavailable")
            return retryOrFail()
        }

        val destinationFiles = try {
            root.listFiles()
        } catch (_: Exception) {
            saveStatus("فهرست مقصد پشتیبان خوانده نشد؛ نسخه‌های قبلی حفظ شدند.")
            recordFailure(requestedKind, "backup_destination_listing_failed")
            return retryOrFail()
        }

        val staleBefore = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        destinationFiles.filter { it.name?.startsWith("AVA_pending_") == true }.forEach { file ->
            val modified = file.lastModified()
            if (modified > 0 && modified < staleBefore) file.delete()
        }

        val repository = AvaBackupChainRepository(app, root, storedPassphrase, localStore)
        val scan = repository.scan()
        if (scan.hasCorruption) {
            saveStatus("زنجیره قبلی قابل اعتماد نیست یا رمز عبور با آن سازگار نیست؛ ساخت نسخه جدید و پاک‌سازی خودکار متوقف شد.")
            recordFailure(requestedKind, "existing_chain_unreadable_or_ambiguous")
            return retryOrFail()
        }
        if (!repository.ensureManifest(scan)) {
            saveStatus("فهرست رمزگذاری‌شده زنجیره قابل تأیید یا بازسازی نیست؛ پشتیبان‌گیری و پاک‌سازی متوقف شد.")
            recordFailure(requestedKind, "backup_manifest_rebuild_failed")
            return retryOrFail()
        }
        val now = Calendar.getInstance()
        val weeklyDue = !hasFullInCurrentPeriod(scan.archives, "weekly", now)
        val monthlyDue = !hasFullInCurrentPeriod(scan.archives, "monthly", now)

        val latestBase = scan.latest?.let { head ->
            scan.archives.firstOrNull {
                it.archive.optString("archiveId") == head.stored.archive.optString("baseArchiveId")
            }
        }
        val baseAge = latestBase?.let {
            System.currentTimeMillis() - it.archive.optLong("createdAt")
        }
        val baseId = latestBase?.archive?.optString("archiveId")
        val deltasSinceBase = if (baseId.isNullOrBlank()) emptyList() else scan.archives.filter {
            it.archive.optString("archiveType") == "delta" &&
                it.archive.optString("baseArchiveId") == baseId
        }
        val deltaBytesSinceBase = deltasSinceBase.sumOf { it.file.length().coerceAtLeast(0L) }
        val baselineBytes = latestBase?.file?.length()?.coerceAtLeast(0L) ?: 0L
        val deltaThresholdReached = AvaBackupChainPolicy.shouldCreateFull(
            baseAge,
            deltasSinceBase.size,
            deltaBytesSinceBase,
            baselineBytes
        )
        val fullKind = when {
            scan.latest == null -> "initial"
            requestedKind == "daily" -> "daily"
            deltaThresholdReached -> "daily"
            else -> null
        }

        val archiveCreatedAt = maxOf(
            System.currentTimeMillis(),
            (scan.latest?.stored?.archive?.optLong("createdAt") ?: 0L) + 1L
        )

        try {
            val payload = synchronized(AvaBackupSnapshotLock.lock) {
                localStore.backupPayload(PersonProfileStore(app).all())
            }
            require(localStore.validateBackupPayload(payload)) {
                "Current backup payload failed schema validation."
            }

            val archive = if (fullKind != null) {
                val labels = mutableListOf(fullKind)
                if (fullKind == "initial") labels.add("daily")
                if (weeklyDue) labels.add("weekly")
                if (monthlyDue) labels.add("monthly")
                AvaBackupChain.createFull(
                    payload = payload,
                    checkpointKind = fullKind,
                    createdAt = archiveCreatedAt,
                    checkpointLabels = labels.distinct()
                )
            } else {
                val head = scan.latest ?: throw IllegalStateException(
                    "No verified full baseline is available; refusing to create an unanchored delta."
                )
                require(requestedKind != "daily") {
                    "A daily backup must be a full snapshot, never an event delta."
                }
                AvaBackupChain.createDelta(
                    parentArchive = head.stored.archive,
                    parentPayload = head.payload,
                    currentPayload = payload,
                    checkpointKind = "event",
                    createdAt = archiveCreatedAt
                )
            }

            if (archive.optString("archiveType") == "delta" &&
                AvaBackupChainPolicy.isEmptyDelta(
                    archive.optJSONObject("changes")?.length() ?: 0,
                    archive.optJSONArray("removedKeys")?.length() ?: 0
                )
            ) {
                saveStatus("تغییر تازه‌ای برای پشتیبان افزایشی وجود نداشت؛ زنجیره بدون نسخه تکراری حفظ شد.")
                return Result.success()
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val archiveIdShort = archive.getString("archiveId").replace("-", "").take(10)
            val finalPrefix = if (archive.optString("archiveType") == "full") "AVA_full_" else "AVA_delta_"
            val finalName = "${finalPrefix}${stamp}_${archiveIdShort}.avaibk"
            var temporary: DocumentFile? = null
            try {
                temporary = root.createFile(
                    "application/octet-stream",
                    "AVA_pending_${archiveIdShort}.tmp.avaibk"
                ) ?: throw IllegalStateException("Backup destination refused temporary archive creation.")

                AvaEncryptedBackup(app).exportEncrypted(
                    temporary.uri, archive, storedPassphrase.toCharArray()
                )

                val preview = AvaEncryptedBackup(app).previewEncrypted(
                    temporary.uri, storedPassphrase.toCharArray()
                )
                require(preview.optString("health") == "authenticated") {
                    "Encrypted archive read-back authentication failed."
                }
                val verifiedArchive = preview.getJSONObject("archive")
                require(verifiedArchive.optString("archiveId") == archive.optString("archiveId")) {
                    "Archive identity changed during read-back."
                }

                val reconstructed = if (verifiedArchive.optString("archiveType") == "full") {
                    AvaBackupChain.materializeFull(verifiedArchive) {
                        localStore.validateBackupPayload(it)
                    }
                } else {
                    AvaBackupChain.reconstruct(
                        scan.archives.map { it.archive } + verifiedArchive,
                        verifiedArchive.getString("archiveId")
                    ) { localStore.validateBackupPayload(it) }
                }
                require(AvaBackupChain.sha256(reconstructed) == AvaBackupChain.sha256(payload)) {
                    "Read-back chain does not reconstruct the captured source snapshot."
                }

                if (!temporary.renameTo(finalName)) {
                    throw IllegalStateException("Verified archive could not be promoted to its immutable name.")
                }
                temporary = null

                if (!access.recordOwnerEvent(
                        "BACKUP_CHAIN_ARCHIVE_VERIFIED",
                        "type=${archive.optString("archiveType")}; checkpoint=${archive.optString("checkpointKind")}; id=${archiveIdShort}; bytes=${preview.optLong("sizeBytes")}"
                    )
                ) {
                    saveStatus("نسخه رمزگذاری‌شده بررسی و ذخیره شد، اما ثبت امنیتی ناموفق بود؛ پاک‌سازی نسخه‌های قبلی متوقف ماند.")
                    return retryOrFail()
                }

                val after = repository.scan()
                val published = after.latest?.stored?.archive?.optString("archiveId") == archive.optString("archiveId")
                if (!after.hasCorruption && published) {
                    if (!repository.ensureManifest(after)) {
                        access.recordOwnerEvent(
                            "BACKUP_MANIFEST_FAILED",
                            "archive verified; pruning skipped because encrypted manifest update failed"
                        )
                    } else {
                        if (!repository.prune(after)) {
                            access.recordOwnerEvent(
                                "BACKUP_RETENTION_DEFERRED",
                                "archive verified; one or more dependency-safe deletions were deferred"
                            )
                        }
                        val afterPrune = repository.scan()
                        if (!afterPrune.hasCorruption && !repository.ensureManifest(afterPrune)) {
                            access.recordOwnerEvent(
                                "BACKUP_MANIFEST_REFRESH_FAILED",
                                "archive and retention scan verified; manifest refresh will be rebuilt on next run"
                            )
                        }
                    }
                } else {
                    access.recordOwnerEvent(
                        "BACKUP_RETENTION_DEFERRED",
                        "new archive verified; existing chain needs recovery review"
                    )
                }

                val typeLabel = if (archive.optString("archiveType") == "full") "کامل" else "افزایشی"
                saveStatus("پشتیبان ${typeLabel} رمزگذاری و زنجیره آن بازخوانی و تأیید شد: ${stamp}")
                return Result.success()
            } catch (error: Exception) {
                temporary?.delete()
                throw error
            }
        } catch (error: Exception) {
            saveStatus("پشتیبان زنجیره‌ای ناموفق بود: ${error.javaClass.simpleName}")
            recordFailure(requestedKind, error.javaClass.simpleName)
            return retryOrFail()
        }
    }

    private fun hasFullInCurrentPeriod(
        archives: List<AvaBackupChainRepository.Stored>,
        label: String,
        now: Calendar
    ): Boolean = archives.any { stored ->
        val archive = stored.archive
        if (archive.optString("archiveType") != "full") return@any false
        val labels = archive.optJSONArray("checkpointLabels")
        val hasLabel = if (labels == null) archive.optString("checkpointKind") == label
        else (0 until labels.length()).any { labels.optString(it) == label }
        if (!hasLabel) return@any false
        val created = Calendar.getInstance().apply {
            timeInMillis = archive.optLong("createdAt")
        }
        if (label == "monthly") {
            created.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                created.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        } else {
            created.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                created.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
        }
    }

    private fun recordFailure(kind: String, reason: String) {
        val audited = AacsController.get(app).recordOwnerEvent(
            "BACKUP_FAILED",
            "kind=${kind}; reason=${reason}"
        )
        if (!audited) saveStatus("پشتیبان‌گیری ناموفق بود و ثبت امنیتی خطا هم کامل نشد؛ نسخه‌های قبلی حفظ شدند.")
    }

    private fun saveStatus(value: String) {
        AvaSecurePrefs(app, "ava_backup_config").edit()
            .putString("last_backup_status", value)
            .putString("last_backup_status_at", System.currentTimeMillis().toString())
            .apply()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount >= 4) Result.failure() else Result.retry()

}
