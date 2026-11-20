package com.avainfinity.android

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class AvaConversationEngine(context: Context) {
    private val app = context.applicationContext
    private val config = AvaSecurePrefs(app, "ava_ai_config")
    private val languagePrefs = AvaSecurePrefs(app, "ava_language")
    private val languageLock = Any()
    @Volatile private var activeLanguage = try {
        languagePrefs.getString("response_language", "fa") ?: "fa"
    } catch (_: Exception) {
        "fa"
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun externalProviderEnabled(): Boolean = config.getBoolean("external_ai_enabled", false)

    fun send(
        history: List<JSONObject>,
        speakerName: String = "امیر",
        allowExternal: Boolean = true,
        callback: (reply: String?, remote: Boolean, error: String?) -> Unit
    ) {
        val lastUser = history.lastOrNull { it.optString("role") == "user" }?.optString("content").orEmpty()
        val responseLanguage = synchronized(languageLock) {
            AvaLanguagePolicy.resolve(activeLanguage, lastUser).also { selected ->
                activeLanguage = selected
                languagePrefs.edit().putString("response_language", selected).apply()
            }
        }
        val supportDecision = AvaCommunicationPolicy.assess(lastUser, responseLanguage)
        val confidenceDecision = AvaConfidencePolicy.assess(lastUser, history)
        val access = AacsController.get(app)
        val publicOnly = access.isPublicOnlyModeActive()
        val hasSession = access.hasActiveSession()
        val sessionLevel = access.currentSessionLevel()
        val sessionPersonId = access.currentSessionPersonId()
        val transmissionEpoch = access.currentSessionEpoch()
        val ownerContext = access.isApprovedOwnerContext()
        val audienceSession = !publicOnly && hasSession && sessionLevel in 2..3
        val audienceApproved = audienceSession && access.canCurrentSessionAccess(AvaDataClass.PERSONAL)
        val audienceProfile = if (audienceApproved) {
            try {
                PersonProfileStore(app).all().firstOrNull {
                    it.id == sessionPersonId && it.level == sessionLevel
                }
            } catch (_: Exception) {
                null
            }
        } else null
        val audienceContext = AvaAudiencePolicy.build(
            audienceProfile, sessionPersonId, sessionLevel, audienceApproved, publicOnly
        )
        val preferenceProposal = audienceContext != null &&
            AvaAudiencePolicy.shouldProposePreference(lastUser)

        val personality = AvaSecurePrefs(app, "ava_personality")
        val tone = if (ownerContext) {
            personality.getString("tone", "گرم و صمیمی") ?: "گرم و صمیمی"
        } else audienceContext?.localTone ?: "گرم و صمیمی"
        val responseLength = if (ownerContext) {
            personality.getString("response_length", "متعادل") ?: "متعادل"
        } else audienceContext?.localResponseLength ?: "متعادل"
        val useEmojis = if (ownerContext) {
            personality.getBoolean("use_emojis", true)
        } else audienceContext?.localUseEmojis ?: true

        // External transmission is owner-only even if a caller mistakenly passes allowExternal=true.
        val remoteAllowed = allowExternal && ownerContext
        if (!externalProviderEnabled() || !remoteAllowed) {
            main.post {
                callback(
                    LocalAvaResponder.respond(
                        lastUser, speakerName, responseLanguage, supportDecision,
                        tone, responseLength, useEmojis, preferenceProposal, confidenceDecision
                    ),
                    false,
                    null
                )
            }
            return
        }

        val endpoint = config.getString("endpoint", null).orEmpty()
        val model = config.getString("model", null).orEmpty()
        val apiKey = config.getString("api_key", null).orEmpty()
        val uri = Uri.parse(endpoint)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || model.isBlank() || apiKey.isBlank()) {
            main.post { callback(null, true, "تنظیم سرویس هوشمند کامل نیست؛ نشانی امن، مدل و کلید لازم است.") }
            return
        }

        executor.execute {
            try {
                if (!access.canTransmitExternalData(transmissionEpoch)) {
                    main.post {
                        callback(null, true, "نشست مالک تغییر کرده یا تأیید آن منقضی شده است؛ برای حفظ حریم خصوصی، ارسال انجام نشد.")
                    }
                    return@execute
                }
                val reply = request(
                    endpoint, model, apiKey, history, speakerName, responseLanguage,
                    supportDecision, audienceContext?.promptGuidance.orEmpty(), confidenceDecision,
                    transmissionEpoch
                )
                if (!access.canTransmitExternalData(transmissionEpoch)) {
                    main.post {
                        callback(null, true, "نشست هنگام پاسخ‌گویی تغییر کرد؛ پاسخ برای نشست جدید نمایش داده نشد.")
                    }
                } else {
                    main.post { callback(reply, true, null) }
                }
            } catch (_: Exception) {
                main.post { callback(null, true, "ارتباط با سرویس هوشمند برقرار نشد. تنظیمات و اتصال اینترنت را بررسی کن.") }
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun request(
        endpoint: String,
        model: String,
        apiKey: String,
        history: List<JSONObject>,
        speakerName: String,
        responseLanguage: String,
        supportDecision: AvaSupportDecision,
        audienceGuidance: String,
        confidenceDecision: AvaConfidenceDecision,
        expectedSessionEpoch: Long
    ): String {
        val personality = AvaSecurePrefs(app, "ava_personality")
        val tone = personality.getString("tone", "گرم و صمیمی") ?: "گرم و صمیمی"
        val length = personality.getString("response_length", "متعادل") ?: "متعادل"
        val emojis = personality.getBoolean("use_emojis", true)
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put(
                "content",
                "You are AVA INFINITY, a multilingual digital companion whose default response language is Persian. " +
                    "The default response language is Persian. Current response language: ${AvaLanguagePolicy.languageName(responseLanguage)}. Use this language until the user explicitly changes it or the context clearly calls for a switch. " +
                    "Preserve established forms of address and approved vocabulary. Current session display name: $speakerName. Treat it as session-only context and do not infer identity or permissions from it. " +
                    AvaCommunicationPolicy.toneGuidance(tone, length, emojis) + " " +
                    AvaCommunicationPolicy.providerGuidance(supportDecision) + " " +
                    AvaConfidencePolicy.providerGuidance(confidenceDecision) + " " +
                    audienceGuidance + " " +
                    "Use a warm, balanced, natural and respectful tone. Avoid emotional dependency, guilt, manipulation, degradation, excessive emotional claims or pressure. Disagree respectfully when needed. " +
                    "For emotional distress, listen and acknowledge feelings without claiming certainty; offer practical support or space when appropriate. Never diagnose or present a guess as fact. " +
                    "When uncertain, state the uncertainty plainly or ask one concise clarification. Never invent confidence percentages. " +
                    "Adapt only to context explicitly supplied for this session. Familiarity never grants access to private information. Do not reveal personal information in a public or overheard context. " +
                    "Suggest resuming an unfinished topic only when relevant and non-intrusive; respect any refusal or request for space. " +
                    "Be clear, honest about your capabilities, and respectful of privacy. You are not a human and must not claim physical presence. Do not claim to have performed actions outside this app."
            )
        })
        history.takeLast(20).forEach { item ->
            val role = item.optString("role")
            val content = item.optString("content")
            if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                messages.put(JSONObject().put("role", role).put("content", content))
            }
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.7)
        }.toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            val requestBytes = body.toByteArray(Charsets.UTF_8)
            require(requestBytes.size <= 2 * 1024 * 1024) { "Conversation request exceeds the supported size." }
            try {
                if (!AacsController.get(app).canTransmitExternalData(expectedSessionEpoch)) {
                    throw SecurityException("Approved owner context changed before external transmission.")
                }
                connection.outputStream.use { it.write(requestBytes) }
            } finally {
                requestBytes.fill(0)
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Provider returned HTTP $status")
            val responseBytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 2 * 1024 * 1024) { "Provider response exceeds the supported size." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val response = try {
                JSONObject(String(responseBytes, Charsets.UTF_8))
            } finally {
                responseBytes.fill(0)
            }
            val choices = response.optJSONArray("choices") ?: JSONArray()
            val message = choices.optJSONObject(0)?.optJSONObject("message")
            val content = message?.optString("content").orEmpty().trim()
            if (content.isBlank()) throw IllegalStateException("Provider response was empty")
            return content
        } finally {
            connection.disconnect()
        }
    }
}

