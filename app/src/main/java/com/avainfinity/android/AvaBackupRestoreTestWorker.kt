package com.avainfinity.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters

class AvaBackupRestoreTestWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val app = context.applicationContext

    override fun doWork(): Result {
        val aacs = AacsController.get(app)
        if (aacs.isPublicOnlyModeActive() || aacs.hasActiveSession()) return retryOrFail()

        val config = AvaSecurePrefs(app, "ava_backup_config")
        val treeValue = config.getString("backup_tree_uri", null)
        val passphrase = config.getString("backup_passphrase", null)
        if (treeValue.isNullOrBlank() || passphrase.isNullOrBlank()) {
            saveStatus("آزمون بازیابی: مقصد پشتیبان یا عبارت عبور تنظیم نشده است.")
            return Result.failure()
        }

        val primaryVaultUri = AvaSecurePrefs(app, "ava_vault_config").getString("tree_uri", null)
        val primaryUri = primaryVaultUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (!AvaVaultStore(app).isIndependentBackupUri(primaryUri, Uri.parse(treeValue))) {
            saveStatus("مقصد پشتیبان باید از نظر فیزیکی مستقل از مخزن اصلی باشد.")
            return auditedRetry("BACKUP_DESTINATION_NOT_INDEPENDENT", "backup target is not a separate removable volume or recognized remote provider")
        }

        val root = DocumentFile.fromTreeUri(app, Uri.parse(treeValue))
        if (root == null || !root.canRead()) {
            saveStatus("آزمون بازیابی به مقصد مستقل دسترسی ندارد؛ داده زنده تغییر نکرد.")
            return auditedRetry("BACKUP_RESTORE_TEST_FAILED", "destination unavailable")
        }

        return try {
            val store = AvaLocalStore(app)
            val repository = AvaBackupChainRepository(app, root, passphrase, store)
            val scan = repository.scan()
            require(!scan.hasCorruption) { "Backup chain graph is damaged or ambiguous." }
            val head = scan.latest ?: throw IllegalStateException("No verified full backup chain is available.")
            require(AvaBackupStagingVerifier.verify(head.payload) { staged ->
                store.validateBackupPayload(staged)
            }) { "Isolated in-memory restore staging did not match the reconstructed chain head." }

            if (!aacs.recordOwnerEvent(
                    "BACKUP_RESTORE_TEST_OK",
                    "isolated staging verified archive=${head.stored.archive.optString("archiveId")}; chain archives=${scan.archives.size}; live stores untouched"
                )
            ) {
                saveStatus("آزمون بازیابی موفق بود، اما ثبت نتیجه امنیتی ناموفق ماند.")
                return retryOrFail()
            }
            saveStatus("آزمون بازیابی ایزوله موفق بود؛ زنجیره بازسازی و در حافظه موقت اعتبارسنجی شد. داده زنده تغییر نکرد.")
            Result.success()
        } catch (error: Exception) {
            auditedRetry("BACKUP_RESTORE_TEST_FAILED", error.javaClass.simpleName)
        }
    }

    private fun auditedRetry(event: String, detail: String): Result {
        val audited = AacsController.get(app).recordOwnerEvent(event, detail)
        if (!audited) saveStatus("آزمون بازیابی ناموفق بود و ثبت امنیتی نیز انجام نشد.")
        return retryOrFail()
    }

    private fun saveStatus(value: String) {
        AvaSecurePrefs(app, "ava_backup_config").edit()
            .putString("last_backup_restore_test_status", value)
            .putString("last_backup_restore_test_at", System.currentTimeMillis().toString())
            .apply()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount >= 4) Result.failure() else Result.retry()
}
