package com.avainfinity.android

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AvaVaultHealthWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val app = context.applicationContext

    override fun doWork(): Result {
        val vault = AvaVaultStore(app)
        if (!vault.isConfigured()) return Result.success()
        val healthy = vault.verifyAll()
        val auditOk = AacsController.get(app).recordOwnerEvent(
            if (healthy) "VAULT_HEALTH_OK" else "VAULT_HEALTH_FAILED",
            "weekly removable-vault integrity verification"
        )
        if (!auditOk) return if (runAttemptCount >= 4) Result.failure() else Result.retry()
        return if (healthy) Result.success() else if (runAttemptCount >= 4) Result.failure() else Result.retry()
    }
}
