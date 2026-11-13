package org.qownnotes.mobile.core

import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageDetailsTest {
    @Test
    fun namesCommonAspectRatios() {
        assertEquals("4:3", aspectRatioLabel(4000, 3000, Locale.US))
        assertEquals("3:4", aspectRatioLabel(3000, 4000, Locale.US))
        assertEquals("16:9", aspectRatioLabel(1920, 1080, Locale.US))
        assertEquals("1:1", aspectRatioLabel(512, 512, Locale.US))
        // Cameras and phones round sizes, so near-common ratios use the common name.
        assertEquals("4:3", aspectRatioLabel(4080, 3072, Locale.US))
        assertEquals("16:9", aspectRatioLabel(2560, 1441, Locale.US))
        assertEquals("20:9", aspectRatioLabel(1080, 2400, Locale.US).reversedRatio())
    }

    @Test
    fun givesUncommonAspectRatiosAgainstOne() {
        assertEquals("2.35:1", aspectRatioLabel(2350, 1000, Locale.US))
        assertEquals("1:2.35", aspectRatioLabel(1000, 2350, Locale.US))
        assertEquals("2,35:1", aspectRatioLabel(2350, 1000, Locale.GERMANY))
        assertThrows(IllegalArgumentException::class.java) { aspectRatioLabel(0, 10) }
    }

    @Test
    fun formatsMegapixels() {
        assertEquals("12 MP", megapixelsLabel(4000, 3000, Locale.US))
        assertEquals("4.9 MP", megapixelsLabel(2560, 1920, Locale.US))
        assertEquals("0.3 MP", megapixelsLabel(640, 480, Locale.US))
        assertEquals("49.9 MP", megapixelsLabel(8160, 6120, Locale.US))
        assertEquals("12.5 MP", megapixelsLabel(4080, 3072, Locale.US))
        assertEquals("200 MP", megapixelsLabel(16320, 12240, Locale.US))
    }

    @Test
    fun formatsExposureLikePhotographers() {
        assertEquals("1/120 s", exposureTimeLabel(1 / 120.0, Locale.US))
        assertEquals("1/4000 s", exposureTimeLabel(0.00025, Locale.US))
        assertEquals("2 s", exposureTimeLabel(2.0, Locale.US))
        assertEquals("1.5 s", exposureTimeLabel(1.5, Locale.US))
        assertEquals("f/1.8", apertureLabel(1.8, Locale.US))
        assertEquals("f/2", apertureLabel(2.0, Locale.US))
        assertEquals("4.3 mm", focalLengthLabel(4.32, Locale.US))
    }

    @Test
    fun formatsCoordinatesWithHemispheres() {
        assertEquals("48.2082° N, 16.3738° E", coordinatesLabel(48.20820, 16.37380, Locale.US))
        assertEquals("33.8688° S, 151.2093° E", coordinatesLabel(-33.8688, 151.2093, Locale.US))
        assertEquals("40.7128° N, 74.006° W", coordinatesLabel(40.7128, -74.0060, Locale.US))
    }

    @Test
    fun doesNotRepeatTheMakerInTheCameraName() {
        assertEquals("Google Pixel 8", cameraLabel("Google", "Pixel 8"))
        assertEquals("samsung SM-S918B", cameraLabel("samsung", "samsung SM-S918B"))
        assertEquals("Canon", cameraLabel(" Canon ", ""))
        assertEquals("EOS R5", cameraLabel(null, "EOS R5"))
        assertNull(cameraLabel(null, " "))
    }

    @Test
    fun readsExifDatesWithoutATimeZone() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 9, 30, 0),
            parseExifDateTime("2026:11:12 09:30:00")
        )
        assertNull(parseExifDateTime("0000:00:00 00:00:00"))
        assertNull(parseExifDateTime("not a date"))
        assertNull(parseExifDateTime(null))
    }

    @Test
    fun takesTheFileNameFromAnImageLink() {
        assertEquals("image-1.webp", imageLinkFileName("../media/image-1.webp"))
        assertEquals("my photo.jpg", imageLinkFileName("media/my%20photo.jpg"))
        assertEquals("a+b.png", imageLinkFileName("media/a+b.png"))
        assertEquals("pic.png", imageLinkFileName("https://example.com/img/pic.png?size=2"))
    }

    private fun String.reversedRatio() = split(':').reversed().joinToString(":")
}
