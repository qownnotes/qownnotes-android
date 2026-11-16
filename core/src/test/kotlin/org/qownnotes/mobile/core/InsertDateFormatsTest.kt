package org.qownnotes.mobile.core

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertDateFormatsTest {
    private val moment = ZonedDateTime.of(2026, 3, 7, 14, 5, 9, 0, ZoneOffset.ofHours(1))

    @Test
    fun keepsThePreviousFormatsByDefault() {
        val formats = InsertDateFormats()

        assertEquals("2026-03-07", formats.format(InsertDateKind.DATE, moment, Locale.ROOT))
        assertEquals(
            "2026-03-07 14:05:09",
            formats.format(InsertDateKind.DATE_TIME, moment, Locale.ROOT)
        )
    }

    @Test
    fun formatsEachKindWithItsOwnPattern() {
        val formats = InsertDateFormats().with(InsertDateKind.DATE, "dd.MM.yy")
            .with(InsertDateKind.DATE_TIME, "dd.MM.yyyy HH:mm")

        assertEquals("07.03.26", formats.format(InsertDateKind.DATE, moment, Locale.ROOT))
        assertEquals(
            "07.03.2026 14:05",
            formats.format(InsertDateKind.DATE_TIME, moment, Locale.ROOT)
        )
    }

    @Test
    fun usesTheLocaleForNames() {
        assertEquals(
            "Samstag, 7 März 2026",
            formatDatePattern("EEEE, d MMMM yyyy", moment, Locale.GERMAN)
        )
        assertEquals(
            "2026-03-07T14:05:09+01:00",
            formatDatePattern("yyyy-MM-dd'T'HH:mm:ssXXX", moment, Locale.ROOT)
        )
    }

    @Test
    fun rejectsUnusablePatterns() {
        assertNull(formatDatePattern("", moment, Locale.ROOT))
        assertNull(formatDatePattern("   ", moment, Locale.ROOT))
        assertNull(formatDatePattern("yyyy-MM-dd'", moment, Locale.ROOT))
        assertNull(formatDatePattern("{yyyy}", moment, Locale.ROOT))
    }

    @Test
    fun fallsBackToTheDefaultForAnUnusableStoredPattern() {
        val formats = InsertDateFormats(date = "bbbb")

        assertEquals("2026-03-07", formats.format(InsertDateKind.DATE, moment, Locale.ROOT))
    }

    @Test
    fun everyPresetFormats() {
        InsertDateKind.entries.forEach { kind ->
            assertEquals(kind.defaultPattern, kind.presets.first())
            kind.presets.forEach { pattern ->
                assertTrue(pattern, formatDatePattern(pattern, moment, Locale.ROOT) != null)
            }
        }
    }

    @Test
    fun flagsWeekBasedYearAndDayOfYearOutsideQuotes() {
        assertEquals(
            setOf(DatePatternPitfall.WEEK_BASED_YEAR, DatePatternPitfall.DAY_OF_YEAR),
            datePatternPitfalls("DD.MM.YY")
        )
        assertEquals(emptySet<DatePatternPitfall>(), datePatternPitfalls("dd.MM.yy 'YD'"))
    }
}
