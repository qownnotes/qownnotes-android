package org.qownnotes.mobile

import androidx.annotation.StringRes
import kotlin.math.pow

/**
 * A user-selectable color. [name] is a stable English identifier used for test tags; [label] is
 * the localized name shown to users and accessibility services.
 */
data class NamedColor(val name: String, val argb: Int, @StringRes val label: Int)

/**
 * The fixed palette for user appearance choices and the text colors drawn on top of them.
 *
 * Users pick surfaces, not text. Text color is derived from the surface so no combination can
 * become unreadable. This file is pure Kotlin so the policy can be unit-tested on the JVM.
 */
object AppearanceColors {
    val palette: List<NamedColor> = listOf(
        NamedColor("White", 0xFFFFFFFF.toInt(), R.string.color_white),
        NamedColor("Light gray", 0xFFE0E0E0.toInt(), R.string.color_light_gray),
        NamedColor("Gray", 0xFF757575.toInt(), R.string.color_gray),
        NamedColor("Dark gray", 0xFF303030.toInt(), R.string.color_dark_gray),
        NamedColor("Black", 0xFF000000.toInt(), R.string.color_black),
        NamedColor("Light yellow", 0xFFFFF9C4.toInt(), R.string.color_light_yellow),
        NamedColor("Light green", 0xFFDCEDC8.toInt(), R.string.color_light_green),
        NamedColor("Light blue", 0xFFBBDEFB.toInt(), R.string.color_light_blue),
        NamedColor("Light purple", 0xFFE1BEE7.toInt(), R.string.color_light_purple),
        NamedColor("Light pink", 0xFFF8BBD0.toInt(), R.string.color_light_pink),
        NamedColor("Red", 0xFFC62828.toInt(), R.string.color_red),
        NamedColor("Orange", 0xFFEF6C00.toInt(), R.string.color_orange),
        NamedColor("Amber", 0xFFFFB300.toInt(), R.string.color_amber),
        NamedColor("Green", 0xFF2E7D32.toInt(), R.string.color_green),
        NamedColor("Teal", 0xFF00796B.toInt(), R.string.color_teal),
        NamedColor("Blue", 0xFF1565C0.toInt(), R.string.color_blue),
        NamedColor("Indigo", 0xFF3949AB.toInt(), R.string.color_indigo),
        NamedColor("Purple", 0xFF6A1B9A.toInt(), R.string.color_purple),
        NamedColor("Pink", 0xFFAD1457.toInt(), R.string.color_pink)
    )

    const val DARK_CONTENT: Int = 0xFF1C1B1F.toInt()
    const val LIGHT_CONTENT: Int = 0xFFFFFFFF.toInt()

    /** Opacity choices are whole percentages; fully transparent backgrounds are not offered. */
    const val MIN_OPACITY_PERCENT = 20
    const val MAX_OPACITY_PERCENT = 100

    fun opaque(argb: Int): Int = argb or OPAQUE

    fun coerceOpacity(percent: Int): Int =
        percent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    /** 0-255 alpha for an opacity percentage. */
    fun alpha(opacityPercent: Int): Int = (coerceOpacity(opacityPercent) * 255 + 50) / 100

    /** Primary text color with the higher WCAG contrast against [surface]. */
    fun contentColor(surface: Int): Int {
        val luminance = relativeLuminance(surface)
        val againstDark = (luminance + 0.05) / (relativeLuminance(DARK_CONTENT) + 0.05)
        val againstLight = (relativeLuminance(LIGHT_CONTENT) + 0.05) / (luminance + 0.05)
        return if (againstDark >= againstLight) DARK_CONTENT else LIGHT_CONTENT
    }

    /** Secondary text: the primary content color at reduced alpha. */
    fun secondaryContentColor(surface: Int): Int =
        (contentColor(surface) and 0x00FFFFFF) or (SECONDARY_ALPHA shl 24)

    fun contrastRatio(first: Int, second: Int): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private const val OPAQUE = 0xFF shl 24
    private const val SECONDARY_ALPHA = 0xC0
}
