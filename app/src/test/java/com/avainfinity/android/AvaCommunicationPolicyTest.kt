package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaCommunicationPolicyTest {
    @Test fun explicitSpaceRequestTakesPriority() {
        val decision = AvaCommunicationPolicy.assess("حالم بده، الان بهم فضا بده", "fa")
        assertEquals(AvaSupportMode.SPACE, decision.mode)
        assertTrue(decision.explicitRequest)
        assertEquals(AvaSignalConfidence.HIGH, decision.confidence)
    }

    @Test fun explicitListeningRequestDoesNotTriggerAdvice() {
        val decision = AvaCommunicationPolicy.assess("فقط گوش بده، نصیحت نکن", "fa")
        assertEquals(AvaSupportMode.LISTEN, decision.mode)
        assertTrue(decision.explicitRequest)
        assertTrue(AvaCommunicationPolicy.providerGuidance(decision).contains("do not offer advice"))
    }

    @Test fun explicitRequestsAreRecognizedInEnglish() {
        assertEquals(AvaSupportMode.SUGGESTIONS, AvaCommunicationPolicy.assess("What should I do?", "en").mode)
        assertEquals(AvaSupportMode.LIGHTEN, AvaCommunicationPolicy.assess("Tell me a joke", "en").mode)
        assertEquals(AvaSupportMode.SPACE, AvaCommunicationPolicy.assess("I need some space", "en").mode)
    }

    @Test fun inferredDistressIsTentativeAndNotExplicit() {
        val decision = AvaCommunicationPolicy.assess("دلم گرفته و دلتنگم", "fa")
        assertEquals(AvaSupportMode.EMPATHY, decision.mode)
        assertEquals(AvaSignalConfidence.MEDIUM, decision.confidence)
        assertFalse(decision.explicitRequest)
    }

    @Test fun vagueRequestsAskForClarificationInsteadOfAssuming() {
        val decision = AvaCommunicationPolicy.assess("نمی‌دونم، کمکم کن", "fa")
        assertEquals(AvaSupportMode.CLARIFY, decision.mode)
        assertEquals(AvaSignalConfidence.LOW, decision.confidence)
    }

    @Test fun neutralMessagesDoNotReceiveAnEmotionalLabel() {
        val decision = AvaCommunicationPolicy.assess("فردا ساعت چند جلسه داریم؟", "fa")
        assertEquals(AvaSupportMode.NEUTRAL, decision.mode)
        assertFalse(decision.explicitRequest)
    }

    @Test fun toneGuidanceKeepsSeriousTopicsGrounded() {
        val guidance = AvaCommunicationPolicy.toneGuidance("پرنشاط و سرزنده", "کوتاه", true)
        assertTrue(guidance.contains("stay calm and serious"))
        assertTrue(guidance.contains("Prefer concise replies"))
        assertTrue(guidance.contains("Avoid them in serious"))
    }
}
