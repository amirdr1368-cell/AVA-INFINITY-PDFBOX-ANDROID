package com.avainfinity.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaConfidencePolicyTest {
    private fun user(text: String) = JSONObject().put("role", "user").put("content", text)
    private fun assistant(text: String) = JSONObject().put("role", "assistant").put("content", text)

    @Test fun clearRequestStaysDirect() {
        val result = AvaConfidencePolicy.assess("فردا هوا چطوره؟", listOf(user("فردا هوا چطوره؟")))
        assertEquals(AvaConfidenceMode.DIRECT, result.mode)
    }

    @Test fun consequentialAmbiguityWithoutContextRequiresClarification() {
        val history = listOf(user("اون رو انجام بده"))
        val result = AvaConfidencePolicy.assess("اون رو انجام بده", history)
        assertEquals(AvaConfidenceMode.CLARIFY, result.mode)
    }

    @Test fun ambiguousReferenceWithRecentAssistantContextIsTentative() {
        val history = listOf(user("این گزینه‌ها چی هستن؟"), assistant("گزینه اول..."), user("همونو انتخاب کن"))
        val result = AvaConfidencePolicy.assess("همونو انتخاب کن", history)
        assertEquals(AvaConfidenceMode.PROBABLE_INTERPRETATION, result.mode)
        assertTrue(result.hasRecentAssistantContext)
    }

    @Test fun staleAssistantMessageDoesNotCountAsImmediateContext() {
        val history = listOf(
            user("گزینه‌ها را بررسی کن"),
            assistant("گزینه اول"),
            user("موضوع دیگری"),
            user("همونو انتخاب کن")
        )
        val result = AvaConfidencePolicy.assess("همونو انتخاب کن", history)
        assertEquals(AvaConfidenceMode.CLARIFY, result.mode)
        assertTrue(!result.hasRecentAssistantContext)
    }

    @Test fun ambiguousDeleteAlwaysRequiresExplicitTargetConfirmation() {
        val history = listOf(
            user("کدام فایل؟"),
            assistant("فایل پشتیبان قدیمی"),
            user("همونو پاک کن")
        )
        val result = AvaConfidencePolicy.assess("همونو پاک کن", history)
        assertEquals(AvaConfidenceMode.CONFIRM_ACTION, result.mode)
        assertTrue(AvaConfidencePolicy.providerGuidance(result).contains("Do not execute it"))
    }

    @Test fun explicitCorrectionTakesPriority() {
        val result = AvaConfidencePolicy.assess("نه، اشتباه کردی، منظورم این نبود", listOf(user("نه، اشتباه کردی")))
        assertEquals(AvaConfidenceMode.CORRECTION, result.mode)
    }

    @Test fun humorAndIronyAreNotTakenLiterallyByDefault() {
        val result = AvaConfidencePolicy.assess("شوخی کردم 😂", listOf(user("شوخی کردم 😂")))
        assertEquals(AvaConfidenceMode.FIGURATIVE, result.mode)
    }

    @Test fun laughterMarkerAloneDoesNotForceFigurativeInterpretation() {
        val result = AvaConfidencePolicy.assess("حالم بده 😂", listOf(user("حالم بده 😂")))
        assertEquals(AvaConfidenceMode.DIRECT, result.mode)
    }

    @Test fun policyNeverUsesNumericConfidencePercentages() {
        val guidance = AvaConfidencePolicy.providerGuidance(
            AvaConfidenceDecision(AvaConfidenceMode.PROBABLE_INTERPRETATION, true)
        )
        assertTrue(guidance.contains("tentative"))
        assertTrue(guidance.contains("before consequential"))
    }
}
