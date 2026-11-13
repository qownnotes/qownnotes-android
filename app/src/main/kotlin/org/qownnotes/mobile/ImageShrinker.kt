package org.qownnotes.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.ImageShrinking
import org.qownnotes.mobile.core.PixelSize
import org.qownnotes.mobile.core.keepsShrunkImage
import org.qownnotes.mobile.core.photoDecodeSampleSize
import org.qownnotes.mobile.core.reducedPhotoSize
import org.qownnotes.mobile.markdown.exifOrientationMatrix

/** An image's format, its upright size in pixels, and its file size. */
internal data class ImageFileInfo(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bytes: Long
)

/**
 * Shrinks images before they are added to a note: scales them down to a maximum dimension and
 * stores them as WebP, as [ImageShrinking] asks.
 *
 * Photos keep a lossy encoding, while PNG images, such as screenshots and diagrams, become lossless
 * WebP so that text and lines stay sharp. A shrunk image has the camera's orientation applied to its
 * pixels, because re-encoding drops the camera's metadata; only the capture time is carried over.
 * An image that would not get smaller, or that cannot be decoded, is kept exactly as it is.
 */
internal object ImageShrinker {
    /** Reads an image's format and upright size from its content, not from its file name. */
    fun inspect(file: File): ImageFileInfo? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val mimeType = bounds.outMimeType?.lowercase() ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val turned = orientationOf(file, mimeType) in TURNED_ORIENTATIONS
        return ImageFileInfo(
            mimeType = mimeType,
            width = if (turned) bounds.outHeight else bounds.outWidth,
            height = if (turned) bounds.outWidth else bounds.outHeight,
            bytes = file.length()
        )
    }

    /**
     * Shrinks the image in [file] in place and returns its MIME type afterwards. The file is left
     * untouched when nothing would be saved.
     */
    suspend fun shrinkInPlace(file: File, shrinking: ImageShrinking): String? =
        withContext(Dispatchers.IO) {
            val shrunk = File(file.parentFile, "${file.name}.shrunk")
            try {
                val result = shrinkTo(file, shrinking, shrunk)
                if (result != null) {
                    check(shrunk.renameTo(file)) { "The image could not be saved" }
                    result.mimeType
                } else {
                    inspect(file)?.mimeType
                }
            } finally {
                shrunk.delete()
            }
        }

    /**
     * Writes a shrunk copy of [source] to [target] and describes it, or returns `null` and leaves
     * no [target] when the source should be kept as it is: nothing is asked, the image already
     * fits, or the copy would not be smaller.
     */
    fun shrinkTo(source: File, shrinking: ImageShrinking, target: File): ImageFileInfo? {
        target.delete()
        if (!shrinking.changesImages) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        val sourceType = bounds.outMimeType?.lowercase() ?: return null
        if (sourceType !in SHRINKABLE_TYPES || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val reduced = reducedPhotoSize(bounds.outWidth, bounds.outHeight, shrinking.maxDimension)
        val outputType = if (shrinking.webp) WEBP else sourceType
        if (reduced == null && outputType == sourceType) return null

        val exif = exifOf(source, sourceType)
        val size = reduced ?: PixelSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = photoDecodeSampleSize(bounds.outWidth, bounds.outHeight, size)
        }
        val decoded = BitmapFactory.decodeFile(source.path, options)
            ?: error("The image could not be decoded")
        val matrix = Matrix().apply {
            setScale(size.width.toFloat() / decoded.width, size.height.toFloat() / decoded.height)
            exif?.let {
                postConcat(
                    exifOrientationMatrix(
                        it.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    )
                )
            }
        }
        val shrunk = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (shrunk !== decoded) decoded.recycle()
        val lossless = sourceType == PNG || (sourceType == WEBP && shrunk.hasAlpha())
        try {
            target.outputStream().use { output ->
                check(shrunk.compressAs(outputType, lossless, output)) {
                    "The image could not be saved"
                }
            }
        } finally {
            shrunk.recycle()
        }
        if (!keepsShrunkImage(source.length(), target.length())) {
            target.delete()
            return null
        }
        exif?.let { copyCaptureTime(it, target) }
        return ImageFileInfo(outputType, shrunk.width, shrunk.height, target.length())
    }

    /**
     * Encodes an image drawn in the app, scaled down as [shrinking] asks. Without WebP, opaque
     * images become JPEG and images with transparency stay lossless as PNG.
     */
    fun encode(image: Bitmap, shrinking: ImageShrinking): Pair<String, ByteArray> {
        val size = reducedPhotoSize(image.width, image.height, shrinking.maxDimension)
        val scaled = size?.let { image.scale(it.width, it.height) } ?: image
        val transparent = scaled.hasAlpha()
        val type = when {
            shrinking.webp -> WEBP
            transparent -> PNG
            else -> JPEG
        }
        return try {
            type to ByteArrayOutputStream().use { output ->
                check(scaled.compressAs(type, lossless = transparent, output)) {
                    "The image could not be encoded"
                }
                output.toByteArray()
            }
        } finally {
            if (scaled !== image) scaled.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun Bitmap.compressAs(
        mimeType: String,
        lossless: Boolean,
        output: java.io.OutputStream
    ): Boolean = when (mimeType) {
        PNG -> compress(Bitmap.CompressFormat.PNG, 100, output)
        JPEG -> compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (lossless) {
                compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
            } else {
                compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, output)
            }
        } else {
            // Before Android 11, quality 100 is the closest to lossless that WebP offers.
            compress(Bitmap.CompressFormat.WEBP, if (lossless) 100 else WEBP_QUALITY, output)
        }
    }

    private fun exifOf(file: File, mimeType: String): ExifInterface? = if (mimeType in
        SHRINKABLE_TYPES
    ) {
        runCatching { ExifInterface(file.path) }.getOrNull()
    } else {
        null
    }

    private fun orientationOf(file: File, mimeType: String): Int =
        exifOf(file, mimeType)?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

    private fun copyCaptureTime(source: ExifInterface, target: File) {
        runCatching {
            val times = CAPTURE_TIME_TAGS.mapNotNull { tag ->
                source.getAttribute(tag)?.let {
                    tag to
                        it
                }
            }
            if (times.isEmpty()) return
            val copy = ExifInterface(target.path)
            times.forEach { (tag, value) -> copy.setAttribute(tag, value) }
            copy.saveAttributes()
        }
    }

    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val WEBP = "image/webp"
    private val SHRINKABLE_TYPES = setOf(JPEG, PNG, WEBP)
    private const val JPEG_QUALITY = 90
    private const val WEBP_QUALITY = 85
    private val TURNED_ORIENTATIONS = setOf(
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_TRANSVERSE,
        ExifInterface.ORIENTATION_ROTATE_270
    )
    private val CAPTURE_TIME_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED
    )
}

/** A shrunk copy of a note image in the cache, waiting to be attached to the note. */
internal data class ShrunkNoteImage(val file: File, val info: ImageFileInfo)
