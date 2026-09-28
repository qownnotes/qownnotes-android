package org.qownnotes.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceColorsTest {
    @Test
    fun everyPaletteColorGetsReadableTextAtAaLargeTextContrastOrBetter() {
        AppearanceColors.palette.forEach { color ->
            val content = AppearanceColors.contentColor(color.argb)
            val ratio = AppearanceColors.contrastRatio(color.argb, content)
            assertTrue("${color.name}: $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun lightSurfacesGetDarkTextAndDarkSurfacesGetLightText() {
        assertEquals(
            AppearanceColors.DARK_CONTENT,
            AppearanceColors.contentColor(0xFFFFFFFF.toInt())
        )
        assertEquals(
            AppearanceColors.DARK_CONTENT,
            AppearanceColors.contentColor(0xFFFFF9C4.toInt())
        )
        assertEquals(
            AppearanceColors.LIGHT_CONTENT,
            AppearanceColors.contentColor(0xFF000000.toInt())
        )
        assertEquals(
            AppearanceColors.LIGHT_CONTENT,
            AppearanceColors.contentColor(0xFF1565C0.toInt())
        )
    }

    @Test
    fun secondaryContentKeepsTheColorWithReducedAlpha() {
        val secondary = AppearanceColors.secondaryContentColor(0xFF000000.toInt())
        assertEquals(0x00FFFFFF, secondary and 0x00FFFFFF)
        assertTrue((secondary ushr 24) in 0x80..0xFE)
    }

    @Test
    fun paletteNamesAndColorsAreUniqueAndOpaque() {
        val palette = AppearanceColors.palette
        assertEquals(palette.size, palette.map { it.name }.toSet().size)
        assertEquals(palette.size, palette.map { it.argb }.toSet().size)
        palette.forEach { assertEquals(0xFF, it.argb ushr 24) }
    }

    @Test
    fun opacityIsBoundedAndMapsToAlpha() {
        assertEquals(20, AppearanceColors.coerceOpacity(0))
        assertEquals(100, AppearanceColors.coerceOpacity(150))
        assertEquals(255, AppearanceColors.alpha(100))
        assertEquals(128, AppearanceColors.alpha(50))
        assertEquals(51, AppearanceColors.alpha(20))
        assertEquals(0xFF123456.toInt(), AppearanceColors.opaque(0x00123456))
    }
}
