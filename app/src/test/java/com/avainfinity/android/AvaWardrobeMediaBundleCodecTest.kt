package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID

class AvaWardrobeMediaBundleCodecTest {
    private val passphrase = "correct horse battery".toCharArray()

    @Test fun encryptedBundleRoundTripsMultipleImageTypesWithoutDevicePaths() {
        val base = Files.createTempDirectory("ava-media-bundle").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val pngId = UUID.randomUUID().toString()
            val gifId = UUID.randomUUID().toString()
            val png = File(root, "wardrobe_$pngId.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3))
            }
            val gif = File(root, "wardrobe_$gifId.gif").apply {
                writeBytes("GIF89a123456789".toByteArray(Charsets.US_ASCII))
            }
            val output = ByteArrayOutputStream()
            AvaWardrobeMediaBundleCodec.exportEncrypted(
                output, root,
                listOf(
                    AvaWardrobeMediaBundleCodec.ExportEntry(pngId, "Lilac look", "private", 100L, png),
                    AvaWardrobeMediaBundleCodec.ExportEntry(gifId, "Hair style", "hairstyle", 200L, gif)
                ),
                passphrase.copyOf(),
                createdAt = 300L
            )
            val encrypted = output.toByteArray()
            assertTrue(encrypted.size > 64)
            assertFalse(String(encrypted, Charsets.ISO_8859_1).contains(root.canonicalPath))

            val stagingParent = File(base, "staging").apply { mkdirs() }
            val preview = AvaWardrobeMediaBundleCodec.decryptToStaging(
                ByteArrayInputStream(encrypted), stagingParent, passphrase.copyOf(), validateStagedImage = { true }
            )
            assertEquals(2, preview.entries.size)
            assertEquals(26L, preview.totalBytes)
            assertTrue(preview.entries.all { it.stagedFile.isFile })
            assertTrue(preview.entries.any { it.mimeType == "image/png" })
            assertTrue(preview.entries.any { it.mimeType == "image/gif" })
            preview.stagingDirectory.deleteRecursively()
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun wrongPassphraseFailsAndRemovesStaging() {
        val base = Files.createTempDirectory("ava-media-wrong-pass").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val image = File(root, "wardrobe_$id.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            val output = ByteArrayOutputStream()
            AvaWardrobeMediaBundleCodec.exportEncrypted(
                output, root, listOf(AvaWardrobeMediaBundleCodec.ExportEntry(id, "test", "other", 1L, image)),
                passphrase.copyOf()
            )
            val stagingParent = File(base, "staging").apply { mkdirs() }
            var failed = false
            try {
                AvaWardrobeMediaBundleCodec.decryptToStaging(
                    ByteArrayInputStream(output.toByteArray()), stagingParent, "wrong password".toCharArray(), validateStagedImage = { true }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(0, stagingParent.listFiles()?.size ?: 0)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun tamperedAuthenticationTagIsRejected() {
        val base = Files.createTempDirectory("ava-media-tamper").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val image = File(root, "wardrobe_$id.gif").apply {
                writeBytes("GIF89a123456".toByteArray(Charsets.US_ASCII))
            }
            val output = ByteArrayOutputStream()
            AvaWardrobeMediaBundleCodec.exportEncrypted(
                output, root, listOf(AvaWardrobeMediaBundleCodec.ExportEntry(id, "test", "private", 1L, image)),
                passphrase.copyOf()
            )
            val damaged = output.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            val stagingParent = File(base, "staging").apply { mkdirs() }
            var failed = false
            try {
                AvaWardrobeMediaBundleCodec.decryptToStaging(
                    ByteArrayInputStream(damaged), stagingParent, passphrase.copyOf(), validateStagedImage = { true }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(0, stagingParent.listFiles()?.size ?: 0)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun truncatedBundleIsRejected() {
        val base = Files.createTempDirectory("ava-media-truncated").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val image = File(root, "wardrobe_$id.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            val output = ByteArrayOutputStream()
            AvaWardrobeMediaBundleCodec.exportEncrypted(
                output, root, listOf(AvaWardrobeMediaBundleCodec.ExportEntry(id, "test", "other", 1L, image)),
                passphrase.copyOf()
            )
            val truncated = output.toByteArray().copyOf(output.size() - 5)
            val stagingParent = File(base, "staging").apply { mkdirs() }
            var failed = false
            try {
                AvaWardrobeMediaBundleCodec.decryptToStaging(
                    ByteArrayInputStream(truncated), stagingParent, passphrase.copyOf(), validateStagedImage = { true }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(0, stagingParent.listFiles()?.size ?: 0)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun platformImageValidationFailureRemovesStaging() {
        val base = Files.createTempDirectory("ava-media-platform-validation").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val image = File(root, "wardrobe_$id.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            val output = ByteArrayOutputStream()
            AvaWardrobeMediaBundleCodec.exportEncrypted(
                output, root, listOf(AvaWardrobeMediaBundleCodec.ExportEntry(id, "test", "other", 1L, image)),
                passphrase.copyOf()
            )
            val stagingParent = File(base, "staging").apply { mkdirs() }
            var failed = false
            try {
                AvaWardrobeMediaBundleCodec.decryptToStaging(
                    ByteArrayInputStream(output.toByteArray()), stagingParent, passphrase.copyOf(),
                    validateStagedImage = { false }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(0, stagingParent.listFiles()?.size ?: 0)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun rejectsUnsafeExportPathAndDuplicateIds() {
        val base = Files.createTempDirectory("ava-media-reject").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val outside = File(base, "wardrobe_$id.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            var unsafeFailed = false
            try {
                AvaWardrobeMediaBundleCodec.exportEncrypted(
                    ByteArrayOutputStream(), root,
                    listOf(AvaWardrobeMediaBundleCodec.ExportEntry(id, "test", "other", 1L, outside)),
                    passphrase.copyOf()
                )
            } catch (_: Exception) { unsafeFailed = true }
            assertTrue(unsafeFailed)

            val safe = File(root, "wardrobe_$id.png").apply { writeBytes(outside.readBytes()) }
            var duplicateFailed = false
            try {
                AvaWardrobeMediaBundleCodec.exportEncrypted(
                    ByteArrayOutputStream(), root,
                    listOf(
                        AvaWardrobeMediaBundleCodec.ExportEntry(id, "one", "other", 1L, safe),
                        AvaWardrobeMediaBundleCodec.ExportEntry(id, "two", "other", 2L, safe)
                    ),
                    passphrase.copyOf()
                )
            } catch (_: Exception) { duplicateFailed = true }
            assertTrue(duplicateFailed)
        } finally {
            base.deleteRecursively()
        }
    }
}
