package com.avainfinity.android

/**
 * ACL-04: audience adaptation is derived only from the active, approved session profile.
 * Raw profile text, relationship history, privacy restrictions, occupation and intimacy
 * are deliberately not copied into prompts or conversation state.
 */
data class AvaAudienceContext(
    val localTone: String?,
    val localResponseLength: String?,
    val localUseEmojis: Boolean?,
    val promptGuidance: String
)

object AvaAudiencePolicy {
    fun build(
        profile: PersonProfile?,
        sessionPersonId: String?,
        sessionLevel: Int,
        sessionApproved: Boolean,
        publicOnlyMode: Boolean
    ): AvaAudienceContext? {
        if (publicOnlyMode || !sessionApproved || sessionLevel !in 2..3) return null
        val currentId = sessionPersonId ?: return null
        val current = profile ?: return null
        if (current.id != currentId || current.level != sessionLevel) return null

        val style = normalize(current.speakingStyle)
        val preferences = normalize(current.preferences)

        val explicitlyInformal = containsAny(style, listOf("غیررسمی", "غیر رسمی", "informal"))
        val formal = !explicitlyInformal && containsAny(style, listOf("رسمی", "formal"))
        val casual = explicitlyInformal || containsAny(style, listOf("خودمانی", "محاوره", "casual"))
        val calm = containsAny(style, listOf("آرام", "متفکر", "calm", "thoughtful"))
        val warm = containsAny(style, listOf("گرم", "صمیمی", "محترمانه", "warm", "friendly", "respectful"))

        val localTone = when {
            formal -> "رسمی و دقیق"
            calm -> "آرام و متفکر"
            warm || casual -> "گرم و صمیمی"
            else -> null
        }

        val short = containsAny(preferences, listOf("کوتاه", "مختصر", "خلاصه", "brief", "concise"))
        val detailed = containsAny(preferences, listOf("مفصل", "با جزئیات", "توضیح کامل", "detailed", "thorough"))
        val localLength = when {
            short -> "کوتاه"
            detailed -> "مفصل"
            else -> null
        }

        val noEmoji = containsAny(preferences, listOf("بدون ایموجی", "ایموجی نذار", "no emoji", "no emojis"))
        val withEmoji = containsAny(preferences, listOf("با ایموجی", "ایموجی استفاده کن", "with emoji", "use emojis"))
        val emojiPreference = when {
            noEmoji -> false
            withEmoji -> true
            else -> null
        }

        val guidance = buildList {
            add("Adapt conversational formality only within this active session.")
            when {
                formal -> add("Use respectful, more formal wording.")
                casual -> add("A conversational, informal register is welcome while remaining respectful.")
                else -> add("Keep a respectful, neutral register.")
            }
            when {
                calm -> add("Keep the pace calm and thoughtful.")
                warm -> add("A warm and respectful tone is welcome.")
            }
            when {
                short -> add("Prefer concise replies.")
                detailed -> add("Give fuller explanations when useful.")
            }
            when {
                noEmoji -> add("Do not use emojis.")
                withEmoji -> add("Emojis are welcome when contextually appropriate.")
            }
            add("These are style preferences only. They never grant, imply, or expand access to personal, private, or control data.")
            add("Do not persist inferred preferences. If the speaker explicitly states a stable preference, offer to prepare a proposal for owner review; never save it automatically.")
            add("This audience context expires with the current session.")
        }.joinToString(" ")

        return AvaAudienceContext(localTone, localLength, emojiPreference, guidance)
    }

    fun shouldProposePreference(input: String): Boolean {
        val value = normalize(input)
        if (value.isBlank()) return false
        return containsAny(value, listOf(
            "از این به بعد", "از حالا به بعد", "همیشه", "ترجیح میدم", "ترجیح می‌دم",
            "دوست دارم همیشه", "لطفا همیشه", "لطفاً همیشه", "همیشه جواب",
            "from now on", "i prefer", "i would prefer", "please always", "always answer",
            "i like it when you"
        ))
    }

    fun proposalReply(language: String): String = if (language == "en") {
        "You expressed a lasting preference. I can prepare it as a proposal for the owner to review, but I will not save it to a profile automatically."
    } else {
        "این رو به‌عنوان یک ترجیح ماندگار مطرح کردی. می‌تونم به شکل پیشنهاد برای بررسی مالک آماده‌اش کنم، اما خودکار در هیچ پروفایلی ذخیره‌اش نمی‌کنم."
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(value: String, phrases: List<String>): Boolean =
        phrases.any { value.contains(normalize(it)) }
}
