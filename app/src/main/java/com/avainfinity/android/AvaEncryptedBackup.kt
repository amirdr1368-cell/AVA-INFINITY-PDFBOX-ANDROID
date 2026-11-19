package com.avainfinity.android

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec

class AvaEncryptedBackup(private val context: Context) {
    companion object {
        private const val MAGIC = "AVAIBK1"
        private const val ITERATIONS = 210_000
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        private const val MAX_PLAINTEXT_BYTES = 35 * 1024 * 1024
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun exportEncrypted(destination: Uri, payload: JSONObject, passphrase: CharArray) {
        try {
            require(passphrase.size >= 12) { "Backup passphrase must contain at least 12 characters." }
            val random = SecureRandom()
            val salt = ByteArray(16).also { random.nextBytes(it) }
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val plain = payload.toString().toByteArray(Charsets.UTF_8)
            require(plain.size <= MAX_PLAINTEXT_BYTES) { "Backup payload exceeds the supported size." }
            val key = derive(passphrase, salt)
            val encrypted = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(MAGIC.toByteArray(Charsets.UTF_8))
                cipher.doFinal(plain)
            } finally {
                key.fill(0)
                plain.fill(0)
            }

            val envelope = JSONObject().apply {
                put("magic", MAGIC)
                put("kdf", "PBKDF2-HMAC-SHA256")
                put("iterations", ITERATIONS)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("Backup destination could not be opened.")
            output.use {
                it.write(envelope.toString().toByteArray(Charsets.UTF_8))
                it.flush()
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    fun previewEncrypted(source: Uri, passphrase: CharArray): JSONObject {
        try {
            val raw = context.contentResolver.openInputStream(source)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BACKUP_BYTES) { "Backup file size is invalid." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("Backup source could not be opened.")
            require(raw.isNotEmpty()) { "Backup file size is invalid." }
            val envelope = JSONObject(String(raw, Charsets.UTF_8))
            require(envelope.optString("magic") == MAGIC) { "Not an AVA backup." }
            require(envelope.optString("kdf") == "PBKDF2-HMAC-SHA256") { "Unsupported backup key derivation." }
            require(envelope.optInt("iterations") == ITERATIONS) { "Unsupported backup security parameters." }

            val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
            val nonce = Base64.decode(envelope.getString("nonce"), Base64.NO_WRAP)
            val encrypted = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
            require(salt.size == 16 && nonce.size == 12 && encrypted.size > 16) { "Backup envelope is invalid." }

            val key = derive(passphrase, salt)
            val plain = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(MAGIC.toByteArray(Charsets.UTF_8))
                cipher.doFinal(encrypted)
            } finally {
                key.fill(0)
            }

            val decoded = try {
                JSONObject(String(plain, Charsets.UTF_8))
            } finally {
                plain.fill(0)
            }
            val chainArchive = decoded.optString("format") == AvaBackupChain.FORMAT
            val legacyPayload = decoded.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1"
            val chainIndex = decoded.optString("format") == "AVA-INFINITY-BACKUP-INDEX-1"
            require(chainArchive || legacyPayload || chainIndex) { "Unsupported AVA backup payload." }
            if (chainIndex) {
                require(decoded.optInt("schemaVersion") == 1)
                require(decoded.optJSONArray("entries") != null)
                return JSONObject()
                    .put("createdAt", decoded.optLong("createdAt"))
                    .put("sizeBytes", raw.size)
                    .put("health", "authenticated")
                    .put("archiveType", "index")
                    .put("index", decoded)
            }

            val portablePayload = if (legacyPayload) {
                decoded
            } else {
                AvaBackupChain.validateHeader(decoded)
                if (decoded.optString("archiveType") == "full") {
                    AvaBackupChain.materializeFull(decoded) {
                        it.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1"
                    }
                } else {
                    null
                }
            }
            val personMemories = portablePayload?.optJSONObject("personMemories")
            var personMemoryCount = 0
            if (personMemories != null) {
                val keys = personMemories.keys()
                while (keys.hasNext()) personMemoryCount += personMemories.optJSONArray(keys.next())?.length() ?: 0
            }
            return JSONObject().apply {
                put("createdAt", if (chainArchive) decoded.optLong("createdAt") else decoded.optLong("createdAt"))
                put("sizeBytes", raw.size)
                put("health", "authenticated")
                put("archiveType", if (legacyPayload) "legacy_full" else decoded.optString("archiveType"))
                put("archiveId", if (chainArchive) decoded.optString("archiveId") else JSONObject.NULL)
                put("baseArchiveId", if (chainArchive) decoded.optString("baseArchiveId") else JSONObject.NULL)
                put("parentArchiveId", if (chainArchive) decoded.opt("parentArchiveId") else JSONObject.NULL)
                put("checkpointKind", if (chainArchive) decoded.optString("checkpointKind") else "legacy")
                put("memoryCount", (portablePayload?.optJSONArray("memories_amir")?.length() ?: 0) + personMemoryCount)
                put("profileCount", portablePayload?.optJSONArray("profiles")?.length() ?: 0)
                put("vocabularyCount", portablePayload?.optJSONArray("vocabulary")?.length() ?: 0)
                put("taskCount", portablePayload?.optJSONArray("tasks")?.length() ?: 0)
                if (portablePayload != null) put("payload", portablePayload)
                if (chainArchive) put("archive", decoded)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }
}
