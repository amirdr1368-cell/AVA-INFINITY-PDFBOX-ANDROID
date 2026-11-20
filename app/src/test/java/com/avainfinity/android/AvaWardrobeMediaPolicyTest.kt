package com.avainfinity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaWardrobeMediaPolicyTest {
    @Test fun allowsOnlyAnApprovedLevelOneOwnerSession() {
        assertTrue(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 1, "owner"))
    }

    @Test fun rejectsMissingOrUnapprovedSessions() {
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, false, true, 1, "owner"))
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, false, 1, "owner"))
    }

    @Test fun rejectsPublicOnlyModeEvenForOwner() {
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(true, true, true, 1, "owner"))
    }

    @Test fun rejectsOtherIdentitiesAndPrivilegeLevels() {
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 2, "owner"))
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 1, "person-amir"))
        assertFalse(AvaWardrobeMediaPolicy.canAccessOwnerMedia(false, true, true, 4, "guest-123"))
    }

    @Test fun acceptsOnlyGeneratedBoundedFilesInsideCanonicalWardrobeDirectory() {
        val base = java.nio.file.Files.createTempDirectory("ava-wardrobe-test").toFile()
        try {
            val root = java.io.File(base, "ava-wardrobe").apply { mkdirs() }
            val valid = java.io.File(root, "wardrobe_123e4567-e89b-42d3-a456-426614174000.jpg")
                .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertTrue(AvaWardrobeMediaPolicy.isSafeImageFile(root, valid))

            val wrongName = java.io.File(root, "photo.jpg").apply { writeBytes(byteArrayOf(1)) }
            assertFalse(AvaWardrobeMediaPolicy.isSafeImageFile(root, wrongName))
            
            val malformedUuid = java.io.File(root, "wardrobe_------------------------------------.jpg")
                .apply { writeBytes(byteArrayOf(1)) }
            assertFalse(AvaWardrobeMediaPolicy.isSafeImageFile(root, malformedUuid))

            val outside = java.io.File(base, "wardrobe_123e4567-e89b-12d3-a456-426614174001.jpg")
                .apply { writeBytes(byteArrayOf(1)) }
            assertFalse(AvaWardrobeMediaPolicy.isSafeImageFile(root, outside))

            val sibling = java.io.File(base, "ava-wardrobe-backup").apply { mkdirs() }
            val siblingFile = java.io.File(sibling, "wardrobe_123e4567-e89b-12d3-a456-426614174002.jpg")
                .apply { writeBytes(byteArrayOf(1)) }
            assertFalse(AvaWardrobeMediaPolicy.isSafeImageFile(root, siblingFile))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun rejectsOversizedAndMissingFiles() {
        val base = java.nio.file.Files.createTempDirectory("ava-wardrobe-size-test").toFile()
        try {
            val root = java.io.File(base, "ava-wardrobe").apply { mkdirs() }
            val oversized = java.io.File(root, "wardrobe_123e4567-e89b-12d3-a456-426614174003.png")
            java.io.RandomAccessFile(oversized, "rw").use {
                it.setLength(15L * 1024L * 1024L + 1L)
            }
            assertFalse(AvaWardrobeMediaPolicy.isSafeImageFile(root, oversized))
            val missing = java.io.File(root, "wardrobe_123e4567-e89b-12d3-a456-426614174004.png")
            assertFalse(AvaWardrobeMediaPolicy.isSafeImageFile(root, missing))
        } finally {
            base.deleteRecursively()
        }
    }
}
