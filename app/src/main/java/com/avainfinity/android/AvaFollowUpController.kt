package com.avainfinity.android

/**
 * ACL-06 in-conversation follow-up state.
 * This controller is intentionally memory-only: no disk, alarm, notification, or WorkManager API.
 */
class AvaFollowUpController {
    private var pendingTopic: String? = null
    private var suggestionShown = false

    @Synchronized
    fun onUserMessage(
        message: String,
        previousUserMessage: String?,
        ownerContext: Boolean,
        publicOnlyMode: Boolean
    ): String? {
        if (!ownerContext || publicOnlyMode) {
            clear()
            return null
        }

        val value = normalize(message)
        if (value.isBlank()) return null

        if (value in setOf("نه", "نه ممنون", "فعلا نه", "فعلاً نه", "الان نه", "no", "not now") ||
            containsAny(value, listOf(
                "نه فعلا", "نه فعلاً", "نمیخوام", "نمی خوام", "دیگه لازم نیست",
                "بیخیال", "بی خیال", "ولش کن", "فراموشش کن", "متوقفش کن",
                "not interested", "no thanks", "drop it", "forget it", "stop reminding me"
            ))
        ) {
            clear()
            return null
        }

        if (containsAny(value, listOf(
                "تموم شد", "تمام شد", "حل شد", "انجام شد", "دیگه حلش کردم",
                "done", "finished", "resolved", "completed"
            ))
        ) {
            clear()
            return null
        }

        if (containsAny(value, listOf(
                "بعدا ادامه بدیم", "بعداً ادامه بدیم", "بعدا برگردیم", "بعداً برگردیم",
                "بعدا در موردش", "بعداً در موردش", "بعدا راجع بهش", "بعداً راجع بهش",
                "بذار بعدا", "بذار بعداً", "فعلا نگهش دار", "فعلاً نگهش دار",
                "we'll come back to this", "we can continue later", "let's pick this up later",
                "remind me to continue"
            ))
        ) {
            val candidate = previousUserMessage?.trim().orEmpty()
                .replace(Regex("\\s+"), " ")
                .take(180)
            if (candidate.isNotBlank()) {
                pendingTopic = candidate
                suggestionShown = false
            }
            return null
        }

        if (!suggestionShown && pendingTopic != null && containsAny(value, listOf(
                "ادامه بدیم", "ادامه بده", "برگردیم بهش", "از کجا مونده بودیم",
                "حالا وقتشه", "وقت دارم", "بریم سراغش",
                "let's continue", "pick it up", "where did we leave off", "i have time now"
            ))
        ) {
            suggestionShown = true
            return suggestionText(pendingTopic!!)
        }

        return null
    }

    @Synchronized
    fun takeSuggestionForChatOpen(): String? {
        val topic = pendingTopic ?: return null
        if (suggestionShown) return null
        suggestionShown = true
        return suggestionText(topic)
    }

    @Synchronized
    fun clear() {
        pendingTopic = null
        suggestionShown = false
    }

    @Synchronized
    fun hasPendingTopic(): Boolean = pendingTopic != null

    private fun suggestionText(topic: String): String =
        "یه موضوع رو برای ادامه گذاشته بودیم: «$topic». اگر هنوز برات مهمه، می‌تونیم برگردیم سراغش؛ اگر نه، کنار می‌ذارمش."

    private fun normalize(input: String): String = input.lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(value: String, phrases: List<String>): Boolean =
        phrases.any { value.contains(normalize(it)) }
}
