package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageShrinkingTest {
    @Test
    fun shrinksEveryAddedImageToWebpByDefault() {
        val settings = ImageShrinkSettings()
        val resolution = PhotoResolution()

        assertTrue(settings.saveAsWebp)
        assertEquals(
            ImageShrinking(2560, webp = true),
            settings.shrinkingFor(ImageOrigin.CAMERA, resolution)
        )
        assertEquals(
            ImageShrinking(2560, webp = true),
            settings.shrinkingFor(ImageOrigin.ANNOTATED, resolution)
        )
        assertEquals(
            ImageShrinking(2560, webp = true),
            settings.shrinkingFor(ImageOrigin.CHOSEN, resolution)
        )
    }

    @Test
    fun eachWayOfAddingImagesCanBeTurnedOnOrOffOnItsOwn() {
        val settings = ImageShrinkSettings()
            .with(ImageOrigin.CAMERA, false)
            .with(ImageOrigin.CHOSEN, false)

        assertFalse(settings.appliesTo(ImageOrigin.CAMERA))
        assertFalse(settings.appliesTo(ImageOrigin.CHOSEN))
        assertTrue(settings.appliesTo(ImageOrigin.ANNOTATED))
        assertEquals(
            ImageShrinking.NONE,
            settings.shrinkingFor(ImageOrigin.CAMERA, PhotoResolution())
        )
    }

    @Test
    fun followsTheResolutionAndFormatSettings() {
        val settings = ImageShrinkSettings(saveAsWebp = false, chosenImages = true)

        assertEquals(
            ImageShrinking(1920, webp = false),
            settings.shrinkingFor(ImageOrigin.CHOSEN, PhotoResolution(size = PhotoSize.SMALL))
        )
        val fullResolution = settings.copy(saveAsWebp = true)
            .shrinkingFor(ImageOrigin.CHOSEN, PhotoResolution(reduce = false))
        assertEquals(ImageShrinking(null, webp = true), fullResolution)
        assertTrue(fullResolution.changesImages)
        assertFalse(
            settings.shrinkingFor(ImageOrigin.CHOSEN, PhotoResolution(reduce = false))
                .changesImages
        )
    }

    @Test
    fun rejectsAnInvalidMaximumDimension() {
        assertThrows(IllegalArgumentException::class.java) { ImageShrinking(0) }
    }

    @Test
    fun keepsOnlyCopiesThatAreSmallerThanTheOriginal() {
        assertTrue(keepsShrunkImage(1_000, 999))
        assertFalse(keepsShrunkImage(1_000, 1_000))
        assertFalse(keepsShrunkImage(1_000, 1_200))
        assertFalse(keepsShrunkImage(1_000, 0))
    }

    @Test
    fun reportsTheSavedShareAsAWholePercentage() {
        assertEquals(80, savedPercent(4_000_000, 800_000))
        assertEquals(1, savedPercent(1_000_000, 999_999))
        assertEquals(99, savedPercent(1_000_000, 1))
        assertEquals(0, savedPercent(1_000, 1_000))
        assertEquals(0, savedPercent(0, 0))
    }

    @Test
    fun estimatesATypicalPhotoForEachSetting() {
        assertEquals(1_200_000L, typicalShrunkPhotoBytes(PhotoResolution(), saveAsWebp = false))
        assertEquals(840_000L, typicalShrunkPhotoBytes(PhotoResolution(), saveAsWebp = true))
        assertEquals(
            TYPICAL_CAMERA_PHOTO_BYTES,
            typicalShrunkPhotoBytes(PhotoResolution(reduce = false), saveAsWebp = false)
        )
        assertTrue(
            typicalShrunkPhotoBytes(PhotoResolution(size = PhotoSize.SMALL), true) <
                typicalShrunkPhotoBytes(PhotoResolution(size = PhotoSize.LARGE), true)
        )
    }
}
