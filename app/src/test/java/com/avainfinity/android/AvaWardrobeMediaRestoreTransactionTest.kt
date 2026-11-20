package com.avainfinity.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

class AvaWardrobeMediaRestoreTransactionTest {
    private data class Fixture(
        val base: File,
        val root: File,
        val journal: File,
        val preview: AvaWardrobeMediaBundleCodec.StagedPreview,
        val digest: String
    )

    private fun fixture(): Fixture {
        val base = Files.createTempDirectory("ava-restore-tx").toFile()
        val root = File(base, "ava-wardrobe").apply { mkdirs() }
        val journal = File(base, "files").apply { mkdirs() }
        val stage = File(base, "preview").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val fileName = "wardrobe_$id.png"
        val staged = File(stage, fileName).apply { writeBytes(bytes) }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val entry = AvaWardrobeMediaBundleCodec.PreviewEntry(
            id, "Imported look", "private", "image/png", bytes.size.toLong(), digest, 123L, fileName, staged
        )
        return Fixture(
            base, root, journal,
            AvaWardrobeMediaBundleCodec.StagedPreview(UUID.randomUUID().toString(), 456L, listOf(entry), bytes.size.toLong(), stage),
            digest
        )
    }

    @Test fun additiveRestoreCommitsIndexAndPreservesExistingRecords() {
        val f = fixture()
        try {
            val original = JSONArray().put(
                JSONObject()
                    .put("itemId", UUID.randomUUID().toString())
                    .put("sha256", "a".repeat(64))
                    .put("name", "Existing")
            ).toString()
            var index = original
            val result = AvaWardrobeMediaRestoreTransaction.apply(
                f.root, f.journal, f.preview, original,
                readIndex = { index }, writeIndex = { index = it; true },
                isOwnerAuthorized = { true }, audit = { _, _ -> true }
            )
            assertEquals(1, result.added)
            assertEquals(2, JSONArray(index).length())
            assertTrue(File(f.root, f.preview.entries.single().fileName).isFile)
            assertFalse(File(f.journal, "ava-wardrobe-media-restore-journal.json").exists())
        } finally { f.base.deleteRecursively() }
    }

