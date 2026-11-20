package com.avainfinity.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.Manifest
import android.content.pm.PackageManager
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { AvaSecurePrefs(this, "ava_ui") }
    private lateinit var root: LinearLayout
    private lateinit var contentArea: LinearLayout
    private lateinit var pageTitleView: TextView
    private lateinit var avatar: ImageView
    private lateinit var avatarLarge: ImageView
    private lateinit var messages: LinearLayout
    private lateinit var chatScroll: ScrollView
    private var page = "home"
    private val navigationHistory = mutableListOf<String>()
    private var suppressNavigationHistory = false
    private var selectedVocabularyCategory: String? = null
    private var pendingVocabularyAction: String? = null
    private var pendingVocabularyEntryId: String? = null
    private var pendingVocabularyTargetVersion: Int? = null
    private var pendingWardrobePhotoName: String? = null
    private var pendingWardrobePhotoCategory: String? = null
    private var accent = AvaGlassTheme.Lavender500
    private var deep = AvaGlassTheme.Night900
    private var lightTheme = false
    private val primaryTextColor: Int get() = if (lightTheme) AvaGlassTheme.LightPrimaryText else AvaGlassTheme.DarkPrimaryText
    private val secondaryTextColor: Int get() = if (lightTheme) AvaGlassTheme.LightSecondaryText else AvaGlassTheme.DarkSecondaryText
    private var fontScale = 1.0f
    private var glassAlpha = 34
    private var avatarSize = 148
    private var avatarVisible = true
    private var profileUri: Uri? = null
    private var startupStorageWarning: String? = null
    private lateinit var localStore: AvaLocalStore
    private var speech: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var ttsGeneration: Long = 0L
    private var audioFocusHeld: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (activeUtteranceId != null) {
                stopAvaVoice()
                Toast.makeText(
                    this@MainActivity,
                    "اولویت صوتی تغییر کرد؛ پخش آوا متوقف شد و خودکار ادامه پیدا نمی‌کند.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                abandonAvaAudioFocus()
            }
        }
    }
    private var audioNoisyReceiverRegistered: Boolean = false
    private var audioDeviceCallbackRegistered: Boolean = false
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            handleAudioDeviceRouteChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            handleAudioDeviceRouteChange()
        }
    }
    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (voiceState == AvaVoiceState.SPEAKING ||
                voiceState == AvaVoiceState.LISTENING ||
                voiceState == AvaVoiceState.PROCESSING
            ) {
                stopAvaVoice()
                Toast.makeText(this@MainActivity, "مسیر خروجی صدا تغییر کرد؛ برای جلوگیری از پخش ناخواسته، صدای آوا متوقف شد.", Toast.LENGTH_LONG).show()
            }
        }
    }
    private var speechRecognizer: SpeechRecognizer? = null
    // A recognition session may only deliver text to the page that started it.
    private var speechSessionPage: String? = null
    private val speechSessionHandler = Handler(Looper.getMainLooper())
    private var speechSessionTimeout: Runnable? = null
    // Permission approval is valid only for the page that initiated the request.
    private var pendingSpeechPermissionPage: String? = null
    private var voiceStatusView: TextView? = null
    private var voiceStopButton: TextView? = null
    private var homeVoiceStatusView: TextView? = null
    private var voiceState: AvaVoiceState = AvaVoiceState.IDLE
    private var voiceGeneration: Long = 0L
    private var activeUtteranceId: String? = null
    private var chatInput: EditText? = null
    private var chatDraftText: String = ""
    private lateinit var profileStore: PersonProfileStore
    private lateinit var aacs: AacsController
    private lateinit var backup: AvaEncryptedBackup
    private lateinit var conversationEngine: AvaConversationEngine
    private val conversationHistory = mutableListOf<org.json.JSONObject>()
    // Invalidates in-flight replies when a privacy/session boundary clears the conversation.
    private var conversationGeneration: Long = 0L
    // Invalidates delayed voice output when the user leaves and later re-enters a page.
    private var pageGeneration: Long = 0L
    private val followUpController = AvaFollowUpController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerAudioNoisyReceiver()
        registerAudioDeviceCallback()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = Color.rgb(28, 19, 37)
        window.navigationBarColor = Color.rgb(22, 15, 30)
        window.decorView.systemUiVisibility = 0
        localStore = AvaLocalStore(this)
        try {
            localStore.cleanupExpiredTemporaryMemories()
        } catch (_: Exception) {
            startupStorageWarning = "بخشی از حافظه آوا قابل خواندن نیست؛ داده‌ها دست‌نخورده نگه داشته شده‌اند و بخش‌های حساس باید بررسی شوند."
        }
        profileStore = PersonProfileStore(this).also {
            try { it.ensureArash() } catch (_: Exception) {
                startupStorageWarning = "پروفایل‌ها قابل بررسی کامل نیستند؛ از بازنویسی یا بازیابی خودکار جلوگیری شده است."
            }
        }
        if (localStore.isVaultConfigured()) AvaBackupScheduler.configureVaultHealth(this)
        aacs = AacsController.get(this)
        if (!aacs.hasActiveSession() && !aacs.isPublicOnlyModeActive()) {
            try {
                profileStore.all().forEach { profile ->
                    val recovered = if (profile.relationshipMemoryApproved) {
                        localStore.promotePendingRelationshipMemory(profile.id)
                    } else {
                        localStore.quarantineRelationshipMemory(profile.id)
                    }
                    if (!recovered) startupStorageWarning = "بازیابی امن خاطرات رابطه کامل نشد؛ فضای شخصی تا تأیید دوباره قفل می‌ماند."
                }
            } catch (_: Exception) {
                startupStorageWarning = "فضای خاطرات رابطه قابل بررسی کامل نیست؛ دسترسی آن در حالت محافظت‌شده باقی می‌ماند."
            }
        }
        if (aacs.hasActiveSession()) {
            selectedMemoryPersonId = aacs.currentSessionPersonId()
            activeSessionDisplayName = try {
                profileStore.all().firstOrNull { it.id == aacs.currentSessionPersonId() }?.name ?: "مهمان"
            } catch (_: Exception) {
                "مهمان"
            }
        }
        backup = AvaEncryptedBackup(this)
        conversationEngine = AvaConversationEngine(this)
        val existingBackupConfig = AvaSecurePrefs(this, "ava_backup_config")
        if (!existingBackupConfig.getString("backup_tree_uri", null).isNullOrBlank() &&
            !existingBackupConfig.getString("backup_passphrase", null).isNullOrBlank()
        ) {
            AvaBackupScheduler.configureDaily(this)
            AvaBackupScheduler.configureBackupHealth(this)
            AvaBackupScheduler.configureRestoreTest(this)
        }
        initializeAvaTts(showFeedback = false)
        profileUri = prefs.getString("avatar_uri", null)?.let { Uri.parse(it) }
        fontScale = prefs.getInt("font_scale", 100).coerceIn(85, 125) / 100f
        glassAlpha = prefs.getInt("glass_alpha", 34).coerceIn(15, 90)
        avatarSize = prefs.getInt("avatar_size", 148).coerceIn(100, 200)
        avatarVisible = prefs.getBoolean("avatar_visible", true)
        applyPaletteValues(prefs.getInt("palette", 0))
        buildShell()
        showPage("home")
        startupStorageWarning?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int = 22,
        stroke: Int = Color.argb(55, 255, 255, 255)
    ): GradientDrawable = AvaGlassComponents.rounded(this, color, radius, stroke)

    private fun glass(): GradientDrawable =
        AvaGlassTheme.glassSurface(this, glassAlpha, lightTheme)

    private fun text(
        value: String,
        size: Float,
        color: Int = Color.WHITE,
        bold: Boolean = false
    ): TextView = AvaGlassComponents.text(
        this, value, size, color, bold, fontScale, primaryTextColor
    )

    private fun label(value: String, size: Float = 14f): TextView =
        text(value, size, secondaryTextColor)

    private fun label(value: String, size: Float, color: Int): TextView =
        text(value, size, color)

    private fun button(value: String, onClick: () -> Unit): TextView =
        createButton(value, secondary = false, onClick = onClick)

    private fun secondaryButton(value: String, onClick: () -> Unit): TextView =
        createButton(value, secondary = true, onClick = onClick)

    private fun createButton(
        value: String,
        secondary: Boolean,
        onClick: () -> Unit
    ): TextView = AvaGlassComponents.button(
        context = this,
        value = value,
        lightTheme = lightTheme,
        fontScale = fontScale,
        primaryTextColor = primaryTextColor,
        secondary = secondary,
        onFailure = {
            Toast.makeText(
                this,
                "عملیات انجام نشد؛ داده‌های قبلی حفظ شدند. وضعیت حافظه و فضای ذخیره‌سازی را بررسی کن.",
                Toast.LENGTH_LONG
            ).show()
        },
        onClick = onClick
    )

    private fun card(): LinearLayout =
        AvaGlassComponents.card(this, glass())

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            background = AvaGlassTheme.backgroundGradient(lightTheme)
        }
        setContentView(root)
        window.statusBarColor = if (lightTheme) Color.rgb(245, 235, 248) else AvaGlassTheme.Night950
        window.navigationBarColor = if (lightTheme) Color.rgb(245, 235, 248) else AvaGlassTheme.Night950
        window.decorView.systemUiVisibility = if (lightTheme)
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(14))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("AVA  ∞", 23f, accent, true))
        pageTitleView = label("خانه", 10f)
        brand.addView(pageTitleView)
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        avatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            background = rounded(Color.rgb(91, 65, 99), 60, Color.rgb(224, 181, 218))
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = if (avatarVisible && !aacs.isPublicOnlyModeActive()) View.VISIBLE else View.GONE
            contentDescription = "ورود به مرکز آوا"
            setOnClickListener { showPage("ava") }
        }
        top.addView(avatar)
        root.addView(top)
        contentArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contentArea, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildNavigation())
        refreshAvatar()
    }

    private fun pageTitleFor(target: String): String = when (target) {
        "home" -> "خانه"
        "chat" -> "گفتگو"
        "memory" -> "حافظه"
        "ava" -> "مرکز آوا"
        "profile" -> "پروفایل و ظاهر آوا"
        "voice" -> "صدا و زبان"
        "secret" -> "AVA SECRET"
        "daily" -> "دستیار روزانه"
        "console" -> "کنسول مرکزی"
        "security" -> "امنیت و دسترسی"
        "wardrobe" -> "کمد لباس آوا"
        else -> "AVA INFINITY"
    }

    private fun buildNavigation(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(9), dp(4), dp(4))
            background = AvaGlassTheme.glassSurface(this@MainActivity, 22, lightTheme, 22f)
        }
        val items = listOf("home" to "⌂\nخانه", "chat" to "◌\nگفتگو", "memory" to "✧\nحافظه", "ava" to "∞\nآوا")
        items.forEach { (key, title) ->
            val selected = if (key == "ava") {
                page in setOf("ava", "profile", "voice", "secret", "daily", "console", "security", "wardrobe")
            } else page == key
            val item = text(title, 11f, if (selected) { if (lightTheme) AvaGlassTheme.LightPrimaryText else AvaGlassTheme.Lavender300 } else secondaryTextColor, true).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                isSelected = selected
                val destinationLabel = when (key) {
                    "home" -> "خانه"
                    "chat" -> "گفتگو"
                    "memory" -> "حافظه"
                    else -> "مرکز آوا"
                }
                contentDescription = if (selected) "$destinationLabel، انتخاب‌شده" else destinationLabel
                background = if (selected) AvaGlassTheme.selectedNavigationSurface(this@MainActivity, lightTheme) else null
                setPadding(dp(2), dp(4), dp(2), dp(4))
                setOnClickListener { showPage(key) }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        return nav
    }

    private fun showPage(target: String) {
        val knownDestinations = setOf(
            "home", "chat", "memory", "profile", "ava", "secret",
            "daily", "voice", "console", "security", "wardrobe"
        )
        if (target !in knownDestinations) {
            Toast.makeText(this, "این بخش در نسخه فعلی در دسترس نیست.", Toast.LENGTH_SHORT).show()
            return
        }
        if (::aacs.isInitialized && aacs.isPublicOnlyModeActive() && target !in setOf("home", "chat", "voice", "security")) {
            Toast.makeText(this, "حالت فقط اطلاعات عمومی فعال است؛ خروج از آن به احراز هویت مالک نیاز دارد.", Toast.LENGTH_LONG).show()
            return
        }
        if (::aacs.isInitialized && aacs.hasActiveSession() && target != "security") {
            val requiredClass = when (target) {
                "home", "chat" -> AvaDataClass.PUBLIC
                "memory" -> AvaDataClass.PERSONAL
                "security" -> AvaDataClass.PUBLIC
                "wardrobe" -> AvaDataClass.CONTROL
                else -> AvaDataClass.CONTROL
            }
            if (!aacs.canCurrentSessionAccess(requiredClass)) {
                Toast.makeText(this, "این بخش برای سطح دسترسی نشست فعلی مجاز نیست.", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (page != target) {
            pageGeneration += 1L
            if (pendingSpeechPermissionPage != null) pendingSpeechPermissionPage = null
            if (speechRecognizer != null || activeUtteranceId != null) {
                // Leaving the active page stops capture or playback before replacing its view.
                stopAvaVoice()
            }
        }
        if (!suppressNavigationHistory && page != target) {
            navigationHistory.add(page)
            if (navigationHistory.size > 30) navigationHistory.removeAt(0)
        }
        page = target
        if (::pageTitleView.isInitialized) pageTitleView.text = pageTitleFor(target)
        contentArea.removeAllViews()
        try {
            when (target) {
                "home" -> homePage()
                "chat" -> chatPage()
                "memory" -> memoryPage()
                "profile" -> profilePage()
                "ava" -> avaHubPage()
                "secret" -> secretPage()
                "daily" -> dailyPage()
                "voice" -> voicePage()
                "console" -> consolePage()
                "security" -> securityPage()
                "wardrobe" -> wardrobePage()
            }
        } catch (_: Exception) {
            contentArea.removeAllViews()
            val safePanel = card()
            safePanel.addView(text("حالت محافظت‌شده", 20f, accent, true))
            safePanel.addView(label("این بخش به دلیل خطای خواندن یا اعتبارسنجی داده‌ها باز نشد. اطلاعات موجود بازنویسی نشدند. ابتدا وضعیت حافظه و گزارش امنیتی را بررسی کن.", 13f))
            safePanel.addView(button("بازگشت به خانه") { showPage("home") }.apply { setMarginsCompat(top = 12) })
            contentArea.addView(safePanel)
        }
        // Rebuild navigation so the active destination is highlighted.
        if (root.childCount > 0) {
            root.removeViewAt(root.childCount - 1)
            root.addView(buildNavigation())
        }
        refreshAvatar()
    }

    private fun homePage() {
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        val hero = card().apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(22), dp(20), dp(22)) }
        avatarLarge = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(avatarSize), dp(avatarSize))
            visibility = if (avatarVisible && !aacs.isPublicOnlyModeActive()) View.VISIBLE else View.GONE
            background = rounded(Color.rgb(92, 65, 105), 90, Color.rgb(229, 190, 222))
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        hero.addView(avatarLarge)
        val greetingName = when {
            aacs.isPublicOnlyModeActive() -> "دوست من"
            aacs.hasActiveSession() ->
                activeSessionDisplayName ?: profileStore.all().firstOrNull { it.id == aacs.currentSessionPersonId() }?.name ?: "دوست من"
            else -> "امیر جان"
        }
        hero.addView(text("سلام $greetingName", 25f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(15), 0, dp(4)) })
        hero.addView(text("خوش اومدی به فضای شخصی آوا", 14f, Color.rgb(224, 205, 224)).apply { gravity = Gravity.CENTER })
        homeVoiceStatusView = text("●  ${voiceStateLabel()}", 12f, voiceStateColor()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        hero.addView(homeVoiceStatusView)
        body.addView(hero)

        val quick = card().apply { setMarginsCompat(top = 14) }
        quick.addView(text("دسترسی سریع", 17f, accent, true))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        row.addView(button("گفتگوی تازه") { showPage("chat") }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Space(this), LinearLayout.LayoutParams(dp(10), 1))
        row.addView(secondaryButton("شخصی‌سازی") { showPage("profile") }, LinearLayout.LayoutParams(0, -2, 1f))
        quick.addView(row)
        val sessionControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        if (!aacs.isPublicOnlyModeActive()) {
            sessionControls.addView(button("حالت فقط عمومی") {
                val secured = aacs.publicOnlyMode("owner activated public-only mode from home")
                stopAvaVoice()
                conversationGeneration += 1L
                conversationHistory.clear()
                chatDraftText = ""
                followUpController.clear()
                Toast.makeText(
                    this,
                    if (secured) "حالت فقط عمومی فعال و ثبت شد؛ بخش‌های خصوصی قفل شدند."
                    else "حالت محدود فعال شد، اما ثبت پایدار یا امنیتی کامل نشد؛ بخش‌های خصوصی فعلاً قفل هستند.",
                    Toast.LENGTH_LONG
                ).show()
                showPage("home")
            }, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            sessionControls.addView(button("خروج با تأیید مالک") {
                requestDisablePublicOnlyMode()
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        if (aacs.hasActiveSession()) {
            if (sessionControls.childCount > 0) sessionControls.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
            sessionControls.addView(button("پایان نشست") {
                aacs.endSession()
                selectedMemoryPersonId = null
                activeSessionDisplayName = null
                conversationGeneration += 1L
                conversationHistory.clear()
                chatDraftText = ""
                followUpController.clear()
                Toast.makeText(this, "نشست پایان یافت، دسترسی‌ها لغو و تاریخچه نشست پاک شد.", Toast.LENGTH_SHORT).show()
                showPage("home")
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        quick.addView(sessionControls)
        if (aacs.hasActiveSession() && aacs.currentSessionLevel() <= 3 && !aacs.isPublicOnlyModeActive()) {
            quick.addView(button("درخواست تأیید مالک برای اطلاعات خصوصی") {
                requestOwnerSessionApproval()
            }.apply { setMarginsCompat(top = 8) })
        }
        body.addView(quick)

        val note = card().apply { setMarginsCompat(top = 14) }
        note.addView(text("دنیای آوا", 17f, accent, true))
        note.addView(label("ظاهر، تصویر و حال‌وهوای برنامه را مطابق سلیقه‌ات تنظیم کن. این نسخه روی تجربه ظاهری و امکانات پایه تمرکز دارد.", 13f).apply { setPadding(0, dp(8), 0, 0) })
        body.addView(note)
        val hub = card().apply { setMarginsCompat(top = 14) }
        hub.addView(text("مرکز آوا", 17f, accent, true))
        hub.addView(label("هویت، صدا، زبان و ابزارهای آوا از یک مسیر یکپارچه در دسترس‌اند.", 13f)
            .apply { setPadding(0, dp(8), 0, dp(10)) })
        hub.addView(secondaryButton("ورود به مرکز آوا") { showPage("ava") })
        body.addView(hub)
        scroll.addView(body)
        contentArea.addView(scroll)
    }

    private fun voiceStateColor(): Int = when (voiceState) {
        AvaVoiceState.IDLE -> AvaGlassTheme.Lavender300
        AvaVoiceState.LISTENING -> AvaGlassTheme.StatusSuccess
        AvaVoiceState.PROCESSING -> AvaGlassTheme.StatusWarning
        AvaVoiceState.SPEAKING -> AvaGlassTheme.RoseGold300
        AvaVoiceState.ERROR -> AvaGlassTheme.StatusDanger
    }

    private fun voiceStateLabel(): String = when (voiceState) {
        AvaVoiceState.IDLE -> "وضعیت صدا: بدون فعالیت"
        AvaVoiceState.LISTENING -> "وضعیت صدا: آوا گوش می‌دهد"
        AvaVoiceState.PROCESSING -> "وضعیت صدا: در حال پردازش گفتار"
        AvaVoiceState.SPEAKING -> "وضعیت صدا: آوا در حال صحبت است"
        AvaVoiceState.ERROR -> "وضعیت صدا: خطای گفتار یا دسترسی"
    }

    private fun setVoiceState(state: AvaVoiceState) {
        voiceState = state
        voiceStatusView?.text = voiceStateLabel()
        voiceStatusView?.setTextColor(voiceStateColor())
        homeVoiceStatusView?.text = "●  ${voiceStateLabel()}"
        homeVoiceStatusView?.setTextColor(voiceStateColor())
        voiceStopButton?.visibility = if (
            state == AvaVoiceState.SPEAKING ||
            state == AvaVoiceState.LISTENING ||
            state == AvaVoiceState.PROCESSING
        ) View.VISIBLE else View.GONE
    }

    private fun handleAudioDeviceRouteChange() {
        if (isFinishing || isDestroyed) return
        if (speechRecognizer != null || activeUtteranceId != null ||
            voiceState == AvaVoiceState.SPEAKING ||
            voiceState == AvaVoiceState.LISTENING ||
            voiceState == AvaVoiceState.PROCESSING
        ) {
            stopAvaVoice()
            Toast.makeText(
                this,
                "دستگاه یا مسیر صوتی تغییر کرد؛ نشست صوتی متوقف شد. برای ادامه، خودت دوباره اقدام کن.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun registerAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || audioDeviceCallbackRegistered) return
        val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            manager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
            audioDeviceCallbackRegistered = true
        } catch (_: Exception) {
            audioDeviceCallbackRegistered = false
        }
    }

    private fun unregisterAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !audioDeviceCallbackRegistered) return
        val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            manager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) { }
        audioDeviceCallbackRegistered = false
    }

    private fun registerAudioNoisyReceiver() {
        if (audioNoisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(audioNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(audioNoisyReceiver, filter)
            }
            audioNoisyReceiverRegistered = true
        } catch (_: Exception) {
            audioNoisyReceiverRegistered = false
        }
    }

    private fun unregisterAudioNoisyReceiver() {
        if (!audioNoisyReceiverRegistered) return
        try {
            unregisterReceiver(audioNoisyReceiver)
        } catch (_: Exception) { }
        audioNoisyReceiverRegistered = false
    }

    private fun requestAvaAudioFocus(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(
                        audioFocusListener,
                        Handler(Looper.getMainLooper())
                    )
                    .setWillPauseWhenDucked(true)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!audioFocusHeld) audioFocusRequest = null
            audioFocusHeld
        } catch (_: Exception) {
            audioFocusHeld = false
            audioFocusRequest = null
            false
        }
    }

    private fun abandonAvaAudioFocus() {
        if (!audioFocusHeld) {
            audioFocusRequest = null
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusListener)
            }
        } catch (_: Exception) { }
        audioFocusHeld = false
        audioFocusRequest = null
    }

    private fun clearSpeechSessionTimeout() {
        speechSessionTimeout?.let { speechSessionHandler.removeCallbacks(it) }
        speechSessionTimeout = null
    }

    private fun scheduleSpeechSessionTimeout(
        recognizer: SpeechRecognizer,
        generation: Long,
        timeoutMs: Long,
        message: String
    ) {
        clearSpeechSessionTimeout()
        val timeout = Runnable {
            if (speechRecognizer !== recognizer || voiceGeneration != generation) return@Runnable
            if (!releaseSpeechRecognizer(recognizer, generation, true)) return@Runnable
            setVoiceState(AvaVoiceState.ERROR)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        speechSessionTimeout = timeout
        speechSessionHandler.postDelayed(timeout, timeoutMs)
    }

    private fun stopAvaVoice() {
        clearSpeechSessionTimeout()
        // Invalidate callbacks before asking Android services to stop. Some
        // engines may still deliver a queued utterance callback after stop().
        voiceGeneration += 1L
        activeUtteranceId = null
        setVoiceState(AvaVoiceState.IDLE)
        try { speech?.stop() } catch (_: Exception) { }
        abandonAvaAudioFocus()
        val activeRecognizer = speechRecognizer
        speechRecognizer = null
        speechSessionPage = null
        try {
            activeRecognizer?.cancel()
            activeRecognizer?.destroy()
        } catch (_: Exception) { }
    }

    private fun initializeAvaTts(showFeedback: Boolean) {
        if (isFinishing || isDestroyed) return

        // A retry is always explicit. It never starts playback or microphone capture.
        stopAvaVoice()
        val generation = ++ttsGeneration
        ttsReady = false
        val previousEngine = speech
        speech = null
        try { previousEngine?.shutdown() } catch (_: Exception) { }

        try {
            lateinit var currentEngine: TextToSpeech
            currentEngine = TextToSpeech(this) { status ->
                runOnUiThread {
                    if (generation != ttsGeneration || speech !== currentEngine || isFinishing || isDestroyed) {
                        if (generation != ttsGeneration || speech !== currentEngine) {
                            try { currentEngine.shutdown() } catch (_: Exception) { }
                        }
                        return@runOnUiThread
                    }

                    if (status != TextToSpeech.SUCCESS) {
                        ttsReady = false
                        setVoiceState(AvaVoiceState.ERROR)
                        if (showFeedback) {
                            Toast.makeText(this, "راه‌اندازی دوباره موتور گفتار ناموفق بود؛ سرویس گفتار دستگاه را بررسی کن.", Toast.LENGTH_LONG).show()
                        }
                        return@runOnUiThread
                    }

                    try {
                        currentEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) {
                                runOnUiThread {
                                    if (generation == ttsGeneration && speech === currentEngine &&
                                        activeUtteranceId == utteranceId
                                    ) setVoiceState(AvaVoiceState.SPEAKING)
                                }
                            }

                            override fun onDone(utteranceId: String) {
                                runOnUiThread {
                                    if (generation == ttsGeneration && speech === currentEngine &&
                                        activeUtteranceId == utteranceId
                                    ) {
                                        activeUtteranceId = null
                                        abandonAvaAudioFocus()
                                        if (voiceState == AvaVoiceState.SPEAKING) setVoiceState(AvaVoiceState.IDLE)
                                    }
                                }
                            }

                            override fun onError(utteranceId: String) {
                                runOnUiThread {
                                    if (generation == ttsGeneration && speech === currentEngine &&
                                        activeUtteranceId == utteranceId
                                    ) {
                                        activeUtteranceId = null
                                        abandonAvaAudioFocus()
                                        // A failed utterance does not by itself prove the engine is disconnected.
                                        // Keep readiness for a user-initiated later attempt.
                                        setVoiceState(AvaVoiceState.ERROR)
                                        Toast.makeText(this@MainActivity, "پخش صدای آوا با خطا متوقف شد؛ در صورت نیاز، موتور گفتار را دستی دوباره راه‌اندازی کن.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        })

                        val languageStatus = currentEngine.setLanguage(Locale("fa", "IR"))
                        if (languageStatus < TextToSpeech.LANG_AVAILABLE) {
                            ttsReady = false
                            setVoiceState(AvaVoiceState.ERROR)
                            if (showFeedback) {
                                Toast.makeText(this, "زبان فارسی در موتور گفتار دستگاه نصب یا پشتیبانی نمی‌شود.", Toast.LENGTH_LONG).show()
                            }
                            return@runOnUiThread
                        }

                        val voiceConfig = AvaSecurePrefs(this, "ava_voice_config")
                        val savedRate = voiceConfig.getInt("rate", 100).coerceIn(70, 140)
                        val savedPitch = voiceConfig.getInt("pitch", 106).coerceIn(70, 140)
                        val rateStatus = currentEngine.setSpeechRate(savedRate / 100f)
                        val pitchStatus = currentEngine.setPitch(savedPitch / 100f)
                        if (rateStatus != TextToSpeech.SUCCESS || pitchStatus != TextToSpeech.SUCCESS) {
                            throw IllegalStateException("Saved speech parameters were rejected")
                        }
                        val savedVoice = voiceConfig.getString("voice_name", null)
                        val savedVoiceObject = currentEngine.voices?.firstOrNull {
                            it.name == savedVoice && it.locale.language == "fa"
                        }
                        val voiceStatus = if (savedVoiceObject != null) {
                            currentEngine.setVoice(savedVoiceObject)
                        } else {
                            currentEngine.setLanguage(Locale("fa", "IR"))
                        }
                        if (voiceStatus < TextToSpeech.LANG_AVAILABLE) {
                            throw IllegalStateException("Persian voice configuration was rejected")
                        }
                        ttsReady = true
                        setVoiceState(AvaVoiceState.IDLE)
                        if (showFeedback) {
                            val message = if (savedVoice != null && savedVoiceObject == null) {
                                "موتور گفتار آماده شد؛ صدای ذخیره‌شده دیگر در دستگاه موجود نیست و صدای پیش‌فرض فارسی انتخاب شد."
                            } else {
                                "موتور گفتار دوباره آماده شد؛ برای پخش، خودت نمونه صدا را اجرا کن."
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            if (page == "voice") showPage("voice")
                        }
                    } catch (_: Exception) {
                        ttsReady = false
                        setVoiceState(AvaVoiceState.ERROR)
                        if (showFeedback) {
                            Toast.makeText(this, "آماده‌سازی موتور گفتار ناموفق بود؛ تنظیمات و داده‌های زبان فارسی را بررسی کن.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            speech = currentEngine
        } catch (_: Exception) {
            ttsReady = false
            speech = null
            setVoiceState(AvaVoiceState.ERROR)
            if (showFeedback) {
                Toast.makeText(this, "ساخت دوباره موتور گفتار ممکن نشد؛ سرویس تبدیل متن به گفتار دستگاه را بررسی کن.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun speakAva(value: String, utteranceId: String): Boolean {
        if (aacs.isPublicOnlyModeActive()) return false
        // A playback request is also a request to end any active microphone
        // session, even when the TTS engine is unavailable or not ready.
        // Invalidate recognizer callbacks before checking playback readiness.
        stopAvaVoice()
        if (!ttsReady || speech == null) {
            setVoiceState(AvaVoiceState.ERROR)
            Toast.makeText(this, "صدای آوا هنوز آماده نیست؛ پشتیبانی زبان فارسی و سرویس گفتار دستگاه را بررسی کن.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!requestAvaAudioFocus()) {
            setVoiceState(AvaVoiceState.ERROR)
            Toast.makeText(this, "اولویت صوتی دستگاه در دسترس نیست؛ پخش آوا آغاز نشد.", Toast.LENGTH_LONG).show()
            return false
        }
        activeUtteranceId = utteranceId
        val result = try {
            speech?.speak(value, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (_: Exception) {
            null
        }
        return if (result == null || result == TextToSpeech.ERROR) {
            if (activeUtteranceId == utteranceId) activeUtteranceId = null
            abandonAvaAudioFocus()
            setVoiceState(AvaVoiceState.ERROR)
            false
        } else {
            setVoiceState(AvaVoiceState.SPEAKING)
            true
        }
    }

    private fun isOwnerConversationContext(): Boolean =
        !aacs.isPublicOnlyModeActive() &&
            (!aacs.hasActiveSession() ||
                (aacs.currentSessionLevel() == 1 && aacs.currentSessionPersonId() == "owner"))

    private fun avaHubPage() {
        contentArea.addView(
            AvaHubScreen(
                context = this,
                lightTheme = lightTheme,
                glassAlpha = glassAlpha,
                fontScale = fontScale,
                accentColor = accent,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                onNavigate = { destination -> showPage(destination) }
            ).build()
        )
    }

    private fun chatPage() {
        val heading = text("گفتگو با آوا", 21f, accent, true)
        contentArea.addView(heading)
        contentArea.addView(label("حباب‌های شیشه‌ای • فضای آرام و شخصی", 11f).apply { setPadding(0, dp(4), 0, dp(10)) })
        voiceStatusView = label(voiceStateLabel(), 11f).apply {
            setTextColor(voiceStateColor())
            setPadding(0, dp(2), 0, dp(6))
        }
        contentArea.addView(voiceStatusView)
        val stopButton = button("⏹ توقف فوری گفتار آوا") { stopAvaVoice() }.apply {
            visibility = if (
                voiceState == AvaVoiceState.SPEAKING ||
                voiceState == AvaVoiceState.LISTENING ||
                voiceState == AvaVoiceState.PROCESSING
            ) View.VISIBLE else View.GONE
            setMarginsCompat(top = 4)
        }
        voiceStopButton = stopButton
        contentArea.addView(stopButton)
        if (aacs.isPublicOnlyModeActive()) {
            contentArea.addView(label(
                "حالت حریم خصوصی عمومی فعاله: حافظه‌های شخصی قفل‌اند و پیام‌ها به سرویس خارجی ارسال نمی‌شن.",
                12f, Color.rgb(238, 194, 220)
            ).apply { setPadding(dp(10), dp(8), dp(10), dp(8)); background = rounded(Color.argb(45, 219, 170, 210), 14) })
        } else {
            contentArea.addView(button("🔒 فعال‌سازی فوری حریم خصوصی") {
                val saved = aacs.publicOnlyMode("privacy mode activated directly from conversation")
                stopAvaVoice()
                conversationGeneration += 1L
                conversationHistory.clear()
                chatDraftText = ""
                followUpController.clear()
                Toast.makeText(
                    this,
                    if (saved) "حالت عمومی فعال شد؛ تاریخچه نشست پاک و ارسال بیرونی متوقف شد."
                    else "محدودیت حریم خصوصی در این نشست فعال شد، اما ثبت پایدار یا امنیتی کامل نشد.",
                    Toast.LENGTH_LONG
                ).show()
                showPage("chat")
            }.apply { setMarginsCompat(top = 2) })
        }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            background = rounded(Color.argb(18, 255, 255, 255), 22)
            addView(messages)
        }
        contentArea.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (conversationHistory.isEmpty()) {
            val greetingName = when {
                aacs.isPublicOnlyModeActive() -> "دوست من"
                aacs.hasActiveSession() ->
                    activeSessionDisplayName ?: profileStore.all().firstOrNull { it.id == aacs.currentSessionPersonId() }?.name ?: "دوست من"
                else -> "امیر جان"
            }
            addBubble("سلام $greetingName 💜\\nبه گفتگوی آوا خوش اومدی.", true)
        } else {
            conversationHistory.forEach { item ->
                addBubble(item.optString("content"), item.optString("role") == "assistant")
            }
        }
        if (isOwnerConversationContext()) {
            followUpController.takeSuggestionForChatOpen()?.let { suggestion ->
                addBubble(suggestion, true)
            }
        } else {
            followUpController.clear()
        }

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(6), dp(6))
            background = rounded(Color.argb(48, 255, 255, 255), 24, Color.argb(95, 231, 192, 224))
        }
        val input = EditText(this).apply {
            hint = "پیامت رو بنویس..."
            setHintTextColor(secondaryTextColor)
            setTextColor(primaryTextColor)
            textSize = 15f * fontScale
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            minHeight = dp(48)
            maxHeight = dp(112)
            gravity = Gravity.CENTER_VERTICAL
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setText(chatDraftText)
            setSelection(text.length)
        }
        chatInput = input
        input.contentDescription = "متن پیام"
        var sendButton: TextView? = null
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                chatDraftText = s?.toString().orEmpty()
                sendButton?.let {
                    it.isEnabled = !s.isNullOrBlank()
                    it.alpha = if (it.isEnabled) 1f else 0.45f
                }
            }
            override fun afterTextChanged(s: Editable?) { }
        })

        val sendMessage: () -> Unit = {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                val previousUserMessage = conversationHistory.lastOrNull {
                    it.optString("role") == "user"
                }?.optString("content")
                val followUpSuggestion = followUpController.onUserMessage(
                    message,
                    previousUserMessage,
                    isOwnerConversationContext(),
                    aacs.isPublicOnlyModeActive()
                )
                addBubble(message, false)
                conversationHistory.add(org.json.JSONObject().put("role", "user").put("content", message))
                if (!followUpSuggestion.isNullOrBlank()) {
                    addBubble(followUpSuggestion, true)
                }
                input.text.clear()
                val speakerName = if (aacs.hasActiveSession()) {
                    activeSessionDisplayName ?: profileStore.all().firstOrNull { it.id == aacs.currentSessionPersonId() }?.name ?: "دوست من"
                } else "امیر"
                // Public-only mode is a privacy boundary: do not transmit this session's text externally.
                val allowExternal = !aacs.isPublicOnlyModeActive() &&
                    (!aacs.hasActiveSession() ||
                        (aacs.currentSessionLevel() == 1 && aacs.currentSessionPersonId() == "owner"))
                val requestGeneration = conversationGeneration
                val requestPageGeneration = pageGeneration
                conversationEngine.send(conversationHistory.toList(), speakerName, allowExternal) { reply, _, error ->
                    if (requestGeneration != conversationGeneration) return@send
                    if (!reply.isNullOrBlank()) {
                        conversationHistory.add(org.json.JSONObject().put("role", "assistant").put("content", reply))
                        if (page == "chat") addBubble(reply, true)
                        val voiceConfig = AvaSecurePrefs(this, "ava_voice_config")
                        if (page == "chat" && pageGeneration == requestPageGeneration &&
                            !aacs.isPublicOnlyModeActive() && voiceConfig.getBoolean("auto_speak", false)
                        ) {
                            speakAva(reply, "ava_reply_${System.currentTimeMillis()}")
                        }
                    } else if (page == "chat") {
                        addBubble(error ?: "پاسخی دریافت نشد.", true)
                    }
                }
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
        composer.minimumHeight = dp(56)
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(
            secondaryButton("🎙") { startSpeechInput() }.apply {
                contentDescription = "شروع ورودی گفتار"
            },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )
        val sendButtonView = button("➤") { sendMessage() }.apply {
            contentDescription = "ارسال پیام"
            minimumWidth = dp(48)
            isEnabled = input.text.isNotBlank()
            alpha = if (isEnabled) 1f else 0.45f
        }
        sendButton = sendButtonView
        composer.addView(sendButtonView, LinearLayout.LayoutParams(dp(48), dp(48)))
        val params = LinearLayout.LayoutParams(-1, -2)
        params.topMargin = dp(10)
        contentArea.addView(composer, params)
    }

    private fun addBubble(value: String, fromAva: Boolean) {
        val bubble = text(value, 15f, Color.WHITE).apply {
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(
                if (fromAva) Color.argb(75, 205, 158, 213) else Color.argb(58, 255, 255, 255),
                19,
                if (fromAva) Color.argb(120, 233, 193, 226) else Color.argb(80, 255, 255, 255)
            )
        }
        val row = LinearLayout(this).apply {
            gravity = if (fromAva) Gravity.START else Gravity.END
            setPadding(0, dp(5), 0, dp(5))
            addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { width = (resources.displayMetrics.widthPixels * 0.76f).toInt() })
        }
        messages.addView(row)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun memoryPage() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text("حافظه آوا", 22f, accent, true))
        val selected = selectedMemoryPersonId?.let { id -> profileStore.all().firstOrNull { it.id == id } }
        val bucket = selected?.let { "memories_" + it.id } ?: "memories_amir"
        val title = selected?.name ?: "امیر • حافظه شخصی"
        val activeSession = aacs.hasActiveSession()
        val sessionPersonId = aacs.currentSessionPersonId()
        val sessionMatchesSelected = !activeSession || (selected != null && selected.id == sessionPersonId)
        val relationshipMemoryAllowed = selected == null || selected.relationshipMemoryApproved
        if (activeSession && (!sessionMatchesSelected || !aacs.canCurrentSessionAccess(AvaDataClass.PERSONAL) || !relationshipMemoryAllowed)) {
            body.addView(text("دسترسی این نشست محدود است", 18f, accent, true))
            body.addView(label("این نشست فقط می‌تواند فضای شخصی خودش را ببیند و خاطرات رابطه نیز به تأیید مالک نیاز دارد.", 13f))
            val lockedScroll = ScrollView(this)
            lockedScroll.addView(body)
            contentArea.addView(lockedScroll)
            return
        }
        if (!activeSession && !relationshipMemoryAllowed) {
            body.addView(label("حافظه این شخص تا زمان تأیید مالک قفل است.", 13f))
            val lockedScroll = ScrollView(this)
            lockedScroll.addView(body)
            contentArea.addView(lockedScroll)
            return
        }
        body.addView(label("فضای فعال: $title. حافظه هر شخص جدا نگهداری می‌شود.", 12f).apply { setPadding(0, dp(6), 0, dp(10)) })
        if (!activeSession) {
            val vaultHealthText = when (localStore.vaultStatus()) {
                "vault_health_ok" -> "بررسی سلامت هفتگی موفق"
                "vault_health_failed" -> "هشدار: بررسی سلامت نیازمند رسیدگی است"
                "vault_sync_failed" -> "همگام‌سازی صف ناموفق بود"
                "vault_safe_mode_queueing" -> "در حال نگهداری تغییرات در صف امن"
                else -> "مقصد رمزگذاری‌شده فعال"
            }
            val vaultStatus = when {
                !localStore.isVaultConfigured() -> "حافظه اصلی microSD هنوز راه‌اندازی نشده؛ فعلاً از ذخیره‌سازی داخلی رمزگذاری‌شده استفاده می‌شود."
                localStore.isVaultSafeMode() -> "حالت امن: microSD در دسترس نیست. تغییرات جدید فقط در صف رمزگذاری‌شده محدود نگهداری می‌شوند. صف: ${localStore.vaultPendingCount()}"
                else -> "حافظه اصلی رمزگذاری‌شده فعال است • $vaultHealthText"
            }
            body.addView(label(vaultStatus, 11f).apply { setPadding(0, 0, 0, dp(8)) })
            if (!localStore.isVaultConfigured()) {
                body.addView(button("راه‌اندازی حافظه اصلی microSD") { requestVaultSetup() })
                val migrationState = AvaSecurePrefs(this, "ava_migration_state")
                if (migrationState.getBoolean("portable_restore_verified", false)) {
                    body.addView(button("آماده‌سازی کارت قدیمی برای این دستگاه") {
                        requestMigrationCardPreparation()
                    }.apply { setMarginsCompat(top = 8) })
                    body.addView(label("این گزینه فقط پس از بازیابی موفق پشتیبان رمزگذاری‌شده نمایش داده می‌شود؛ کلید دستگاه قبلی به گوشی جدید منتقل نمی‌شود.", 11f))
                }
            } else {
                body.addView(button("همگام‌سازی صف حافظه") {
                    val synced = localStore.flushVaultQueue()
                    if (synced) profileStore.refreshCacheFromVault()
                    Toast.makeText(this, if (synced) "همگام‌سازی انجام شد." else "حافظه هنوز در دسترس نیست.", Toast.LENGTH_LONG).show()
                    showPage("memory")
                })
            }
        }
        if (!activeSession) {
            body.addView(button("انتخاب فضای حافظه") {
                val profiles = profileStore.all()
                val names = (listOf("امیر • حافظه شخصی") + profiles.map { it.name }).toTypedArray()
                AlertDialog.Builder(this).setTitle("انتخاب حافظه").setItems(names) { _, which ->
                    selectedMemoryPersonId = if (which == 0) null else profiles[which - 1].id
                    showPage("memory")
                }.show()
            })
        }
        val input = EditText(this).apply { hint = "یادداشت تازه..."; setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY); background = rounded(Color.argb(35,255,255,255)); setPadding(dp(12),dp(10),dp(12),dp(10)) }
        val temporary = CheckBox(this).apply {
            text = "یادداشت موقت؛ حذف خودکار پس از ۷ روز"
            setTextColor(primaryTextColor)
            isChecked = true
            isEnabled = !activeSession
        }
        val permittedContent = CheckBox(this).apply {
            text = "تأیید می‌کنم این محتوا برای ذخیره‌سازی مجاز است"
            setTextColor(primaryTextColor)
        }
        body.addView(input)
        body.addView(temporary)
        body.addView(permittedContent)
        body.addView(label("موقت بودن، مجوز نگهداری اطلاعات ممنوع یا غیرمجاز نیست. مشاهده یادداشت زمان انقضا را تمدید نمی‌کند.", 11f))
        body.addView(button("ذخیره یادداشت") {
            val value = input.text.toString().trim()
            val canSave = selected == null || selected.relationshipMemoryApproved
            val sessionCanWrite = !aacs.hasActiveSession() ||
                (selected != null && selected.id == aacs.currentSessionPersonId() &&
                    aacs.canCurrentSessionAccess(AvaDataClass.PERSONAL))
            if (!permittedContent.isChecked) {
                Toast.makeText(this, "ابتدا مجاز بودن محتوای یادداشت را تأیید کن.", Toast.LENGTH_LONG).show()
            } else if (!canSave || !sessionCanWrite) {
                Toast.makeText(this, "ثبت یا مشاهده این فضای حافظه با سطح دسترسی فعلی مجاز نیست.", Toast.LENGTH_LONG).show()
            } else if (value.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val isTemporary = temporary.isChecked
                localStore.add(bucket, mapOf(
                    "text" to value,
                    "retention" to if (isTemporary) "temporary" else "permanent",
                    "expiresAt" to if (isTemporary) (now + 7L * 24L * 60L * 60L * 1000L).toString() else "0"
                ))
                showPage("memory")
            }
        }.apply { setMarginsCompat(top = 8) })
        if (!activeSession) {
            body.addView(button("ساخت پشتیبان رمزگذاری‌شده") { requestBackupExport() }.apply { setMarginsCompat(top = 8) })
            body.addView(button("تنظیم پشتیبان‌گیری خودکار روی حافظه مستقل") { requestAutoBackupSetup() }.apply { setMarginsCompat(top = 8) })
            body.addView(button("توقف پشتیبان‌گیری خودکار") { stopAutoBackup() }.apply { setMarginsCompat(top = 8) })
            val backupStatusStore = AvaSecurePrefs(this, "ava_backup_config")
            val backupStatus = backupStatusStore.getString("last_backup_status", "پشتیبان‌گیری خودکار هنوز تنظیم نشده است.")
            val backupHealth = backupStatusStore.getString("last_backup_health_status", "بررسی سلامت پشتیبان هنوز اجرا نشده است.")
            val restoreTest = backupStatusStore.getString("last_backup_restore_test_status", "آزمون بازیابی ایزوله هنوز اجرا نشده است.")
            body.addView(label(backupStatus ?: "وضعیت پشتیبان در دسترس نیست.", 11f).apply { setPadding(0, dp(8), 0, dp(4)) })
            body.addView(label(backupHealth ?: "وضعیت سلامت پشتیبان در دسترس نیست.", 11f).apply { setPadding(0, dp(4), 0, dp(4)) })
            body.addView(label(restoreTest ?: "وضعیت آزمون بازیابی در دسترس نیست.", 11f).apply { setPadding(0, dp(4), 0, dp(4)) })
            body.addView(button("بازیابی از پشتیبان") { requestBackupImport() }.apply { setMarginsCompat(top = 8) })
        }
        val memoryItems = localStore.all(bucket)
        if (memoryItems.isEmpty()) {
            val empty = card().apply { setMarginsCompat(top = 10) }
            empty.addView(text("این فضای حافظه هنوز خالی است", 15f, accent, true))
            empty.addView(label("یادداشت‌های مجاز این بخش پس از ثبت، با محدوده دسترسی همین فضا نگهداری می‌شوند.", 12f)
                .apply { setPadding(0, dp(6), 0, 0) })
            body.addView(empty)
        }
        memoryItems.forEachIndexed { index, item ->
            val row = card().apply { setMarginsCompat(top = 8) }
            row.addView(text(item.optString("text"), 14f))
            if (item.optString("retention") == "temporary") {
                val expiresAt = item.optLong("expiresAt", item.optLong("createdAt") + 7L * 24L * 60L * 60L * 1000L)
                val expiryText = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(expiresAt))
                row.addView(label("موقت • انقضا: $expiryText", 11f))
                if (!activeSession) {
                    row.addView(button("تمدید ۷ روزه با دلیل") {
                        val reason = EditText(this).apply {
                            hint = "دلیل تمدید را بنویس"
                            setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
                        }
                        AlertDialog.Builder(this).setTitle("تمدید حافظه موقت")
                            .setMessage("تمدید فقط با تأیید مالک و ثبت دلیل انجام می‌شود.")
                            .setView(reason)
                            .setNegativeButton("انصراف", null)
                            .setPositiveButton("تأیید تمدید") { _, _ ->
                                val why = reason.text.toString().trim()
                                if (why.isBlank()) {
                                    Toast.makeText(this, "دلیل تمدید الزامی است.", Toast.LENGTH_LONG).show()
                                } else if (!aacs.recordOwnerEvent("TEMP_MEMORY_EXTENDED", "bucket=$bucket; index=$index; reason=$why")) {
                                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ تمدید انجام نشد.", Toast.LENGTH_LONG).show()
                                } else {
                                    localStore.extendTemporaryMemory(bucket, index, why)
                                    showPage("memory")
                                }
                            }.show()
                    }.apply { setMarginsCompat(top = 6) })
                }
            } else {
                row.addView(label("نگهداری دائمی", 11f))
            }
            row.addView(button("حذف") {
                AlertDialog.Builder(this)
                    .setTitle("حذف یادداشت")
                    .setMessage("این یادداشت از فضای حافظه انتخاب‌شده حذف می‌شود. این کار قابل بازگشت از همین صفحه نیست.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف یادداشت") { _, _ ->
                        // Re-check the active session boundary immediately before mutation.
                        val sessionStillAllowsDelete = !aacs.hasActiveSession() ||
                            (selected != null &&
                                selected.id == aacs.currentSessionPersonId() &&
                                aacs.canCurrentSessionAccess(AvaDataClass.PERSONAL) &&
                                selected.relationshipMemoryApproved)
                        if (!sessionStillAllowsDelete) {
                            Toast.makeText(this, "نشست یا سطح دسترسی تغییر کرده است؛ حذف انجام نشد.", Toast.LENGTH_LONG).show()
                        } else {
                            localStore.remove(bucket, index)
                            showPage("memory")
                        }
                    }
                    .show()
            }.apply { setMarginsCompat(top = 6) })
            body.addView(row)
        }
        val scroll = ScrollView(this); scroll.addView(body); contentArea.addView(scroll)
    }

    private fun profilePage() {
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(18)) }
        val panel = card().apply { gravity = Gravity.CENTER }
        val preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(156), dp(156))
            background = rounded(Color.rgb(91, 65, 105), 90, Color.rgb(229, 190, 222))
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        avatarLarge = preview
        refreshAvatar()
        panel.addView(preview)
        panel.addView(text("شخصی‌سازی آوا", 22f, accent, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(4)) })
        panel.addView(label("تصویر پروفایل و ظاهر برنامه را تغییر بده.", 13f).apply { gravity = Gravity.CENTER })
        panel.addView(button("انتخاب عکس از گالری") { chooseAvatar() }.apply { setMarginsCompat(top = 18) })
        panel.addView(button("بازگشت به تصویر پیش‌فرض") {
            profileUri = null
            prefs.edit().remove("avatar_uri").apply()
            refreshAvatar()
        }.apply { setMarginsCompat(top = 10) })
        body.addView(panel)

        val people = card().apply { setMarginsCompat(top = 14) }
        people.addView(text("پروفایل‌های اشخاص", 18f, accent, true))
        people.addView(button("افزودن پروفایل شخص جدید") { createPersonProfileDialog() }.apply { setMarginsCompat(top = 10) })
        profileStore.all().forEach { person ->
            val personCard = card().apply { setMarginsCompat(top = 9) }
            personCard.addView(text(person.name, 17f, accent, true))
            personCard.addView(label("سطح ${person.level} • ${person.relation}", 13f))
            personCard.addView(label(if (person.intimacy == 0) "${person.occupation} • صمیمیت بسیار بالا، امتیاز عددی تعیین نشده" else "${person.occupation} • صمیمیت ${person.intimacy} از ۵", 12f))
            personCard.addView(label("سبک گفتگو: ${person.speakingStyle}", 12f))
            if (person.preferences.isNotBlank()) personCard.addView(label("ترجیحات: ${person.preferences}", 11f))
            if (person.privacyRestrictions.isNotBlank()) personCard.addView(label("محدودیت حریم خصوصی: ${person.privacyRestrictions}", 11f))
            if (person.relationshipMemoryApproved && person.relationshipHistory.isNotBlank()) {
                personCard.addView(label("پیشینه رابطه: ${person.relationshipHistory}", 11f))
            } else if (person.pendingRelationshipHistory.isNotBlank()) {
                personCard.addView(label("پیشینه رابطه در حافظه قفل‌شده است؛ برای فعال‌سازی به تأیید مالک نیاز دارد.", 11f))
            }
            personCard.addView(label("منبع اطلاعات: ${person.informationProvenance}", 10f))
            personCard.addView(label(if (person.photoConsent) "دریافت رضایت عکس ثبت شده" else "عکس: بدون رضایت شخص، ذخیره نمی‌شود", 11f))
            if (person.photoConsent) {
                person.photoUri?.let { savedUri ->
                    val personPhoto = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(92), dp(92))
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try { setImageURI(Uri.parse(savedUri)) } catch (_: Exception) { }
                    }
                    personCard.addView(personPhoto)
                }
                personCard.addView(button("انتخاب عکس شخص") { choosePersonPhoto(person) }.apply { setMarginsCompat(top = 7) })
            }
            personCard.addView(button("ویرایش پروفایل") { editPersonProfileDialog(person) }.apply { setMarginsCompat(top = 7) })
            personCard.addView(button("مدیریت تأییدها") { showPersonConsentDialog(person) }.apply { setMarginsCompat(top = 7) })
            people.addView(personCard)
        }
        body.addView(people)
        body.addView(personalitySettingsCard().apply { setMarginsCompat(top = 14) })

        val theme = card().apply { setMarginsCompat(top = 14) }
        theme.addView(text("رنگ و حال‌وهوا", 18f, accent, true))
        theme.addView(label("پالت فعلی: یاسی سلطانی، صورتی ملایم و رزگلد", 13f).apply { setPadding(0, dp(8), 0, dp(12)) })
        val palettes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        palettes.addView(button("یاسی") { applyPalette(0) }, LinearLayout.LayoutParams(0, -2, 1f))
        palettes.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        palettes.addView(button("رزگلد") { applyPalette(1) }, LinearLayout.LayoutParams(0, -2, 1f))
        palettes.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        palettes.addView(button("شب") { applyPalette(2) }, LinearLayout.LayoutParams(0, -2, 1f))
        palettes.addView(button("روشن") { applyPalette(3) }, LinearLayout.LayoutParams(0, -2, 1f))
        theme.addView(palettes)

        var selectedFontPercent = (fontScale * 100).toInt()
        var selectedGlassAlpha = glassAlpha
        val fontLabel = label("اندازه نوشته: $selectedFontPercent٪", 12f).apply { setPadding(0, dp(12), 0, dp(4)) }
        val fontSeek = SeekBar(this).apply {
            max = 40
            progress = (selectedFontPercent - 85).coerceIn(0, 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedFontPercent = 85 + progress
                    fontLabel.text = "اندازه نوشته: $selectedFontPercent٪"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            })
        }
        val glassLabel = label("شفافیت قاب شیشه‌ای: $selectedGlassAlpha", 12f).apply { setPadding(0, dp(10), 0, dp(4)) }
        val glassSeek = SeekBar(this).apply {
            max = 75
            progress = (selectedGlassAlpha - 15).coerceIn(0, 75)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedGlassAlpha = 15 + progress
                    glassLabel.text = "شفافیت قاب شیشه‌ای: $selectedGlassAlpha"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            })
        }
        theme.addView(fontLabel); theme.addView(fontSeek)
        theme.addView(glassLabel); theme.addView(glassSeek)
        val avatarSizeLabel = label("اندازه تصویر آوا: $avatarSize", 12f).apply { setPadding(0, dp(10), 0, dp(4)) }
        var selectedAvatarSize = avatarSize
        val avatarSizeSeek = SeekBar(this).apply {
            max = 100
            progress = (selectedAvatarSize - 100).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedAvatarSize = 100 + progress
                    avatarSizeLabel.text = "اندازه تصویر آوا: $selectedAvatarSize"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            })
        }
        val avatarVisibility = CheckBox(this).apply {
            text = "نمایش تصویر آوا در خانه و نوار بالا"
            setTextColor(primaryTextColor)
            isChecked = avatarVisible
        }
        theme.addView(avatarSizeLabel); theme.addView(avatarSizeSeek); theme.addView(avatarVisibility)
        theme.addView(button("اعمال تنظیمات ظاهری") {
            fontScale = selectedFontPercent / 100f
            glassAlpha = selectedGlassAlpha
            avatarSize = selectedAvatarSize
            avatarVisible = avatarVisibility.isChecked
            synchronized(AvaBackupSnapshotLock.lock) {
                prefs.edit().putInt("font_scale", selectedFontPercent)
                    .putInt("glass_alpha", glassAlpha)
                    .putInt("avatar_size", avatarSize)
                    .putBoolean("avatar_visible", avatarVisible).apply()
            }
            buildShell()
            showPage("profile")
        }.apply { setMarginsCompat(top = 8) })
        body.addView(theme)
        scroll.addView(body)
        contentArea.addView(scroll)
    }

    private fun wardrobePage() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        val ownerOnly = AvaWardrobeMediaPolicy.canAccessOwnerMedia(
            aacs.isPublicOnlyModeActive(),
            aacs.hasActiveSession(),
            aacs.isCurrentSessionApproved(),
            aacs.currentSessionLevel(),
            aacs.currentSessionPersonId()
        )
        if (!ownerOnly) {
            val locked = card()
            locked.addView(text("مجموعه خصوصی", 20f, accent, true))
            locked.addView(label("این بخش فقط پس از ورود و احراز هویت نشست مالک در دسترس است.", 13f))
            locked.addView(button("بازگشت به مرکز آوا") { showPage("ava") }.apply { setMarginsCompat(top = 12) })
            body.addView(locked)
            contentArea.addView(body)
            return
        }

        val heading = card()
        heading.addView(text("AVA INFINITY WARDROBE", 20f, accent, true))
        heading.addView(label("همه مجموعه‌های تأییدشده آوا • بخش خصوصی فقط برای مالک قابل مشاهده است", 12f)
            .apply { setPadding(0, dp(6), 0, 0) })
        body.addView(heading)
        body.addView(button("＋ افزودن تصویر از گوشی") { showAddWardrobeImageDialog() }
            .apply { setMarginsCompat(top = 10) })
        body.addView(label("تصاویر انتخابی تو در حافظه داخلی همین گوشی ذخیره می‌شوند و در همین بخش قابل مشاهده خواهند بود.", 11f)
            .apply { setPadding(dp(2), dp(6), dp(2), 0) })
        body.addView(label("نکته پشتیبان: تصاویر شخصی فعلاً داخل فایل پشتیبان رمزگذاری‌شده قرار نمی‌گیرند. پیش از حذف برنامه یا پاک‌کردن داده‌های آن، نسخه جداگانه‌ای از عکس‌ها نگه دار.", 11f, Color.rgb(190, 132, 75))
            .apply { setPadding(dp(2), dp(6), dp(2), dp(4)) })

        try {
            val raw = assets.open("ava-wardrobe-catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val catalog = org.json.JSONObject(raw)
            if (catalog.optInt("schemaVersion", -1) != 1) throw IllegalStateException("Unsupported wardrobe schema")
            val items = catalog.optJSONArray("items") ?: throw IllegalStateException("Missing wardrobe items")
            var shown = 0
            val coreLooks = catalog.optJSONArray("coreLooks") ?: org.json.JSONArray()
            if (coreLooks.length() > 0) {
                body.addView(label("استایل‌های پایه", 15f, accent).apply { setPadding(0, dp(12), 0, dp(2)) })
                for (index in 0 until coreLooks.length()) {
                    val item = coreLooks.optJSONObject(index) ?: continue
                    if (item.optString("approval") != "APPROVED_BY_AMIR") continue
                    val name = item.optString("name").trim()
                    val description = item.optString("description").trim()
                    if (name.isBlank() || description.isBlank()) continue
                    val entry = card().apply { setMarginsCompat(top = 8) }
                    entry.addView(text(name, 16f, accent, true))
                    entry.addView(label(description, 13f).apply { setPadding(0, dp(5), 0, 0) })
                    body.addView(entry)
                    shown++
                }
            }
            val categoryLabels = mapOf(
                "private" to "مجموعه خصوصی",
                "sporty" to "اسپرت",
                "everyday" to "روزمره",
                "formal" to "رسمی و شب",
                "homewear" to "لباس راحتی"
            )
            var lastCategory = ""
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                if (item.optString("approval") != "APPROVED_BY_AMIR") continue
                val id = item.optInt("id", -1)
                val name = item.optString("name").trim()
                val description = item.optString("description").trim()
                val category = item.optString("category").trim()
                if (id <= 0 || name.isBlank() || description.isBlank()) continue
                if (category != lastCategory) {
                    body.addView(label(categoryLabels[category] ?: "سایر مدل‌ها", 15f, accent)
                        .apply { setPadding(0, dp(14), 0, dp(2)) })
                    lastCategory = category
                }
                val entry = card().apply { setMarginsCompat(top = 8) }
                entry.addView(text("$id. $name", 16f, accent, true))
                entry.addView(label(description, 13f).apply { setPadding(0, dp(5), 0, 0) })
                entry.addView(label("تأیید طراحی: امیر • تصویر نهایی: در انتظار تهیه", 10f)
                    .apply { setPadding(0, dp(6), 0, 0) })
                body.addView(entry)
                shown++
            }
            if (shown == 0) {
                body.addView(label("هنوز مدل تأییدشده‌ای در کاتالوگ وجود ندارد.", 13f))
            }
            body.addView(label("تصاویر نمونه نمایش‌داده‌شده در مرحله طراحی، هنوز فایل تصویری داخل برنامه نیستند. این کاتالوگ فقط ثبت داده و نمایش اطلاعات لباس را انجام می‌دهد.", 11f)
                .apply { setPadding(dp(2), dp(12), dp(2), dp(4)) })
        } catch (_: Exception) {
            val error = card().apply { setMarginsCompat(top = 10) }
            error.addView(text("کاتالوگ در دسترس نیست", 16f, accent, true))
            error.addView(label("خواندن یا اعتبارسنجی فایل کمد انجام نشد؛ داده‌ها تغییر داده نشدند.", 12f))
            body.addView(error)
        }
        // Owner-added wardrobe images are stored privately in app-internal storage.
        try {
            val savedImages = org.json.JSONArray(prefs.getString("owner_wardrobe_images", "[]") ?: "[]")
            if (savedImages.length() > 0) {
                body.addView(label("تصاویر اضافه‌شده توسط امیر", 17f, accent).apply { setPadding(0, dp(18), 0, dp(4)) })
                val imageRoot = java.io.File(filesDir, "ava-wardrobe").canonicalFile
                for (index in 0 until savedImages.length()) {
                    val item = savedImages.optJSONObject(index) ?: continue
                    val storedPath = item.optString("file", "")
                    val file = try {
                        java.io.File(storedPath).canonicalFile
                    } catch (_: Exception) {
                        continue
                    }
                    if (!AvaWardrobeMediaPolicy.isSafeImageFile(imageRoot, file)) continue
                    val entry = card().apply { setMarginsCompat(top = 8) }
                    entry.addView(text(item.optString("name", "تصویر کمد"), 15f, accent, true))
                    entry.addView(label(item.optString("categoryLabel", "بدون دسته"), 11f))
                    val preview = ImageView(this).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageBitmap(decodeWardrobePreview(file))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
                        ).apply { topMargin = dp(8) }
                    }
                    entry.addView(preview)
                    body.addView(entry)
                }
            }
        } catch (_: Exception) {
            body.addView(label("خواندن تصاویر شخصی کمد انجام نشد؛ فایل‌های قبلی حفظ شدند.", 11f))
        }
        try {
            val hairRaw = assets.open("ava-hairstyle-catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val hairCatalog = org.json.JSONObject(hairRaw)
            if (hairCatalog.optInt("schemaVersion", -1) != 1) throw IllegalStateException("Unsupported hairstyle schema")
            val hairItems = hairCatalog.optJSONArray("items") ?: org.json.JSONArray()
            body.addView(label("استودیوی مدل مو آوا", 17f, accent).apply { setPadding(0, dp(18), 0, dp(4)) })
            for (index in 0 until hairItems.length()) {
                val style = hairItems.optJSONObject(index) ?: continue
                val name = style.optString("name").trim()
                val fa = style.optString("fa").trim()
                val description = style.optString("description").trim()
                val imageReference = style.optString("imageReference").trim()
                val source = style.optString("source").trim()
                if (name.isBlank() || fa.isBlank()) continue
                val entry = card().apply { setMarginsCompat(top = 8) }
                entry.addView(text("${style.optInt("id")}. $fa • $name", 15f, accent, true))
                entry.addView(label(description, 12f).apply { setPadding(0, dp(4), 0, 0) })
                entry.addView(label(if (index == 0) "انتخاب امیر • مدل اصلی" else "پیشنهاد استایل آوا", 10f)
                    .apply { setPadding(0, dp(4), 0, 0) })
                if (imageReference.isNotBlank()) entry.addView(label("مرجع تصویر: $imageReference", 9f))
                if (source.isNotBlank()) entry.addView(label("منبع: $source", 9f))
                body.addView(entry)
            }
        } catch (_: Exception) {
            body.addView(label("کاتالوگ مدل مو فعلاً در دسترس نیست.", 11f))
        }
        body.addView(button("بازگشت به مرکز آوا") { showPage("ava") }.apply { setMarginsCompat(top = 12) })
        contentArea.addView(body)
    }

    private fun decodeWardrobePreview(file: java.io.File): android.graphics.Bitmap? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) {
                sample *= 2
            }
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun showAddWardrobeImageDialog() {
        val ownerOnly = AvaWardrobeMediaPolicy.canAccessOwnerMedia(
            aacs.isPublicOnlyModeActive(),
            aacs.hasActiveSession(),
            aacs.isCurrentSessionApproved(),
            aacs.currentSessionLevel(),
            aacs.currentSessionPersonId()
        )
        if (!ownerOnly) {
            Toast.makeText(this, "برای افزودن تصویر، نشست تأییدشده مالک لازم است.", Toast.LENGTH_LONG).show()
            return
        }
        val categories = arrayOf("اسپرت", "روزمره", "رسمی و شب", "لباس راحتی", "مجموعه خصوصی", "مدل مو", "سایر")
        val keys = arrayOf("sporty", "everyday", "formal", "homewear", "private", "hairstyle", "other")
        val input = EditText(this).apply {
            hint = "نام لباس یا تصویر"
            setSingleLine(true)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
            addView(label("دسته‌بندی تصویر", 12f).apply { setPadding(0, dp(10), 0, dp(2)) })
            addView(spinner)
        }
        AlertDialog.Builder(this)
            .setTitle("افزودن تصویر به کمد آوا")
            .setView(container)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("انتخاب عکس") { _, _ ->
                pendingWardrobePhotoName = input.text.toString().trim().ifBlank { "تصویر جدید کمد" }
                pendingWardrobePhotoCategory = keys[spinner.selectedItemPosition]
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, 4104)
            }.show()
    }

    private fun saveOwnerWardrobeImage(uri: Uri) {
        val ownerOnly = AvaWardrobeMediaPolicy.canAccessOwnerMedia(
            aacs.isPublicOnlyModeActive(),
            aacs.hasActiveSession(),
            aacs.isCurrentSessionApproved(),
            aacs.currentSessionLevel(),
            aacs.currentSessionPersonId()
        )
        val name = pendingWardrobePhotoName
        val category = pendingWardrobePhotoCategory
        pendingWardrobePhotoName = null
        pendingWardrobePhotoCategory = null
        if (!ownerOnly || name.isNullOrBlank() || category.isNullOrBlank()) {
            Toast.makeText(this, "نشست مالک معتبر نیست؛ تصویر ذخیره نشد.", Toast.LENGTH_LONG).show()
            return
        }
        if (!aacs.recordOwnerEvent("WARDROBE_IMAGE_SAVE_REQUESTED", "category=$category")) {
            Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ تصویر ذخیره نشد.", Toast.LENGTH_LONG).show()
            return
        }
        var pendingImageFile: java.io.File? = null
        try {
            val directory = java.io.File(filesDir, "ava-wardrobe").apply { if (!exists() && !mkdirs()) error("Cannot create private image directory") }
            val fileId = java.util.UUID.randomUUID().toString()
            val temporary = java.io.File(directory, "wardrobe_$fileId.pending")
            pendingImageFile = temporary
            var total = 0L
            val imageDigest = java.security.MessageDigest.getInstance("SHA-256")
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 15L * 1024L * 1024L) error("Image exceeds 15 MB")
                        imageDigest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: error("Unable to read selected image")

            val header = ByteArray(12)
            val headerBytes = java.io.FileInputStream(temporary).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count < 0) break
                    offset += count
                }
                offset
            }
            val format = AvaWardrobeImageFormat.detect(header.copyOf(headerBytes))
                ?: error("Unsupported or invalid image signature")
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width > 12000 || height > 12000 ||
                width.toLong() * height.toLong() > 40_000_000L
            ) error("Image dimensions are invalid or exceed the safe limit")

            val destination = java.io.File(directory, "wardrobe_$fileId" + format.extension)
            if (!temporary.renameTo(destination)) error("Unable to finalize validated image")
            pendingImageFile = destination
            val saved = org.json.JSONArray(prefs.getString("owner_wardrobe_images", "[]") ?: "[]")
            val categoryLabel = when (category) {
                "sporty" -> "اسپرت"
                "everyday" -> "روزمره"
                "formal" -> "رسمی و شب"
                "homewear" -> "لباس راحتی"
                "private" -> "مجموعه خصوصی"
                "hairstyle" -> "مدل مو"
                else -> "سایر"
            }
            val digestHex = imageDigest.digest().joinToString("") { byte ->
                "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
            }
            saved.put(org.json.JSONObject()
                .put("itemId", fileId)
                .put("name", name)
                .put("category", category)
                .put("categoryLabel", categoryLabel)
                .put("mimeType", format.mimeType)
                .put("byteLength", total)
                .put("sha256", digestHex)
                .put("file", destination.canonicalPath)
                .put("addedAt", System.currentTimeMillis()))
            if (!prefs.edit().putString("owner_wardrobe_images", saved.toString()).commit()) error("Unable to save image index")
            if (!aacs.recordOwnerEvent("WARDROBE_IMAGE_SAVED", "category=$category;bytes=$total")) {
                saved.remove(saved.length() - 1)
                prefs.edit().putString("owner_wardrobe_images", saved.toString()).commit()
                destination.delete()
                pendingImageFile = null
                Toast.makeText(this, "ثبت امنیتی کامل نشد؛ تصویر جدید لغو شد.", Toast.LENGTH_LONG).show()
                return
            }
            pendingImageFile = null
            Toast.makeText(this, "تصویر به کمد آوا اضافه شد.", Toast.LENGTH_LONG).show()
            showPage("wardrobe")
        } catch (_: Exception) {
            pendingImageFile?.delete()
            aacs.recordOwnerEvent("WARDROBE_IMAGE_SAVE_FAILED", "selected image could not be stored")
            Toast.makeText(this, "ذخیره تصویر انجام نشد؛ اطلاعات قبلی حفظ شدند. حجم مجاز هر عکس ۱۵ مگابایت است.", Toast.LENGTH_LONG).show()
        }
    }

    private fun secretPage() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text("AVA SECRET", 22f, accent, true))
        if (aacs.isPublicOnlyModeActive() ||
            (aacs.hasActiveSession() &&
                (aacs.currentSessionLevel() != 1 || aacs.currentSessionPersonId() != "owner"))
        ) {
            body.addView(label("فرهنگ واژگان در این نشست قفل است. فقط مالک می‌تواند واژه‌ها را مدیریت یا منتشر کند.", 13f))
            contentArea.addView(body)
            return
        }
        try {
            localStore.migrateLegacyVocabularyRecords()
        } catch (_: Exception) {
            body.addView(label("مهاجرت امن واژه‌های قدیمی انجام نشد؛ داده‌ها حفظ شده‌اند و تغییرات متوقف شد.", 12f))
            contentArea.addView(body)
            return
        }
        val entries = localStore.all("vocabulary")
            .filter { it.optString("status", "legacy_review_required") != "archived" }
        val categories = entries.map { it.optString("category", "عمومی").ifBlank { "عمومی" } }.distinct().sorted()
        if (entries.isEmpty()) {
            val empty = card().apply { setMarginsCompat(top = 10) }
            empty.addView(text("فرهنگ واژگان هنوز خالی است", 15f, accent, true))
            empty.addView(label("می‌توانی نخستین واژه را به‌صورت پیش‌نویس ثبت کنی؛ تا تأیید مالک منتشر نمی‌شود.", 12f)
                .apply { setPadding(0, dp(6), 0, 0) })
            body.addView(empty)
        }
        body.addView(label("واژه تازه ابتدا پیشنویس است. انتشار در فرهنگ معتبر فقط با احراز هویت مالک انجام می‌شود. معادل‌های چندزبانه، تلفظ و تاریخچه نسخه‌ها نگهداری می‌شوند.", 12f)
            .apply { setPadding(0, dp(6), 0, dp(10)) })
        body.addView(button(
            selectedVocabularyCategory?.let { "فیلتر فعال: $it • تغییر فیلتر" } ?: "فیلتر دسته‌بندی"
        ) {
            val choices = (listOf("همه دسته‌ها") + categories).toTypedArray()
            AlertDialog.Builder(this).setTitle("دسته‌بندی واژه‌ها").setItems(choices) { _, which ->
                selectedVocabularyCategory = if (which == 0) null else categories[which - 1]
                showPage("secret")
            }.show()
        })
        if (selectedVocabularyCategory != null) {
            body.addView(button("نمایش همه دسته‌ها") {
                selectedVocabularyCategory = null
                showPage("secret")
            }.apply { setMarginsCompat(top = 6) })
        }
        body.addView(button("افزودن پیشنویس واژه") {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(4), dp(8), 0)
            }
            fun field(hintText: String) = EditText(this).apply {
                hint = hintText
                setTextColor(primaryTextColor)
                setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
            }
            val word = field("واژه یا عبارت")
            val meaning = field("معنی یا کاربرد")
            val category = field("دسته‌بندی")
            val equivalentLanguage = field("کد زبان معادل، مانند en یا ar (اختیاری)").apply { setText("en") }
            val equivalentValue = field("معادل در زبان انتخاب‌شده (اختیاری)")
            val pronunciation = field("راهنمای تلفظ (اختیاری)")
            box.addView(word); box.addView(meaning); box.addView(category)
            box.addView(equivalentLanguage); box.addView(equivalentValue); box.addView(pronunciation)
            AlertDialog.Builder(this).setTitle("پیشنویس تازه").setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره پیشنویس") { _, _ ->
                    try {
                        if (word.text.isNotBlank() && meaning.text.isNotBlank()) {
                            localStore.addVocabulary(
                                word.text.toString(),
                                meaning.text.toString(),
                                category.text.toString(),
                                "fa",
                                if (equivalentLanguage.text.isNotBlank() && equivalentValue.text.isNotBlank())
                                    mapOf(equivalentLanguage.text.toString().trim() to equivalentValue.text.toString())
                                else emptyMap(),
                                pronunciation.text.toString()
                            )
                            showPage("secret")
                        } else {
                            Toast.makeText(this, "واژه و معنی الزامی هستند.", Toast.LENGTH_LONG).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this, "ذخیره پیشنویس انجام نشد؛ دسترسی و سلامت حافظه را بررسی کن.", Toast.LENGTH_LONG).show()
                    }
                }.show()
        }.apply { setMarginsCompat(top = 8) })

        entries.forEach { item ->
            val entryId = item.optString("id")
            val category = item.optString("category", "عمومی").ifBlank { "عمومی" }
            if (selectedVocabularyCategory == null || selectedVocabularyCategory == category) {
                val status = item.optString("status", "legacy_review_required")
                val statusLabel = when (status) {
                    "approved" -> "منتشرشده و معتبر"
                    "draft" -> "پیشنویس • منتشر نشده"
                    else -> "نسخه قدیمی • نیازمند بازبینی مالک"
                }
                val row = card().apply { setMarginsCompat(top = 8) }
                row.addView(text(item.optString("word"), 16f, accent, true))
                row.addView(label(item.optString("meaning"), 13f))
                row.addView(label("دسته: $category • نسخه ${item.optInt("version", 1)}", 11f))
                row.addView(label(statusLabel, 11f, if (status == "approved") Color.rgb(170, 225, 190) else Color.rgb(242, 205, 155)))
                val equivalents = item.optJSONObject("equivalents")
                if (equivalents != null && equivalents.length() > 0) {
                    val langs = equivalents.keys()
                    while (langs.hasNext()) {
                        val code = langs.next()
                        row.addView(label("معادل $code: ${equivalents.optString(code)}", 11f))
                    }
                }
                if (item.optString("pronunciation").isNotBlank()) {
                    row.addView(label("تلفظ: ${item.optString("pronunciation")}", 11f))
                }
                row.addView(button("ویرایش و ایجاد نسخه پیشنویس") {
                    editVocabularyDialog(item)
                }.apply { setMarginsCompat(top = 6) })
                if (status != "approved") {
                    row.addView(button("تأیید و انتشار با احراز هویت مالک") {
                        requestVocabularyOwnerAction("ava_secret_publish", entryId)
                    }.apply { setMarginsCompat(top = 6) })
                }
                row.addView(button("تاریخچه و بازگردانی نسخه") {
                    showVocabularyHistory(item)
                }.apply { setMarginsCompat(top = 6) })
                row.addView(button("بایگانی واژه با احراز هویت مالک") {
                    requestVocabularyOwnerAction("ava_secret_archive", entryId)
                }.apply { setMarginsCompat(top = 6) })
                body.addView(row)
            }
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        contentArea.addView(scroll)
    }

    private fun editVocabularyDialog(item: org.json.JSONObject) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        fun field(value: String, hintText: String) = EditText(this).apply {
            setText(value)
            hint = hintText
            setTextColor(primaryTextColor)
            setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
        }
        val word = field(item.optString("word"), "واژه")
        val meaning = field(item.optString("meaning"), "معنی")
        val category = field(item.optString("category", "عمومی"), "دسته‌بندی")
        val equivalentLanguage = field("en", "کد زبان معادل برای افزودن، تغییر یا حذف")
        val equivalentValue = field(item.optJSONObject("equivalents")?.optString("en").orEmpty(), "معادل در این زبان؛ خالی = حذف همین معادل")
        val pronunciation = field(item.optString("pronunciation"), "تلفظ")
        box.addView(word); box.addView(meaning); box.addView(category)
        box.addView(equivalentLanguage); box.addView(equivalentValue); box.addView(pronunciation)
        AlertDialog.Builder(this).setTitle("ویرایش واژه")
            .setMessage("ذخیره تغییرات، نسخه جدید و پیشنویس منتشرنشده می‌سازد؛ انتشار دوباره نیازمند تأیید مالک است.")
            .setView(box)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره نسخه پیشنویس") { _, _ ->
                try {
                    val saved = localStore.updateVocabulary(
                        item.optString("id"),
                        word.text.toString(),
                        meaning.text.toString(),
                        category.text.toString(),
                        mapOf(equivalentLanguage.text.toString().trim() to equivalentValue.text.toString()),
                        pronunciation.text.toString()
                    )
                    if (saved) showPage("secret")
                    else Toast.makeText(this, "واژه برای ویرایش پیدا نشد.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "ویرایش انجام نشد؛ داده قبلی حفظ شد.", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun showVocabularyHistory(item: org.json.JSONObject) {
        val history = item.optJSONArray("history") ?: org.json.JSONArray()
        val versions = (0 until history.length()).mapNotNull { index ->
            history.optJSONObject(index)?.let { version ->
                index to "نسخه ${version.optInt("version")} • ${version.optString("word")} • ${version.optString("status", "قدیمی")}"
            }
        }
        val message = if (versions.isEmpty()) "هنوز نسخه قبلی برای این واژه ثبت نشده است." else versions.joinToString("\\n") { it.second }
        val builder = AlertDialog.Builder(this).setTitle("تاریخچه ${item.optString("word")}")
            .setMessage(message).setPositiveButton("بستن", null)
        if (versions.isNotEmpty()) {
            builder.setNeutralButton("بازگردانی نسخه") { _, _ ->
                AlertDialog.Builder(this).setTitle("انتخاب نسخه برای بازگردانی")
                    .setItems(versions.map { it.second }.toTypedArray()) { _, which ->
                        val target = versions.getOrNull(which)?.first?.let { history.optJSONObject(it)?.optInt("version") }
                            ?: return@setItems
                        requestVocabularyOwnerAction("ava_secret_rollback", item.optString("id"), target)
                    }.show()
            }
        }
        builder.show()
    }

    private fun requestVocabularyOwnerAction(action: String, entryId: String, targetVersion: Int? = null) {
        if (action !in setOf("ava_secret_publish", "ava_secret_rollback", "ava_secret_archive")) return
        if (aacs.isPublicOnlyModeActive() ||
            (aacs.hasActiveSession() &&
                (aacs.currentSessionLevel() != 1 || aacs.currentSessionPersonId() != "owner"))
        ) {
            Toast.makeText(this, "این عملیات فقط در نشست مالک مجاز است.", Toast.LENGTH_LONG).show()
            return
        }
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(this, "برای انتشار، بازگردانی یا بایگانی، قفل امن دستگاه باید فعال باشد.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "تأیید مالک AVA SECRET",
            "برای انجام این تغییر، هویت مالک را تأیید کن."
        )
        if (intent == null) {
            Toast.makeText(this, "احراز هویت امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        pendingVocabularyAction = action
        pendingVocabularyEntryId = entryId
        pendingVocabularyTargetVersion = targetVersion
        startActivityForResult(intent, 6106)
    }

    private fun dailyPage() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text("دستیار روزانه", 22f, accent, true))
        body.addView(label("کارها با اولویت، توضیح و تاریخ سررسید ذخیره می‌شوند. اعلان زمان‌بندی‌شده هنوز فعال نیست.", 12f)
            .apply { setPadding(0, dp(6), 0, dp(10)) })
        body.addView(button("افزودن کار") { addTaskDialog() })
        val tasks = localStore.all("tasks")
        if (tasks.isEmpty()) {
            val empty = card().apply { setMarginsCompat(top = 12) }
            empty.addView(text("هنوز کاری ثبت نشده", 16f, accent, true))
            empty.addView(label("وقتی کاری اضافه کنی، وضعیت و سررسیدش اینجا نمایش داده می‌شود.", 12f)
                .apply { setPadding(0, dp(6), 0, 0) })
            body.addView(empty)
        }
        tasks.forEachIndexed { index, item ->
            val row = card().apply { setMarginsCompat(top = 8) }
            val check = CheckBox(this).apply {
                text = item.optString("title")
                setTextColor(primaryTextColor)
                isChecked = item.optString("done") == "true"
                setOnCheckedChangeListener { _, checked ->
                    try {
                        localStore.setDone(index, checked)
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "وضعیت کار ذخیره نشد؛ اطلاعات قبلی حفظ شدند.", Toast.LENGTH_LONG).show()
                        showPage("daily")
                    }
                }
            }
            row.addView(check)
            val detail = item.optString("detail")
            if (detail.isNotBlank()) row.addView(label(detail, 12f))
            val dueAt = item.optLong("dueAt", 0L)
            if (dueAt > 0L) {
                val dueLabel = java.text.DateFormat.getDateInstance().format(java.util.Date(dueAt))
                val overdue = dueAt < System.currentTimeMillis() && !check.isChecked
                row.addView(label(
                    (if (overdue) "عقب‌افتاده • " else "سررسید • ") + dueLabel,
                    11f
                ))
            }
            row.addView(label("اولویت: ${item.optString("priority", "معمولی")}", 11f))
            row.addView(button("حذف کار") {
                AlertDialog.Builder(this)
                    .setTitle("حذف این کار؟")
                    .setMessage("«${item.optString("title")}» از فهرست کارها حذف می‌شود.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف کار") { _, _ ->
                        localStore.remove("tasks", index)
                        showPage("daily")
                    }
                    .show()
            }.apply { setMarginsCompat(top = 6) })
            body.addView(row)
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        contentArea.addView(scroll)
    }

    private fun addTaskDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        val title = EditText(this).apply { hint = "عنوان کار"; setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY) }
        val detail = EditText(this).apply { hint = "توضیحات اختیاری"; setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY) }
        val priority = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("کم", "معمولی", "بالا"))
            setSelection(1)
        }
        var dueAt: Long? = null
        val dueLabel = label("بدون تاریخ سررسید", 12f)
        val dueButton = button("تعیین تاریخ سررسید (اختیاری)") {
            val now = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    val selected = java.util.Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    dueAt = selected.timeInMillis
                    dueLabel.text = "سررسید: $day/${month + 1}/$year"
                },
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        box.addView(title); box.addView(detail); box.addView(label("اولویت", 12f)); box.addView(priority)
        box.addView(dueButton); box.addView(dueLabel)
        AlertDialog.Builder(this).setTitle("کار تازه").setView(box)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره کار") { _, _ ->
                val value = title.text.toString().trim()
                if (value.isNotBlank()) {
                    localStore.add("tasks", mapOf(
                        "title" to value,
                        "detail" to detail.text.toString().trim(),
                        "priority" to priority.selectedItem.toString(),
                        "dueAt" to (dueAt?.toString() ?: "0"),
                        "done" to "false"
                    ))
                    showPage("daily")
                }
            }.show()
    }

    private fun voicePage() {
        val panel = card()
        val config = AvaSecurePrefs(this, "ava_voice_config")
        panel.addView(text("صدا و گفتار", 22f, accent, true))
        voiceStatusView = label(voiceStateLabel(), 11f).apply { setPadding(0, dp(4), 0, dp(8)) }
        panel.addView(voiceStatusView)
        panel.addView(label("ورودی گفتار فارسی با سرویس تشخیص گفتار اندروید انجام می‌شود؛ بسته به سرویس انتخابی دستگاه، پردازش صدا ممکن است بیرون از گوشی انجام شود. خروجی با موتور TTS نصب‌شده است و جنسیت و لهجه به صدای نصب‌شده وابسته‌اند.", 13f)
            .apply { setPadding(0, dp(8), 0, dp(12)) })
        panel.addView(label("تشخیص خودکار صدای امیر در زمان پخش آوا هنوز فعال نیست؛ برای جلوگیری از قطع اشتباه با صدای محیط یا بازشنوی صدای آوا، از توقف فوری یا دکمه شروع گفتار استفاده کن.", 11f))
        if (!aacs.isPublicOnlyModeActive()) {
            panel.addView(button("شروع گفتار فارسی") { startSpeechInput() })
        } else {
            panel.addView(label("در حالت حریم خصوصی عمومی، میکروفون و خواندن بلند پاسخ‌ها غیرفعال‌اند تا گفت‌وگو ناخواسته افشا نشود.", 12f))
        }
        val persianVoices = speech?.voices?.filter { it.locale.language == "fa" }?.sortedBy { it.name } ?: emptyList()
        val voiceOptions = listOf("پیش‌فرض دستگاه") + persianVoices.map { voice ->
            voice.name + if (voice.isNetworkConnectionRequired) " • آنلاین" else " • محلی"
        }
        val voicePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, voiceOptions)
            val savedName = config.getString("voice_name", null)
            setSelection((persianVoices.indexOfFirst { it.name == savedName } + 1).coerceAtLeast(0))
        }
        panel.addView(label("صدای فارسی نصب‌شده", 12f))
        panel.addView(voicePicker)
        voicePicker.isEnabled = ttsReady && persianVoices.isNotEmpty()
        panel.addView(label(
            if (ttsReady && persianVoices.isNotEmpty()) "نام صدا و لهجه از موتور گفتار دستگاه می‌آیند؛ صدای آنلاین ممکن است به اینترنت نیاز داشته باشد."
            else "فهرست صداهای فارسی پس از آماده‌شدن موتور گفتار نمایش داده می‌شود. تنظیمات سرعت و زیر و بمی را می‌توانی ذخیره کنی تا هنگام آماده‌سازی بعدی اعمال شوند.",
            11f
        ))
        panel.addView(button("راه‌اندازی دوباره موتور گفتار") {
            initializeAvaTts(showFeedback = true)
        }.apply { setMarginsCompat(top = 8) })
        var rate = config.getInt("rate", 100).coerceIn(70, 140)
        var pitch = config.getInt("pitch", 106).coerceIn(70, 140)
        val rateLabel = label("سرعت گفتار: $rate٪", 12f).apply { setPadding(0, dp(12), 0, dp(4)) }
        val rateSeek = SeekBar(this).apply {
            max = 70
            progress = rate - 70
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    rate = 70 + progress
                    rateLabel.text = "سرعت گفتار: $rate٪"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            })
        }
        val pitchLabel = label("زیر و بمی صدا: $pitch٪", 12f).apply { setPadding(0, dp(10), 0, dp(4)) }
        val pitchSeek = SeekBar(this).apply {
            max = 70
            progress = pitch - 70
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pitch = 70 + progress
                    pitchLabel.text = "زیر و بمی صدا: $pitch٪"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            })
        }
        panel.addView(rateLabel); panel.addView(rateSeek)
        panel.addView(pitchLabel); panel.addView(pitchSeek)
        val autoSpeak = CheckBox(this).apply {
            text = "خواندن خودکار پاسخ‌های آوا"
            setTextColor(primaryTextColor)
            isChecked = config.getBoolean("auto_speak", false)
            isEnabled = !aacs.isPublicOnlyModeActive()
        }
        panel.addView(autoSpeak)
        panel.addView(button("اعمال تنظیمات صدا") {
            val activeEngine = speech
            val canChangeVoice = voicePicker.isEnabled
            val selectedVoice = if (canChangeVoice) {
                persianVoices.getOrNull(voicePicker.selectedItemPosition - 1)
            } else null
            if (!ttsReady || activeEngine == null) {
                val pendingEditor = config.edit()
                    .putInt("rate", rate)
                    .putInt("pitch", pitch)
                    .putBoolean("auto_speak", autoSpeak.isChecked)
                // Preserve the saved voice while the engine is unavailable and the picker is disabled.
                synchronized(AvaBackupSnapshotLock.lock) { pendingEditor.apply() }
                Toast.makeText(this, "سرعت، زیر و بمی و حالت خواندن ذخیره شدند؛ پس از آماده‌شدن موتور اعمال می‌شوند. انتخاب صدا هنوز تغییر نکرده است.", Toast.LENGTH_LONG).show()
                return@button
            }

            val oldRate = config.getInt("rate", 100).coerceIn(70, 140)
            val oldPitch = config.getInt("pitch", 106).coerceIn(70, 140)
            val oldVoiceName = config.getString("voice_name", null)
            val oldVoice = activeEngine.voices?.firstOrNull { it.name == oldVoiceName && it.locale.language == "fa" }
            val applied = try {
                val rateResult = activeEngine.setSpeechRate(rate / 100f)
                val pitchResult = activeEngine.setPitch(pitch / 100f)
                val voiceResult = if (!canChangeVoice) {
                    TextToSpeech.SUCCESS
                } else if (selectedVoice == null) {
                    activeEngine.setLanguage(Locale("fa", "IR"))
                } else {
                    activeEngine.setVoice(selectedVoice)
                }
                rateResult == TextToSpeech.SUCCESS &&
                    pitchResult == TextToSpeech.SUCCESS &&
                    voiceResult >= TextToSpeech.LANG_AVAILABLE
            } catch (_: Exception) {
                false
            }

            if (!applied) {
                try {
                    activeEngine.setSpeechRate(oldRate / 100f)
                    activeEngine.setPitch(oldPitch / 100f)
                    if (oldVoice != null) activeEngine.setVoice(oldVoice)
                    else activeEngine.setLanguage(Locale("fa", "IR"))
                } catch (_: Exception) { }
                Toast.makeText(this, "موتور یکی از تنظیمات را نپذیرفت؛ تنظیمات ذخیره‌شده قبلی حفظ شدند. وضعیت موتور را بررسی کن.", Toast.LENGTH_LONG).show()
                return@button
            }

            val editor = config.edit().putInt("rate", rate).putInt("pitch", pitch)
                .putBoolean("auto_speak", autoSpeak.isChecked)
            if (canChangeVoice) {
                if (selectedVoice == null) editor.remove("voice_name") else editor.putString("voice_name", selectedVoice.name)
            }
            synchronized(AvaBackupSnapshotLock.lock) { editor.apply() }
            Toast.makeText(this, "تنظیمات صدا با موفقیت روی موتور فعال اعمال و ذخیره شدند.", Toast.LENGTH_SHORT).show()
        }.apply { setMarginsCompat(top = 8) })
        if (!aacs.isPublicOnlyModeActive()) {
            panel.addView(button("پخش نمونه صدای آوا") {
                if (!speakAva("سلام امیر جان، من آوا هستم. آماده‌ام.", "ava_greeting")) {
                    Toast.makeText(this, "موتور گفتار در دسترس نیست", Toast.LENGTH_SHORT).show()
                }
            }.apply { setMarginsCompat(top = 8) })
        }
        panel.addView(button("توقف فوری گفتار") {
            stopAvaVoice()
        }.apply { setMarginsCompat(top = 8) })
        contentArea.addView(panel)
    }

    private fun releaseSpeechRecognizer(
        recognizer: SpeechRecognizer,
        generation: Long,
        cancel: Boolean
    ): Boolean {
        if (speechRecognizer !== recognizer || voiceGeneration != generation) return false
        // Invalidate the session before invoking platform methods because they
        // may enqueue callbacks after cancellation or destruction.
        clearSpeechSessionTimeout()
        speechRecognizer = null
        speechSessionPage = null
        voiceGeneration += 1L
        try {
            if (cancel) recognizer.cancel()
        } catch (_: Exception) { }
        try {
            recognizer.destroy()
        } catch (_: Exception) { }
        return true
    }

    private fun startSpeechInput() {
        stopAvaVoice()
        if (aacs.isPublicOnlyModeActive()) {
            Toast.makeText(this, "در حالت حریم خصوصی عمومی، ورودی میکروفون برای جلوگیری از افشای ناخواسته غیرفعال است.", Toast.LENGTH_LONG).show()
            return
        }
        if (isFinishing || isDestroyed) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setVoiceState(AvaVoiceState.ERROR)
            Toast.makeText(this, "سرویس تشخیص گفتار روی دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setVoiceState(AvaVoiceState.ERROR)
            pendingSpeechPermissionPage = page
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 6201)
            return
        }
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) {
            setVoiceState(AvaVoiceState.ERROR)
            Toast.makeText(this, "ساخت سرویس تشخیص گفتار ممکن نشد.", Toast.LENGTH_LONG).show()
            return
        }
        val generation = voiceGeneration
        val requestedFromPage = page
        speechRecognizer = recognizer
        speechSessionPage = requestedFromPage
        fun isCurrentRecognizer(): Boolean =
            speechRecognizer === recognizer && voiceGeneration == generation

        try {
            recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrentRecognizer()) return
                    setVoiceState(AvaVoiceState.LISTENING)
                    Toast.makeText(this@MainActivity, "دارم گوش می‌دم…", Toast.LENGTH_SHORT).show()
                }
                override fun onBeginningOfSpeech() {
                    if (isCurrentRecognizer()) setVoiceState(AvaVoiceState.LISTENING)
                }
                override fun onRmsChanged(rmsdB: Float) { }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() {
                    if (isCurrentRecognizer()) {
                        setVoiceState(AvaVoiceState.PROCESSING)
                        scheduleSpeechSessionTimeout(
                            recognizer,
                            generation,
                            20_000L,
                            "پردازش گفتار بیش از حد طول کشید؛ نشست بسته شد. اگر خواستی دوباره دکمه میکروفون را بزن."
                        )
                    }
                }
                override fun onError(error: Int) {
                    if (!releaseSpeechRecognizer(recognizer, generation, false)) return
                    setVoiceState(AvaVoiceState.ERROR)
                    Toast.makeText(this@MainActivity, speechErrorMessage(error), Toast.LENGTH_LONG).show()
                }
                override fun onResults(results: Bundle?) {
                    if (!isCurrentRecognizer()) return
                    if (page != requestedFromPage || speechSessionPage != requestedFromPage) {
                        releaseSpeechRecognizer(recognizer, generation, true)
                        return
                    }
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!releaseSpeechRecognizer(recognizer, generation, false)) return
                    setVoiceState(AvaVoiceState.IDLE)
                    if (!spoken.isNullOrBlank() && !isFinishing && !isDestroyed) {
                        showPage("chat")
                        chatInput?.setText(spoken)
                        chatInput?.setSelection(spoken.length)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) { }
                override fun onEvent(eventType: Int, params: Bundle?) { }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(intent)
            if (isCurrentRecognizer()) {
                setVoiceState(AvaVoiceState.LISTENING)
                scheduleSpeechSessionTimeout(
                    recognizer,
                    generation,
                    45_000L,
                    "زمان دریافت گفتار به پایان رسید؛ نشست بسته شد. برای تلاش دوباره، دکمه میکروفون را خودت بزن."
                )
            }
        } catch (_: Exception) {
            releaseSpeechRecognizer(recognizer, generation, true)
            setVoiceState(AvaVoiceState.ERROR)
            Toast.makeText(this, "شروع ورودی گفتار ممکن نشد؛ سرویس تشخیص یا اجازه میکروفون را بررسی کن.", Toast.LENGTH_LONG).show()
        }
    }

    // Recovery is deliberately user-driven: never reopen the microphone or replay a
    // command automatically after a recognition-service failure.
    private fun speechErrorMessage(error: Int): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        ) {
            return "ارتباط با سرویس تشخیص گفتار قطع شد؛ نشست بسته شد و میکروفون خودکار دوباره فعال نمی‌شود. اگر خواستی، پس از بررسی سرویس، دستی دوباره تلاش کن."
        }
        return when (error) {
        SpeechRecognizer.ERROR_AUDIO ->
            "دریافت صدا ناموفق بود؛ میکروفون و مسیر ورودی صدا را بررسی کن و در صورت تمایل دوباره دکمه میکروفون را بزن."
        SpeechRecognizer.ERROR_CLIENT ->
            "سرویس تشخیص گفتار متوقف شد؛ چند لحظه صبر کن و برای شروع دوباره خودت دکمه میکروفون را بزن."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "اجازه میکروفون کافی نیست؛ دسترسی برنامه به میکروفون را در تنظیمات اندروید بررسی کن."
        SpeechRecognizer.ERROR_NETWORK ->
            "ارتباط شبکه با سرویس تشخیص گفتار برقرار نشد؛ اتصال را بررسی کن و بعداً با دکمه میکروفون دوباره تلاش کن."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "پاسخ سرویس تشخیص گفتار دیر رسید؛ اتصال را بررسی کن و در صورت تمایل دستی دوباره تلاش کن."
        SpeechRecognizer.ERROR_NO_MATCH ->
            "گفتار قابل تشخیصی دریافت نشد؛ محیط آرام‌تری انتخاب کن و دوباره دکمه میکروفون را بزن."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "سرویس تشخیص گفتار هنوز مشغول است؛ چند لحظه صبر کن و سپس دستی دوباره شروع کن."
        SpeechRecognizer.ERROR_SERVER ->
            "سرویس تشخیص گفتار خطا داد؛ چند لحظه صبر کن و فقط در صورت تمایل دوباره شروع کن."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "گفتاری دریافت نشد؛ برای تلاش دوباره، دکمه میکروفون را خودت بزن."
        else ->
            "تشخیص گفتار کامل نشد؛ نشست قبلی بسته شد و برای تلاش دوباره باید خودت دکمه میکروفون را بزنی."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 6201) {
            val requestedFromPage = pendingSpeechPermissionPage
            pendingSpeechPermissionPage = null
            val stillGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val sameContext = requestedFromPage != null &&
                requestedFromPage == page &&
                !aacs.isPublicOnlyModeActive()
            if (stillGranted && sameContext) {
                startSpeechInput()
            } else {
                setVoiceState(AvaVoiceState.IDLE)
                if (!stillGranted) {
                    Toast.makeText(this, "بدون اجازه میکروفون، ورودی گفتار فعال نمی‌شود.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun consolePage() {
        val panel = card()
        panel.addView(text("کنسول مرکزی", 22f, accent, true))
        panel.addView(label("وضعیت زنده هسته", 16f).apply { setPadding(0, dp(10), 0, dp(6)) })
        val backupConfig = AvaSecurePrefs(this, "ava_backup_config")
        val sessionState = if (aacs.hasActiveSession())
            "نشست سطح ${aacs.currentSessionLevel()} فعال"
        else "نشست شخصی فعال نیست"
        val privacyState = if (aacs.isPublicOnlyModeActive()) "حالت فقط عمومی فعال" else "حریم خصوصی عادی"
        val vaultState = if (!localStore.isVaultConfigured()) "حافظه اصلی: داخلی رمزگذاری‌شده"
            else "حافظه اصلی: ${localStore.vaultStatus()} • صف: ${localStore.vaultPendingCount()}"
        val backupState = backupConfig.getString("last_backup_status", "پشتیبان خودکار هنوز تنظیم نشده")
            ?: "پشتیبان خودکار هنوز تنظیم نشده"
        val aiState = if (conversationEngine.externalProviderEnabled()) "اتصال هوش مصنوعی خارجی مجاز است" else "اتصال خارجی خاموش است"
        listOf(
            sessionState,
            privacyState,
            vaultState,
            backupState,
            aiState,
            voiceStateLabel()
        ).forEach { panel.addView(label("• $it", 12f).apply { setPadding(0, dp(3), 0, dp(3)) }) }
        val quickActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quickActions.addView(button("امنیت") { showPage("security") }, LinearLayout.LayoutParams(0, -2, 1f))
        quickActions.addView(Space(this), LinearLayout.LayoutParams(dp(6), 1))
        quickActions.addView(button("حافظه") { showPage("memory") }, LinearLayout.LayoutParams(0, -2, 1f))
        quickActions.addView(Space(this), LinearLayout.LayoutParams(dp(6), 1))
        quickActions.addView(button("صدا") { showPage("voice") }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(quickActions)
        panel.addView(label("وضعیت ماژول‌های نسخه موبایل", 13f).apply { setPadding(0, dp(14), 0, dp(10)) })
        listOf(
            "رابط کاربری 2.0 و حباب‌های شیشه‌ای" to "نسخه پایه فعال",
            "پوسته روشن و تیره" to "یاسی، رزگلد، شب و روشن",
            "پروفایل اشخاص، رضایت عکس و PRP" to "فیلدها و کنترل رضایت فعال",
            "ذخیره محلی" to "AES-GCM با کلید Android Keystore",
            "AVA SECRET" to "دسته‌بندی، ویرایش و تاریخچه نسخه‌ها",
            "دستیار روزانه" to "کارها، اولویت و تاریخ سررسید",
            "ورودی و خروجی گفتار" to "تشخیص گفتار فارسی، TTS و خواندن خودکار اختیاری",
            "حریم خصوصی صوتی" to "میکروفون و پخش بلند در حالت عمومی خاموش",
            "موتور گفتگوی هوشمند" to "اتصال اختیاری سازگار با Chat Completions؛ بدون جریان زنده",
            "AACS" to "نشست، مجوز اختصاصی، حالت عمومی و ثبت رویداد",
            "پشتیبان‌گیری خودکار" to "روزانه، هفتگی، ماهانه و نسخه تغییرمحور؛ رمزگذاری و بازخوانی",
            "بازیابی پشتیبان" to "پیش‌نمایش، احراز هویت مالک و تأیید نهایی",
            "آزمون بازیابی ایزوله و مهاجرت بین دستگاه‌ها" to "هنوز تکمیل نشده",
            "گفت‌وگوی محلی" to "پاسخ‌های محدود؛ مدل زبانی کامل نیازمند اتصال اختیاری"
        ).forEach { (name, state) ->
            panel.addView(label("$name  •  $state", 13f).apply { setPadding(0, dp(5), 0, dp(5)) })
        }
        panel.addView(button("تنظیم موتور گفتگوی هوشمند") { configureConversationProvider() }
            .apply { setMarginsCompat(top = 12) })
        contentArea.addView(panel)
    }

    private fun configureConversationProvider() {
        if (!ownerStorageActionAllowed()) return
        val config = AvaSecurePrefs(this, "ava_ai_config")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        val endpoint = EditText(this).apply {
            hint = "نشانی HTTPS سازگار با Chat Completions"
            setText(config.getString("endpoint", "") ?: "")
            setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
        }
        val model = EditText(this).apply {
            hint = "شناسه مدل ارائه‌دهنده"
            setText(config.getString("model", "") ?: "")
            setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
        }
        val apiKey = EditText(this).apply {
            hint = "کلید API جدید (خالی = حفظ کلید قبلی)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
        }
        val enabled = CheckBox(this).apply {
            text = "اجازه ارسال پیام‌ها و تاریخچه اخیر به سرویس خارجی را می‌دهم"
            setTextColor(primaryTextColor)
            isChecked = config.getBoolean("external_ai_enabled", false)
        }
        box.addView(label("فقط HTTPS مجاز است. با فعال‌سازی، پیام‌ها و حداکثر ۲۰ نوبت اخیر برای ارائه‌دهنده انتخابی ارسال می‌شوند. ممکن است ارائه‌دهنده هزینه مصرف دریافت کند؛ هزینه و نگهداری داده تابع شرایط همان سرویس است.", 12f))
        box.addView(endpoint); box.addView(model); box.addView(apiKey); box.addView(enabled)
        AlertDialog.Builder(this).setTitle("اتصال اختیاری موتور هوشمند")
            .setView(box)
            .setNegativeButton("انصراف", null)
            .setNeutralButton("حذف کلید") { _, _ ->
                val oldKey = config.getString("api_key", null)
                val oldEnabled = config.getBoolean("external_ai_enabled", false)
                if (!aacs.recordOwnerEvent("AI_API_KEY_REMOVAL_REQUESTED", "owner requested API credential removal")) {
                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ کلید تغییر نکرد.", Toast.LENGTH_LONG).show()
                    return@setNeutralButton
                }
                val saved = config.edit().remove("api_key").putBoolean("external_ai_enabled", false).commit()
                if (!saved) {
                    Toast.makeText(this, "حذف کلید ذخیره نشد.", Toast.LENGTH_LONG).show()
                    return@setNeutralButton
                }
                if (!aacs.recordOwnerEvent("AI_API_KEY_REMOVED", "external provider disabled and API credential cleared")) {
                    val rollback = config.edit().putBoolean("external_ai_enabled", oldEnabled)
                    if (oldKey == null) rollback.remove("api_key") else rollback.putString("api_key", oldKey)
                    rollback.commit()
                    Toast.makeText(this, "ثبت نتیجه امنیتی ناموفق بود؛ تغییر کلید بازگردانی شد.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "کلید ذخیره‌شده حذف و اتصال خارجی غیرفعال شد.", Toast.LENGTH_LONG).show()
                }
            }
            .setPositiveButton("ذخیره تنظیمات") { _, _ ->
                val endpointValue = endpoint.text.toString().trim()
                val modelValue = model.text.toString().trim()
                val newKey = apiKey.text.toString()
                val existingKey = config.getString("api_key", null)
                val endpointUri = try { java.net.URI(endpointValue) } catch (_: Exception) { null }
                val endpointValid = endpointValue.isBlank() || (
                    endpointUri?.scheme.equals("https", ignoreCase = true) &&
                        !endpointUri?.host.isNullOrBlank() &&
                        endpointUri?.userInfo == null && endpointUri?.query == null && endpointUri?.fragment == null
                )
                if (!endpointValid || (enabled.isChecked &&
                    (endpointValue.isBlank() || modelValue.isBlank() || (newKey.isBlank() && existingKey.isNullOrBlank())))
                ) {
                    Toast.makeText(this, "نشانی باید HTTPS و بدون اطلاعات ورود در URL باشد؛ برای فعال‌سازی مدل و کلید API هم لازم است.", Toast.LENGTH_LONG).show()
                    apiKey.text.clear()
                } else {
                    val oldEndpoint = config.getString("endpoint", "") ?: ""
                    val oldModel = config.getString("model", "") ?: ""
                    val oldEnabled = config.getBoolean("external_ai_enabled", false)
                    if (!aacs.recordOwnerEvent(
                            "AI_PROVIDER_CONFIG_REQUESTED",
                            "externalEnabled=${enabled.isChecked}; host=${endpointUri?.host ?: "none"}; model=$modelValue"
                        )
                    ) {
                        apiKey.text.clear()
                        Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ تنظیم موتور ذخیره نشد.", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    val saved = synchronized(AvaBackupSnapshotLock.lock) {
                        val editor = config.edit()
                            .putString("endpoint", endpointValue)
                            .putString("model", modelValue)
                            .putBoolean("external_ai_enabled", enabled.isChecked)
                        if (newKey.isNotBlank()) editor.putString("api_key", newKey)
                        editor.commit()
                    }
                    apiKey.text.clear()
                    if (!saved) {
                        Toast.makeText(this, "ذخیره تنظیمات موتور ناموفق بود.", Toast.LENGTH_LONG).show()
                    } else if (!aacs.recordOwnerEvent(
                            "AI_PROVIDER_CONFIG_SAVED",
                            "externalEnabled=${enabled.isChecked}; host=${endpointUri?.host ?: "none"}; model=$modelValue"
                        )
                    ) {
                        synchronized(AvaBackupSnapshotLock.lock) {
                            val rollback = config.edit()
                                .putString("endpoint", oldEndpoint)
                                .putString("model", oldModel)
                                .putBoolean("external_ai_enabled", oldEnabled)
                            if (existingKey == null) rollback.remove("api_key") else rollback.putString("api_key", existingKey)
                            rollback.commit()
                        }
                        Toast.makeText(this, "ثبت نتیجه امنیتی ناموفق بود؛ تنظیمات موتور بازگردانی شد.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, if (enabled.isChecked) "اتصال خارجی با اجازه تو فعال شد." else "حالت محلی فعال است؛ پیامی به بیرون ارسال نمی‌شود.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun securityPage() {
        val panel = card()
        panel.addView(text("امنیت و سطح دسترسی", 22f, accent, true))
        val ownerContext = !aacs.isPublicOnlyModeActive() &&
            (!aacs.hasActiveSession() ||
                (aacs.currentSessionLevel() == 1 && aacs.currentSessionPersonId() == "owner"))
        if (!ownerContext) {
            panel.addView(label("این نشست به پنل مدیریتی یا گزارش‌های خصوصی دسترسی ندارد.", 13f))
            if (!aacs.isPublicOnlyModeActive() && aacs.hasActiveSession() && aacs.currentSessionLevel() in 2..3) {
                panel.addView(button("درخواست دسترسی خصوصی با تأیید جداگانه مالک") {
                    requestPrivateInformationApproval()
                }.apply { setMarginsCompat(top = 10) })
            }
            if (aacs.hasActiveSession()) {
                panel.addView(button("پایان نشست و لغو تأیید") {
                    aacs.endSession()
                    selectedMemoryPersonId = null
                    activeSessionDisplayName = null
                    conversationGeneration += 1L
                conversationHistory.clear()
                    chatDraftText = ""
                    followUpController.clear()
                    if (!aacs.isPublicOnlyModeActive()) AvaBackupScheduler.enqueueEventSnapshot(this)
                    Toast.makeText(this, "نشست پایان یافت؛ دسترسی‌های موقت لغو شدند.", Toast.LENGTH_LONG).show()
                    showPage("security")
                }.apply { setMarginsCompat(top = 12) })
            } else {
                panel.addView(button("احراز هویت مالک و خروج از حالت عمومی") {
                    requestDisablePublicOnlyMode()
                }.apply { setMarginsCompat(top = 12) })
            }
            contentArea.addView(panel)
            return
        }
        panel.addView(label("پایه کنترل دسترسی AACS فعال است: نشست، تأیید مالک، حالت عمومی و مجوزهای اختصاصی ثبت می‌شوند. اجرای کامل همه محدودیت‌ها در تمام ماژول‌ها هنوز در حال تکمیل است.", 13f).apply { setPadding(0, dp(8), 0, dp(12)) })
        listOf(
            "سطح ۱ • امیر: مالک و مدیر اصلی",
            "سطح ۲ • شریک عاطفی آینده: دسترسی نزدیک و محدودشده",
            "سطح ۳ • خانواده و دوستان نزدیک، از جمله آرش نادری",
            "سطح ۴ • آشنایان و افراد دیگر: دسترسی عمومی و محدود"
        ).forEach { line -> panel.addView(label(line, 13f).apply { setPadding(0, dp(6), 0, dp(6)) }) }
        panel.addView(label("اطلاعات خصوصی نیازمند تأیید جداگانه مالک است. تأیید از پنجره امن خود اندروید درخواست می‌شود؛ آوا هرگز PIN یا داده اثر انگشت را دریافت نمی‌کند.", 12f).apply { setPadding(0, dp(12), 0, 0) })
        panel.addView(button("شروع نشست مهمان (سطح ۴، بدون پروفایل دائمی)") {
            val guestName = EditText(this).apply {
                hint = "نام نمایشی اختیاری"
                setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
            }
            AlertDialog.Builder(this).setTitle("نشست مهمان")
                .setMessage("این نشست فقط به بخش‌های عمومی دسترسی دارد و هیچ پروفایل یا حافظه دائمی برای مهمان ساخته نمی‌شود.")
                .setView(guestName)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("شروع نشست عمومی") { _, _ ->
                    val displayName = guestName.text.toString().trim().ifBlank { "مهمان" }
                    val guestId = "guest-" + java.util.UUID.randomUUID().toString()
                    if (aacs.beginSession(guestId, 4)) {
                        val publicOnlySaved = aacs.publicOnlyMode("Level 4 ephemeral guest session")
                        stopAvaVoice()
                        if (publicOnlySaved) {
                            activeSessionDisplayName = displayName
                            selectedMemoryPersonId = null
                            conversationGeneration += 1L
                conversationHistory.clear()
                            chatDraftText = ""
                            followUpController.clear()
                            showPage("home")
                        } else {
                            aacs.endSession()
                            Toast.makeText(this, "ثبت پایدار حالت عمومی ناموفق بود؛ نشست مهمان آغاز نشد.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ نشست مهمان آغاز نشد.", Toast.LENGTH_LONG).show()
                    }
                }.show()
        }.apply { setMarginsCompat(top = 8) })
        panel.addView(button("شروع نشست برای پروفایل ثبت‌شده (سطح ۲ یا ۳)") {
            val eligible = profileStore.all().filter { it.level in 2..3 }
            if (eligible.isEmpty()) {
                Toast.makeText(this, "هنوز پروفایل سطح ۲ یا ۳ ثبت نشده است.", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("انتخاب شخص")
                    .setItems(eligible.map { it.name }.toTypedArray()) { _, which ->
                        val person = eligible.getOrNull(which) ?: return@setItems
                        AlertDialog.Builder(this)
                            .setTitle("معرفی شخص")
                            .setMessage("آیا شخص حاضر خودش را ${person.name} معرفی کرده است؟ پس از آن، تأیید مالک با قفل امن اندروید لازم است.")
                            .setNegativeButton("انصراف", null)
                            .setPositiveButton("بله، خودش را معرفی کرد") { _, _ ->
                                if (aacs.recordOwnerEvent("PERSON_SELF_IDENTIFIED", "personId=${person.id}; self-identification attested")) {
                                    requestSessionStartApproval(person.id, person.level)
                                } else {
                                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ نشست آغاز نشد.", Toast.LENGTH_LONG).show()
                                }
                            }.show()
                    }.setNegativeButton("بستن", null).show()
            }
        }.apply { setMarginsCompat(top = 8) })
        panel.addView(button("شروع نشست آزمایشی آرش") {
            val arash = profileStore.all().firstOrNull { it.id == "person-arash-naderi" }
            if (arash != null) {
                AlertDialog.Builder(this)
                    .setTitle("معرفی شخص")
                    .setMessage("آیا شخص حاضر خودش را آرش نادری معرفی کرده است؟ پس از این مرحله، تأیید مالک با قفل امن اندروید لازم است.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("بله، خودش را معرفی کرد") { _, _ ->
                        if (aacs.recordOwnerEvent("PERSON_SELF_IDENTIFIED", "personId=${arash.id}; self-identification attested")) {
                            requestSessionStartApproval(arash.id, arash.level)
                        } else {
                            Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ نشست آغاز نشد.", Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }
        }.apply { setMarginsCompat(top = 12) })
        panel.addView(button("تأیید مالک برای نشست فعلی") { requestOwnerSessionApproval() }.apply { setMarginsCompat(top = 8) })
        panel.addView(button("پایان نشست و لغو تأیید") {
            aacs.endSession()
            selectedMemoryPersonId = null
            activeSessionDisplayName = null
            conversationGeneration += 1L
                conversationHistory.clear()
            chatDraftText = ""
            followUpController.clear()
            Toast.makeText(this, "نشست پایان یافت و تأیید و تاریخچه لغو شدند.", Toast.LENGTH_SHORT).show()
            showPage("security")
        }.apply { setMarginsCompat(top = 8) })
        panel.addView(button("فعال‌سازی حالت فقط اطلاعات عمومی") {
            aacs.publicOnlyMode("manual privacy precaution")
            stopAvaVoice()
            conversationGeneration += 1L
                conversationHistory.clear()
            chatDraftText = ""
            followUpController.clear()
            Toast.makeText(this, "حالت اطلاعات عمومی فعال شد.", Toast.LENGTH_SHORT).show()
            showPage("home")
        }.apply { setMarginsCompat(top = 8) })
        addAacsPermissionEditor(panel)
        panel.addView(label("رویدادهای امنیتی اخیر", 17f).apply { setPadding(0, dp(18), 0, dp(8)) })
        val audit = aacs.auditSnapshot()
        if (audit == null) {
            panel.addView(label("هشدار: گزارش امنیتی قابل خواندن نیست. دسترسی‌های حساس باید بسته بمانند تا مشکل بررسی شود.", 12f, Color.rgb(255, 170, 170)))
        } else if (audit.length() == 0) {
            panel.addView(label("هنوز رویداد امنیتی ثبت نشده است.", 12f))
        } else {
            val start = (audit.length() - 15).coerceAtLeast(0)
            for (index in audit.length() - 1 downTo start) {
                val event = audit.optJSONObject(index) ?: continue
                val time = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(event.optLong("time")))
                panel.addView(label(
                    "$time • ${event.optString("event")} • ${event.optString("personId")}",
                    11f
                ).apply { setPadding(dp(6), dp(5), dp(6), dp(5)) })
            }
        }
        contentArea.addView(panel)
    }

    private fun addAacsPermissionEditor(panel: LinearLayout) {
        panel.addView(label("مدیریت مجوزهای اختصاصی", 17f).apply { setPadding(0, dp(18), 0, dp(8)) })
        panel.addView(label("توجه: مجوز خصوصیِ ذخیره‌شده هرگز جایگزین تأیید و احراز هویت جداگانه مالک نمی‌شود.", 11f))
        val profiles = profileStore.all().filter { it.level in 2..3 }
        if (profiles.isEmpty()) {
            panel.addView(label("برای تعریف مجوز اختصاصی، ابتدا یک پروفایل پایدار سطح ۲ یا ۳ بساز.", 12f))
            return
        }
        val personPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, profiles.map { it.name })
        }
        val dataPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("اطلاعات عمومی", "اطلاعات شخصی", "اطلاعات خصوصی", "کنترل و مدیریت"))
        }
        val allow = CheckBox(this).apply {
            text = "اجازه دسترسی فعال باشد"
            setTextColor(primaryTextColor)
        }
        val expiryPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("بدون تاریخ انقضا", "۱ ساعت", "۲۴ ساعت", "۷ روز"))
        }
        panel.addView(label("شخص", 12f)); panel.addView(personPicker)
        panel.addView(label("دسته اطلاعات", 12f)); panel.addView(dataPicker)
        panel.addView(allow); panel.addView(label("انقضای مجوز", 12f)); panel.addView(expiryPicker)
        panel.addView(button("ثبت مجوز اختصاصی") {
            val person = profiles.getOrNull(personPicker.selectedItemPosition) ?: return@button
            val dataClass = AvaDataClass.values()[dataPicker.selectedItemPosition]
            val duration = when (expiryPicker.selectedItemPosition) {
                1 -> 60L * 60L * 1000L
                2 -> 24L * 60L * 60L * 1000L
                3 -> 7L * 24L * 60L * 60L * 1000L
                else -> null
            }
            val expiry = duration?.let { System.currentTimeMillis() + it }
            val saved = aacs.setPersonPermission(person.id, dataClass, allow.isChecked, expiry)
            Toast.makeText(
                this,
                if (saved) "مجوز اختصاصی ثبت شد." else "ثبت مجوز به دلیل خطای ثبت امنیتی انجام نشد.",
                Toast.LENGTH_LONG
            ).show()
        }.apply { setMarginsCompat(top = 8) })
    }

    override fun onPause() {
        // Never keep microphone capture or speech output active while the app is backgrounded.
        pendingSpeechPermissionPage = null
        stopAvaVoice()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::localStore.isInitialized) {
            try {
                localStore.cleanupExpiredTemporaryMemories()
                if (localStore.isVaultConfigured()) {
                    val synced = localStore.flushVaultQueue()
                    if (synced && ::profileStore.isInitialized) profileStore.refreshCacheFromVault()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "حافظه آوا در حالت محافظت‌شده باقی ماند؛ داده خراب یا تأییدنشده بازنویسی نشد.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (navigationHistory.isNotEmpty()) {
            val previous = navigationHistory.removeAt(navigationHistory.lastIndex)
            suppressNavigationHistory = true
            showPage(previous)
            suppressNavigationHistory = false
            return
        }
        if (page != "home") {
            showPage("home")
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        ttsGeneration += 1L
        ttsReady = false
        stopAvaVoice()
        unregisterAudioNoisyReceiver()
        unregisterAudioDeviceCallback()
        speech?.shutdown()
        speech = null
        if (::conversationEngine.isInitialized) conversationEngine.close()
        followUpController.clear()
        super.onDestroy()
    }

    private fun requestDisablePublicOnlyMode() {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(this, "برای خروج از حالت عمومی، قفل امن دستگاه لازم است.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "خروج از حالت فقط عمومی",
            "برای بازکردن بخش‌های شخصی، هویت مالک را تأیید کن."
        )
        if (intent == null) {
            Toast.makeText(this, "تأیید امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, 6104)
    }

    private fun requestSessionStartApproval(personId: String, level: Int) {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(this, "برای آغاز نشست آرش، قفل امن دستگاه و تأیید مالک لازم است.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "تأیید مالک برای شروع نشست",
            "برای آغاز نشست این شخص، هویت مالک را در پنجره امن اندروید تأیید کن."
        )
        if (intent == null) {
            Toast.makeText(this, "تأیید امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        pendingSessionPersonId = personId
        pendingSessionLevel = level
        startActivityForResult(intent, 6103)
    }

    private fun requestPrivateInformationApproval() {
        if (!aacs.hasActiveSession() || aacs.currentSessionLevel() !in 2..3 || aacs.isPublicOnlyModeActive()) {
            Toast.makeText(this, "درخواست اطلاعات خصوصی برای این نشست مجاز نیست.", Toast.LENGTH_LONG).show()
            return
        }
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(this, "برای دسترسی خصوصی، قفل امن دستگاه باید فعال باشد.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "تأیید جداگانه دسترسی خصوصی",
            "برای اجازه یک‌باره به اطلاعات خصوصی این نشست، هویت مالک را تأیید کن."
        )
        if (intent == null) {
            Toast.makeText(this, "احراز هویت امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, 6107)
    }

    private fun requestOwnerSessionApproval() {
        if (!aacs.hasActiveSession() || aacs.currentSessionLevel() >= 4 || aacs.isPublicOnlyModeActive()) {
            Toast.makeText(this, "برای این نشست درخواست دسترسی خصوصی مجاز نیست.", Toast.LENGTH_LONG).show()
            return
        }
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(this, "برای تأیید مالک ابتدا قفل امن دستگاه را فعال کن.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent("تأیید مالک آوا", "برای تأیید دسترسی این نشست، هویت خود را تأیید کن.")
        if (intent == null) {
            Toast.makeText(this, "تأیید امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, 6101)
    }

    private fun editPersonProfileDialog(person: PersonProfile) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        fun field(value: String) = EditText(this).apply { setText(value); setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY) }
        val name = field(person.name)
        val relation = field(person.relation)
        val occupation = field(person.occupation)
        val interests = field(person.interests)
        val preferences = field(person.preferences)
        val privacyRestrictions = field(person.privacyRestrictions)
        val relationshipHistory = field(person.relationshipHistory).apply {
            isEnabled = person.relationshipMemoryApproved
            if (!person.relationshipMemoryApproved) hint = "پیشینه رابطه پس از تأیید مالک قابل ویرایش است"
        }
        val informationProvenance = field(person.informationProvenance)
        val style = field(person.speakingStyle)
        val level = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("سطح ۲ • شریک", "سطح ۳ • نزدیکان"))
            setSelection(person.level.coerceIn(2, 3) - 2)
        }
        val closeness = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("تعیین نشده", "۱", "۲", "۳", "۴", "۵"))
            setSelection(person.intimacy.coerceIn(0, 5))
        }
        listOf(name, relation, occupation, interests, preferences, privacyRestrictions, relationshipHistory, informationProvenance, style).forEach { box.addView(it) }
        box.addView(level); box.addView(closeness)
        AlertDialog.Builder(this).setTitle("ویرایش پروفایل").setView(box)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره تغییرات") { _, _ ->
                val newLevel = level.selectedItemPosition + 2
                val updatedProfile = person.copy(
                    name = name.text.toString().trim().ifBlank { person.name },
                    level = newLevel,
                    relation = relation.text.toString().trim(),
                    occupation = occupation.text.toString().trim(),
                    interests = interests.text.toString().trim(),
                    preferences = preferences.text.toString().trim(),
                    privacyRestrictions = privacyRestrictions.text.toString().trim(),
                    relationshipHistory = relationshipHistory.text.toString().trim(),
                    informationProvenance = informationProvenance.text.toString().trim().ifBlank { "ثبت‌شده توسط مالک" },
                    speakingStyle = style.text.toString().trim(),
                    intimacy = closeness.selectedItemPosition
                )
                if (!aacs.recordOwnerEvent("PROFILE_UPDATE_REQUESTED", "personId=${person.id}; level=$newLevel")) {
                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ پروفایل تغییر نکرد.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (newLevel > person.level && !aacs.onAccessDowngraded(person.id, newLevel)) {
                    Toast.makeText(this, "مجوزها برای ایمنی محدود شدند، اما ثبت امنیتی کامل نشد؛ تغییر پروفایل متوقف شد.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                try {
                    profileStore.save(updatedProfile)
                    if (!aacs.recordOwnerEvent("PROFILE_UPDATED", "personId=${person.id}; level=$newLevel")) {
                        val rolledBack = try { profileStore.save(person); true } catch (_: Exception) { false }
                        Toast.makeText(
                            this,
                            if (rolledBack) "ثبت نتیجه امنیتی ناموفق بود؛ پروفایل به نسخه قبلی برگشت."
                            else "هشدار: ثبت نتیجه و بازگردانی پروفایل کامل نشد؛ وضعیت نیازمند بررسی است.",
                            Toast.LENGTH_LONG
                        ).show()
                        if (rolledBack) showPage("profile")
                        return@setPositiveButton
                    }
                    showPage("profile")
                } catch (_: Exception) {
                    aacs.recordOwnerEvent("PROFILE_UPDATE_FAILED", "personId=${person.id}")
                    Toast.makeText(this, "ذخیره پروفایل انجام نشد؛ اطلاعات قبلی حفظ شدند.", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun createPersonProfileDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        fun field(hintText: String) = EditText(this).apply {
            hint = hintText; setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
        }
        val name = field("نام و نام خانوادگی")
        val relation = field("نوع ارتباط")
        val occupation = field("شغل و نقش")
        val interests = field("علاقه‌مندی‌ها")
        val preferences = field("ترجیحات و نحوه تعامل")
        val privacyRestrictions = field("محدودیت‌های حریم خصوصی")
        val relationshipHistory = field("پیشینه رابطه").apply {
            isEnabled = false
            hint = "پس از تأیید مالک فعال می‌شود"
        }
        val informationProvenance = field("منبع اطلاعات، مثلاً گفته مستقیم شخص یا ثبت مالک")
        val level = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("سطح ۲ • شریک عاطفی", "سطح ۳ • خانواده و دوست نزدیک"))
        }
        box.addView(name); box.addView(relation); box.addView(occupation); box.addView(interests)
        box.addView(preferences); box.addView(privacyRestrictions); box.addView(relationshipHistory)
        box.addView(informationProvenance); box.addView(level)
        AlertDialog.Builder(this).setTitle("پروفایل شخص جدید").setView(box)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ساخت پروفایل") { _, _ ->
                val value = name.text.toString().trim()
                if (value.isBlank()) {
                    Toast.makeText(this, "نام پروفایل ضروری است.", Toast.LENGTH_SHORT).show()
                } else {
                    val selectedLevel = if (level.selectedItemPosition == 0) 2 else 3
                    val id = "person-" + java.util.UUID.randomUUID().toString()
                    val profile = PersonProfile(
                        id = id, name = value, level = selectedLevel,
                        relation = relation.text.toString().trim(),
                        occupation = occupation.text.toString().trim(),
                        interests = interests.text.toString().trim(),
                        speakingStyle = "محترمانه و متناسب با رابطه",
                        intimacy = 0, photoUri = null, photoConsent = false,
                        relationshipMemoryApproved = false,
                        preferences = preferences.text.toString().trim(),
                        privacyRestrictions = privacyRestrictions.text.toString().trim(),
                        relationshipHistory = relationshipHistory.text.toString().trim(),
                        informationProvenance = informationProvenance.text.toString().trim().ifBlank { "ثبت‌شده توسط مالک" }
                    )
                    if (!aacs.recordOwnerEvent("PROFILE_CREATE_REQUESTED", "personId=$id; level=$selectedLevel")) {
                        Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ پروفایل ساخته نشد.", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    try {
                        profileStore.save(profile)
                        if (!aacs.recordOwnerEvent("PROFILE_CREATED", "personId=$id; level=$selectedLevel")) {
                            profileStore.remove(id)
                            Toast.makeText(this, "ثبت نتیجه امنیتی ناموفق بود؛ پروفایل تازه حذف شد.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        showPage("profile")
                    } catch (_: Exception) {
                        aacs.recordOwnerEvent("PROFILE_CREATE_FAILED", "personId=$id")
                        Toast.makeText(this, "ساخت پروفایل انجام نشد؛ داده‌های قبلی حفظ شدند.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun choosePersonPhoto(person: PersonProfile) {
        if (!person.photoConsent) {
            Toast.makeText(this, "تا زمان ثبت دریافت رضایت شخص، انتخاب عکس بسته است.", Toast.LENGTH_LONG).show()
            return
        }
        if (!aacs.recordOwnerEvent("PERSON_PHOTO_SELECTION_REQUESTED", "personId=${person.id}")) {
            Toast.makeText(this, "ثبت رویداد امنیتی ناموفق بود؛ انتخاب عکس متوقف شد.", Toast.LENGTH_LONG).show()
            return
        }
        pendingPersonPhotoId = person.id
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 4103)
    }

    private fun showPersonConsentDialog(person: PersonProfile) {
        val options = arrayOf("ثبت دریافت رضایت عکس از شخص", "لغو رضایت عکس و حذف تصویر", "اجازه ثبت خاطرات رابطه", "لغو اجازه خاطرات")
        AlertDialog.Builder(this).setTitle(person.name).setItems(options) { _, which ->
            val event = when (which) {
                0 -> "PHOTO_CONSENT_RECORDED"
                1 -> "PHOTO_CONSENT_REVOKED"
                2 -> "RELATIONSHIP_MEMORY_APPROVED"
                else -> "RELATIONSHIP_MEMORY_REVOKED"
            }
            if (which == 2 && !createPreChangeMilestone("relationship_memory_approval")) {
                Toast.makeText(this, "پیش‌نسخه امن ساخته نشد؛ تغییر مهم پروفایل متوقف شد.", Toast.LENGTH_LONG).show()
                return@setItems
            }
            if (!aacs.recordOwnerEvent(event, "personId=${person.id}; action recorded by owner")) {
                Toast.makeText(this, "ثبت رویداد امنیتی ناموفق بود؛ تغییری اعمال نشد.", Toast.LENGTH_LONG).show()
                return@setItems
            }
            val updated = when (which) {
                0 -> person.copy(photoConsent = true)
                1 -> person.copy(photoConsent = false, photoUri = null)
                2 -> person.copy(
                    relationshipMemoryApproved = true,
                    relationshipHistory = person.pendingRelationshipHistory.ifBlank { person.relationshipHistory },
                    pendingRelationshipHistory = ""
                )
                else -> person.copy(
                    relationshipMemoryApproved = false,
                    pendingRelationshipHistory = person.relationshipHistory.ifBlank { person.pendingRelationshipHistory },
                    relationshipHistory = ""
                )
            }
            try {
                profileStore.save(updated)
                val memoryMoved = when (which) {
                    2 -> localStore.promotePendingRelationshipMemory(person.id)
                    3 -> localStore.quarantineRelationshipMemory(person.id)
                    else -> true
                }
                if (!memoryMoved) {
                    profileStore.save(person)
                    Toast.makeText(this, "انتقال امن خاطرات انجام نشد؛ تغییر رضایت اعمال نشد.", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                if (!aacs.recordOwnerEvent("PROFILE_CONSENT_CHANGE_SAVED", "personId=${person.id}; action=$event")) {
                    val memoryRolledBack = when (which) {
                        2 -> localStore.quarantineRelationshipMemory(person.id)
                        3 -> localStore.promotePendingRelationshipMemory(person.id)
                        else -> true
                    }
                    val profileRolledBack = try { profileStore.save(person); true } catch (_: Exception) { false }
                    Toast.makeText(
                        this,
                        if (memoryRolledBack && profileRolledBack) "ثبت نتیجه امنیتی ناموفق بود؛ تغییر رضایت و حافظه بازگردانی شدند."
                        else "هشدار: بازگردانی کامل نشد؛ خاطرات تا بررسی بعدی قفل می‌مانند.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setItems
                }
                if (which == 1 && person.photoUri != null) {
                    try {
                        contentResolver.releasePersistableUriPermission(
                            Uri.parse(person.photoUri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                }
                showPage("profile")
            } catch (_: Exception) {
                aacs.recordOwnerEvent("PROFILE_CONSENT_CHANGE_FAILED", "personId=${person.id}; action=$event")
                Toast.makeText(this, "تغییر رضایت ذخیره نشد؛ وضعیت قبلی حفظ شد.", Toast.LENGTH_LONG).show()
            }
        }.show()
    }

    private fun passwordDialog(title: String, onSubmit: (CharArray) -> Unit) {
        val field = EditText(this).apply {
            hint = "عبارت عبور حداقل ۱۲ نویسه"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(primaryTextColor); setHintTextColor(if (lightTheme) Color.rgb(130, 108, 139) else Color.LTGRAY)
        }
        AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ادامه") { _, _ ->
                val value = field.text.toString()
                if (value.length < 12) Toast.makeText(this, "عبارت عبور باید حداقل ۱۲ نویسه باشد", Toast.LENGTH_LONG).show()
                else onSubmit(value.toCharArray())
            }.show()
    }

    private fun requestBackupExport() {
        if (!ownerStorageActionAllowed()) return
        passwordDialog("رمزگذاری پشتیبان") { pass ->
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "AVA-INFINITY-backup.avaibk")
            }
            pendingBackupPassphrase = pass
            startActivityForResult(intent, 5101)
        }
    }

    private var pendingBackupPassphrase: CharArray? = null
    private var pendingPersonPhotoId: String? = null
    private var pendingRestorePayload: org.json.JSONObject? = null
    private var pendingRestorePreview: org.json.JSONObject? = null
    private data class BackupRestoreChoice(
        val label: String,
        val createdAt: Long,
        val preview: org.json.JSONObject
    )
    private var selectedMemoryPersonId: String? = null
    private var activeSessionDisplayName: String? = null
    private var pendingSessionPersonId: String? = null
    private var pendingSessionLevel: Int = 3
    private var pendingMigrationUri: Uri? = null
    private var pendingMigrationFlags: Int = 0

    private fun ownerStorageActionAllowed(): Boolean {
        if (aacs.hasActiveSession() || aacs.isPublicOnlyModeActive()) {
            Toast.makeText(this, "این عملیات فقط در نشست مالک و خارج از حالت فقط عمومی مجاز است.", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun requestMigrationCardPreparation() {
        if (!ownerStorageActionAllowed()) return
        if (localStore.isVaultConfigured()) {
            Toast.makeText(this, "ابتدا باید حافظه اصلی فعلی به‌درستی غیرفعال شود.", Toast.LENGTH_LONG).show()
            return
        }
        if (!AvaSecurePrefs(this, "ava_migration_state").getBoolean("portable_restore_verified", false)) {
            Toast.makeText(this, "ابتدا پشتیبان رمزگذاری‌شده گوشی قبلی را بازیابی و تأیید کن.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("آماده‌سازی کارت برای گوشی جدید")
            .setMessage("این مرحله فقط پس از بازیابی موفق پشتیبان قابل انجام است. فایل‌های رمزگذاری‌شده دستگاه قبلی روی کارت با کلید گوشی جدید باز نمی‌شوند. پس از احراز هویت مالک، فقط فایل‌های اختصاصی AVA روی کارت پاک و حافظه با کلید جدید این گوشی ساخته می‌شود. تنظیمات و تاریخچه منتقل می‌شوند، اما کلید API و اجازه ارسال خارجی منتقل نمی‌شوند و باید جداگانه دوباره تنظیم شوند. نسخه گوشی قبلی را تا پایان بررسی نگه دار.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("انتخاب کارت microSD") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                startActivityForResult(intent, 5302)
            }.show()
    }

    private fun migrationVaultFiles(root: androidx.documentfile.provider.DocumentFile): List<androidx.documentfile.provider.DocumentFile> =
        root.listFiles().filter { file ->
            val name = file.name ?: return@filter false
            file.isFile && (
                (name.startsWith("AVA_vault_") && (name.contains(".avavlt") || name.contains(".corrupt_"))) ||
                    name.startsWith("AVA_pending_vault_")
            )
        }

    private fun handleMigrationCardSelection(uri: Uri, flags: Int) {
        if (!ownerStorageActionAllowed()) return
        val vault = AvaVaultStore(this)
        if (!vault.isRemovableMicroSdUri(uri)) {
            Toast.makeText(this, "برای مهاجرت، کارت microSD متصل و قابل‌نوشتن انتخاب کن.", Toast.LENGTH_LONG).show()
            return
        }
        val grantedFlags = flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, grantedFlags)
        } catch (_: Exception) {
            Toast.makeText(this, "اجازه پایدار دسترسی به کارت دریافت نشد.", Toast.LENGTH_LONG).show()
            return
        }
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
        if (root == null || !root.canWrite()) {
            try { contentResolver.releasePersistableUriPermission(uri, grantedFlags) } catch (_: Exception) { }
            Toast.makeText(this, "کارت انتخاب‌شده قابل نوشتن نیست.", Toast.LENGTH_LONG).show()
            return
        }
        pendingMigrationUri = uri
        pendingMigrationFlags = grantedFlags
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            pendingMigrationUri = null
            try { contentResolver.releasePersistableUriPermission(uri, grantedFlags) } catch (_: Exception) { }
            Toast.makeText(this, "برای پاک‌سازی فایل‌های دستگاه قبلی، قفل امن گوشی و احراز هویت مالک لازم است.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "تأیید مالک برای مهاجرت حافظه",
            "برای آماده‌سازی کارت microSD روی این گوشی، هویت مالک را تأیید کن."
        )
        if (intent == null) {
            pendingMigrationUri = null
            try { contentResolver.releasePersistableUriPermission(uri, grantedFlags) } catch (_: Exception) { }
            Toast.makeText(this, "تأیید امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, 6105)
    }

    private fun showMigrationFinalConfirmation() {
        if (!ownerStorageActionAllowed()) return
        val uri = pendingMigrationUri ?: return
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
        if (root == null || !root.canWrite()) {
            Toast.makeText(this, "کارت دیگر در دسترس نیست؛ هیچ فایلی پاک نشد.", Toast.LENGTH_LONG).show()
            pendingMigrationUri = null
            return
        }
        val files = migrationVaultFiles(root)
        val restoreAt = AvaSecurePrefs(this, "ava_migration_state").getString("portable_restore_at", null)
            ?.toLongOrNull() ?: 0L
        val restoreTime = if (restoreAt > 0L)
            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(restoreAt))
        else "زمان نامشخص"
        AlertDialog.Builder(this)
            .setTitle("تأیید نهایی آماده‌سازی کارت")
            .setMessage("پشتیبان روی این گوشی بازیابی شده است: $restoreTime\nتعداد فایل‌های اختصاصی قدیمی AVA: ${files.size}\nفقط فایل‌های رمزگذاری‌شده AVA روی این کارت حذف می‌شوند. سپس حافظه اصلی با کلید جدید این گوشی ساخته خواهد شد. این حذف قابل بازگشت نیست.")
            .setNegativeButton("لغو") { _, _ ->
                val selected = pendingMigrationUri
                pendingMigrationUri = null
                if (selected != null) {
                    try { contentResolver.releasePersistableUriPermission(selected, pendingMigrationFlags) } catch (_: Exception) { }
                }
            }
            .setPositiveButton("تأیید حذف فایل‌های قدیمی") { _, _ ->
                if (!aacs.recordOwnerEvent("VAULT_MIGRATION_WIPE_REQUESTED", "oldVaultFiles=${files.size}; portable backup restored at=$restoreAt")) {
                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ هیچ فایلی پاک نشد.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                var deleted = true
                files.forEach { file -> if (file.exists() && !file.delete()) deleted = false }
                val remaining = migrationVaultFiles(root)
                if (!deleted || remaining.isNotEmpty()) {
                    aacs.recordOwnerEvent("VAULT_MIGRATION_WIPE_FAILED", "remainingFiles=${remaining.size}")
                    Toast.makeText(this, "پاک‌سازی کامل نشد؛ کارت را جدا نکن و دوباره وضعیت را بررسی کن.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (!aacs.recordOwnerEvent("VAULT_MIGRATION_OLD_FILES_CLEARED", "removedFiles=${files.size}")) {
                    Toast.makeText(this, "فایل‌های قدیمی پاک شدند، اما ثبت نتیجه امنیتی ناموفق بود. حافظه جدید هنوز ساخته نشد.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val selected = pendingMigrationUri ?: return@setPositiveButton
                val selectedFlags = pendingMigrationFlags
                pendingMigrationUri = null
                initializePrimaryVault(selected, selectedFlags)
            }.show()
    }

    private fun requestVaultSetup() {
        if (!ownerStorageActionAllowed()) return
        Toast.makeText(this, "در پنجره بعد پوشه کارت microSD را انتخاب کن؛ داده‌های فعلی به‌صورت رمزگذاری‌شده منتقل و بررسی می‌شوند.", Toast.LENGTH_LONG).show()
        if (localStore.isVaultConfigured()) {
            Toast.makeText(this, "حافظه اصلی قبلاً تنظیم شده است.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, 5301)
    }

    private fun initializePrimaryVault(uri: Uri, flags: Int) {
        if (!ownerStorageActionAllowed()) return
        if (!aacs.recordOwnerEvent("VAULT_SETUP_REQUESTED", "primary removable-storage vault setup")) {
            Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ راه‌اندازی حافظه متوقف شد.", Toast.LENGTH_LONG).show()
            return
        }
        val backupUri = AvaSecurePrefs(this, "ava_backup_config").getString("backup_tree_uri", null)
        if (!backupUri.isNullOrBlank() &&
            AvaVaultStore(this).isSameLocalStorageVolume(Uri.parse(backupUri), uri)
        ) {
            Toast.makeText(this, "حافظه اصلی و مقصد پشتیبان باید روی دو حافظه فیزیکی مستقل باشند.", Toast.LENGTH_LONG).show()
            return
        }
        val grantedFlags = flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, grantedFlags)
        } catch (_: Exception) {
            Toast.makeText(this, "اجازه پایدار دسترسی به microSD دریافت نشد.", Toast.LENGTH_LONG).show()
            return
        }
        val vault = AvaVaultStore(this)
        if (!vault.isRemovableMicroSdUri(uri)) {
            try { contentResolver.releasePersistableUriPermission(uri, grantedFlags) } catch (_: Exception) { }
            Toast.makeText(this, "برای حافظه اصلی، پوشه‌ای روی کارت microSD متصل و قابل‌نوشتن انتخاب کن.", Toast.LENGTH_LONG).show()
            return
        }
        vault.configure(uri)
        if (!vault.isAvailable()) {
            vault.disable()
            Toast.makeText(this, "مقصد انتخاب‌شده قابل خواندن و نوشتن نیست.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val buckets = localStore.exportRawBuckets()
            var verified = true
            buckets.forEach { (bucket, raw) ->
                if (!vault.writeBucket(bucket, raw)) verified = false
            }
            val profiles = profileStore.exportRawProfiles()
            if (!vault.writeBucket("profiles", profiles)) verified = false
            if (verified) verified = vault.flushPending()
            if (verified) {
                buckets.forEach { (bucket, raw) ->
                    if (vault.readBucket(bucket) != raw) verified = false
                }
                if (vault.readBucket("profiles") != profiles) verified = false
            }
            if (!verified) {
                vault.disable()
                Toast.makeText(this, "انتقال یا بررسی حافظه اصلی کامل نشد؛ مقصد قبلی یا صف امن حفظ شد و داده‌های داخلی دست‌نخورده ماندند.", Toast.LENGTH_LONG).show()
                return
            }
            if (!aacs.recordOwnerEvent("VAULT_SETUP_VERIFIED", "primary vault objects read back and verified")) {
                vault.disable()
                Toast.makeText(this, "ثبت نتیجه امنیتی ناموفق بود؛ مقصد قبلی حفظ شد و حافظه جدید فعال نشد.", Toast.LENGTH_LONG).show()
                return
            }
            if (!vault.commitConfiguration()) {
                vault.disable()
                Toast.makeText(this, "تأیید نهایی مقصد حافظه انجام نشد؛ مقصد قبلی حفظ شد.", Toast.LENGTH_LONG).show()
                return
            }
            AvaBackupScheduler.configureVaultHealth(this)
            AvaSecurePrefs(this, "ava_migration_state").edit()
                .putBoolean("portable_restore_verified", false)
                .putString("migration_completed_at", System.currentTimeMillis().toString())
                .commit()
            Toast.makeText(this, "حافظه اصلی رمزگذاری‌شده با کلید این گوشی راه‌اندازی و داده‌های فعلی بررسی شدند.", Toast.LENGTH_LONG).show()
            showPage("memory")
        } catch (_: Exception) {
            vault.disable()
            Toast.makeText(this, "راه‌اندازی حافظه اصلی ناموفق بود؛ داده‌های داخلی حفظ شدند.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAutoBackupSetup() {
        if (!ownerStorageActionAllowed()) return
        Toast.makeText(this, "برای پشتیبان، یک مقصد جدا از حافظه اصلی انتخاب کن؛ ترجیحاً دستگاه فیزیکی یا فضای ابری مستقل.", Toast.LENGTH_LONG).show()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, 5201)
    }

    private fun stopAutoBackup() {
        if (!ownerStorageActionAllowed()) return
        if (!aacs.recordOwnerEvent("AUTO_BACKUP_STOP_REQUESTED", "owner requested automatic backup stop")) {
            Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ تنظیم پشتیبان تغییر نکرد.", Toast.LENGTH_LONG).show()
            return
        }
        AvaBackupScheduler.cancel(this)
        val config = AvaSecurePrefs(this, "ava_backup_config")
        val oldTree = config.getString("backup_tree_uri", null)
        if (!oldTree.isNullOrBlank()) {
            try {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(oldTree),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }
        config.edit()
            .remove("backup_tree_uri")
            .remove("backup_passphrase")
            .putString("last_backup_status", "پشتیبان‌گیری خودکار متوقف شده است.")
            .apply()
        val audited = aacs.recordOwnerEvent("AUTO_BACKUP_STOPPED", "automatic backup destination and schedule cleared")
        Toast.makeText(
            this,
            if (audited) "پشتیبان‌گیری خودکار متوقف شد."
            else "پشتیبان‌گیری متوقف شد، اما ثبت نتیجه امنیتی ناموفق بود.",
            Toast.LENGTH_LONG
        ).show()
        showPage("memory")
    }

    private fun requestBackupImport() {
        if (!ownerStorageActionAllowed()) return
        AlertDialog.Builder(this)
            .setTitle("انتخاب روش بازیابی")
            .setItems(arrayOf("بازیابی از پوشه زنجیره‌ای پشتیبان", "بازیابی از یک فایل کامل مستقل")) { _, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        )
                    }
                    startActivityForResult(intent, 5102)
                } else {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    startActivityForResult(intent, 5103)
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun handleBackupFolderSelection(uri: Uri, flags: Int) {
        val grantedFlags = flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, grantedFlags)
        } catch (_: Exception) {
            Toast.makeText(this, "اجازه پایدار خواندن پوشه پشتیبان دریافت نشد.", Toast.LENGTH_LONG).show()
            return
        }
        passwordDialog("رمزگشایی زنجیره پشتیبان") { pass ->
            try {
                val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                    ?: throw IllegalStateException("Backup folder could not be opened.")
                require(root.canRead()) { "Backup folder is not readable." }
                val repository = AvaBackupChainRepository(this, root, String(pass), localStore)
                val scan = repository.scan()
                val choices = mutableListOf<BackupRestoreChoice>()

                repository.restorePoints(scan).forEach { point ->
                    val archive = point.stored.archive
                    val payload = point.payload
                    val kind = archive.optString("checkpointKind")
                    val date = java.text.DateFormat.getDateTimeInstance().format(
                        java.util.Date(archive.optLong("createdAt"))
                    )
                    val people = payload.optJSONObject("personMemories")
                    var memoryCount = payload.optJSONArray("memories_amir")?.length() ?: 0
                    if (people != null) {
                        val keys = people.keys()
                        while (keys.hasNext()) memoryCount += people.optJSONArray(keys.next())?.length() ?: 0
                    }
                    var chainLength = 1
                    var chainSizeBytes = point.stored.file.length()
                    var cursor = archive
                    val byId = scan.archives.associateBy { it.archive.optString("archiveId") }
                    val seen = mutableSetOf<String>()
                    while (cursor.optString("archiveType") == "delta" &&
                        seen.add(cursor.optString("archiveId"))
                    ) {
                        val parentStored = byId[cursor.optString("parentArchiveId")] ?: break
                        chainLength++
                        chainSizeBytes += parentStored.file.length()
                        cursor = parentStored.archive
                    }
                    val preview = org.json.JSONObject()
                        .put("createdAt", archive.optLong("createdAt"))
                        .put("sizeBytes", chainSizeBytes)
                        .put("health", "authenticated")
                        .put("archiveType", archive.optString("archiveType"))
                        .put("archiveId", archive.optString("archiveId"))
                        .put("checkpointKind", kind)
                        .put("chainLength", chainLength)
                        .put("memoryCount", memoryCount)
                        .put("profileCount", payload.optJSONArray("profiles")?.length() ?: 0)
                        .put("vocabularyCount", payload.optJSONArray("vocabulary")?.length() ?: 0)
                        .put("taskCount", payload.optJSONArray("tasks")?.length() ?: 0)
                        .put("payload", payload)
                        .put("archive", archive)
                    val type = if (archive.optString("archiveType") == "full") "کامل" else "زنجیره‌ای"
                    choices.add(
                        BackupRestoreChoice(
                            date + " • " + kind + " • " + type + " • " + chainLength + " حلقه",
                            archive.optLong("createdAt"),
                            preview
                        )
                    )
                }

                root.listFiles().filter { file ->
                    val name = file.name ?: ""
                    file.isFile && name.endsWith(".avaibk", ignoreCase = true) &&
                        (name.startsWith("AVA_daily_") || name.startsWith("AVA_weekly_") ||
                            name.startsWith("AVA_monthly_") || name.startsWith("AVA_event_") ||
                            name.startsWith("AVA_milestone_"))
                }.forEach { file ->
                    try {
                        val preview = backup.previewEncrypted(file.uri, pass.copyOf())
                        if (preview.optString("archiveType") == "legacy_full") {
                            val payload = preview.getJSONObject("payload")
                            require(localStore.validateBackupPayload(payload))
                            val date = java.text.DateFormat.getDateTimeInstance().format(
                                java.util.Date(preview.optLong("createdAt"))
                            )
                            choices.add(
                                BackupRestoreChoice(
                                    date + " • نسخه قدیمی مستقل • " + (file.name ?: ""),
                                    preview.optLong("createdAt"),
                                    preview
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // Invalid legacy files are excluded from selectable restore points.
                    }
                }

                if (choices.isEmpty()) {
                    Toast.makeText(this, "هیچ نقطه بازیابی سالمی در این پوشه پیدا نشد.", Toast.LENGTH_LONG).show()
                    return@passwordDialog
                }
                val sorted = choices.sortedByDescending { it.createdAt }
                val warning = if (scan.hasCorruption)
                    "بخشی از زنجیره ناقص یا مبهم است. فقط نقاطی که مستقل از آن بخش قابل اعتبارسنجی بودند نمایش داده می‌شوند. هیچ فایلی تغییر نمی‌کند."
                else
                    "نقطه بازیابی را انتخاب کن. هنوز هیچ داده‌ای تغییر نمی‌کند."
                AlertDialog.Builder(this)
                    .setTitle("نقاط بازیابی موجود")
                    .setMessage(warning)
                    .setItems(sorted.map { it.label }.toTypedArray()) { _, selected ->
                        presentRestorePreview(sorted[selected].preview)
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            } catch (_: Exception) {
                Toast.makeText(this, "خواندن زنجیره پشتیبان ناموفق بود؛ فایل‌ها دست‌نخورده ماندند.", Toast.LENGTH_LONG).show()
            } finally {
                pass.fill(0.toChar())
            }
        }
    }

    private fun presentRestorePreview(preview: org.json.JSONObject) {
        val validatedPayload = preview.getJSONObject("payload")
        require(localStore.validateBackupPayload(validatedPayload)) {
            "Backup payload failed AVA schema validation."
        }
        pendingRestorePayload = org.json.JSONObject(validatedPayload.toString())
        pendingRestorePreview = org.json.JSONObject(preview.toString())
        val createdAt = java.text.DateFormat.getDateTimeInstance().format(
            java.util.Date(preview.optLong("createdAt"))
        )
        val sizeKb = preview.optLong("sizeBytes") / 1024
        val chainText = if (preview.optInt("chainLength", 0) > 0)
            "\nزنجیره: " + preview.optInt("chainLength") + " حلقه"
        else ""
        val message = "تاریخ پشتیبان: " + createdAt + "\nحجم: " + sizeKb +
            " KB\nسلامت رمزنگاری: تأیید شد" + chainText +
            "\nیادداشت‌ها: " + preview.optInt("memoryCount") +
            "\nپروفایل‌ها: " + preview.optInt("profileCount") +
            "\nواژه‌ها: " + preview.optInt("vocabularyCount") +
            "\nکارها: " + preview.optInt("taskCount") +
            "\n\nمرحله بعد احراز هویت مالک است؛ سپس تأیید نهایی جداگانه لازم خواهد بود."
        AlertDialog.Builder(this).setTitle("پیش‌نمایش بازیابی")
            .setMessage(message)
            .setNegativeButton("انصراف") { _, _ ->
                pendingRestorePayload = null
                pendingRestorePreview = null
            }
            .setPositiveButton("احراز هویت مالک") { _, _ -> requestRestoreOwnerApproval() }.show()
    }

    private fun chooseAvatar() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 4102)
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 6106) {
            val action = pendingVocabularyAction
            val entryId = pendingVocabularyEntryId
            val targetVersion = pendingVocabularyTargetVersion
            pendingVocabularyAction = null
            pendingVocabularyEntryId = null
            pendingVocabularyTargetVersion = null
            val granted = resultCode == RESULT_OK && action != null &&
                aacs.grantOwnerActionAfterDeviceCredential(action)
            val completed = if (granted && !entryId.isNullOrBlank()) {
                try {
                    when (action) {
                        "ava_secret_publish" -> localStore.approveVocabulary(entryId)
                        "ava_secret_rollback", "ava_secret_archive" -> {
                            if (!createPreChangeMilestone(action)) false
                            else if (action == "ava_secret_rollback") {
                                targetVersion != null && localStore.rollbackVocabulary(entryId, targetVersion)
                            } else {
                                localStore.archiveVocabulary(entryId)
                            }
                        }
                        else -> false
                    }
                } catch (_: Exception) {
                    false
                }
            } else false
            Toast.makeText(
                this,
                if (completed) "تغییر AVA SECRET با تأیید مالک ثبت شد."
                else "عملیات انجام نشد؛ داده قبلی حفظ شد.",
                Toast.LENGTH_LONG
            ).show()
            showPage("secret")
            return
        }
        if (requestCode == 6105) {
            if (resultCode == RESULT_OK) {
                showMigrationFinalConfirmation()
            } else {
                val selected = pendingMigrationUri
                pendingMigrationUri = null
                if (selected != null) {
                    try { contentResolver.releasePersistableUriPermission(selected, pendingMigrationFlags) } catch (_: Exception) { }
                }
                Toast.makeText(this, "احراز هویت انجام نشد؛ هیچ فایلی از کارت پاک نشد.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == 6104) {
            if (resultCode == RESULT_OK &&
                aacs.grantPublicOnlyExitAfterDeviceCredential() &&
                aacs.clearPublicOnlyModeAfterOwnerApproval()
            ) {
                Toast.makeText(this, "حالت فقط عمومی با تأیید مالک غیرفعال شد.", Toast.LENGTH_SHORT).show()
                AvaBackupScheduler.enqueueEventSnapshot(this)
            } else {
                val message = if (aacs.hasActiveSession())
                    "ابتدا نشست فعال را پایان بده؛ سپس با تأیید مالک می‌توانی از حالت عمومی خارج شوی."
                else "حالت عمومی حفظ شد؛ تأیید مالک یا ثبت امنیتی کامل نشد."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            showPage("home")
            return
        }
        if (requestCode == 6103) {
            val personId = pendingSessionPersonId
            val level = pendingSessionLevel
            pendingSessionPersonId = null
            if (resultCode == RESULT_OK && !personId.isNullOrBlank()) {
                val person = profileStore.all().firstOrNull { it.id == personId }
                val started = person != null &&
                    aacs.beginSession(personId, level) &&
                    aacs.grantCurrentSessionApprovalAfterDeviceCredential() &&
                    aacs.approveCurrentSessionByOwner()
                if (started && person != null) {
                    conversationGeneration += 1L
                conversationHistory.clear()
                    chatDraftText = ""
                    followUpController.clear()
                    activeSessionDisplayName = person.name
                    selectedMemoryPersonId = person.id
                    Toast.makeText(this, "نشست سطح $level با تأیید مالک آغاز شد؛ دسترسی خصوصی همچنان نیازمند تأیید جداگانه است.", Toast.LENGTH_LONG).show()
                    showPage("home")
                } else {
                    if (aacs.hasActiveSession()) aacs.endSession()
                    Toast.makeText(this, "تأیید یا ثبت امنیتی کامل نشد؛ نشست آغاز نشد.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "تأیید مالک لغو شد؛ نشست آغاز نشد.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == 6101) {
            val approved = resultCode == RESULT_OK &&
                aacs.grantCurrentSessionApprovalAfterDeviceCredential() &&
                aacs.approveCurrentSessionByOwner()
            Toast.makeText(
                this,
                if (approved) "نشست تأیید شد؛ دسترسی به اطلاعات خصوصی نیازمند تأیید جداگانه است."
                else "تأیید مالک انجام نشد؛ نشست در حالت محدود باقی ماند.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (requestCode == 6107) {
            val allowed = resultCode == RESULT_OK &&
                aacs.grantPrivateInformationAfterDeviceCredential() &&
                aacs.allowPrivateInformation()
            Toast.makeText(
                this,
                if (allowed) "دسترسی خصوصی یک‌باره و حداکثر ۶۰ ثانیه‌ای فعال شد."
                else "دسترسی خصوصی فعال نشد؛ اطلاعات محرمانه قفل ماندند.",
                Toast.LENGTH_LONG
            ).show()
            showPage("security")
            return
        }
        if (requestCode == 6102) {
            if (resultCode == RESULT_OK) {
                showRestoreFinalConfirmation()
            } else {
                pendingRestorePayload = null
                pendingRestorePreview = null
                aacs.recordOwnerEvent("RESTORE_CANCELLED", "owner re-authentication was not completed")
                Toast.makeText(this, "بازیابی لغو شد؛ داده‌های فعلی دست‌نخورده ماندند.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (resultCode != RESULT_OK) {
            if (requestCode == 4103) pendingPersonPhotoId = null
            if (requestCode == 4104) {
                pendingWardrobePhotoName = null
                pendingWardrobePhotoCategory = null
            }
            if (requestCode == 5101) {
                pendingBackupPassphrase?.fill('\u0000')
                pendingBackupPassphrase = null
            }
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            4104 -> saveOwnerWardrobeImage(uri)
            5301 -> initializePrimaryVault(uri, data?.flags ?: 0)
            5302 -> handleMigrationCardSelection(uri, data?.flags ?: 0)
            5201 -> {
                val vaultUri = AvaSecurePrefs(this, "ava_vault_config").getString("tree_uri", null)
                val primaryUri = vaultUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                if (!AvaVaultStore(this).isIndependentBackupUri(primaryUri, uri)) {
                    Toast.makeText(this, "مقصد باید یک حافظه جداشدنیِ مجزا یا سرویس ابری پشتیبانی‌شده باشد؛ حافظه داخلی یا پوشه‌ای روی همان کارت کافی نیست.", Toast.LENGTH_LONG).show()
                    return
                }
                val backupRoot = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                if (backupRoot == null || !backupRoot.canRead() || !backupRoot.canWrite()) {
                    Toast.makeText(this, "پوشه انتخاب‌شده قابل خواندن و نوشتن نیست.", Toast.LENGTH_LONG).show()
                    return
                }
                val grantedFlags = (data?.flags ?: 0) and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                try {
                    contentResolver.takePersistableUriPermission(uri, grantedFlags)
                } catch (_: Exception) {
                    Toast.makeText(this, "اجازه پایدار نوشتن در مقصد دریافت نشد.", Toast.LENGTH_LONG).show()
                    return
                }
                passwordDialog("عبارت عبور پشتیبان خودکار") { pass ->
                    if (!aacs.recordOwnerEvent("AUTO_BACKUP_CONFIG_REQUESTED", "independent destination provider=${uri.authority}")) {
                        pass.fill('\u0000')
                        Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ مقصد پشتیبان ذخیره نشد.", Toast.LENGTH_LONG).show()
                        return@passwordDialog
                    }
                    val config = AvaSecurePrefs(this, "ava_backup_config")
                    val saved = config.edit()
                        .putString("backup_tree_uri", uri.toString())
                        .putString("backup_passphrase", String(pass))
                        .putString("last_backup_status", "مقصد ذخیره شد؛ اولین نسخه در حال آماده‌سازی است.")
                        .commit()
                    pass.fill('\u0000')
                    if (!saved) {
                        Toast.makeText(this, "تنظیم مقصد پشتیبان ذخیره نشد.", Toast.LENGTH_LONG).show()
                        return@passwordDialog
                    }
                    AvaBackupScheduler.configureDaily(this)
                    AvaBackupScheduler.configureBackupHealth(this)
                    AvaBackupScheduler.configureRestoreTest(this)
                    AvaBackupScheduler.enqueueEventSnapshot(this)
                    val audited = aacs.recordOwnerEvent("AUTO_BACKUP_CONFIGURED", "independent destination saved and schedule enqueued")
                    if (!audited) {
                        AvaBackupScheduler.cancel(this)
                        config.edit().remove("backup_tree_uri").remove("backup_passphrase")
                            .putString("last_backup_status", "تنظیم به دلیل شکست ثبت امنیتی لغو شد.")
                            .commit()
                        try {
                            contentResolver.releasePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) { }
                        Toast.makeText(this, "ثبت نتیجه امنیتی ناموفق بود؛ پشتیبان‌گیری خودکار لغو شد.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "پشتیبان‌گیری خودکار تنظیم شد.", Toast.LENGTH_LONG).show()
                    }
                    showPage("memory")
                }
            }
            4102 -> {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                profileUri = uri
                prefs.edit().putString("avatar_uri", uri.toString()).apply()
                refreshAvatar()
            }
            4103 -> {
                val personId = pendingPersonPhotoId ?: return
                pendingPersonPhotoId = null
                val person = profileStore.all().firstOrNull { it.id == personId } ?: return
                if (!person.photoConsent) {
                    Toast.makeText(this, "رضایت عکس لغو شده است؛ تصویر ذخیره نشد.", Toast.LENGTH_LONG).show()
                    return
                }
                if (!aacs.recordOwnerEvent("PERSON_PHOTO_SAVE_REQUESTED", "personId=${person.id}")) {
                    Toast.makeText(this, "ثبت رویداد امنیتی ناموفق بود؛ عکس ذخیره نشد.", Toast.LENGTH_LONG).show()
                    return
                }
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                try {
                    profileStore.save(person.copy(photoUri = uri.toString()))
                    if (!aacs.recordOwnerEvent("PERSON_PHOTO_SAVED", "personId=${person.id}")) {
                        profileStore.save(person)
                        try { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                        Toast.makeText(this, "ثبت نتیجه امنیتی ناموفق بود؛ عکس جدید لغو شد.", Toast.LENGTH_LONG).show()
                        return
                    }
                    showPage("profile")
                } catch (_: Exception) {
                    try { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                    aacs.recordOwnerEvent("PERSON_PHOTO_SAVE_FAILED", "personId=${person.id}")
                    Toast.makeText(this, "ذخیره عکس انجام نشد؛ اطلاعات قبلی حفظ شدند.", Toast.LENGTH_LONG).show()
                }
            }
            5101 -> {
                val primaryVaultValue = AvaSecurePrefs(this, "ava_vault_config").getString("tree_uri", null)
                val primaryVault = primaryVaultValue?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                if (!AvaVaultStore(this).isIndependentBackupUri(primaryVault, uri)) {
                    pendingBackupPassphrase?.fill('\u0000')
                    pendingBackupPassphrase = null
                    Toast.makeText(this, "فایل پشتیبان باید روی حافظه جداشدنیِ مجزا یا سرویس ابری پشتیبانی‌شده ذخیره شود.", Toast.LENGTH_LONG).show()
                    return
                }
                val pass = pendingBackupPassphrase ?: return
                pendingBackupPassphrase = null
                val verificationPass = pass.copyOf()
                if (!aacs.recordOwnerEvent("MANUAL_BACKUP_EXPORT_REQUESTED", "portable encrypted backup export")) {
                    pass.fill('\u0000')
                    verificationPass.fill('\u0000')
                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ ساخت پشتیبان متوقف شد.", Toast.LENGTH_LONG).show()
                    return
                }
                try {
                    val sourcePayload = synchronized(AvaBackupSnapshotLock.lock) {
                        localStore.backupPayload(profileStore.all())
                    }
                    require(localStore.validateBackupPayload(sourcePayload)) { "Backup source payload failed schema validation." }
                    val fullArchive = AvaBackupChain.createFull(sourcePayload, "recovery")
                    backup.exportEncrypted(uri, fullArchive, pass)
                    val verified = backup.previewEncrypted(uri, verificationPass)
                    require(verified.optString("health") == "authenticated") { "Backup read-back verification failed." }
                    val verifiedArchive = verified.getJSONObject("archive")
                    val restoredPreview = AvaBackupChain.materializeFull(verifiedArchive) {
                        localStore.validateBackupPayload(it)
                    }
                    require(AvaBackupChain.sha256(restoredPreview) == AvaBackupChain.sha256(sourcePayload)) {
                        "Backup read-back does not match the source snapshot."
                    }
                    val auditSaved = aacs.recordOwnerEvent(
                        "MANUAL_BACKUP_EXPORT_VERIFIED",
                        "archiveId=${verifiedArchive.optString("archiveId")}; bytes=${verified.optLong("sizeBytes")}; profiles=${verified.optInt("profileCount")}"
                    )
                    if (auditSaved) {
                        Toast.makeText(this, "پشتیبان رمزگذاری‌شده ساخته و دوباره بررسی شد.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "پشتیبان ساخته و بررسی شد، اما ثبت نتیجه امنیتی ناموفق بود؛ وضعیت گزارش را بررسی کن.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    pass.fill('\u0000')
                    verificationPass.fill('\u0000')
                    aacs.recordOwnerEvent("MANUAL_BACKUP_EXPORT_FAILED", e.javaClass.simpleName)
                    Toast.makeText(this, "ساخت یا بررسی پشتیبان ناموفق بود.", Toast.LENGTH_LONG).show()
                }
            }
            5102 -> handleBackupFolderSelection(uri, data?.flags ?: 0)
            5103 -> passwordDialog("رمزگشایی فایل پشتیبان") { pass ->
                try {
                    val preview = backup.previewEncrypted(uri, pass)
                    require(preview.optString("archiveType") != "delta") {
                        "A delta file is not independently restorable; select its backup folder."
                    }
                    presentRestorePreview(preview)
                } catch (_: Exception) {
                    Toast.makeText(this, "فایل مستقل نامعتبر است یا یک حلقه افزایشی انتخاب شده؛ برای زنجیره، پوشه پشتیبان را انتخاب کن.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestRestoreOwnerApproval() {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            Toast.makeText(this, "برای بازیابی امن، قفل دستگاه باید فعال باشد.", Toast.LENGTH_LONG).show()
            return
        }
        if (!aacs.recordOwnerEvent("RESTORE_REAUTH_REQUESTED", "encrypted backup preview completed")) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            Toast.makeText(this, "ثبت رویداد امنیتی ناموفق بود؛ بازیابی متوقف شد.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "تأیید بازیابی آوا",
            "برای بازیابی نسخه پشتیبان، هویت مالک را تأیید کن."
        )
        if (intent == null) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            Toast.makeText(this, "احراز هویت امن دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, 6102)
    }

    private fun showRestoreFinalConfirmation() {
        val preview = pendingRestorePreview ?: return
        val createdAt = java.text.DateFormat.getDateTimeInstance().format(
            java.util.Date(preview.optLong("createdAt"))
        )
        AlertDialog.Builder(this)
            .setTitle("تأیید نهایی جایگزینی داده‌ها")
            .setMessage(
                "مالک احراز هویت شد.\\nنسخه: $createdAt\\n" +
                    "حجم: ${preview.optLong("sizeBytes") / 1024} KB\\n" +
                    "در صورت تنظیم مقصد خودکار، پیش‌نسخه رمزگذاری‌شده از داده‌های فعلی ساخته می‌شود.\\n" +
                    "این عملیات داده‌های فعلی آوا را با محتوای پشتیبان جایگزین می‌کند. ادامه می‌دهی؟"
            )
            .setNegativeButton("لغو") { _, _ ->
                pendingRestorePayload = null
                pendingRestorePreview = null
                aacs.recordOwnerEvent("RESTORE_CANCELLED", "owner declined final replacement confirmation")
            }
            .setPositiveButton("تأیید نهایی بازیابی") { _, _ -> applyPendingRestore() }
            .show()
    }

    private fun applyPendingRestore() {
        synchronized(AvaBackupChainLock.lock) {
            synchronized(AvaBackupSnapshotLock.lock) {
                applyPendingRestoreLocked()
            }
        }
    }

    private fun applyPendingRestoreLocked() {
        if (!ownerStorageActionAllowed()) return
        val restoreData = pendingRestorePayload ?: return
        if (!localStore.validateBackupPayload(restoreData)) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            Toast.makeText(this, "ساختار پشتیبان معتبر نیست؛ هیچ داده‌ای جایگزین نشد.", Toast.LENGTH_LONG).show()
            return
        }
        if (!aacs.recordOwnerEvent("RESTORE_APPROVED", "owner re-authenticated and confirmed replacement")) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            Toast.makeText(this, "ثبت رویداد امنیتی ناموفق بود؛ هیچ داده‌ای جایگزین نشد.", Toast.LENGTH_LONG).show()
            return
        }
        val backupConfig = AvaSecurePrefs(this, "ava_backup_config")
        val hasIndependentDestination = !backupConfig.getString("backup_tree_uri", null).isNullOrBlank() &&
            !backupConfig.getString("backup_passphrase", null).isNullOrBlank()
        if (!hasIndependentDestination) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            aacs.recordOwnerEvent("RESTORE_BLOCKED", "independent pre-restore backup destination is not configured")
            Toast.makeText(this, "برای بازیابی امن، ابتدا مقصد مستقل پشتیبان‌گیری را تنظیم کن.", Toast.LENGTH_LONG).show()
            return
        }
        if (!createPreChangeMilestone("restore")) {
            pendingRestorePayload = null
            pendingRestorePreview = null
            aacs.recordOwnerEvent("RESTORE_BLOCKED", "pre-restore milestone backup failed")
            Toast.makeText(this, "پیش‌نسخه امن ساخته نشد؛ برای جلوگیری از ریسک، بازیابی متوقف شد.", Toast.LENGTH_LONG).show()
            return
        }
        var rollbackPayload: org.json.JSONObject? = null
        var rollbackProfiles: List<PersonProfile>? = null
        var restoreMutationStarted = false
        try {
            rollbackProfiles = profileStore.all()
            rollbackPayload = localStore.backupPayload(rollbackProfiles!!)
            val personMemories = restoreData.optJSONObject("personMemories") ?: org.json.JSONObject()
            val allowedIds = profileStore.all().filter { it.relationshipMemoryApproved }.map { it.id }.toSet()
            val pendingById = linkedMapOf<String, org.json.JSONObject>()
            val existingPending = restoreData.optJSONArray("pendingRelationshipMemories") ?: org.json.JSONArray()
            for (index in 0 until existingPending.length()) {
                val entry = existingPending.optJSONObject(index) ?: continue
                val id = entry.optString("personId")
                if (id.matches(Regex("person-[A-Za-z0-9-]{1,80}"))) pendingById[id] = org.json.JSONObject(entry.toString())
            }
            val keys = personMemories.keys()
            val moveIds = mutableListOf<String>()
            while (keys.hasNext()) {
                val id = keys.next()
                if (id !in allowedIds) moveIds.add(id)
            }
            moveIds.forEach { id ->
                val incomingItems = personMemories.optJSONArray(id) ?: org.json.JSONArray()
                val prior = pendingById[id]
                val mergedItems = org.json.JSONArray()
                prior?.optJSONArray("items")?.let { oldItems ->
                    for (index in 0 until oldItems.length()) oldItems.optJSONObject(index)?.let { mergedItems.put(it) }
                }
                for (index in 0 until incomingItems.length()) incomingItems.optJSONObject(index)?.let { mergedItems.put(it) }
                if (mergedItems.length() > 0) {
                    pendingById[id] = org.json.JSONObject()
                        .put("personId", id)
                        .put("items", mergedItems)
                        .put("quarantinedAt", System.currentTimeMillis())
                }
                personMemories.remove(id)
            }
            val pendingForRestore = org.json.JSONArray()
            pendingById.values.forEach { pendingForRestore.put(it) }
            restoreData.put("pendingRelationshipMemories", pendingForRestore)
            restoreData.put("personMemories", personMemories)
            restoreMutationStarted = true
            localStore.restorePayload(restoreData)
            profileStore.restoreFromBackup(restoreData.optJSONArray("profiles"))
            require(localStore.restoreSettings(restoreData)) { "Settings could not be restored securely." }

            // Read every restored bucket and profile back before marking the package migration-ready.
            verifyRestoredPayload(restoreData)
            val restoredProfileIds = profileStore.all().map { it.id }.toSet()
            require(aacs.restorePortableState(restoreData.optJSONObject("aacsState"), restoredProfileIds)) {
                "Portable AACS state could not be verified or merged."
            }

            // Apply migrated settings to the live Activity. Device-specific avatar URIs were excluded.
            profileUri = null
            fontScale = prefs.getInt("font_scale", 100).coerceIn(85, 125) / 100f
            glassAlpha = prefs.getInt("glass_alpha", 34).coerceIn(15, 90)
            avatarSize = prefs.getInt("avatar_size", 148).coerceIn(100, 200)
            avatarVisible = prefs.getBoolean("avatar_visible", true)
            applyPaletteValues(prefs.getInt("palette", 0))
            val restoredVoice = AvaSecurePrefs(this, "ava_voice_config")
            speech?.setSpeechRate(restoredVoice.getInt("rate", 100).coerceIn(70, 140) / 100f)
            speech?.setPitch(restoredVoice.getInt("pitch", 106).coerceIn(70, 140) / 100f)
            val restoredVoiceName = restoredVoice.getString("voice_name", null)
            speech?.voices?.firstOrNull { it.name == restoredVoiceName && it.locale.language == "fa" }?.let {
                speech?.voice = it
            }
            buildShell()
            refreshAvatar()

            val migrationMarkerSaved = AvaSecurePrefs(this, "ava_migration_state").edit()
                .putBoolean("portable_restore_verified", true)
                .putString("portable_restore_at", System.currentTimeMillis().toString())
                .commit()
            require(migrationMarkerSaved) { "Migration verification marker could not be stored." }
            val restoreAudited = aacs.recordOwnerEvent(
                "RESTORE_SUCCESS", "encrypted backup restored after explicit owner confirmation"
            )
            pendingRestorePayload = null
            pendingRestorePreview = null
            if (!restoreAudited) {
                stopAvaVoice()
                aacs.publicOnlyMode("restore completed but success audit could not be committed")
                Toast.makeText(this, "بازیابی انجام شد، اما ثبت امنیتی ناموفق بود؛ آوا در حالت فقط عمومی باقی ماند.", Toast.LENGTH_LONG).show()
            } else {
                AvaBackupScheduler.enqueueEventSnapshot(this)
                Toast.makeText(this, "بازیابی داده‌های محلی انجام شد.", Toast.LENGTH_LONG).show()
            }
            showPage("memory")
        } catch (error: Exception) {
            val rollbackSucceeded = if (!restoreMutationStarted) {
                true
            } else try {
                val snapshot = rollbackPayload ?: throw IllegalStateException("Pre-restore memory snapshot is unavailable.")
                val profiles = rollbackProfiles ?: throw IllegalStateException("Pre-restore profile snapshot is unavailable.")
                localStore.restorePayload(snapshot)
                profileStore.restoreOwnerSnapshot(profiles)
                require(localStore.restoreSettings(snapshot)) { "Previous settings could not be restored." }
                val previousIds = profiles.map { it.id }.toSet()
                require(aacs.restorePortableSnapshotForRollback(snapshot.optJSONObject("aacsState"), previousIds)) {
                    "Previous AACS permission snapshot could not be restored."
                }
                verifyRestoredPayload(snapshot)
                true
            } catch (rollbackError: Exception) {
                aacs.recordOwnerEvent(
                    "RESTORE_ROLLBACK_FAILED",
                    "restoreError=${error.javaClass.simpleName}; rollbackError=${rollbackError.javaClass.simpleName}"
                )
                stopAvaVoice()
                aacs.publicOnlyMode("restore rollback failed; data state requires owner review")
                false
            }
            if (restoreMutationStarted && rollbackSucceeded) {
                val rollbackAudited = aacs.recordOwnerEvent(
                    "RESTORE_ROLLED_BACK",
                    "failed restore reverted to pre-restore snapshot; cause=${error.javaClass.simpleName}"
                )
                if (!rollbackAudited) {
                    stopAvaVoice()
                    aacs.publicOnlyMode("restore rollback completed but audit failed")
                }
            }
            pendingRestorePayload = null
            pendingRestorePreview = null
            val failureAudited = aacs.recordOwnerEvent(
                "RESTORE_FAILED",
                "cause=${error.javaClass.simpleName}; state=${if (!restoreMutationStarted) "unchanged" else if (rollbackSucceeded) "rolled_back" else "uncertain"}"
            )
            if (!failureAudited) {
                stopAvaVoice()
                aacs.publicOnlyMode("restore failure audit could not be committed")
            }
            Toast.makeText(
                this,
                if (!restoreMutationStarted) "بازیابی انجام نشد؛ داده‌های قبلی دست‌نخورده ماندند."
                else if (rollbackSucceeded) "بازیابی ناموفق بود؛ نسخه قبلی بازگردانی و بررسی شد."
                else "بازیابی و بازگردانی کامل نشد؛ آوا در حالت فقط عمومی قرار گرفت و بررسی مالک لازم است.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun verifyRestoredPayload(payload: org.json.JSONObject) {
        require(localStore.validateBackupPayload(payload)) { "Restored backup schema is invalid." }
        listOf("memories", "memories_amir", "vocabulary", "tasks").forEach { bucketName ->
            val expected = payload.optJSONArray(bucketName) ?: org.json.JSONArray()
            val actual = org.json.JSONArray()
            localStore.all(bucketName).forEach { actual.put(it) }
            require(actual.toString() == expected.toString()) { "Restored bucket verification failed: $bucketName" }
        }
        val expectedPending = payload.optJSONArray("pendingRelationshipMemories") ?: org.json.JSONArray()
        val actualPending = org.json.JSONArray()
        localStore.all("pending_relationship_memories").forEach { actualPending.put(it) }
        require(actualPending.toString() == expectedPending.toString()) { "Quarantined relationship-memory verification failed." }

        val restoredPeople = payload.optJSONObject("personMemories") ?: org.json.JSONObject()
        val peopleKeys = restoredPeople.keys()
        while (peopleKeys.hasNext()) {
            val personId = peopleKeys.next()
            val expected = restoredPeople.optJSONArray(personId) ?: org.json.JSONArray()
            val actual = org.json.JSONArray()
            localStore.all("memories_" + personId).forEach { actual.put(it) }
            require(actual.toString() == expected.toString()) { "Restored person memory verification failed." }
        }

        val expectedProfileIds = (0 until (payload.optJSONArray("profiles")?.length() ?: 0))
            .mapNotNull { payload.optJSONArray("profiles")?.optJSONObject(it)?.optString("id") }
            .toSet()
        val actualProfileIds = profileStore.all().map { it.id }.toSet()
        require(actualProfileIds == expectedProfileIds) { "Restored profile verification failed." }
    }

    private fun createPreChangeMilestone(reason: String): Boolean =
        synchronized(AvaBackupChainLock.lock) { createPreChangeMilestoneLocked(reason) }

    private fun createPreChangeMilestoneLocked(reason: String): Boolean {
        if (!ownerStorageActionAllowed()) return false
        val config = AvaSecurePrefs(this, "ava_backup_config")
        val treeValue = config.getString("backup_tree_uri", null) ?: return false
        val passphrase = config.getString("backup_passphrase", null) ?: return false
        val primaryVaultUri = AvaSecurePrefs(this, "ava_vault_config").getString("tree_uri", null)
        val primaryUri = primaryVaultUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (!AvaVaultStore(this).isIndependentBackupUri(primaryUri, Uri.parse(treeValue))) return false
        var temporary: androidx.documentfile.provider.DocumentFile? = null
        return try {
            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, Uri.parse(treeValue))
                ?: return false
            if (!root.canRead() || !root.canWrite()) return false
            val repository = AvaBackupChainRepository(this, root, passphrase, localStore)
            val scan = repository.scan()
            if (scan.hasCorruption) return false
            val archiveCreatedAt = maxOf(
                System.currentTimeMillis(),
                (scan.latest?.stored?.archive?.optLong("createdAt") ?: 0L) + 1L
            )
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date(archiveCreatedAt)) + "_" + java.util.UUID.randomUUID().toString().take(8)
            val safeReason = reason.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(32).ifBlank { "important_change" }
            val finalName = "AVA_milestone_${safeReason}_$stamp.avaibk"
            temporary = root.createFile("application/octet-stream", "AVA_pending_milestone_$stamp.tmp.avaibk")
                ?: return false
            val payload = synchronized(AvaBackupSnapshotLock.lock) {
                localStore.backupPayload(profileStore.all())
            }
            require(localStore.validateBackupPayload(payload))
            val archive = AvaBackupChain.createFull(
                payload, "milestone", createdAt = archiveCreatedAt
            )
            backup.exportEncrypted(temporary!!.uri, archive, passphrase.toCharArray())
            val preview = backup.previewEncrypted(temporary!!.uri, passphrase.toCharArray())
            require(preview.optString("health") == "authenticated")
            val verifiedArchive = preview.getJSONObject("archive")
            val verifiedPayload = AvaBackupChain.materializeFull(verifiedArchive) {
                localStore.validateBackupPayload(it)
            }
            require(AvaBackupChain.sha256(verifiedPayload) == AvaBackupChain.sha256(payload))
            if (!temporary!!.renameTo(finalName)) {
                temporary?.delete()
                return false
            }
            val afterMilestone = repository.scan()
            if (afterMilestone.hasCorruption) return false
            if (!repository.writeManifest(afterMilestone)) {
                if (!aacs.recordOwnerEvent(
                        "BACKUP_MANIFEST_FAILED",
                        "verified milestone exists; encrypted index update failed; reason=$safeReason"
                    )
                ) return false
            }
            aacs.recordOwnerEvent(
                "PRE_CHANGE_MILESTONE_VERIFIED",
                "reason=$safeReason; archiveId=${verifiedArchive.optString("archiveId")}; file=$finalName; bytes=${preview.optLong("sizeBytes")}"
            )
        } catch (_: Exception) {
            temporary?.delete()
            false
        }
    }

    private fun refreshAvatar() {
        val targets = listOfNotNull(
            if (::avatar.isInitialized) avatar else null,
            if (::avatarLarge.isInitialized) avatarLarge else null
        )
        targets.forEach { image ->
            val canShowOwnerAvatar = !::aacs.isInitialized || !aacs.isPublicOnlyModeActive()
            image.visibility = if (avatarVisible && canShowOwnerAvatar) View.VISIBLE else View.GONE
            try {
                if (profileUri != null) image.setImageURI(profileUri)
                else image.setImageResource(android.R.drawable.sym_def_app_icon)
            } catch (_: Exception) {
                image.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
    }

    private fun personalitySettingsCard(): LinearLayout {
        val panel = card()
        val config = AvaSecurePrefs(this, "ava_personality")
        panel.addView(text("شخصیت و لحن آوا", 18f, accent, true))
        val tones = listOf("گرم و صمیمی", "آرام و متفکر", "پرنشاط و سرزنده", "رسمی و دقیق")
        val lengths = listOf("کوتاه", "متعادل", "مفصل")
        val tone = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, tones)
            setSelection(tones.indexOf(config.getString("tone", "گرم و صمیمی") ?: "گرم و صمیمی").coerceAtLeast(0))
        }
        val length = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, lengths)
            setSelection(lengths.indexOf(config.getString("response_length", "متعادل") ?: "متعادل").coerceAtLeast(0))
        }
        val emojis = CheckBox(this).apply {
            text = "استفاده متعادل از ایموجی‌ها"
            setTextColor(primaryTextColor)
            isChecked = config.getBoolean("use_emojis", true)
        }
        fun currentSnapshot(version: Int) = org.json.JSONObject().apply {
            put("version", version)
            put("tone", config.getString("tone", "گرم و صمیمی") ?: "گرم و صمیمی")
            put("length", config.getString("response_length", "متعادل") ?: "متعادل")
            put("emojis", config.getBoolean("use_emojis", true))
            put("savedAt", System.currentTimeMillis())
        }
        panel.addView(label("لحن گفتگو", 12f)); panel.addView(tone)
        panel.addView(label("طول پاسخ", 12f)); panel.addView(length)
        panel.addView(emojis)
        panel.addView(button("ذخیره شخصیت آوا") {
            val version = config.getInt("version", 1).coerceAtLeast(1)
            val oldTone = config.getString("tone", "گرم و صمیمی") ?: "گرم و صمیمی"
            val oldLength = config.getString("response_length", "متعادل") ?: "متعادل"
            val oldEmojis = config.getBoolean("use_emojis", true)
            val changed = oldTone != tone.selectedItem.toString() ||
                oldLength != length.selectedItem.toString() || oldEmojis != emojis.isChecked
            if (!changed) {
                Toast.makeText(this, "شخصیت آوا تغییری نکرده است.", Toast.LENGTH_SHORT).show()
                return@button
            }
            val oldHistory = config.getString("history", "[]") ?: "[]"
            if (!aacs.recordOwnerEvent("PERSONALITY_UPDATE_REQUESTED", "targetVersion=${version + 1}")) {
                Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ تغییر شخصیت انجام نشد.", Toast.LENGTH_LONG).show()
                return@button
            }
            val history = org.json.JSONArray(oldHistory)
            history.put(currentSnapshot(version))
            while (history.length() > 30) history.remove(0)
            val saved = synchronized(AvaBackupSnapshotLock.lock) {
                config.edit()
                    .putString("tone", tone.selectedItem.toString())
                    .putString("response_length", length.selectedItem.toString())
                    .putBoolean("use_emojis", emojis.isChecked)
                    .putInt("version", version + 1)
                    .putString("history", history.toString())
                    .commit()
            }
            if (saved) {
                val auditSaved = aacs.recordOwnerEvent("PERSONALITY_UPDATE_SUCCESS", "version=${version + 1}")
                if (auditSaved) {
                    Toast.makeText(this, "نسخه ${version + 1} شخصیت آوا ذخیره شد.", Toast.LENGTH_LONG).show()
                } else {
                    val rollback = synchronized(AvaBackupSnapshotLock.lock) {
                        config.edit()
                            .putString("tone", oldTone)
                            .putString("response_length", oldLength)
                            .putBoolean("use_emojis", oldEmojis)
                            .putInt("version", version)
                            .putString("history", oldHistory)
                            .commit()
                    }
                    Toast.makeText(
                        this,
                        if (rollback) "ثبت گزارش امنیتی ناموفق بود؛ تغییر شخصیت لغو شد."
                        else "هشدار: ثبت گزارش و بازگردانی کامل نشد؛ تنظیمات نیازمند بررسی‌اند.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(this, "ذخیره شخصیت انجام نشد؛ نسخه قبلی حفظ شد.", Toast.LENGTH_LONG).show()
            }
        }.apply { setMarginsCompat(top = 8) })
        panel.addView(button("تاریخچه و بازگردانی نسخه شخصیت") {
            val history = org.json.JSONArray(config.getString("history", "[]") ?: "[]")
            if (history.length() == 0) {
                Toast.makeText(this, "هنوز نسخه قبلی برای بازگردانی وجود ندارد.", Toast.LENGTH_SHORT).show()
            } else {
                val labels = (history.length() - 1 downTo 0).map { index ->
                    val item = history.optJSONObject(index) ?: org.json.JSONObject()
                    val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.optLong("savedAt")))
                    "نسخه ${item.optInt("version")} • ${item.optString("tone")} • $time"
                }.toTypedArray()
                AlertDialog.Builder(this).setTitle("انتخاب نسخه سالم قبلی")
                    .setItems(labels) { _, selectedIndex ->
                        val index = history.length() - 1 - selectedIndex
                        val chosen = history.optJSONObject(index) ?: return@setItems
                        AlertDialog.Builder(this)
                            .setTitle("بازگردانی شخصیت آوا")
                            .setMessage("لحن: ${chosen.optString("tone")}\nطول پاسخ: ${chosen.optString("length")}\nاین تغییر نسخه جدیدی می‌سازد و نسخه فعلی را نیز در تاریخچه نگه می‌دارد.")
                            .setNegativeButton("انصراف", null)
                            .setPositiveButton("تأیید بازگردانی") { _, _ ->
                                val currentVersion = config.getInt("version", 1).coerceAtLeast(1)
                                val oldTone = config.getString("tone", "گرم و صمیمی") ?: "گرم و صمیمی"
                                val oldLength = config.getString("response_length", "متعادل") ?: "متعادل"
                                val oldEmojis = config.getBoolean("use_emojis", true)
                                val oldHistory = config.getString("history", "[]") ?: "[]"
                                if (!aacs.recordOwnerEvent("PERSONALITY_ROLLBACK_REQUESTED", "targetVersion=${chosen.optInt("version")}")) {
                                    Toast.makeText(this, "ثبت امنیتی ناموفق بود؛ بازگردانی انجام نشد.", Toast.LENGTH_LONG).show()
                                    return@setPositiveButton
                                }
                                val nextHistory = org.json.JSONArray(history.toString())
                                nextHistory.put(currentSnapshot(currentVersion))
                                while (nextHistory.length() > 30) nextHistory.remove(0)
                                val restored = synchronized(AvaBackupSnapshotLock.lock) {
                                    config.edit()
                                        .putString("tone", chosen.optString("tone", "گرم و صمیمی"))
                                        .putString("response_length", chosen.optString("length", "متعادل"))
                                        .putBoolean("use_emojis", chosen.optBoolean("emojis", true))
                                        .putInt("version", currentVersion + 1)
                                        .putString("history", nextHistory.toString())
                                        .commit()
                                }
                                if (restored) {
                                    val auditSaved = aacs.recordOwnerEvent(
                                        "PERSONALITY_ROLLBACK_SUCCESS",
                                        "newVersion=${currentVersion + 1}; sourceVersion=${chosen.optInt("version")}"
                                    )
                                    if (!auditSaved) {
                                        val rolledBack = synchronized(AvaBackupSnapshotLock.lock) {
                                            config.edit()
                                                .putString("tone", oldTone)
                                                .putString("response_length", oldLength)
                                                .putBoolean("use_emojis", oldEmojis)
                                                .putInt("version", currentVersion)
                                                .putString("history", oldHistory)
                                                .commit()
                                        }
                                        Toast.makeText(
                                            this,
                                            if (rolledBack) "ثبت نتیجه امنیتی ناموفق بود؛ بازگردانی لغو و نسخه قبلی حفظ شد."
                                            else "هشدار: ثبت نتیجه و بازگردانی تنظیمات کامل نشد؛ برنامه را در حالت محافظت‌شده نگه دار.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@setPositiveButton
                                    }
                                    Toast.makeText(this, "شخصیت آوا به نسخه انتخابی بازگردانده شد.", Toast.LENGTH_LONG).show()
                                    showPage("profile")
                                } else {
                                    Toast.makeText(this, "بازگردانی ذخیره نشد؛ تنظیمات قبلی حفظ شدند.", Toast.LENGTH_LONG).show()
                                }
                            }.show()
                    }.setNegativeButton("بستن", null).show()
            }
        }.apply { setMarginsCompat(top = 8) })
        return panel
    }

    private fun applyPaletteValues(which: Int) {
        lightTheme = which == 3
        when (which) {
            1 -> { deep = Color.rgb(38, 22, 29); accent = AvaGlassTheme.RoseGold300 }
            2 -> { deep = AvaGlassTheme.Night950; accent = AvaGlassTheme.Lavender300 }
            3 -> { deep = Color.rgb(244, 232, 247); accent = Color.rgb(120, 75, 130) }
            else -> { deep = AvaGlassTheme.Night900; accent = AvaGlassTheme.Lavender500 }
        }
    }

    private fun applyPalette(which: Int) {
        synchronized(AvaBackupSnapshotLock.lock) {
            prefs.edit().putInt("palette", which).apply()
        }
        applyPaletteValues(which)
        buildShell()
        showPage("profile")
    }

    private fun View.setMarginsCompat(top: Int = 0) {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(top)
        layoutParams = lp
    }
}
