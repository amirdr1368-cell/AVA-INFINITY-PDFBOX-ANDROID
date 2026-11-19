package com.avainfinity.android

enum class AvaSupportMode {
    NEUTRAL,
    LISTEN,
    EMPATHY,
    SUGGESTIONS,
    LIGHTEN,
    SPACE,
    CLARIFY
}

enum class AvaSignalConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class AvaSupportDecision(
    val mode: AvaSupportMode,
    val confidence: AvaSignalConfidence,
    val explicitRequest: Boolean
)

/**
 * Stateless ACL-02/ACL-03 communication policy.
 * Emotional signals are evaluated for the current turn only and are never persisted.
 */
object AvaCommunicationPolicy {
    fun assess(input: String, language: String): AvaSupportDecision {
        val value = normalize(input)
        if (value.isBlank()) {
            return AvaSupportDecision(AvaSupportMode.NEUTRAL, AvaSignalConfidence.HIGH, false)
        }

        val english = language.lowercase().startsWith("en")
        if (containsAny(value, if (english) {
                listOf("leave me alone", "i need space", "need some space", "don't want to talk", "do not want to talk", "stop talking")
            } else {
                listOf("بهم فضا بده", "به من فضا بده", "الان نمیخوام حرف بزنم", "فعلا نمیخوام حرف بزنم", "دیگه حرف نزن", "تنها باشم")
            })
        ) return AvaSupportDecision(AvaSupportMode.SPACE, AvaSignalConfidence.HIGH, true)

        if (containsAny(value, if (english) {
                listOf("just listen", "listen to me", "no advice", "don't give advice", "do not give advice")
            } else {
                listOf("فقط گوش بده", "فقط شنونده باش", "فقط میخوام حرف بزنم", "راهکار نده", "نصیحت نکن", "فعلا فقط گوش کن")
            })
        ) return AvaSupportDecision(AvaSupportMode.LISTEN, AvaSignalConfidence.HIGH, true)

        if (containsAny(value, if (english) {
                listOf("what should i do", "give me advice", "suggest something", "help me figure out", "give me a solution")
            } else {
                listOf("چی کار کنم", "چیکار کنم", "چه کار کنم", "راهکار بده", "پیشنهاد بده", "کمکم کن راه")
            })
        ) return AvaSupportDecision(AvaSupportMode.SUGGESTIONS, AvaSignalConfidence.HIGH, true)

        if (containsAny(value, if (english) {
                listOf("make me laugh", "tell me a joke", "cheer me up", "lighten the mood")
            } else {
                listOf("یه جوک", "جوک بگو", "بخندون", "حالمو عوض کن", "یه چیزی بگو بخندم")
            })
        ) return AvaSupportDecision(AvaSupportMode.LIGHTEN, AvaSignalConfidence.HIGH, true)

        if (containsAny(value, if (english) {
                listOf("sad", "lonely", "heartbroken", "overwhelmed", "i'm tired", "i am tired", "anxious", "feeling down", "not okay", "not okay today")
            } else {
                listOf("دلم گرفته", "حالم بده", "ناراحتم", "غمگینم", "تنها هستم", "احساس تنهایی", "دلتنگم", "خسته ام", "خستم", "فشار زیادی", "نگرانم", "اضطراب دارم")
            })
        ) return AvaSupportDecision(AvaSupportMode.EMPATHY, AvaSignalConfidence.MEDIUM, false)

        if (containsAny(value, if (english) {
                listOf("help me", "i don't know", "i do not know", "i feel", "i need help")
            } else {
                listOf("کمک میخوام", "کمکم کن", "نمیدونم", "نمی دونم", "نمی‌دونم", "حالم")
            })
        ) return AvaSupportDecision(AvaSupportMode.CLARIFY, AvaSignalConfidence.LOW, false)

        return AvaSupportDecision(AvaSupportMode.NEUTRAL, AvaSignalConfidence.HIGH, false)
    }

    fun toneGuidance(tone: String, responseLength: String, useEmojis: Boolean): String {
        val toneInstruction = when (tone) {
            "آرام و متفکر" -> "Use a calm, thoughtful and unhurried tone."
            "پرنشاط و سرزنده" -> "Use a lively and positive tone only when the subject is light; stay calm and serious for distress or sensitive topics."
            "رسمی و دقیق" -> "Use a respectful, precise and moderately formal tone."
            else -> "Use a warm, friendly and balanced tone."
        }
        val lengthInstruction = when (responseLength) {
            "کوتاه" -> "Prefer concise replies."
            "مفصل" -> "Give a fuller explanation when it adds value, without repeating yourself."
            else -> "Use a balanced response length."
        }
        val emojiInstruction = if (useEmojis) {
            "Use emojis sparingly when they fit the context. Avoid them in serious or sensitive moments."
        } else {
            "Avoid emojis."
        }
        return "$toneInstruction $lengthInstruction $emojiInstruction"
    }

    fun providerGuidance(decision: AvaSupportDecision): String = when (decision.mode) {
        AvaSupportMode.LISTEN ->
            "The user explicitly asked to be heard. Acknowledge briefly, listen, and do not offer advice unless requested."
        AvaSupportMode.EMPATHY ->
            "The message may indicate emotional distress. Acknowledge gently without claiming certainty; ask whether the user prefers listening or practical suggestions."
        AvaSupportMode.SUGGESTIONS ->
            "The user explicitly requested suggestions. Offer one or two small, practical and relevant next steps; ask one concise question only if essential context is missing."
        AvaSupportMode.LIGHTEN ->
            "The user explicitly invited lightening the mood. A little respectful humor is welcome unless the message contains an urgent or serious safety concern."
        AvaSupportMode.SPACE ->
            "The user explicitly requested space. Respond briefly, do not ask a follow-up question, and do not pressure them to continue."
        AvaSupportMode.CLARIFY ->
            "The user's support preference is unclear. Ask one brief question about whether they want listening or practical suggestions; do not assume an emotional state."
        AvaSupportMode.NEUTRAL ->
            "Do not infer an emotional state when the message does not support one. Respond to the actual request."
    }

    private fun normalize(input: String): String = input.lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(value: String, phrases: List<String>): Boolean =
        phrases.any { value.contains(normalize(it)) }
}
