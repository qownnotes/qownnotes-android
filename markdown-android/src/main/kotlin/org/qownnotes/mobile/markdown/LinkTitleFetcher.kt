package org.qownnotes.mobile.markdown

import java.io.IOException
import java.io.InputStream
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal class LinkTitleFetcher(private val client: OkHttpClient = safeLinkTitleClient()) {
    fun fetch(rawUrl: String): FetchedLink {
        var url = requireSafeWebUrl(rawUrl)
        repeat(MAX_LINK_REDIRECTS + 1) { redirectCount ->
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "text/html, application/xhtml+xml")
                    .get()
                    .build()
            ).execute().use { response ->
                if (response.isRedirect) {
                    if (redirectCount == MAX_LINK_REDIRECTS) throw IOException("Too many redirects")
                    val location = response.header("Location")
                        ?: throw IOException("Redirect has no location")
                    url = requireSafeWebUrl(url.resolve(location)?.toString().orEmpty())
                    return@repeat
                }
                if (!response.isSuccessful) throw IOException("Page request failed")
                val body = response.body ?: throw IOException("Page has no content")
                val mediaType = body.contentType()
                if (mediaType?.let { it.type != "text" || it.subtype !in HTML_SUBTYPES } == true) {
                    throw IOException("URL does not point to an HTML page")
                }
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_TITLE_DOCUMENT_BYTES) {
                    throw IOException("Page is too large")
                }
                val bytes = body.byteStream().use { it.readAtMost(MAX_TITLE_DOCUMENT_BYTES) }
                val charset = mediaType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                val title = extractHtmlTitle(bytes.toString(charset))
                    ?: throw IOException("Page has no title")
                return FetchedLink(url.toString(), title)
            }
        }
        throw IOException("Redirect failed")
    }
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        total += count
        if (total > limit) throw IOException("Page is too large")
        output.write(buffer, 0, count)
    }
}

internal data class FetchedLink(val url: String, val title: String)

internal fun canonicalSafeWebUrl(raw: String): String? =
    runCatching { requireSafeWebUrl(raw).toString() }.getOrNull()

internal fun requireSafeWebUrl(raw: String): HttpUrl {
    val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid web URL")
    require(url.scheme == "http" || url.scheme == "https") { "Only web URLs are allowed" }
    require(url.host.isNotBlank() && url.username.isEmpty() && url.password.isEmpty()) {
        "URL credentials are blocked"
    }
    return url
}

internal fun extractHtmlTitle(html: String): String? {
    val rawTitle = HTML_TITLE.find(html)?.groupValues?.get(1) ?: return null
    val decoded = HTML_ENTITY.replace(rawTitle) { match -> decodeHtmlEntity(match.value) }
    return decoded.replace(HTML_WHITESPACE, " ").trim().take(MAX_LINK_TITLE_LENGTH)
        .takeIf(String::isNotEmpty)
}

internal fun markdownLink(title: String, url: String): String {
    val escapedTitle = title
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
    return "[$escapedTitle]($url)"
}

private fun decodeHtmlEntity(entity: String): String {
    val value = entity.substring(1, entity.length - 1)
    val codePoint = when {
        value.startsWith("#x", ignoreCase = true) -> value.drop(2).toIntOrNull(16)
        value.startsWith('#') -> value.drop(1).toIntOrNull()
        else -> HTML_NAMED_ENTITIES[value.lowercase()]
    } ?: return entity
    return runCatching { String(Character.toChars(codePoint)) }.getOrDefault(entity)
}

private fun safeLinkTitleClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .dns(
        object : Dns {
            override fun lookup(hostname: String) =
                Dns.SYSTEM.lookup(hostname).takeIf { addresses ->
                    addresses.isNotEmpty() && addresses.all(::isPublicImageAddress)
                } ?: throw UnknownHostException("Blocked link host")
        }
    )
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

private val HTML_TITLE = Regex(
    "<title(?:\\s[^>]*)?>([\\s\\S]*?)</title\\s*>",
    RegexOption.IGNORE_CASE
)
private val HTML_ENTITY = Regex("&(?:#[xX][0-9a-fA-F]+|#\\d+|[A-Za-z]+);")
private val HTML_WHITESPACE = Regex("\\s+")
private val HTML_SUBTYPES = setOf("html", "xhtml+xml")
private val HTML_NAMED_ENTITIES = mapOf(
    "amp" to '&'.code,
    "apos" to '\''.code,
    "gt" to '>'.code,
    "lt" to '<'.code,
    "nbsp" to ' '.code,
    "quot" to '"'.code
)
private const val MAX_LINK_REDIRECTS = 3
private const val MAX_TITLE_DOCUMENT_BYTES = 512 * 1024
private const val MAX_LINK_TITLE_LENGTH = 512
