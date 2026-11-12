package org.qownnotes.mobile.markdown

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/**
 * The transformation that shows an image upright for its Exif orientation tag. Cameras commonly
 * store photos sideways and record the rotation there, which the bitmap decoder ignores.
 */
fun exifOrientationMatrix(orientation: Int): Matrix = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            postRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            postRotate(-90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
    }
}

/** The Exif orientation of a JPEG, or [ExifInterface.ORIENTATION_NORMAL] when it has none. */
internal fun exifOrientation(open: () -> InputStream): Int = runCatching {
    open().use { input ->
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

internal fun exifOrientation(file: File): Int = exifOrientation(file::inputStream)

/** Returns this bitmap turned upright for [orientation], recycling it when a copy was needed. */
internal fun Bitmap.uprightFor(orientation: Int): Bitmap {
    val matrix = exifOrientationMatrix(orientation)
    if (matrix.isIdentity) return this
    val upright = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (upright !== this) recycle()
    return upright
}
