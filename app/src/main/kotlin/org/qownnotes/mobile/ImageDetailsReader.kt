package org.qownnotes.mobile

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDateTime
import org.qownnotes.mobile.core.cameraLabel
import org.qownnotes.mobile.core.parseExifDateTime

/** What an image file says about itself: its format and size, and what the camera recorded. */
internal data class ImageFileDetails(
    val info: ImageFileInfo,
    val takenAt: LocalDateTime? = null,
    val camera: String? = null,
    val lens: String? = null,
    val fNumber: Double? = null,
    val exposureSeconds: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val software: String? = null
) {
    val hasPhotoDetails: Boolean
        get() = takenAt != null || camera != null || lens != null || fNumber != null ||
            exposureSeconds != null || iso != null || focalLength != null || latitude != null ||
            software != null
}

internal object ImageDetailsReader {
    /** Reads [file]'s details, or `null` when it is not an image Android can decode. */
    fun read(file: File): ImageFileDetails? {
        val info = ImageShrinker.inspect(file) ?: return null
        val exif = runCatching { ExifInterface(file.path) }.getOrNull()
            ?: return ImageFileDetails(info)
        fun text(tag: String) = exif.getAttribute(tag)?.trim()?.takeIf(String::isNotEmpty)
        fun positive(tag: String) =
            exif.getAttributeDouble(tag, 0.0).takeIf { it > 0 && it.isFinite() }
        val latLong = runCatching { exif.latLong }.getOrNull()
            ?.takeIf { it.size == 2 && !(it[0] == 0.0 && it[1] == 0.0) }
        return ImageFileDetails(
            info = info,
            takenAt = parseExifDateTime(text(ExifInterface.TAG_DATETIME_ORIGINAL))
                ?: parseExifDateTime(text(ExifInterface.TAG_DATETIME)),
            camera = cameraLabel(text(ExifInterface.TAG_MAKE), text(ExifInterface.TAG_MODEL)),
            lens = text(ExifInterface.TAG_LENS_MODEL),
            fNumber = positive(ExifInterface.TAG_F_NUMBER),
            exposureSeconds = positive(ExifInterface.TAG_EXPOSURE_TIME),
            iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                .takeIf { it > 0 },
            focalLength = positive(ExifInterface.TAG_FOCAL_LENGTH),
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            software = text(ExifInterface.TAG_SOFTWARE)
        )
    }
}
