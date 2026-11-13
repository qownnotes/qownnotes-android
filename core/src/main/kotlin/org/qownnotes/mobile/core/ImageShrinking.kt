package org.qownnotes.mobile.core

import kotlin.math.roundToInt

/** A way an image is added to a note. Each can shrink images or keep them as they are. */
enum class ImageOrigin {
    /** Photos taken with the camera in a note or from a widget. */
    CAMERA,

    /** Images chosen from the gallery or from files. */
    CHOSEN,

    /** Copies saved from the image annotation editor. */
    ANNOTATED
}

/**
 * Which ways of adding an image shrink it, and whether shrinking stores it as WebP. The resolution
 * shrunk images are scaled down to is the [PhotoResolution] setting.
 *
 * Every way of adding an image shrinks it by default; each can be turned off on its own, for
 * example to keep chosen images that were already prepared elsewhere exactly as they are.
 */
data class ImageShrinkSettings(
    val saveAsWebp: Boolean = true,
    val cameraPhotos: Boolean = true,
    val chosenImages: Boolean = true,
    val annotatedImages: Boolean = true
) {
    fun appliesTo(origin: ImageOrigin): Boolean = when (origin) {
        ImageOrigin.CAMERA -> cameraPhotos
        ImageOrigin.CHOSEN -> chosenImages
        ImageOrigin.ANNOTATED -> annotatedImages
    }

    fun with(origin: ImageOrigin, enabled: Boolean): ImageShrinkSettings = when (origin) {
        ImageOrigin.CAMERA -> copy(cameraPhotos = enabled)
        ImageOrigin.CHOSEN -> copy(chosenImages = enabled)
        ImageOrigin.ANNOTATED -> copy(annotatedImages = enabled)
    }

    /** How an image added in the given way is shrunk. */
    fun shrinkingFor(origin: ImageOrigin, resolution: PhotoResolution): ImageShrinking =
        if (appliesTo(origin)) {
            ImageShrinking(resolution.maxDimension, saveAsWebp)
        } else {
            ImageShrinking.NONE
        }
}

/**
 * How one image is shrunk: scaled down so its longest side is at most [maxDimension], or kept at
 * its resolution when that is `null`, and re-encoded as WebP when [webp] is set.
 */
data class ImageShrinking(val maxDimension: Int? = null, val webp: Boolean = false) {
    init {
        require(maxDimension == null || maxDimension > 0) { "Invalid maximum image dimension" }
    }

    /** Whether this can change an image at all. */
    val changesImages: Boolean get() = maxDimension != null || webp

    companion object {
        val NONE = ImageShrinking()
    }
}

/**
 * Whether a shrunk copy of [originalBytes] that is [shrunkBytes] long is worth keeping. Shrinking
 * never makes an image larger: when re-encoding does not save space, the original is kept.
 */
fun keepsShrunkImage(originalBytes: Long, shrunkBytes: Long): Boolean =
    shrunkBytes in 1 until originalBytes

/** The share of [originalBytes] that shrinking to [shrunkBytes] saves, as a whole percentage. */
fun savedPercent(originalBytes: Long, shrunkBytes: Long): Int {
    if (originalBytes <= 0 || shrunkBytes >= originalBytes) return 0
    return ((originalBytes - shrunkBytes) * 100.0 / originalBytes).roundToInt().coerceIn(1, 99)
}

/** A typical 12-megapixel camera photo, as the camera saves it. */
const val TYPICAL_CAMERA_PHOTO_BYTES = 4_000_000L

/**
 * A rough size of a typical 12-megapixel camera photo after it is shrunk as [resolution] and
 * [saveAsWebp] ask. WebP is about 30 % smaller than JPEG at a similar visual quality.
 */
fun typicalShrunkPhotoBytes(resolution: PhotoResolution, saveAsWebp: Boolean): Long {
    val jpegBytes = if (resolution.reduce) {
        when (resolution.size) {
            PhotoSize.SMALL -> 700_000L
            PhotoSize.MEDIUM -> 1_200_000L
            PhotoSize.LARGE -> 2_700_000L
        }
    } else {
        TYPICAL_CAMERA_PHOTO_BYTES
    }
    return if (saveAsWebp) jpegBytes * WEBP_SIZE_PERCENT / 100 else jpegBytes
}

private const val WEBP_SIZE_PERCENT = 70
