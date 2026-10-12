package org.qownnotes.mobile.markdown

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows app-owned image files, such as a captured photo that has not been uploaded yet.
 *
 * Only files the application registered through [register] can be loaded; a note cannot name an
 * arbitrary path. When the file is gone, for example because synchronization removed it after the
 * upload, the registered Nextcloud attachment destination is loaded instead.
 */
internal class LocalImageSchemeHandler(
    private val resources: Resources,
    private val fallback: SchemeHandler?
) : SchemeHandler() {
    private val images = ConcurrentHashMap<String, LocalImage>()

    fun register(file: File, fallbackDestination: String?): String {
        val token = URLEncoder.encode(file.absolutePath, StandardCharsets.UTF_8.name()) +
            "?remote=" +
            URLEncoder.encode(fallbackDestination.orEmpty(), StandardCharsets.UTF_8.name())
        images[token] = LocalImage(file, fallbackDestination)
        return "$LOCAL_IMAGE_SCHEME:$token"
    }

    override fun supportedSchemes(): Collection<String> = listOf(LOCAL_IMAGE_SCHEME)

    override fun handle(raw: String, uri: Uri): ImageItem {
        val image = images[raw.removePrefix("$LOCAL_IMAGE_SCHEME:")]
            ?: error("Unknown local image")
        if (image.file.isFile) {
            runCatching { return ImageItem.withResult(decodeLocalImage(resources, image.file)) }
        }
        val remote = image.fallbackDestination ?: error("Local image is missing")
        return fallback?.handle(remote, Uri.parse(remote)) ?: error("Local image is missing")
    }

    private data class LocalImage(val file: File, val fallbackDestination: String?)
}

/** Camera photos may be larger than remote images, so they are only downsampled, not rejected. */
private fun decodeLocalImage(resources: Resources, file: File): BitmapDrawable {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outMimeType?.lowercase(Locale.ROOT) in LOCAL_IMAGE_TYPES) {
        "Unsupported image type"
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions" }
    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
    while (bounds.outWidth / options.inSampleSize > MAX_LOCAL_DECODED_DIMENSION ||
        bounds.outHeight / options.inSampleSize > MAX_LOCAL_DECODED_DIMENSION
    ) {
        options.inSampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeFile(file.path, options) ?: error("Image could not be decoded")
    return BitmapDrawable(resources, bitmap)
}

internal const val LOCAL_IMAGE_SCHEME = "qon-local-image"
private const val MAX_LOCAL_DECODED_DIMENSION = 2048
private val LOCAL_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
