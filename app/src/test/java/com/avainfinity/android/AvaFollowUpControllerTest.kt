package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaFollowUpControllerTest {
    @Test fun deferralStoresOnlyInMemoryAndOffersOneInConversationSuggestion() {
        val controller = AvaFollowUpController()
        assertNull(controller.onUserMessage(
            "بعداً ادامه بدیم", "می‌خواستم درباره برنامه سفر صحبت کنیم", true, false
        ))
        assertTrue(controller.hasPendingTopic())
        val suggestion = controller.takeSuggestionForChatOpen()
        assertTrue(suggestion.orEmpty().contains("برنامه سفر"))
        assertNull(controller.takeSuggestionForChatOpen())
    }

    @Test fun explicitContinuationCanTriggerTheSuggestionOnce() {
        val controller = AvaFollowUpController()
        controller.onUserMessage("بعداً ادامه بدیم", "موضوع قبلی", true, false)
        val suggestion = controller.onUserMessage("حالا ادامه بدیم", null, true, false)
        assertTrue(suggestion.orEmpty().contains("موضوع قبلی"))
        assertNull(controller.onUserMessage("ادامه بدیم", null, true, false))
    }

    @Test fun userDeclineClearsPendingTopicAndStopsFurtherSuggestions() {
        val controller = AvaFollowUpController()
        controller.onUserMessage("بعداً ادامه بدیم", "موضوع قبلی", true, false)
        assertNull(controller.onUserMessage("نه فعلاً، نمی‌خوام", null, true, false))
        assertFalse(controller.hasPendingTopic())
        assertNull(controller.takeSuggestionForChatOpen())
    }

    @Test fun completionClearsPendingTopic() {
        val controller = AvaFollowUpController()
        controller.onUserMessage("بعداً ادامه بدیم", "موضوع قبلی", true, false)
        controller.onUserMessage("حل شد", null, true, false)
        assertFalse(controller.hasPendingTopic())
    }

    @Test fun nonOwnerAndPublicOnlyContextsClearExistingTopics() {
        val controller = AvaFollowUpController()
        controller.onUserMessage("بعداً ادامه بدیم", "موضوع خصوصی", true, false)
        assertTrue(controller.hasPendingTopic())
        controller.onUserMessage("سلام", null, false, false)
        assertFalse(controller.hasPendingTopic())
        controller.onUserMessage("بعداً ادامه بدیم", "موضوع خصوصی", true, false)
        controller.onUserMessage("سلام", null, true, true)
        assertFalse(controller.hasPendingTopic())
    }

    @Test fun missingPreviousTopicDoesNotCreateEmptyReminder() {
        val controller = AvaFollowUpController()
        controller.onUserMessage("بعداً ادامه بدیم", null, true, false)
        assertFalse(controller.hasPendingTopic())
        assertNull(controller.takeSuggestionForChatOpen())
    }
}
