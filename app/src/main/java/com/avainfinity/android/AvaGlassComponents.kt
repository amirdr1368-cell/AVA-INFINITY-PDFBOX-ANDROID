package com.avainfinity.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Reusable native-View primitives for the AVA GLASS presentation layer.
 * This class contains no storage, conversation or access-control behavior.
 */
object AvaGlassComponents {
    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(
        context: Context,
        color: Int,
        radiusDp: Int = 22,
        strokeColor: Int = Color.argb(55, 255, 255, 255)
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1).coerceAtLeast(1), strokeColor)
    }

    fun text(
        context: Context,
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean,
        fontScale: Float,
        primaryTextColor: Int
    ): TextView = TextView(context).apply {
        text = value
        textSize = sizeSp * fontScale
        setTextColor(if (color == Color.WHITE) primaryTextColor else color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    fun button(
        context: Context,
        value: String,
        lightTheme: Boolean,
        fontScale: Float,
        primaryTextColor: Int,
        secondary: Boolean = false,
        onFailure: () -> Unit,
        onClick: () -> Unit
    ): TextView {
        val destructive = value.contains("حذف") ||
            value.contains("بایگانی") ||
            value.contains("پاک‌سازی")
        return text(
            context, value, 15f, Color.WHITE, true, fontScale, primaryTextColor
        ).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(context, 48)
            isFocusable = true
            contentDescription = value
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            background = when {
                destructive -> AvaGlassTheme.destructiveButtonSurface(context, lightTheme)
                secondary -> AvaGlassTheme.secondaryButtonSurface(context, lightTheme)
                else -> AvaGlassTheme.primaryButtonSurface(context, lightTheme)
            }
            setOnClickListener {
                try {
                    onClick()
                } catch (_: Exception) {
                    onFailure()
                }
            }
        }
    }

    fun card(
        context: Context,
        background: GradientDrawable,
        elevationDp: Int = 5
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(context, 16)
        setPadding(padding, padding, padding, padding)
        this.background = background
        elevation = dp(context, elevationDp).toFloat()
    }
}
