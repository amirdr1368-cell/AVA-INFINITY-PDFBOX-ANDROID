package com.avainfinity.android

/**
 * Lightweight language-selection policy for the mobile conversation layer.
 * Full language generation remains the responsibility of the configured provider.
 */
object AvaLanguagePolicy {
    private val aliases = linkedMapOf(
        "fa" to listOf("فارسی", "farsi", "persian"),
        "en" to listOf("انگلیسی", "english"),
        "ar" to listOf("عربی", "العربية", "arabic"),
        "fr" to listOf("فرانسوی", "french"),
        "es" to listOf("اسپانیایی", "spanish"),
        "de" to listOf("آلمانی", "german"),
        "it" to listOf("ایتالیایی", "italian"),
        "pt" to listOf("پرتغالی", "portuguese"),
        "tr" to listOf("ترکی", "turkish"),
        "ru" to listOf("روسی", "russian"),
        "hi" to listOf("هندی", "hindi"),
        "zh" to listOf("چینی", "chinese"),
        "ja" to listOf("ژاپنی", "japanese"),
        "ko" to listOf("کره‌ای", "korean")
    )

    private val languageNames = mapOf(
        "fa" to "Persian", "en" to "English", "ar" to "Arabic", "fr" to "French",
        "es" to "Spanish", "de" to "German", "it" to "Italian", "pt" to "Portuguese",
        "tr" to "Turkish", "ru" to "Russian", "hi" to "Hindi", "zh" to "Chinese",
        "ja" to "Japanese", "ko" to "Korean"
    )

    fun languageName(code: String): String = languageNames[code] ?: code

    fun resolve(currentLanguage: String, userText: String): String {
        val text = userText.trim().lowercase()
        if (text.isBlank()) return currentLanguage
        aliases.forEach { (code, names) ->
            if (names.any { name ->
                    text.contains("speak $name") ||
                        text.contains("in $name") ||
                        text.contains("reply in $name") ||
                        text.contains("use $name") ||
                        text.contains("switch to $name") ||
                        text.contains("$name please") ||
                        text.contains("$name from now on") ||
                        text == name ||
                        text.contains("به $name") ||
                        text.contains("تحدث $name") ||
                        text.contains("$name صحبت") ||
                        text.contains("$name جواب")
                }
            ) return code
        }

        val persianArabic = text.count { it in '\u0600'..'\u06FF' }
        val latin = text.count { it in 'a'..'z' }
        val cyrillic = text.count { it in '\u0400'..'\u04FF' }
        val devanagari = text.count { it in '\u0900'..'\u097F' }
        val hangul = text.count { it in '\uAC00'..'\uD7AF' }
        val kana = text.count { it in '\u3040'..'\u30FF' }
        val cjk = text.count { it in '\u4E00'..'\u9FFF' }

        if (persianArabic > 0 && persianArabic >= latin) {
            if (text.any { it in "پچژگکی" }) return "fa"
            if (text.any { it in "ثذضظة" } ||
                listOf("مرحبا", "كيف", "شكرا", "أهلا", "السلام عليكم", "العربية").any { text.contains(it) }
            ) return "ar"
            return if (currentLanguage == "ar") "ar" else "fa"
        }
        if (cyrillic > 0) return "ru"
        if (devanagari > 0) return "hi"
        if (hangul > 0) return "ko"
        if (kana > 0) return "ja"
        if (cjk > 0) return "zh"
        if (latin > 0) return detectLatinLanguage(text) ?: "en"
        return currentLanguage
    }

    private fun detectLatinLanguage(text: String): String? {
        val markers = listOf(
            "fr" to listOf("bonjour", "merci", "je ", "vous ", "une ", "est "),
            "es" to listOf("hola", "gracias", "por favor", "que ", "como ", "está"),
            "de" to listOf("hallo", "danke", "ich ", "nicht", "und ", "der "),
            "it" to listOf("ciao", "grazie", "come ", "sono ", "che ", "non "),
            "pt" to listOf("olá", "obrigado", "você", "não ", "uma ", "por favor"),
            "tr" to listOf("merhaba", "teşekkür", "bir ", "ben ", "değil", "nasılsın")
        )
        return markers.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first
    }
}