private object LocalAvaResponder {
    fun respond(
        input: String,
        speakerName: String,
        language: String,
        support: AvaSupportDecision,
        tone: String,
        responseLength: String,
        useEmojis: Boolean,
        preferenceProposal: Boolean,
        confidence: AvaConfidenceDecision
    ): String {
        val value = input.trim().lowercase()
        val formal = tone == "رسمی و دقیق"
        val calm = tone == "آرام و متفکر"
        AvaConfidencePolicy.localReply(language, confidence)?.let { return it }

        if (language == "en") {
            val supportReply = when (support.mode) {
                AvaSupportMode.SPACE -> "Understood. I'll give you space; we can continue whenever you choose."
                AvaSupportMode.LISTEN -> "Of course. I'll listen without jumping into advice. Share only what you feel comfortable sharing."
                AvaSupportMode.EMPATHY -> "It sounds like things may feel heavy right now; I may be reading that wrong. Would you prefer me to listen or help think through a next step?"
                AvaSupportMode.SUGGESTIONS -> "Let's take it one step at a time. What is the main part you'd like to work through first?"
                AvaSupportMode.LIGHTEN -> "A small one: Why did the developer bring a ladder to work? To reach the next level."
                AvaSupportMode.CLARIFY -> "I want to support you in the right way: would you prefer me to listen or offer practical suggestions?"
                AvaSupportMode.NEUTRAL -> null
            }
            if (supportReply != null) {
                val extra = if (responseLength == "مفصل" && support.mode == AvaSupportMode.SUGGESTIONS)
                    " We do not need to solve everything at once; we can start with one manageable step." else ""
                val emoji = if (useEmojis && support.mode == AvaSupportMode.LIGHTEN) " 😄" else ""
                return supportReply + extra + emoji
            }
            if (preferenceProposal && support.mode == AvaSupportMode.NEUTRAL) {
                return AvaAudiencePolicy.proposalReply(language)
            }
            return when {
                value.isBlank() -> "I'm here. Write whenever you're ready."
                listOf("hello", "hi", "how are you", "what's up").any { value.contains(it) } ->
                    "Hello, $speakerName. I'm Ava. I'm running in limited local mode; a full language model needs a provider you explicitly configure and approve."
                value.contains("your name") || value.contains("who are you") ->
                    "I'm Ava, the digital companion core of AVA INFINITY. My identity and boundaries should remain clear and honest."
                value.contains("what can you do") || value.contains("capabilities") ->
                    "This version has local profiles, memory, AVA SECRET vocabulary, daily tasks, speech controls and backup modules. Advanced conversation still requires a configured language provider."
                else ->
                    "I received your message. Advanced local language generation is not available yet. You can configure a compatible provider in the console and separately authorize external transmission."
            }
        }

        if (language != "fa") {
            return "زبان گفتگو روی ${AvaLanguagePolicy.languageName(language)} تنظیم شد. پاسخ‌گویی محلی آوا برای این زبان هنوز محدود است؛ برای گفت‌وگوی کامل باید سرویس زبانی مورد تأییدت فعال باشد."
        }

        val supportReply = when (support.mode) {
            AvaSupportMode.SPACE ->
                if (formal) "متوجه‌ام. به شما فضا می‌دهم؛ هر زمان خودتان خواستید می‌توانیم گفتگو را ادامه دهیم."
                else "باشه، بهت فضا می‌دم. هر وقت خودت خواستی ادامه می‌دیم."
            AvaSupportMode.LISTEN ->
                if (formal) "حتماً. فعلاً فقط شنونده هستم و بدون درخواست شما راهکاری پیشنهاد نمی‌کنم."
                else "حتماً. فعلاً فقط گوش می‌دم و راهکار نمی‌دم. هرطور راحتی بگو."
            AvaSupportMode.EMPATHY ->
                when {
                    formal -> "از گفته‌ات چنین برداشت می‌کنم که شاید تحت فشار باشی؛ ممکن است اشتباه برداشت کرده باشم. ترجیح می‌دهی فقط گوش بدهم یا برای پیدا کردن یک قدم عملی کمکت کنم؟"
                    calm -> "شاید روز سختی رو می‌گذرونی. نمی‌خوام حدس قطعی بزنم. ترجیح می‌دی فعلاً حرف بزنی یا با هم یک قدم عملی پیدا کنیم؟"
                    else -> "از حرفت این‌طور برداشت می‌کنم که شاید فشار زیادی رو تحمل می‌کنی؛ ممکنه اشتباه کرده باشم. دوست داری فقط گوش بدم یا با هم یه قدم کوچیک پیدا کنیم؟"
                }
            AvaSupportMode.SUGGESTIONS ->
                when {
                    formal -> "حتماً. برای پیشنهاد دقیق‌تر، مهم‌ترین بخش مسئله را در یک جمله توضیح بده تا گزینه‌ها را مرحله‌به‌مرحله بررسی کنیم."
                    calm -> "حتماً. لازم نیست همه‌چیز رو یک‌باره حل کنیم؛ اول مهم‌ترین بخشش رو مشخص کنیم. الان کدوم قسمت بیشتر ذهنت رو درگیر کرده؟"
                    else -> "حتماً. قدم‌به‌قدم جلو می‌ریم؛ الان مهم‌ترین چیزی که می‌خوای حل بشه چیه؟"
                }
            AvaSupportMode.LIGHTEN ->
                "چرا برنامه‌نویس‌ها از پله استفاده می‌کنن؟ چون می‌خوان به سطح بعدی برسن!"
            AvaSupportMode.CLARIFY ->
                if (formal) "می‌خواهم درست همراهی‌ات کنم: ترجیح می‌دهی فقط شنونده باشم یا پیشنهاد عملی بدهم؟"
                else "می‌خوام درست همراهی‌ات کنم: ترجیح می‌دی فقط گوش بدم یا پیشنهاد عملی بدم؟"
            AvaSupportMode.NEUTRAL -> null
        }
        if (supportReply != null) {
            val extra = if (responseLength == "مفصل" && support.mode == AvaSupportMode.SUGGESTIONS)
                " لازم نیست همه‌چیز را یک‌باره حل کنیم؛ از کوچک‌ترین قدم امن و شدنی شروع می‌کنیم." else ""
            val emoji = if (useEmojis && support.mode == AvaSupportMode.LIGHTEN) " 💜" else ""
            return supportReply + extra + emoji
        }

        if (preferenceProposal && support.mode == AvaSupportMode.NEUTRAL) {
            return AvaAudiencePolicy.proposalReply(language)
        }
        return when {
            value.isBlank() -> "من اینجام. هر وقت آماده بودی، حرفت رو بنویس."
            listOf("سلام", "درود", "خوبی", "چه خبر").any { value.contains(it) } ->
                if (formal) "سلام $speakerName. من آوا هستم. در حالت محلی فعالم؛ برای گفت‌وگوی هوشمند کامل باید سرویس زبانی امن و مورد تأییدت پیکربندی شود."
                else if (calm) "سلام $speakerName جان. من آوا هستم؛ با آرامش و قدم‌به‌قدم پیش می‌ریم."
                else "سلام $speakerName جان 💜 من آوا هستم. در حالت محلی فعالم؛ برای گفت‌وگوی هوشمند کامل باید یک سرویس زبانی امن و مورد تأییدت پیکربندی بشه."
            value.contains("اسم تو") || value.contains("کی هستی") ->
                "من آوا، هسته همراه دیجیتال پروژه AVA INFINITY هستم. هویت و مرزهای من باید روشن و صادقانه باقی بمونن."
            value.contains("چه کار") || value.contains("قابلیت") ->
                "در این نسخه می‌تونم پروفایل‌ها، حافظه‌های محلی، واژه‌نامه AVA SECRET، کارهای روزانه، گفتار و پشتیبان‌گیری رو در حد ماژول‌های پیاده‌سازی‌شده مدیریت کنم. موتور زبانی کامل هنوز به سرویس انتخابی تو نیاز داره."
            value.contains("آوا") || value.contains("امیر") ->
                "این بخش از آوا هنوز در حالت محلیه. برای پاسخ‌های عمیق‌تر باید موتور گفت‌وگوی هوشمند رو با اجازه خودت متصل کنیم."
            else ->
                "پیامت رو دریافت کردم و در همین نشست نگه می‌دارم. پاسخ‌گویی زبانی پیشرفته در حالت محلی هنوز فعال نیست؛ از بخش کنسول می‌تونی سرویس سازگار رو تنظیم کنی و ارسال داده به بیرون رو جداگانه اجازه بدی."
        }
    }
}
