package com.avainfinity.android

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.os.storage.StorageManager
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-bound encrypted primary-vault adapter for a user-selected removable-storage folder.
 * The AES key remains in Android Keystore and is never written to the selected folder.
 */
class AvaVaultStore(context: Context) {
    companion object { private val ioLock = Any() }

    private val app = context.applicationContext
    private val config = AvaSecurePrefs(app, "ava_vault_config")
    private val queue = AvaSecurePrefs(app, "ava_vault_queue")
    private val alias = "ava_infinity_primary_vault_aes_v1"
    private val magic = "AVAVLT1"
    private val deleteMarker = "__AVA_VAULT_DELETE__"
    private val maxObjectBytes = 50 * 1024 * 1024
    private val maxQueuedBuckets = 100
    private val maxQueuedBucketBytes = 10 * 1024 * 1024
    private val maxQueueBytes = 50 * 1024 * 1024

    fun isSameLocalStorageVolume(first: Uri, second: Uri): Boolean {
        if (first == second) return true
        if (first.authority != "com.android.externalstorage.documents" ||
            second.authority != "com.android.externalstorage.documents"
        ) return false
        return try {
            val firstVolume = DocumentsContract.getTreeDocumentId(first).substringBefore(':')
            val secondVolume = DocumentsContract.getTreeDocumentId(second).substringBefore(':')
            firstVolume.equals(secondVolume, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun isIndependentBackupUri(primaryUri: Uri?, backupUri: Uri): Boolean {
        if (AvaBackupDestinationPolicy.isKnownRemoteProvider(backupUri.authority)) {
            return AvaBackupDestinationPolicy.isIndependentTarget(
                primaryUri?.authority, null, backupUri.authority, null, false
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            backupUri.authority != AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY
        ) return false
        return try {
            fun volumeId(uri: Uri): String {
                val documentId = when {
                    uri.pathSegments.contains("tree") ->
                        DocumentsContract.getTreeDocumentId(uri)
                    uri.pathSegments.contains("document") ->
                        DocumentsContract.getDocumentId(uri)
                    else -> throw IllegalArgumentException("Unsupported SAF URI shape.")
                }
                return documentId.substringBefore(':')
            }
            val backupVolumeId = volumeId(backupUri)
            val primaryVolumeId = if (
                primaryUri?.authority == AvaBackupDestinationPolicy.EXTERNAL_STORAGE_AUTHORITY
            ) volumeId(primaryUri) else null
            val manager = app.getSystemService(StorageManager::class.java)
            val volume = manager.storageVolumes.firstOrNull {
                it.uuid?.equals(backupVolumeId, ignoreCase = true) == true
            }
            val removableAndMounted = volume?.isRemovable == true &&
                volume.state == Environment.MEDIA_MOUNTED
            AvaBackupDestinationPolicy.isIndependentTarget(
                primaryUri?.authority,
                primaryVolumeId,
                backupUri.authority,
                backupVolumeId,
                removableAndMounted
            )
        } catch (_: Exception) {
            false
        }
    }

    fun isRemovableMicroSdUri(treeUri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        if (treeUri.authority != "com.android.externalstorage.documents") return false
        return try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val volumeId = documentId.substringBefore(':')
            val manager = app.getSystemService(StorageManager::class.java)
            val volume = manager.storageVolumes.firstOrNull {
                it.uuid?.equals(volumeId, ignoreCase = true) == true
            }
            volume?.isRemovable == true && volume.state == Environment.MEDIA_MOUNTED
        } catch (_: Exception) {
            false
        }
    }

    fun configure(treeUri: Uri) = synchronized(ioLock) {
        val currentUri = config.getString("tree_uri", null)
        val savedPreviousUri = config.getString("previous_tree_uri", null)
        val editor = config.edit()
            .putString("tree_uri", treeUri.toString())
            .putBoolean("enabled", true)
            .putString("status", "vault_configuring")
        if (!currentUri.isNullOrBlank() && currentUri != treeUri.toString() && savedPreviousUri.isNullOrBlank()) {
            editor.putString("previous_tree_uri", currentUri)
        } else if (currentUri.isNullOrBlank() && savedPreviousUri.isNullOrBlank()) {
            editor.remove("previous_tree_uri")
        }
        editor.commit()
    }

    fun commitConfiguration(): Boolean = synchronized(ioLock) {
        if (!isAvailable()) return@synchronized false
        config.edit().remove("previous_tree_uri").putString("status", "vault_available").commit()
    }

    fun disable() = synchronized(ioLock) {
        val pending = readQueue()
        val previous = config.getString("previous_tree_uri", null)
        if (pending.length() > 0) {
            // Keep queued values authoritative; do not switch away from a destination holding newer writes.
            config.edit().putBoolean("enabled", true).putString("status", "vault_safe_mode_queueing").commit()
        } else if (!previous.isNullOrBlank()) {
            config.edit()
                .putString("tree_uri", previous)
                .remove("previous_tree_uri")
                .putBoolean("enabled", true)
                .putString("status", "vault_previous_restored")
                .commit()
        } else {
            config.edit().putBoolean("enabled", false).putString("status", "vault_disabled").commit()
        }
    }

    fun isConfigured(): Boolean = config.getBoolean("enabled", false)

    fun isAvailable(): Boolean {
        if (!isConfigured()) return false
        val root = rootDocument() ?: return false
        return try { root.canRead() && root.canWrite() } catch (_: Exception) { false }
    }

    fun isSafeMode(): Boolean = isConfigured() && (!isAvailable() || pendingCount() > 0)

    fun pendingCount(): Int = readQueue().length()

    fun status(): String = config.getString("status", "vault_unconfigured") ?: "vault_unconfigured"

    fun verifyAll(): Boolean = synchronized(ioLock) { verifyAllLocked() }

    private fun verifyAllLocked(): Boolean {
        if (!isAvailable()) {
            config.edit().putString("status", "vault_unavailable").apply()
            return false
        }
        if (!flushPending()) return false
        return try {
            listBucketNames("").forEach { bucket ->
                readBucket(bucket) ?: throw IllegalStateException("Vault bucket missing during health check.")
            }
            config.edit().putString("status", "vault_health_ok").apply()
            true
        } catch (_: Exception) {
            config.edit().putString("status", "vault_health_failed").apply()
            false
        }
    }

    fun hasQueued(bucket: String): Boolean = readQueue().has(bucket)

    fun isDeleteMarker(value: String): Boolean = value == deleteMarker

    fun readBucket(bucket: String): String? = synchronized(ioLock) { readBucketLocked(bucket) }

    private fun readBucketLocked(bucket: String): String? {
        validateBucket(bucket)
        val pending = readQueue()
        if (pending.has(bucket)) return pending.optString(bucket)
        if (!isConfigured()) return null
        val root = rootDocument() ?: return null
        if (!root.canRead()) return null
        val finalName = fileName(bucket)
        val candidates = listOfNotNull(root.findFile(finalName), root.findFile("$finalName.previous"))
        if (candidates.isEmpty()) return null
        var lastError: Exception? = null
        candidates.forEachIndexed { index, file ->
            try {
                val bytes = readBounded(file.uri)
                require(bytes.isNotEmpty()) { "Vault object size is invalid." }
                val value = decryptBucket(bucket, bytes)
                if (index > 0) config.edit().putString("status", "vault_previous_recovered").apply()
                return value
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IllegalStateException("Primary vault object failed integrity verification.", lastError)
    }

    fun writeBucket(bucket: String, rawJson: String): Boolean = synchronized(ioLock) { writeBucketLocked(bucket, rawJson) }

    private fun writeBucketLocked(bucket: String, rawJson: String): Boolean {
        validateBucket(bucket)
        if (!isConfigured()) return false
        if (rawJson == deleteMarker) return deleteBucket(bucket)
        if (rawJson.toByteArray(Charsets.UTF_8).size > 35 * 1024 * 1024) return false
        val root = rootDocument()
        if (root == null || !root.canWrite()) return queueBucket(bucket, rawJson)
        if (hasQueued(bucket) && !queueBucket(bucket, rawJson)) return false
        return try {
            writeVerified(root, bucket, rawJson)
            removeQueued(bucket)
            config.edit().putString("status", "vault_available").apply()
            true
        } catch (_: Exception) {
            queueBucket(bucket, rawJson)
        }
    }

    fun deleteBucket(bucket: String): Boolean = synchronized(ioLock) { deleteBucketLocked(bucket) }

    private fun deleteBucketLocked(bucket: String): Boolean {
        validateBucket(bucket)
        if (!isConfigured()) return true
        val root = rootDocument()
        if (root == null || !root.canWrite()) return queueBucket(bucket, deleteMarker)
        if (hasQueued(bucket) && !queueBucket(bucket, deleteMarker)) return false
        return try {
            deleteBucketOnDisk(root, bucket)
            removeQueued(bucket)
            true
        } catch (_: Exception) {
            queueBucket(bucket, deleteMarker)
        }
    }

    fun isQueuedDelete(bucket: String): Boolean = readQueue().optString(bucket) == deleteMarker

    fun listBucketNames(prefix: String): Set<String> {
        val root = rootDocument() ?: return emptySet()
        if (!root.canRead()) return emptySet()
        return root.listFiles().mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            if (!name.startsWith("AVA_vault_")) return@mapNotNull null
            val base = name.removePrefix("AVA_vault_")
                .removeSuffix(".previous").removeSuffix(".avavlt")
            base.takeIf { it.startsWith(prefix) }
        }.toSet()
    }

    fun flushPending(): Boolean = synchronized(ioLock) { flushPendingLocked() }

    private fun flushPendingLocked(): Boolean {
        if (!isAvailable()) return false
        val root = rootDocument() ?: return false
        val pending = readQueue()
        val keys = pending.keys().asSequence().toList()
        for (bucket in keys) {
            val value = pending.optString(bucket, "")
            if (value.isBlank()) return false
            try {
                if (value == deleteMarker) deleteBucketOnDisk(root, bucket)
                else writeVerified(root, bucket, value)
                removeQueued(bucket)
            } catch (_: Exception) {
                config.edit().putString("status", "vault_sync_failed").apply()
                return false
            }
        }
        config.edit().putString("status", "vault_available").apply()
        return true
    }

    fun rawQueueSnapshot(): JSONObject = JSONObject(readQueue().toString())

    private fun writeVerified(root: DocumentFile, bucket: String, rawJson: String) {
        // Synchronous vault writes are serialized on the app thread; clean abandoned temp objects.
        root.listFiles().filter { it.name?.startsWith("AVA_pending_vault_") == true }.forEach { it.delete() }
        val finalName = fileName(bucket)
        val previousName = "$finalName.previous"
        val token = java.util.UUID.randomUUID().toString().take(10)
        val temporary = root.createFile("application/octet-stream", "AVA_pending_vault_${bucket}_$token.tmp")
            ?: throw IllegalStateException("Vault refused temporary object creation.")
        var fallback: DocumentFile? = root.findFile(previousName)
        var corruptFile: DocumentFile? = null
        try {
            val encrypted = encryptBucket(bucket, rawJson)
            val output = app.contentResolver.openOutputStream(temporary.uri, "wt")
                ?: throw IllegalStateException("Vault output stream unavailable.")
            output.use { it.write(encrypted); it.flush() }
            val verifyBytes = readBounded(temporary.uri)
            require(decryptBucket(bucket, verifyBytes) == rawJson) { "Vault read-back verification mismatch." }

            val existing = root.findFile(finalName)
            if (existing != null) {
                val existingHealthy = try {
                    val existingBytes = readBounded(existing.uri)
                    decryptBucket(bucket, existingBytes)
                    true
                } catch (_: Exception) { false }

                if (existingHealthy) {
                    fallback?.delete()
                    if (!existing.renameTo(previousName)) {
                        throw IllegalStateException("Previous vault object could not be protected.")
                    }
                    fallback = root.findFile(previousName)
                } else {
                    val corruptName = "$finalName.corrupt_$token"
                    if (!existing.renameTo(corruptName)) {
                        throw IllegalStateException("Corrupt vault object could not be isolated.")
                    }
                    corruptFile = root.findFile(corruptName)
                }
            }
            if (!temporary.renameTo(finalName)) {
                fallback?.renameTo(finalName)
                if (fallback == null) corruptFile?.renameTo(finalName)
                throw IllegalStateException("Verified vault object could not be published.")
            }
            // Keep the last-known-good previous object after publishing the new verified object.
            // It is replaced only by the next successful transaction.
            corruptFile?.delete()
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun readBounded(uri: Uri): ByteArray {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Vault object stream is unavailable.")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxObjectBytes) { "Vault object exceeds the supported size." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun encryptBucket(bucket: String, rawJson: String): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$magic|$bucket".toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(rawJson.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("magic", magic)
            put("bucket", bucket)
            put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decryptBucket(bucket: String, bytes: ByteArray): String {
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.optString("magic") == magic && envelope.optString("bucket") == bucket) {
            "Vault object identity mismatch."
        }
        val nonce = Base64.decode(envelope.getString("nonce"), Base64.NO_WRAP)
        val encrypted = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(nonce.size == 12 && encrypted.size > 16) { "Vault object is truncated." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$magic|$bucket".toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun queueBucket(bucket: String, rawJson: String): Boolean {
        return try {
            val current = readQueue()
            val incomingBytes = rawJson.toByteArray(Charsets.UTF_8).size
            if (incomingBytes > maxQueuedBucketBytes) return false
            if (!current.has(bucket) && current.length() >= maxQueuedBuckets) return false
            var totalBytes = 0
            val existingKeys = current.keys()
            while (existingKeys.hasNext()) {
                val existingKey = existingKeys.next()
                if (existingKey != bucket) {
                    totalBytes += current.optString(existingKey).toByteArray(Charsets.UTF_8).size
                }
            }
            if (totalBytes + incomingBytes > maxQueueBytes) return false
            current.put(bucket, rawJson)
            val saved = queue.edit().putString("pending_buckets", current.toString()).commit()
            if (saved) config.edit().putString("status", "vault_safe_mode_queueing").apply()
            saved
        } catch (_: Exception) {
            false
        }
    }

    private fun removeQueued(bucket: String) {
        val current = readQueue()
        current.remove(bucket)
        queue.edit().putString("pending_buckets", current.toString()).commit()
    }

    private fun readQueue(): JSONObject {
        val raw = queue.getString("pending_buckets", "{}") ?: "{}"
        return try { JSONObject(raw) } catch (error: Exception) {
            throw IllegalStateException("Encrypted vault recovery queue is corrupted.", error)
        }
    }

    private fun rootDocument(): DocumentFile? {
        if (!isConfigured()) return null
        val value = config.getString("tree_uri", null) ?: return null
        return DocumentFile.fromTreeUri(app, Uri.parse(value))
    }

    private fun deleteBucketOnDisk(root: DocumentFile, bucket: String) {
        root.findFile(fileName(bucket))?.delete()
        root.findFile(fileName(bucket) + ".previous")?.delete()
    }

    private fun fileName(bucket: String): String = "AVA_vault_$bucket.avavlt"

    private fun validateBucket(bucket: String) {
        require(bucket.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid vault bucket identifier." }
    }
}
