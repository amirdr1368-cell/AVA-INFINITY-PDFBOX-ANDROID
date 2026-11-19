package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AvaLanguagePolicyTest {
    @Test fun defaultsToPersianAndSwitchesOnPersianOrEnglishInput() {
        assertEquals("fa", AvaLanguagePolicy.resolve("fa", ""))
        assertEquals("fa", AvaLanguagePolicy.resolve("fa", "سلام"))
        assertEquals("en", AvaLanguagePolicy.resolve("fa", "Hello, how are you?"))
        assertEquals("fa", AvaLanguagePolicy.resolve("en", "سلام، حالت چطوره؟"))
        assertEquals("ar", AvaLanguagePolicy.resolve("fa", "مرحبا كيف حالك؟"))
    }

    @Test fun explicitLanguageSelectionOverridesCurrentLanguage() {
        assertEquals("fr", AvaLanguagePolicy.resolve("fa", "Please speak French from now on."))
        assertEquals("fa", AvaLanguagePolicy.resolve("en", "لطفاً فارسی صحبت کن"))
        assertEquals("ar", AvaLanguagePolicy.resolve("fa", "Please use Arabic"))
        assertEquals("fa", AvaLanguagePolicy.resolve("en", "فارسی"))
        assertEquals("ar", AvaLanguagePolicy.resolve("fa", "العربية"))
    }

    @Test fun detectsCommonNonLatinScripts() {
        assertEquals("ru", AvaLanguagePolicy.resolve("fa", "Привет, как дела?"))
        assertEquals("ja", AvaLanguagePolicy.resolve("fa", "こんにちは"))
        assertEquals("ko", AvaLanguagePolicy.resolve("fa", "안녕하세요"))
    }
}
