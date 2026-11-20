package com.avainfinity.android

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Shared visual tokens for AVA GLASS UI 2.0.
 * Kept independent from domain, storage and access-control policy.
 */
object AvaGlassTheme {
    // Obsidian x Nexus app palette
    val Night950: Int = Color.rgb(7, 10, 16)
    val Night900: Int = Color.rgb(15, 20, 30)
    val Plum800: Int = Color.rgb(23, 34, 50)
    val NexusIce: Int = Color.rgb(135, 205, 235)
    val NexusBlue: Int = Color.rgb(55, 135, 190)
    // Ava's character palette, not the app-wide theme
    val Lavender500: Int = Color.rgb(169, 133, 200)
    val Lavender300: Int = Color.rgb(216, 188, 232)
    val RoseGold500: Int = Color.rgb(197, 142, 134)
    val RoseGold300: Int = Color.rgb(224, 182, 169)
    val DarkPrimaryText: Int = Color.rgb(247, 240, 250)
    val DarkSecondaryText: Int = Color.rgb(200, 185, 209)
    val LightPrimaryText: Int = Color.rgb(41, 29, 49)
    val LightSecondaryText: Int = Color.rgb(103, 84, 111)
    val StatusSuccess: Int = Color.rgb(156, 205, 177)
    val StatusWarning: Int = Color.rgb(232, 197, 141)
    val StatusDanger: Int = Color.rgb(230, 161, 176)

    fun backgroundGradient(light: Boolean): GradientDrawable {
        val colors = if (light) {
            intArrayOf(
                Color.rgb(255, 249, 255),
                Color.rgb(239, 228, 245),
                Color.rgb(250, 239, 247)
            )
        } else {
            intArrayOf(Night950, Night900, Plum800)
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    fun glassSurface(
        context: Context,
        alpha: Int,
        light: Boolean,
        radiusDp: Float = 24f
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val fillAlpha = alpha.coerceIn(8, 90)
        val fill = Color.argb(fillAlpha, 255, 255, 255)
        val stroke = if (light) {
            Color.argb(165, 190, 150, 205)
        } else {
            Color.argb(92, 135, 205, 235)
        }
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusDp * density
            setStroke((1f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    fun selectedNavigationSurface(context: Context, light: Boolean): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val fill = if (light) {
            Color.argb(125, 216, 188, 232)
        } else {
            Color.argb(54, 55, 135, 190)
        }
        val stroke = if (light) {
            Color.argb(170, 197, 142, 134)
        } else {
            Color.argb(150, 135, 205, 235)
        }
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 16f * density
            setStroke((1f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    fun secondaryButtonSurface(context: Context, light: Boolean): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val fill = if (light) {
            Color.argb(175, 244, 231, 247)
        } else {
            Color.argb(28, 255, 255, 255)
        }
        val stroke = if (light) {
            Color.argb(175, 190, 150, 205)
        } else {
            Color.argb(105, 216, 188, 232)
        }
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 18f * density
            setStroke((1f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    fun destructiveButtonSurface(context: Context, light: Boolean): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val fill = if (light) Color.rgb(250, 226, 231) else Color.rgb(103, 39, 57)
        val stroke = if (light) Color.rgb(190, 90, 110) else Color.rgb(202, 112, 133)
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 18f * density
            setStroke((1f * density).toInt().coerceAtLeast(1), stroke)
        }
    }

    fun primaryButtonSurface(context: Context, light: Boolean): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val colors = if (light) {
            intArrayOf(Color.rgb(229, 203, 225), Color.rgb(244, 225, 228))
        } else {
            intArrayOf(Color.rgb(32, 91, 128), Color.rgb(27, 59, 88))
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = 18f * density
            setStroke(
                (1f * density).toInt().coerceAtLeast(1),
                if (light) Color.argb(190, 190, 145, 204) else Color.argb(155, 135, 205, 235)
            )
        }
    }
}
