package com.avainfinity.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters

class AvaBackupHealthWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val app = context.applicationContext

    override fun doWork(): Result {
        val aacs = AacsController.get(app)
        if (aacs.isPublicOnlyModeActive() || aacs.hasActiveSession()) return retryOrFail()

        val config = AvaSecurePrefs(app, "ava_backup_config")
        val treeValue = config.getString("backup_tree_uri", null)
        val passphrase = config.getString("backup_passphrase", null)
        if (treeValue.isNullOrBlank() || passphrase.isNullOrBlank()) {
            saveStatus("بررسی سلامت پشتیبان: مقصد یا عبارت عبور تنظیم نشده است.")
            return Result.failure()
        }

        val primaryVaultUri = AvaSecurePrefs(app, "ava_vault_config").getString("tree_uri", null)
        val primaryUri = primaryVaultUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (!AvaVaultStore(app).isIndependentBackupUri(primaryUri, Uri.parse(treeValue))) {
            saveStatus("مقصد پشتیبان باید از نظر فیزیکی مستقل از مخزن اصلی باشد.")
            return auditedRetry("BACKUP_DESTINATION_NOT_INDEPENDENT", "backup target is not a separate removable volume or recognized remote provider")
        }

        val root = DocumentFile.fromTreeUri(app, Uri.parse(treeValue))
        if (root == null || !root.canRead() || !root.canWrite()) {
            saveStatus("مقصد مستقل پشتیبان در دسترس نیست؛ نسخه‌های قبلی حذف یا تغییر داده نشدند.")
            return auditedRetry("BACKUP_DESTINATION_UNAVAILABLE", "weekly chain health check could not access destination")
        }

        val store = AvaLocalStore(app)
        val repository = AvaBackupChainRepository(app, root, passphrase, store)
        val scan = repository.scan()
        if (scan.hasCorruption) {
            saveStatus("بررسی سلامت زنجیره: فایل ناقص، پیوند شکسته یا شاخه مبهم پیدا شد؛ هیچ نسخه‌ای حذف نشد.")
            return auditedRetry("BACKUP_CHAIN_HEALTH_FAILED", "chain graph contains an invalid archive, missing parent, duplicate id or fork")
        }
        if (scan.latest == null) {
            saveStatus("هنوز نسخه کامل زنجیره‌ای معتبر وجود ندارد؛ نسخه‌های قدیمی دست‌نخورده باقی ماندند.")
            return auditedRetry("BACKUP_CHAIN_HEALTH_FAILED", "no verified v2 full baseline found")
        }

        if (!repository.ensureManifest(scan)) {
            saveStatus("فهرست رمزگذاری‌شده زنجیره قابل بازسازی یا تأیید نیست؛ نسخه‌ها حذف نشدند.")
            return auditedRetry("BACKUP_MANIFEST_REBUILD_FAILED", "encrypted chain index could not be verified or rebuilt")
        }

        val points = repository.restorePoints(scan)
        if (points.isEmpty()) {
            saveStatus("هیچ نقطه بازیابی زنجیره‌ای قابل بازسازی نیست.")
            return auditedRetry("BACKUP_CHAIN_HEALTH_FAILED", "no reconstructable restore points")
        }

        val legacyFiles = try {
            root.listFiles().filter { file ->
                val name = file.name ?: ""
                file.isFile && name.endsWith(".avaibk", ignoreCase = true) &&
                    (name.startsWith("AVA_daily_") || name.startsWith("AVA_weekly_") ||
                        name.startsWith("AVA_monthly_") || name.startsWith("AVA_event_") ||
                        name.startsWith("AVA_milestone_"))
            }
        } catch (_: Exception) {
            return auditedRetry("BACKUP_HEALTH_FAILED", "legacy archive listing failed")
        }

        var legacyChecked = 0
        var legacyUnverified = 0
        var legacyInvalidPayloads = 0
        legacyFiles.forEach { file ->
            try {
                val preview = AvaEncryptedBackup(app).previewEncrypted(file.uri, passphrase.toCharArray())
                if (preview.optString("archiveType") == "legacy_full") {
                    legacyChecked++
                    if (!store.validateBackupPayload(preview.getJSONObject("payload"))) {
                        legacyInvalidPayloads++
                    }
                }
            } catch (_: Exception) {
                // Legacy archives may use an older passphrase; never classify them as v3 chain corruption.
                legacyUnverified++
            }
        }

        if (!aacs.recordOwnerEvent(
                "BACKUP_HEALTH_OK",
                "verified v3 chain restore points=${points.size}; chain archives=${scan.archives.size}; legacy authenticated=${legacyChecked}"
            )
        ) {
            saveStatus("بررسی زنجیره انجام شد، اما ثبت نتیجه امنیتی ناموفق بود.")
            return retryOrFail()
        }
        val legacyWarning = legacyUnverified + legacyInvalidPayloads
        if (legacyWarning > 0) {
            if (!aacs.recordOwnerEvent(
                    "BACKUP_LEGACY_HEALTH_WARNING",
                    "unverified legacy archives=${legacyUnverified}; invalid legacy payloads=${legacyInvalidPayloads}; authenticated=${legacyChecked}"
                )
            ) {
                saveStatus("زنجیره v3 سالم است؛ بررسی نسخه‌های قدیمی هشدار دارد و ثبت هشدار امنیتی ناموفق بود.")
                return retryOrFail()
            }
            saveStatus("زنجیره v3 سالم است؛ ${points.size} نقطه بازیابی تأیید شد. نسخه قدیمی احراز‌شده: ${legacyChecked}؛ نسخه قدیمی بررسی‌نشده/نامعتبر: ${legacyWarning}.")
        } else {
            saveStatus("سلامت زنجیره تأیید شد؛ ${points.size} نقطه بازیابی و ${scan.archives.size} آرشیو زنجیره‌ای بازسازی شدند. نسخه قدیمی بررسی‌شده: ${legacyChecked}")
        }
        return Result.success()
    }

    private fun auditedRetry(event: String, detail: String): Result {
        val audited = AacsController.get(app).recordOwnerEvent(event, detail)
        if (!audited) saveStatus("بررسی پشتیبان ناموفق بود و ثبت امنیتی نیز انجام نشد.")
        return retryOrFail()
    }

    private fun saveStatus(value: String) {
        AvaSecurePrefs(app, "ava_backup_config").edit()
            .putString("last_backup_health_status", value)
            .putString("last_backup_health_at", System.currentTimeMillis().toString())
            .apply()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount >= 4) Result.failure() else Result.retry()
}
