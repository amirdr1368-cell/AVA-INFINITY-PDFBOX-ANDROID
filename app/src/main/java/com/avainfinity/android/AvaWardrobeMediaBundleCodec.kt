package com.avainfinity.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming encrypted companion bundle for owner wardrobe media.
 *
 * This codec only exports to and extracts into caller-supplied streams/directories.
 * It never mutates the live wardrobe index or directory. The caller must keep extracted
 * files staged until explicit owner confirmation and a separate recoverable apply step.
 */
object AvaWardrobeMediaBundleCodec {
    private const val MAGIC = "AVAWMB01"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 8 + 4 + SALT_BYTES + NONCE_BYTES
    const val MAX_ITEMS = 200
    const val MAX_ITEM_BYTES = 15L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 100L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 1024 * 1024
    const val MAX_ENCRYPTED_BYTES = 110L * 1024L * 1024L + HEADER_BYTES
    private val uuidV4 = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    private val allowedCategories = setOf("private", "sporty", "everyday", "formal", "homewear", "hairstyle", "other")

    data class ExportEntry(
        val itemId: String,
        val displayName: String,
        val category: String,
        val addedAt: Long,
        val file: File
    )

    data class PreviewEntry(
        val itemId: String,
        val displayName: String,
        val category: String,
        val mimeType: String,
        val byteLength: Long,
        val sha256: String,
        val addedAt: Long,
        val fileName: String,
        val stagedFile: File
    )

    data class StagedPreview(
        val bundleId: String,
        val createdAt: Long,
        val entries: List<PreviewEntry>,
        val totalBytes: Long,
        val stagingDirectory: File
    )

    private data class FileFacts(
        val mimeType: String,
        val extension: String,
        val byteLength: Long,
        val sha256: String
    )

