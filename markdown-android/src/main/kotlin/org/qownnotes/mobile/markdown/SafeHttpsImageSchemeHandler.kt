package org.qownnotes.mobile.markdown

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class SafeHttpsImageSchemeHandler(
    private val resources: Resources,
    private val client: OkHttpClient = safeImageClient()
) : SchemeHandler() {
    override fun supportedSchemes(): Collection<String> = listOf(HTTPS_SCHEME)

    override fun handle(raw: String, uri: Uri): ImageItem {
        var url = requireSafeHttpsUrl(raw)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            response.use {
                if (it.isRedirect) {
                    if (redirectCount == MAX_REDIRECTS) error("Too many image redirects")
                    val location = it.header("Location") ?: error("Image redirect has no location")
                    url = requireSafeHttpsUrl(url.resolve(location)?.toString().orEmpty())
                    return@repeat
                }
                if (!it.isSuccessful) error("Image request failed")
                return ImageItem.withResult(it.decodeBoundedDrawable(resources))
            }
        }
        error("Image redirect failed")
    }
}

internal fun requireSafeHttpsUrl(raw: String): HttpUrl {
    val url = raw.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid image URL")
    require(url.scheme == HTTPS_SCHEME && url.host.isNotBlank()) { "Only HTTPS images are allowed" }
    require(url.username.isEmpty() && url.password.isEmpty()) {
        "Image URL credentials are blocked"
    }
    return url
}

internal fun isPublicImageAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) {
        return false
    }
    val bytes = address.address
    return !(bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC)
}

private fun safeImageClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .dns(
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                Dns.SYSTEM.lookup(hostname).takeIf { addresses ->
                    addresses.isNotEmpty() && addresses.all(::isPublicImageAddress)
                } ?: throw UnknownHostException("Blocked image host")
        }
    )
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

private fun Response.decodeBoundedDrawable(resources: Resources): BitmapDrawable {
    val responseBody = body ?: error("Image response has no body")
    val declaredLength = responseBody.contentLength()
    require(declaredLength in -1..MAX_IMAGE_BYTES.toLong()) { "Image response is too large" }
    val bytes = responseBody.byteStream().use { it.readBoundedBytes(MAX_IMAGE_BYTES) }

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

private const val HTTPS_SCHEME = "https"
private const val MAX_REDIRECTS = 3
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
private const val MAX_SOURCE_DIMENSION = 8192
private const val MAX_SOURCE_PIXELS = 32L * 1024 * 1024
private const val MAX_DECODED_DIMENSION = 2048
private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