    @Test fun failedIndexWriteRollsBackOnlyNewlyPromotedFile() {
        val f = fixture()
        try {
            val original = "[]"
            var index = original
            var failed = false
            try {
                AvaWardrobeMediaRestoreTransaction.apply(
                    f.root, f.journal, f.preview, original,
                    readIndex = { index }, writeIndex = { false },
                    isOwnerAuthorized = { true }, audit = { _, _ -> true }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(original, index)
            assertFalse(File(f.root, f.preview.entries.single().fileName).exists())
            assertFalse(File(f.journal, "ava-wardrobe-media-restore-journal.json").exists())
        } finally { f.base.deleteRecursively() }
    }

    @Test fun sameIdDifferentDigestIsRejectedWithoutChangingCurrentFiles() {
        val f = fixture()
        try {
            val entry = f.preview.entries.single()
            val existing = File(f.root, entry.fileName).apply { writeBytes("GIF89a123456".toByteArray()) }
            val record = JSONObject()
                .put("itemId", entry.itemId)
                .put("sha256", "0".repeat(64))
                .put("file", existing.absolutePath)
            val original = JSONArray().put(record).toString()
            var index = original
            var failed = false
            try {
                AvaWardrobeMediaRestoreTransaction.apply(
                    f.root, f.journal, f.preview, original,
                    readIndex = { index }, writeIndex = { index = it; true },
                    isOwnerAuthorized = { true }, audit = { _, _ -> true }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(original, index)
            assertTrue(existing.isFile)
        } finally { f.base.deleteRecursively() }
    }

    @Test fun staleIndexAbortsBeforeJournalOrLivePromotion() {
        val f = fixture()
        try {
            val newerIndex = """[{"itemId":"${UUID.randomUUID()}","sha256":"${"b".repeat(64)}"}]"""
            var writes = 0
            var failed = false
            try {
                AvaWardrobeMediaRestoreTransaction.apply(
                    f.root, f.journal, f.preview, "[]",
                    readIndex = { newerIndex },
                    writeIndex = { writes++; true },
                    isOwnerAuthorized = { true }, audit = { _, _ -> true }
                )
            } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(0, writes)
            assertFalse(File(f.root, f.preview.entries.single().fileName).exists())
            assertFalse(File(f.journal, "ava-wardrobe-media-restore-journal.json").exists())
        } finally { f.base.deleteRecursively() }
    }

    @Test fun recoveryRollsBackOnlyFilesMatchingJournalDigestWhenOldIndexIsLive() {
        val f = fixture()
        try {
            val entry = f.preview.entries.single()
            val promoted = File(f.root, entry.fileName).apply { writeBytes(entry.stagedFile.readBytes()) }
            val oldDigest = MessageDigest.getInstance("SHA-256")
                .digest("[]".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val journalFile = File(f.journal, "ava-wardrobe-media-restore-journal.json")
            JSONObject()
                .put("version", 1)
                .put("transactionId", UUID.randomUUID().toString())
                .put("oldIndexSha256", oldDigest)
                .put("nextIndexSha256", "d".repeat(64))
                .put("stageDirectory", "ava-wardrobe-restore-${UUID.randomUUID()}.stage")
                .put("promotedFiles", JSONArray().put(
                    JSONObject().put("name", entry.fileName).put("sha256", entry.sha256)
                ))
                .also { journalFile.writeText(it.toString()) }
            assertTrue(AvaWardrobeMediaRestoreTransaction.recover(
                f.root, f.journal, readIndex = { "[]" },
                isOwnerAuthorized = { true }, audit = { _, _ -> true }
            ))
            assertFalse(promoted.exists())
            assertFalse(journalFile.exists())
        } finally { f.base.deleteRecursively() }
    }

    @Test fun recoveryRefusesToDeleteFileWithUnexpectedDigest() {
        val f = fixture()
        try {
            val entry = f.preview.entries.single()
            val collision = File(f.root, entry.fileName).apply { writeBytes("unrelated file".toByteArray()) }
            val journalFile = File(f.journal, "ava-wardrobe-media-restore-journal.json")
            val oldDigest = MessageDigest.getInstance("SHA-256")
                .digest("[]".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            JSONObject()
                .put("version", 1)
                .put("transactionId", UUID.randomUUID().toString())
                .put("oldIndexSha256", oldDigest)
                .put("nextIndexSha256", "c".repeat(64))
                .put("stageDirectory", "ava-wardrobe-restore-${UUID.randomUUID()}.stage")
                .put("promotedFiles", JSONArray().put(
                    JSONObject().put("name", entry.fileName).put("sha256", entry.sha256)
                ))
                .also { journalFile.writeText(it.toString()) }
            val recovered = AvaWardrobeMediaRestoreTransaction.recover(
                f.root, f.journal, readIndex = { "[]" },
                isOwnerAuthorized = { true }, audit = { _, _ -> true }
            )
            assertFalse(recovered)
            assertTrue(collision.isFile)
            assertTrue(journalFile.isFile)
        } finally { f.base.deleteRecursively() }
    }

    @Test fun identicalExistingItemIsARealNoOpWithoutIndexWriteOrJournal() {
        val f = fixture()
        try {
            val entry = f.preview.entries.single()
            val existingFile = File(f.root, entry.fileName).apply {
                writeBytes(entry.stagedFile.readBytes())
            }
            val original = JSONArray().put(
                JSONObject()
                    .put("itemId", entry.itemId)
                    .put("sha256", entry.sha256)
                    .put("file", existingFile.absolutePath)
            ).toString()
            var index = original
            var writes = 0
            val result = AvaWardrobeMediaRestoreTransaction.apply(
                f.root, f.journal, f.preview, original,
                readIndex = { index },
                writeIndex = { writes++; index = it; true },
                isOwnerAuthorized = { true }, audit = { _, _ -> true }
            )
            assertEquals(0, result.added)
            assertEquals(1, result.unchanged)
            assertEquals(0, writes)
            assertEquals(original, index)
            assertFalse(File(f.journal, "ava-wardrobe-media-restore-journal.json").exists())
        } finally { f.base.deleteRecursively() }
    }

    @Test fun recoveryFinishesCleanupWhenIndexCommitSucceededBeforeProcessExit() {
        val f = fixture()
        try {
            var index = "[]"
            var commitAuditFailed = false
            try {
                AvaWardrobeMediaRestoreTransaction.apply(
                    f.root, f.journal, f.preview, index,
                    readIndex = { index }, writeIndex = { index = it; true },
                    isOwnerAuthorized = { true },
                    audit = { event, _ ->
                        if (event == "WARDROBE_MEDIA_RESTORE_COMMITTED" && !commitAuditFailed) {
                            commitAuditFailed = true
                            false
                        } else true
                    }
                )
            } catch (_: Exception) { }
            assertTrue(commitAuditFailed)
            assertTrue(File(f.journal, "ava-wardrobe-media-restore-journal.json").isFile)
            val auditBlocked = AvaWardrobeMediaRestoreTransaction.recover(
                f.root, f.journal, readIndex = { index },
                isOwnerAuthorized = { true },
                audit = { event, _ -> event != "WARDROBE_MEDIA_RESTORE_RECOVERY_COMPLETED" }
            )
            assertFalse(auditBlocked)
            assertTrue(File(f.journal, "ava-wardrobe-media-restore-journal.json").isFile)
            assertTrue(AvaWardrobeMediaRestoreTransaction.recover(
                f.root, f.journal, readIndex = { index },
                isOwnerAuthorized = { true }, audit = { _, _ -> true }
            ))
            assertTrue(File(f.root, f.preview.entries.single().fileName).isFile)
            assertFalse(File(f.journal, "ava-wardrobe-media-restore-journal.json").exists())
        } finally { f.base.deleteRecursively() }
    }
}
