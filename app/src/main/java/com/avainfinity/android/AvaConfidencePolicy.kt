package com.avainfinity.android

enum class AvaConfidenceMode {
    DIRECT,
    PROBABLE_INTERPRETATION,
    CLARIFY,
    CORRECTION,
    CONFIRM_ACTION,
    FIGURATIVE
}

data class AvaConfidenceDecision(
    val mode: AvaConfidenceMode,
    val hasRecentAssistantContext: Boolean
)

/**
 * ACL-05 confidence handling without fabricated numeric confidence scores.
 * Heuristics are deliberately conservative and only flag narrow, consequential ambiguity.
 */
object AvaConfidencePolicy {
    fun assess(input: String, history: List<org.json.JSONObject>): AvaConfidenceDecision {
        val value = normalize(input)
        val recentAssistant = history.dropLast(1).lastOrNull()?.let {
            it.optString("role") == "assistant" && it.optString("content").isNotBlank()
        } ?: false

        if (containsAny(value, listOf(
                "اشتباه کردی", "اشتباه گفتی", "منظورم این نبود", "نه منظورم", "منظورم اینه",
                "اصلاح می کنم", "اصلاح میکنم", "you misunderstood", "that's not what i meant",
                "that is not what i meant", "i meant", "you got that wrong"
            ))
        ) return AvaConfidenceDecision(AvaConfidenceMode.CORRECTION, recentAssistant)

        if (containsAny(value, listOf(
                "شوخی کردم", "شوخی بود", "کنایه بود", "دارم شوخی می کنم",
                "just kidding", "jk", "sarcasm", "sarcastic"
            ))
        ) return AvaConfidenceDecision(AvaConfidenceMode.FIGURATIVE, recentAssistant)

        val ambiguousHighImpactAction = containsAny(value, listOf(
            "اون رو پاک کن", "اونو پاک کن", "همونو پاک کن", "حذفش کن", "پاکش کن",
            "بفرستش", "ارسالش کن", "تغییرش بده", "عوضش کن",
            "delete it", "delete that", "remove it", "send it", "send that", "change it", "change that", "remove that"
        ))
        if (ambiguousHighImpactAction) {
            return AvaConfidenceDecision(AvaConfidenceMode.CONFIRM_ACTION, recentAssistant)
        }

        val consequentialReference = containsAny(value, listOf(
            "اون رو انجام بده", "اونو انجام بده", "همونو انجام بده", "این رو انجام بده",
            "اون رو پاک کن", "اونو پاک کن", "همونو پاک کن", "حذفش کن", "پاکش کن",
            "بفرستش", "ارسالش کن", "تغییرش بده", "عوضش کن", "انتخابش کن", "همونو انتخاب کن",
            "do it", "delete it", "remove it", "send it", "change it", "choose that",
            "select that", "remove that"
        ))
        if (consequentialReference) {
            return AvaConfidenceDecision(
                if (recentAssistant) AvaConfidenceMode.PROBABLE_INTERPRETATION else AvaConfidenceMode.CLARIFY,
                recentAssistant
            )
        }

        return AvaConfidenceDecision(AvaConfidenceMode.DIRECT, recentAssistant)
    }

    fun providerGuidance(decision: AvaConfidenceDecision): String = when (decision.mode) {
        AvaConfidenceMode.DIRECT ->
            "When the request is clear, answer directly without unnecessary hedging. Do not invent certainty or evidence."
        AvaConfidenceMode.PROBABLE_INTERPRETATION ->
            "Use recent conversation context to identify the most likely referent. State the interpretation briefly as a tentative reading and invite correction before consequential or irreversible action."
        AvaConfidenceMode.CLARIFY ->
            "The request contains a consequential ambiguous reference with no clear recent antecedent. Ask one short clarifying question before acting; do not guess the target."
        AvaConfidenceMode.CORRECTION ->
            "The user is correcting a prior interpretation or answer. Acknowledge the correction, revisit the conversation context, and correct the answer plainly without defensiveness."
        AvaConfidenceMode.CONFIRM_ACTION ->
            "The user used an ambiguous reference for a potentially destructive, externally visible, or consequential action. Do not execute it, claim it was executed, or transmit anything. Ask which exact target they mean and require explicit confirmation of the target and action before proceeding."
        AvaConfidenceMode.FIGURATIVE ->
            "The message may contain humor, irony, or figurative language. Consider conversational context and avoid taking it hastily or literally; ask one brief clarification if meaning materially changes the answer."
    }

    fun localReply(language: String, decision: AvaConfidenceDecision): String? = when (decision.mode) {
        AvaConfidenceMode.DIRECT -> null
        AvaConfidenceMode.PROBABLE_INTERPRETATION -> if (language == "en") {
            "I think you're referring to the recent topic. If I picked the wrong one, briefly correct me before I proceed."
        } else {
            "برداشتم اینه که به موضوع قبلی اشاره می‌کنی. اگر مورد دیگه‌ای منظورت هست، قبل از ادامه کوتاه اصلاحم کن."
        }
        AvaConfidenceMode.CLARIFY -> if (language == "en") {
            "Which exact item or action do you mean? I should clarify the target before proceeding."
        } else {
            "منظورت دقیقاً کدوم مورد یا کاره؟ قبل از ادامه باید هدف رو روشن کنیم."
        }
        AvaConfidenceMode.CORRECTION -> if (language == "en") {
            "Thanks for correcting me. I'll revise the previous answer using your clarification."
        } else {
            "ممنون که اصلاحم کردی. برداشت قبلی رو بازبینی می‌کنم و پاسخ رو بر اساس توضیح تازه‌ات اصلاح می‌کنم."
        }
        AvaConfidenceMode.CONFIRM_ACTION -> if (language == "en") {
            "Before any change or sending, which exact item do you mean? Please confirm the target and the action explicitly."
        } else {
            "قبل از هر حذف، تغییر یا ارسالی باید دقیق روشن بشه منظورت کدوم مورده. لطفاً هدف و کاری رو که می‌خوای انجام بشه صریح تأیید کن."
        }
        AvaConfidenceMode.FIGURATIVE -> if (language == "en") {
            "That may be humor or irony, and I don't want to take it literally. Did I understand your meaning correctly?"
        } else {
            "ممکنه شوخی یا کنایه باشه و نمی‌خوام لفظی برداشت کنم. منظورت رو درست گرفتم؟"
        }
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
