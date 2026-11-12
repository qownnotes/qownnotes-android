package org.qownnotes.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.photoDecodeSampleSize
import org.qownnotes.mobile.core.reducedPhotoSize
import org.qownnotes.mobile.markdown.exifOrientationMatrix

/**
 * Scales a camera photo down in place so its longest side is at most a maximum dimension.
 *
 * Photos that already fit, and every photo when no maximum is given, are left exactly as the
 * camera wrote them. A reduced photo has the camera's orientation applied to its pixels, because
 * re-encoding drops the camera's metadata; only the capture time is carried over.
 */
internal object CapturedPhotoProcessor {
    suspend fun reduce(file: File, maxDimension: Int?) = withContext(Dispatchers.IO) {
        if (maxDimension == null || !file.isFile) return@withContext
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext
        val target = reducedPhotoSize(bounds.outWidth, bounds.outHeight, maxDimension)
            ?: return@withContext
        val exif = runCatching { ExifInterface(file.path) }.getOrNull()
        val options = BitmapFactory.Options().apply {
            inSampleSize = photoDecodeSampleSize(bounds.outWidth, bounds.outHeight, target)
        }
        val decoded = BitmapFactory.decodeFile(file.path, options)
            ?: error("The photo could not be decoded")
        val matrix = Matrix().apply {
            setScale(
                target.width.toFloat() / decoded.width,
                target.height.toFloat() / decoded.height
            )
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
        val reduced = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true
        )
        if (reduced !== decoded) decoded.recycle()
        val temporary = File(file.parentFile, "${file.name}.reduced")
        try {
            temporary.outputStream().use { output ->
                check(reduced.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "The photo could not be saved"
                }
            }
            reduced.recycle()
            exif?.let { copyCaptureTime(it, temporary) }
            check(temporary.renameTo(file)) { "The photo could not be saved" }
        } finally {
            temporary.delete()
        }
    }

    private fun copyCaptureTime(source: ExifInterface, target: File) {
        runCatching {
            val copy = ExifInterface(target.path)
            CAPTURE_TIME_TAGS.forEach { tag ->
                source.getAttribute(tag)?.let { copy.setAttribute(tag, it) }
            }
            copy.saveAttributes()
        }
    }

    private const val JPEG_QUALITY = 90
    private val CAPTURE_TIME_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED
    )
}
