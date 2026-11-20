package com.avainfinity.android

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AvaBackupScheduler {
    private const val DAILY_WORK = "ava_infinity_daily_backup"
    private const val EVENT_WORK = "ava_infinity_event_backup"

    fun configureDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AvaBackupWorker>(24, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString("kind", "daily").build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun configureVaultHealth(context: Context) {
        val request = PeriodicWorkRequestBuilder<AvaVaultHealthWorker>(7, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork("ava_infinity_vault_health", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun configureBackupHealth(context: Context) {
        val request = PeriodicWorkRequestBuilder<AvaBackupHealthWorker>(7, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork("ava_infinity_backup_health", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun configureRestoreTest(context: Context) {
        val request = PeriodicWorkRequestBuilder<AvaBackupRestoreTestWorker>(30, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork("ava_infinity_backup_restore_test", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun enqueueEventSnapshot(context: Context) {
        val config = AvaSecurePrefs(context, "ava_backup_config")
        if (config.getString("backup_tree_uri", null).isNullOrBlank() ||
            config.getString("backup_passphrase", null).isNullOrBlank()
        ) return

        val request = OneTimeWorkRequestBuilder<AvaBackupWorker>()
            .setInputData(Data.Builder().putString("kind", "event").build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(EVENT_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(DAILY_WORK)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(EVENT_WORK)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("ava_infinity_backup_health")
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("ava_infinity_backup_restore_test")
    }
}
