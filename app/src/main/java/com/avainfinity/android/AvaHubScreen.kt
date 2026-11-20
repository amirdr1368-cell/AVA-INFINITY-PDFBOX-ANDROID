package com.avainfinity.android

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space

/**
 * Owner-facing module hub. Navigation/access checks remain in MainActivity.
 */
class AvaHubScreen(
    private val context: Context,
    private val lightTheme: Boolean,
    private val glassAlpha: Int,
    private val fontScale: Float,
    private val accentColor: Int,
    private val primaryTextColor: Int,
    private val secondaryTextColor: Int,
    private val onNavigate: (String) -> Unit
) {
    fun build(): View {
        val scroll = ScrollView(context).apply { isFillViewport = true }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, AvaGlassComponents.dp(context, 8), 0, AvaGlassComponents.dp(context, 20))
        }

        body.addView(
            AvaGlassComponents.text(
                context, "مرکز آوا", 23f, accentColor, true, fontScale, primaryTextColor
            )
        )
        body.addView(
            AvaGlassComponents.text(
                context,
                "هویت، صدا و ابزارهای AVA INFINITY در یک فضای یکپارچه.",
                12f,
                secondaryTextColor,
                false,
                fontScale,
                primaryTextColor
            ).apply { setPadding(0, AvaGlassComponents.dp(context, 4), 0, AvaGlassComponents.dp(context, 14)) }
        )

        fun addTile(symbol: String, title: String, description: String, destination: String) {
            val tile = AvaGlassComponents.card(
                context,
                AvaGlassTheme.glassSurface(context, glassAlpha, lightTheme)
            ).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = AvaGlassComponents.dp(context, 82)
                isClickable = true
                isFocusable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "باز کردن $title. $description"
                setOnClickListener { onNavigate(destination) }
                setPadding(
                    AvaGlassComponents.dp(context, 12),
                    AvaGlassComponents.dp(context, 12),
                    AvaGlassComponents.dp(context, 12),
                    AvaGlassComponents.dp(context, 12)
                )
                val params = LinearLayout.LayoutParams(-1, -2)
                params.topMargin = AvaGlassComponents.dp(context, 8)
                layoutParams = params
            }

            val mark = AvaGlassComponents.text(
                context, symbol, 22f, accentColor, true, fontScale, primaryTextColor
            ).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = AvaGlassTheme.selectedNavigationSurface(context, lightTheme)
            }
            tile.addView(mark, LinearLayout.LayoutParams(
                AvaGlassComponents.dp(context, 50),
                AvaGlassComponents.dp(context, 50)
            ))
            tile.addView(Space(context), LinearLayout.LayoutParams(
                AvaGlassComponents.dp(context, 12), 1
            ))

            val copy = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            copy.addView(AvaGlassComponents.text(
                context, title, 15f, primaryTextColor, true, fontScale, primaryTextColor
            ))
            copy.addView(AvaGlassComponents.text(
                context, description, 11f, secondaryTextColor, false, fontScale, primaryTextColor
            ).apply { setPadding(0, AvaGlassComponents.dp(context, 3), 0, 0) })
            tile.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            tile.addView(AvaGlassComponents.text(
                context, "‹", 22f, secondaryTextColor, true, fontScale, primaryTextColor
            ).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(
                AvaGlassComponents.dp(context, 24),
                AvaGlassComponents.dp(context, 48)
            ))
            body.addView(tile)
        }

        addTile("◈", "هویت، شخصیت و ظاهر آوا", "تصویر آوا، لحن و تنظیمات نمایش", "profile")
        addTile("♢", "مجموعه خصوصی", "کاتالوگ لباس‌های خصوصی آوا؛ دسترسی محدود به مالک", "wardrobe")
        addTile("♫", "صدا و زبان", "ورودی میکروفون، گفتار و زبان پاسخ", "voice")
        addTile("✧", "AVA SECRET", "واژگان، معادل‌ها و تاریخچه نسخه‌ها", "secret")
        addTile("✓", "دستیار روزانه", "کارها، اولویت‌ها و پیگیری‌ها", "daily")
        addTile("⌘", "کنسول مرکزی", "وضعیت واقعی هسته و ماژول‌های پروژه", "console")
        addTile("◇", "امنیت و دسترسی", "نشست‌ها، سطح دسترسی و حالت حریم خصوصی", "security")

        scroll.addView(body)
        return scroll
    }
}
