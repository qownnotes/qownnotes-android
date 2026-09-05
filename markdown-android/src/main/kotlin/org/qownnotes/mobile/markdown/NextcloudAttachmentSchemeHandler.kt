package org.qownnotes.mobile.markdown

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * Fetches note-attachment images through SSO-authenticated Nextcloud API requests.
 *
 * Image destinations are rewritten to a `nextcloud-attachment:` URL by the
 * [AttachmentDestinationProcessor][MarkdownRenderer] when a server URL and note ID are available.
 * This handler then delegates to [NextcloudAttachmentHttpClient] for the actual HTTP fetch.
 */
internal class NextcloudAttachmentSchemeHandler(
    private val resources: Resources,
    private val httpClient: NextcloudAttachmentHttpClient
) : SchemeHandler() {

    @Volatile
    var accountName: String = ""

    override fun supportedSchemes(): Collection<String> = listOf(SCHEME)

    override fun handle(raw: String, uri: Uri): ImageItem {
        val url = uri.toString().removePrefix("$SCHEME:")
        android.util.Log.d(
            "QOwnNotes",
            "Attachment handle: raw=$raw, uri=$uri, url=$url, account=$accountName"
        )
        val inputStream = httpClient.fetch(url, accountName)
            ?: error("Failed to fetch attachment: $url (account=$accountName)")
        return inputStream.use { ImageItem.withResult(it.decodeBoundedDrawable(resources)) }
    }
}

private fun InputStream.decodeBoundedDrawable(resources: Resources): BitmapDrawable {
    val bytes = readBoundedBytes(MAX_IMAGE_BYTES)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val mimeType = bounds.outMimeType?.lowercase(Locale.ROOT)
    require(mimeType in ALLOWED_IMAGE_TYPES) { "Unsupported image type" }
    require(
        bounds.outWidth in 1..MAX_SOURCE_DIMENSION && bounds.outHeight in 1..MAX_SOURCE_DIMENSION
    ) {
        "Invalid image dimensions"
    }
    require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) {
        "Image dimensions are too large"
    }

    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
    while (bounds.outWidth / options.inSampleSize > MAX_DECODED_DIMENSION ||
        bounds.outHeight / options.inSampleSize > MAX_DECODED_DIMENSION
    ) {
        options.inSampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("Image could not be decoded")
    return BitmapDrawable(resources, bitmap)
}

private fun InputStream.readBoundedBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "Image response is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal const val ATTACHMENT_SCHEME = "nextcloud-attachment"
private const val SCHEME = ATTACHMENT_SCHEME
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
private const val MAX_SOURCE_DIMENSION = 8192
private const val MAX_SOURCE_PIXELS = 32L * 1024 * 1024
private const val MAX_DECODED_DIMENSION = 2048
private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
