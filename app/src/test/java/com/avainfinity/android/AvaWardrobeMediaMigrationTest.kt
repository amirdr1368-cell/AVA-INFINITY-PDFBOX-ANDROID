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

class AvaWardrobeMediaMigrationTest {
    @Test fun enrichesLegacyRecordWithoutRemovingExistingFields() {
        val base = Files.createTempDirectory("ava-wardrobe-migrate").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
            val file = File(root, "wardrobe_123e4567-e89b-42d3-a456-426614174000.png").apply { writeBytes(bytes) }
            val legacy = JSONObject()
                .put("name", "لباس قدیمی")
                .put("category", "private")
                .put("categoryLabel", "مجموعه خصوصی")
                .put("file", file.canonicalPath)
                .put("addedAt", 123L)
            val outcome = AvaWardrobeMediaMigration.migrateLegacyIndex(JSONArray().put(legacy).toString(), root)
            assertTrue(outcome.changed)
            assertEquals(1, outcome.migratedRecords)
            val migrated = JSONArray(outcome.json).getJSONObject(0)
            assertEquals("لباس قدیمی", migrated.getString("name"))
            assertEquals("private", migrated.getString("category"))
            assertEquals("مجموعه خصوصی", migrated.getString("categoryLabel"))
            assertEquals(123L, migrated.getLong("addedAt"))
            assertEquals("123e4567-e89b-42d3-a456-426614174000", migrated.getString("itemId"))
            assertEquals("image/png", migrated.getString("mimeType"))
            assertEquals(bytes.size.toLong(), migrated.getLong("byteLength"))
            val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
            assertEquals(expected, migrated.getString("sha256"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun migrationIsIdempotent() {
        val base = Files.createTempDirectory("ava-wardrobe-idempotent").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val file = File(root, "wardrobe_123e4567-e89b-42d3-a456-426614174000.gif")
                .apply { writeBytes("GIF89a123456".toByteArray(Charsets.US_ASCII)) }
            val first = AvaWardrobeMediaMigration.migrateLegacyIndex(
                JSONArray().put(JSONObject().put("file", file.canonicalPath).put("name", "old")).toString(), root
            )
            val second = AvaWardrobeMediaMigration.migrateLegacyIndex(first.json, root)
            assertTrue(first.changed)
            assertEquals(1, first.migratedRecords)
            assertFalse(second.changed)
            assertEquals(0, second.migratedRecords)
            assertEquals(first.json, second.json)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun leavesUnsafeOrMissingLegacyRecordsUntouched() {
        val base = Files.createTempDirectory("ava-wardrobe-unsafe").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val outside = File(base, "wardrobe_123e4567-e89b-42d3-a456-426614174000.png")
                .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val legacy = JSONArray()
                .put(JSONObject().put("name", "outside").put("file", outside.canonicalPath))
                .put(JSONObject().put("name", "missing").put("file", File(root, "missing.png").absolutePath))
            val outcome = AvaWardrobeMediaMigration.migrateLegacyIndex(legacy.toString(), root)
            assertEquals(0, outcome.migratedRecords)
            assertEquals("outside", JSONArray(outcome.json).getJSONObject(0).getString("name"))
            assertEquals("missing", JSONArray(outcome.json).getJSONObject(1).getString("name"))
            assertFalse(JSONArray(outcome.json).getJSONObject(0).has("sha256"))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun rejectsImageSignatureThatDoesNotMatchFilenameExtension() {
        val base = Files.createTempDirectory("ava-wardrobe-extension").toFile()
        try {
            val root = File(base, "ava-wardrobe").apply { mkdirs() }
            val file = File(root, "wardrobe_123e4567-e89b-42d3-a456-426614174000.jpg")
                .apply { writeBytes("GIF89a123456".toByteArray(Charsets.US_ASCII)) }
            val outcome = AvaWardrobeMediaMigration.migrateLegacyIndex(
                JSONArray().put(JSONObject().put("file", file.canonicalPath)).toString(), root
            )
            assertEquals(0, outcome.migratedRecords)
            assertFalse(JSONArray(outcome.json).getJSONObject(0).has("sha256"))
        } finally {
            base.deleteRecursively()
        }
    }
}
