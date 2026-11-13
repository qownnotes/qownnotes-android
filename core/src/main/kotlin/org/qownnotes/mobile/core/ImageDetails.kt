package org.qownnotes.mobile.core

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The aspect ratio of a [width] by [height] image as people name it, such as `4:3` or `16:9`.
 * Ratios close to a common one use its name; other ratios are given against 1, such as `2.35:1`.
 */
fun aspectRatioLabel(width: Int, height: Int, locale: Locale = Locale.getDefault()): String {
    require(width > 0 && height > 0) { "Invalid image dimensions" }
    val divisor = greatestCommonDivisor(width, height)
    val (exactWidth, exactHeight) = width / divisor to height / divisor
    if (exactWidth <= 21 && exactHeight <= 21) return "$exactWidth:$exactHeight"
    val ratio = width.toDouble() / height
    COMMON_RATIOS.firstOrNull { (a, b) -> abs(a.toDouble() / b - ratio) / ratio < 0.005 }
        ?.let { (a, b) -> return "$a:$b" }
    return if (ratio >= 1) {
        "${decimal(ratio, 2, locale)}:1"
    } else {
        "1:${decimal(1 / ratio, 2, locale)}"
    }
}

/** Megapixels of a [width] by [height] image, such as `12.5 MP`. */
fun megapixelsLabel(width: Int, height: Int, locale: Locale = Locale.getDefault()): String {
    val megapixels = width.toLong() * height / 1_000_000.0
    return "${decimal(megapixels, if (megapixels < 100) 1 else 0, locale)} MP"
}

/** An exposure time as photographers write it: `1/120 s` below a second, `2 s` or `1.5 s` above. */
fun exposureTimeLabel(seconds: Double, locale: Locale = Locale.getDefault()): String {
    require(seconds > 0) { "Invalid exposure time" }
    return if (seconds >= 0.5) {
        "${decimal(seconds, 1, locale)} s"
    } else {
        "1/${(1 / seconds).roundToLong()} s"
    }
}

/** An aperture, such as `f/1.8`. */
fun apertureLabel(fNumber: Double, locale: Locale = Locale.getDefault()): String =
    "f/${decimal(fNumber, 1, locale)}"

/** A focal length, such as `4.3 mm`. */
fun focalLengthLabel(millimeters: Double, locale: Locale = Locale.getDefault()): String =
    "${decimal(millimeters, 1, locale)} mm"

/** A position such as `48.2082° N, 16.3738° E`. */
fun coordinatesLabel(
    latitude: Double,
    longitude: Double,
    locale: Locale = Locale.getDefault()
): String = "${decimal(abs(latitude), 4, locale)}° ${if (latitude < 0) "S" else "N"}, " +
    "${decimal(abs(longitude), 4, locale)}° ${if (longitude < 0) "W" else "E"}"

/**
 * The camera's name from Exif [make] and [model]. Many cameras repeat the maker in the model, as
 * in `Google` and `Pixel 8` against `samsung` and `samsung SM-S918B`, so it is not doubled.
 */
fun cameraLabel(make: String?, model: String?): String? {
    val cleanMake = make?.trim()?.takeIf(String::isNotEmpty)
    val cleanModel = model?.trim()?.takeIf(String::isNotEmpty)
    return when {
        cleanModel == null -> cleanMake
        cleanMake == null -> cleanModel
        cleanModel.startsWith(cleanMake, ignoreCase = true) -> cleanModel
        else -> "$cleanMake $cleanModel"
    }
}

/** Reads an Exif date and time, `2026:11:12 09:30:00`, which has no time zone. */
fun parseExifDateTime(value: String?): LocalDateTime? = value?.trim()?.let {
    runCatching { LocalDateTime.parse(it, EXIF_DATE_TIME) }.getOrNull()
}

/** The file name at the end of an image link, decoding `%20` and similar escapes. */
fun imageLinkFileName(link: String): String {
    val path = link.substringBefore('?').substringBefore('#').trimEnd('/')
    val name = path.substringAfterLast('/')
    return runCatching { java.net.URLDecoder.decode(name.replace("+", "%2B"), "UTF-8") }
        .getOrDefault(name)
        .ifEmpty { link }
}

private fun decimal(value: Double, fractionDigits: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = fractionDigits
    }.format(value)

private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int =
    if (b == 0) a else greatestCommonDivisor(b, a % b)

private val COMMON_RATIOS = listOf(
    1 to 1, 4 to 3, 3 to 4, 3 to 2, 2 to 3, 16 to 9, 9 to 16, 16 to 10, 10 to 16,
    5 to 4, 4 to 5, 21 to 9, 9 to 21, 2 to 1, 1 to 2, 20 to 9, 9 to 20
)

private val EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
