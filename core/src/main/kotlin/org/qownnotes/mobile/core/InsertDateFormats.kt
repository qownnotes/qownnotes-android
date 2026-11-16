package org.qownnotes.mobile.core

import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The two editor actions that insert the current moment, each with its own pattern. */
enum class InsertDateKind(
    /** The pattern used before the reader chooses one, and whenever a stored one is unusable. */
    val defaultPattern: String,
    /** Common patterns offered for selection, starting with [defaultPattern]. */
    val presets: List<String>
) {
    DATE(
        "yyyy-MM-dd",
        listOf(
            "yyyy-MM-dd",
            "dd.MM.yy",
            "dd.MM.yyyy",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "d MMMM yyyy",
            "EEEE, d MMMM yyyy"
        )
    ),
    DATE_TIME(
        "yyyy-MM-dd HH:mm:ss",
        listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd.MM.yy HH:mm",
            "dd.MM.yyyy HH:mm",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy h:mm a",
            "EEEE, d MMMM yyyy HH:mm",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
    )
}

/**
 * `java.time` patterns for the editor's insert-date and insert-date-and-time actions, such as
 * `dd.MM.yy`. Unusable patterns are never stored, but formatting still falls back to the default
 * so that inserting a date can never fail.
 */
data class InsertDateFormats(
    val date: String = InsertDateKind.DATE.defaultPattern,
    val dateTime: String = InsertDateKind.DATE_TIME.defaultPattern
) {
    fun pattern(kind: InsertDateKind): String = when (kind) {
        InsertDateKind.DATE -> date
        InsertDateKind.DATE_TIME -> dateTime
    }

    fun with(kind: InsertDateKind, pattern: String): InsertDateFormats = when (kind) {
        InsertDateKind.DATE -> copy(date = pattern)
        InsertDateKind.DATE_TIME -> copy(dateTime = pattern)
    }

    fun format(kind: InsertDateKind, moment: ZonedDateTime, locale: Locale): String =
        formatDatePattern(pattern(kind), moment, locale)
            ?: formatDatePattern(kind.defaultPattern, moment, locale)!!
}

/**
 * [moment] in [pattern], or `null` when the pattern is blank, malformed, asks for something a
 * moment cannot supply, or produces no text.
 */
fun formatDatePattern(pattern: String, moment: ZonedDateTime, locale: Locale): String? {
    if (pattern.isBlank()) return null
    return try {
        DateTimeFormatter.ofPattern(pattern, locale).format(moment).takeIf(String::isNotEmpty)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeException) {
        null
    }
}

/** Valid but likely unintended pattern letters, which often only show their effect at year end. */
enum class DatePatternPitfall {
    /** `Y` is the week-based year, which differs from the calendar year around New Year. */
    WEEK_BASED_YEAR,

    /** `D` is the day of the year, not the day of the month. */
    DAY_OF_YEAR
}

fun datePatternPitfalls(pattern: String): Set<DatePatternPitfall> {
    val pitfalls = mutableSetOf<DatePatternPitfall>()
    var quoted = false
    pattern.forEach { char ->
        when {
            char == '\'' -> quoted = !quoted
            quoted -> Unit
            char == 'Y' -> pitfalls += DatePatternPitfall.WEEK_BASED_YEAR
            char == 'D' -> pitfalls += DatePatternPitfall.DAY_OF_YEAR
        }
    }
    return pitfalls
}
