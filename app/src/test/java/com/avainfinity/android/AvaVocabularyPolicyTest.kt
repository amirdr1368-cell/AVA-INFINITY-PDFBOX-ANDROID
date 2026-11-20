package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaVocabularyPolicyTest {
    @Test fun acceptsLanguageTagsAndRejectsMalformedCodes() {
        assertTrue(AvaVocabularyPolicy.isValidLanguageCode("fa"))
        assertTrue(AvaVocabularyPolicy.isValidLanguageCode("en-US"))
        assertFalse(AvaVocabularyPolicy.isValidLanguageCode("english"))
        assertFalse(AvaVocabularyPolicy.isValidLanguageCode("fa/../../"))
    }

    @Test fun publicOnlyModeAlwaysBlocksVocabularyManagement() {
        assertFalse(AvaVocabularyPolicy.canManage(true, false, 1, "owner", false))
    }

    @Test fun onlyOwnerContextCanManageVocabularyDuringASession() {
        assertTrue(AvaVocabularyPolicy.canManage(false, false, 4, null, false))
        assertTrue(AvaVocabularyPolicy.canManage(false, true, 1, "owner", true))
        assertFalse(AvaVocabularyPolicy.canManage(false, true, 1, "owner", false))
        assertFalse(AvaVocabularyPolicy.canManage(false, true, 3, "person-arash-naderi", true))
        assertFalse(AvaVocabularyPolicy.canManage(false, true, 4, "guest-123", true))
    }

    @Test fun onlyDraftsAndLegacyReviewEntriesCanBePublished() {
        assertTrue(AvaVocabularyPolicy.canPublish("draft", "word", "meaning"))
        assertTrue(AvaVocabularyPolicy.canPublish("legacy_review_required", "word", "meaning"))
        assertFalse(AvaVocabularyPolicy.canPublish("approved", "word", "meaning"))
        assertFalse(AvaVocabularyPolicy.canPublish("archived", "word", "meaning"))
        assertFalse(AvaVocabularyPolicy.canPublish("draft", "", "meaning"))
    }

    @Test fun duplicateDetectionNormalizesCaseAndWhitespace() {
        assertTrue(AvaVocabularyPolicy.hasTermConflict(listOf("Hello World"), " hello   world "))
        assertFalse(AvaVocabularyPolicy.hasTermConflict(listOf("Hello"), "Hello there"))
        assertFalse(AvaVocabularyPolicy.hasTermConflict(listOf("Hello"), " "))
    }

    @Test fun versionNumbersAlwaysIncrease() {
        assertEquals(2, AvaVocabularyPolicy.nextVersion(1))
        assertEquals(2, AvaVocabularyPolicy.nextVersion(0))
        assertEquals(8, AvaVocabularyPolicy.nextVersion(7))
    }
}
