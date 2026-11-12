package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoResolutionTest {
    @Test
    fun reducesPhotosByDefault() {
        val resolution = PhotoResolution()

        assertEquals(PhotoSize.MEDIUM, resolution.size)
        assertEquals(2560, resolution.maxDimension)
    }

    @Test
    fun keepsTheFullResolutionWhenNotReducing() {
        assertNull(PhotoResolution(reduce = false, size = PhotoSize.SMALL).maxDimension)
        assertNull(reducedPhotoSize(8000, 6000, null))
    }

    @Test
    fun scalesTheLongestSideAndKeepsTheAspectRatio() {
        assertEquals(PixelSize(2560, 1920), reducedPhotoSize(4000, 3000, 2560))
        assertEquals(PixelSize(1920, 2560), reducedPhotoSize(3000, 4000, 2560))
        assertEquals(PixelSize(2560, 1440), reducedPhotoSize(4080, 2295, 2560))
        assertEquals(PixelSize(1920, 1920), reducedPhotoSize(4000, 4000, 1920))
    }

    @Test
    fun keepsPhotosThatAlreadyFit() {
        assertNull(reducedPhotoSize(2560, 1920, 2560))
        assertNull(reducedPhotoSize(1280, 960, 2560))
    }

    @Test
    fun neverScalesASideToNothing() {
        assertEquals(PixelSize(1920, 1), reducedPhotoSize(20000, 2, 1920))
    }

    @Test
    fun rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException::class.java) { reducedPhotoSize(0, 100, 2560) }
        assertThrows(IllegalArgumentException::class.java) { reducedPhotoSize(100, 100, 0) }
    }

    @Test
    fun subsamplesOnlyWhileTheDecodedImageStaysLargerThanTheTarget() {
        assertEquals(1, photoDecodeSampleSize(4000, 3000, PixelSize(2560, 1920)))
        assertEquals(2, photoDecodeSampleSize(8160, 6120, PixelSize(2560, 1920)))
        assertEquals(4, photoDecodeSampleSize(16320, 12240, PixelSize(2560, 1920)))
        assertEquals(2, photoDecodeSampleSize(4000, 3000, PixelSize(1920, 1440)))
    }
}
