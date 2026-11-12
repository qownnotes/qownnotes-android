package org.qownnotes.mobile.core

/**
 * The longest side, in pixels, that a photo taken in the application is reduced to.
 *
 * [MEDIUM] is the default: about five megapixels keeps text on documents and whiteboards legible
 * when zoomed in, while a photo stays near one megabyte instead of the several megabytes a modern
 * camera produces.
 */
enum class PhotoSize(val maxDimension: Int) {
    SMALL(1920),
    MEDIUM(2560),
    LARGE(3840);

    companion object {
        val DEFAULT = MEDIUM
    }
}

/** How photos taken in the application are stored. Not reducing keeps the camera's own file. */
data class PhotoResolution(val reduce: Boolean = true, val size: PhotoSize = PhotoSize.DEFAULT) {
    /** The longest side to scale a photo down to, or `null` to keep its full resolution. */
    val maxDimension: Int? get() = if (reduce) size.maxDimension else null
}

/** Pixel dimensions of an image. */
data class PixelSize(val width: Int, val height: Int)

/**
 * The size a [width] by [height] photo is scaled down to so that its longest side is at most
 * [maxDimension], keeping its aspect ratio. Returns `null` when the photo already fits, so that it
 * is kept unchanged rather than re-encoded.
 */
fun reducedPhotoSize(width: Int, height: Int, maxDimension: Int?): PixelSize? {
    require(width > 0 && height > 0) { "Invalid photo dimensions" }
    if (maxDimension == null) return null
    require(maxDimension > 0) { "Invalid maximum photo dimension" }
    val longest = maxOf(width, height)
    if (longest <= maxDimension) return null
    val scale = maxDimension.toDouble() / longest
    fun scaled(side: Int) =
        if (side == longest) maxDimension else Math.round(side * scale).toInt().coerceAtLeast(1)
    return PixelSize(scaled(width), scaled(height))
}

/**
 * The largest power-of-two subsampling that still decodes a [width] by [height] image at least as
 * large as [target], so the final scaling step only ever reduces and keeps its detail.
 */
fun photoDecodeSampleSize(width: Int, height: Int, target: PixelSize): Int {
    var sample = 1
    while (width / (sample * 2) >= target.width && height / (sample * 2) >= target.height) {
        sample *= 2
    }
    return sample
}