    fun exportEncrypted(
        destination: OutputStream,
        wardrobeRoot: File,
        entries: List<ExportEntry>,
        passphrase: CharArray,
        createdAt: Long = System.currentTimeMillis(),
        bundleId: String = UUID.randomUUID().toString()
    ) {
        try {
            require(passphrase.size >= 12) { "Media backup passphrase must contain at least 12 characters." }
            require(createdAt > 0 && uuidV4.matches(bundleId))
            require(entries.size <= MAX_ITEMS)
            require(entries.map { it.itemId }.distinct().size == entries.size)
            val facts = entries.map { entry ->
                require(uuidV4.matches(entry.itemId))
                require(entry.displayName.isNotBlank() && entry.displayName.length <= 200)
                require(entry.category in allowedCategories)
                require(entry.addedAt > 0)
                require(AvaWardrobeMediaPolicy.isSafeImageFile(wardrobeRoot, entry.file))
                val canonical = entry.file.canonicalFile
                val inspected = inspect(canonical)
                require(inspected.byteLength in 1L..MAX_ITEM_BYTES)
                inspected
            }
            val totalBytes = facts.sumOf { it.byteLength }
            require(totalBytes <= MAX_TOTAL_BYTES)

            val manifest = buildManifest(bundleId, createdAt, entries, facts, totalBytes)
            val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
            require(manifestBytes.size in 1..MAX_MANIFEST_BYTES)

            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
            val header = MAGIC.toByteArray(Charsets.US_ASCII) +
                intBytes(ITERATIONS) + salt + nonce
            val key = derive(passphrase, salt)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(header)
                destination.write(header)
                CipherOutputStream(destination, cipher).use { encrypted ->
                    val data = java.io.DataOutputStream(encrypted)
                    data.writeInt(manifestBytes.size)
                    data.write(manifestBytes)
                    entries.forEachIndexed { index, entry ->
                        val expected = facts[index]
                        val current = inspect(entry.file.canonicalFile)
                        require(current == expected) { "A wardrobe image changed during media export." }
                        FileInputStream(entry.file.canonicalFile).use { input ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            var copied = 0L
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                copied += count
                                require(copied <= expected.byteLength && copied <= MAX_ITEM_BYTES)
                                digest.update(buffer, 0, count)
                                data.write(buffer, 0, count)
                            }
                            val digestHex = digest.digest().toHex()
                            require(copied == expected.byteLength && digestHex == expected.sha256) {
                                "A wardrobe image changed while it was being exported."
                            }
                        }
                    }
                    data.flush()
                }
            } finally {
                key.fill(0)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Authenticates and extracts into a new isolated staging directory.
     * The returned files are not live wardrobe files and must not be applied automatically.
     */
    fun decryptToStaging(
        source: InputStream,
        stagingParent: File,
        passphrase: CharArray,
        validateStagedImage: (File) -> Boolean
    ): StagedPreview {
        var staging: File? = null
        try {
            require(passphrase.size >= 12) { "Media backup passphrase is invalid." }
            require(stagingParent.isDirectory && stagingParent.canWrite())
            val bounded = BoundedInputStream(BufferedInputStream(source), MAX_ENCRYPTED_BYTES)
            val header = ByteArray(HEADER_BYTES)
            readFully(bounded, header)
            require(String(header, 0, 8, Charsets.US_ASCII) == MAGIC) { "Not an AVA wardrobe media bundle." }
            val iterations = readInt(header, 8)
            require(iterations == ITERATIONS) { "Unsupported media bundle security parameters." }
            val salt = header.copyOfRange(12, 12 + SALT_BYTES)
            val nonce = header.copyOfRange(12 + SALT_BYTES, HEADER_BYTES)
            val key = derive(passphrase, salt)
            try {
                staging = File(stagingParent, "ava-media-restore_" + UUID.randomUUID().toString())
                require(staging.mkdirs()) { "Media restore staging directory could not be created." }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(header)
                val stagedEntries = mutableListOf<PreviewEntry>()
                var totalBytes = 0L
                AuthenticatedCipherInputStream(bounded, cipher).use { decrypted ->
                    val data = DataInputStream(decrypted)
                    val manifestLength = data.readInt()
                    require(manifestLength in 1..MAX_MANIFEST_BYTES) { "Media manifest size is invalid." }
                    val manifestBytes = ByteArray(manifestLength)
                    data.readFully(manifestBytes)
                    val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
                    require(manifest.optString("format") == "AVA-WARDROBE-MEDIA-1")
                    require(manifest.optInt("schemaVersion") == 1)
                    val bundleId = manifest.optString("bundleId")
                    val createdAt = manifest.optLong("createdAt")
                    require(uuidV4.matches(bundleId) && createdAt > 0)
                    val array = manifest.optJSONArray("entries")
                        ?: throw IllegalArgumentException("Media manifest entries are missing.")
                    require(array.length() in 0..MAX_ITEMS)
                    require(manifest.optInt("itemCount", -1) == array.length())
                    val declaredTotal = manifest.optLong("totalBytes", -1L)
                    require(declaredTotal in 0L..MAX_TOTAL_BYTES)

                    val seenIds = mutableSetOf<String>()
                    val seenNames = mutableSetOf<String>()
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val id = item.optString("itemId")
                        val name = item.optString("displayName")
                        val category = item.optString("category")
                        val mime = item.optString("mimeType")
                        val size = item.optLong("byteLength", -1L)
                        val digest = item.optString("sha256")
                        val addedAt = item.optLong("addedAt")
                        val fileName = item.optString("fileName")
                        require(uuidV4.matches(id) && seenIds.add(id))
                        require(name.isNotBlank() && name.length <= 200)
                        require(category in allowedCategories && addedAt > 0)
                        require(size in 1L..MAX_ITEM_BYTES)
                        require(digest.matches(Regex("[0-9a-f]{64}")))
                        val expectedName = "wardrobe_$id" + when (mime) {
                            "image/jpeg" -> ".jpg"
                            "image/png" -> ".png"
                            "image/gif" -> ".gif"
                            "image/webp" -> ".webp"
                            else -> throw IllegalArgumentException("Unsupported media MIME type.")
                        }
                        require(fileName == expectedName && seenNames.add(fileName))
                        totalBytes += size
                        require(totalBytes <= MAX_TOTAL_BYTES)

                        val stagedFile = File(staging, fileName)
                        require(stagedFile.canonicalFile.parentFile == staging.canonicalFile)
                        val actualDigest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        FileOutputStream(stagedFile).use { output ->
                            val buffer = ByteArray(8192)
                            while (copied < size) {
                                val wanted = minOf(buffer.size.toLong(), size - copied).toInt()
                                val count = data.read(buffer, 0, wanted)
                                if (count < 0) throw IOException("Media item is truncated.")
                                if (count == 0) continue
                                copied += count
                                actualDigest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                        val format = inspect(stagedFile)
                        require(format.mimeType == mime && format.byteLength == size)
                        require(actualDigest.digest().toHex() == digest)
                        require(validateStagedImage(stagedFile)) {
                            "Staged image failed platform decoder and dimension validation."
                        }
                        stagedEntries.add(
                            PreviewEntry(id, name, category, mime, size, digest, addedAt, fileName, stagedFile)
                        )
                    }
                    require(totalBytes == declaredTotal) { "Media bundle aggregate size mismatch." }
                    require(data.read() == -1) { "Unexpected trailing media payload." }
                }
                return StagedPreview(bundleId, createdAt, stagedEntries, totalBytes, staging)
            } finally {
                key.fill(0)
            }
        } catch (error: Exception) {
            staging?.deleteRecursively()
            throw error
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun buildManifest(
        bundleId: String,
        createdAt: Long,
        entries: List<ExportEntry>,
        facts: List<FileFacts>,
        totalBytes: Long
    ): JSONObject {
        val items = JSONArray()
        entries.forEachIndexed { index, entry ->
            val fact = facts[index]
            items.put(JSONObject()
                .put("itemId", entry.itemId)
                .put("displayName", entry.displayName)
                .put("category", entry.category)
                .put("mimeType", fact.mimeType)
                .put("byteLength", fact.byteLength)
                .put("sha256", fact.sha256)
                .put("addedAt", entry.addedAt)
                .put("fileName", "wardrobe_" + entry.itemId + fact.extension))
        }
        return JSONObject()
            .put("format", "AVA-WARDROBE-MEDIA-1")
            .put("schemaVersion", 1)
            .put("bundleId", bundleId)
            .put("createdAt", createdAt)
            .put("itemCount", entries.size)
            .put("totalBytes", totalBytes)
            .put("entries", items)
    }

    private fun inspect(file: File): FileFacts {
        require(file.isFile && file.length() in 1L..MAX_ITEM_BYTES)
        val header = ByteArray(12)
        val digest = MessageDigest.getInstance("SHA-256")
        var headerLength = 0
        var total = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_ITEM_BYTES)
                digest.update(buffer, 0, count)
                if (headerLength < header.size) {
                    val copy = minOf(count, header.size - headerLength)
                    System.arraycopy(buffer, 0, header, headerLength, copy)
                    headerLength += copy
                }
            }
        }
        require(total == file.length())
        val format = AvaWardrobeImageFormat.detect(header.copyOf(headerLength))
            ?: throw IllegalArgumentException("Unsupported or corrupt image signature.")
        require(file.name.endsWith(format.extension, ignoreCase = true)) {
            "Image signature does not match its generated filename."
        }
        return FileFacts(format.mimeType, format.extension, total, digest.digest().toHex())
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) throw IOException("Media bundle header is truncated.")
            if (count == 0) continue
            offset += count
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /**
     * Streams GCM plaintext but always propagates doFinal() authentication failures.
     * CipherInputStream implementations have historically differed in how they
     * surface AEAD tag failures, so this wrapper makes final authentication explicit.
     */
    private class AuthenticatedCipherInputStream(
        private val source: InputStream,
        private val cipher: Cipher
    ) : InputStream() {
        private val encryptedBuffer = ByteArray(8192)
        private var plaintext = ByteArray(0)
        private var plaintextOffset = 0
        private var finalized = false
        private var closed = false

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("Decryption stream is closed.")
            if (offset < 0 || length < 0 || length > buffer.size - offset) {
                throw IndexOutOfBoundsException()
            }
            if (length == 0) return 0
            if (!ensurePlaintext()) return -1
            val count = minOf(length, plaintext.size - plaintextOffset)
            System.arraycopy(plaintext, plaintextOffset, buffer, offset, count)
            plaintextOffset += count
            return count
        }

        private fun ensurePlaintext(): Boolean {
            while (plaintextOffset >= plaintext.size) {
                if (finalized) return false
                val count = source.read(encryptedBuffer)
                plaintextOffset = 0
                if (count < 0) {
                    // This is the only point at which the GCM tag is authenticated.
                    // Any AEADBadTagException must escape and abort staging.
                    plaintext = cipher.doFinal()
                    finalized = true
                } else if (count == 0) {
                    continue
                } else {
                    plaintext = cipher.update(encryptedBuffer, 0, count) ?: ByteArray(0)
                }
            }
            return plaintextOffset < plaintext.size
        }

        override fun close() {
            if (!closed) {
                closed = true
                source.close()
                plaintext.fill(0)
                encryptedBuffer.fill(0)
            }
        }
    }

    private class BoundedInputStream(input: InputStream, private val maxBytes: Long) : FilterInputStream(input) {
        var count: Long = 0
            private set

        override fun read(): Int {
            if (count >= maxBytes) {
                if (super.read() >= 0) throw IOException("Encrypted media bundle exceeds the size limit.")
                return -1
            }
            val value = super.read()
            if (value >= 0) count++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (count >= maxBytes) {
                if (super.read() >= 0) throw IOException("Encrypted media bundle exceeds the size limit.")
                return -1
            }
            val allowed = minOf(length.toLong(), maxBytes - count).toInt()
            val read = super.read(buffer, offset, allowed)
            if (read > 0) count += read
            return read
        }
    }
}
