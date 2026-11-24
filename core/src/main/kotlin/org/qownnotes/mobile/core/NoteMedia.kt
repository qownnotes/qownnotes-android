package org.qownnotes.mobile.core

import java.util.UUID

private val IMAGE_EXTENSIONS = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp"
)

fun uniqueMediaImageFileName(
    mimeType: String,
    uniqueId: String = UUID.randomUUID().toString()
): String {
    val extension = IMAGE_EXTENSIONS[mimeType.lowercase()]
        ?: throw IllegalArgumentException("Choose a JPEG, PNG, or WebP image")
    val safeId = uniqueId.filter(Char::isLetterOrDigit)
    require(safeId.isNotEmpty())
    return "image-$safeId.$extension"
}

/** Whether an image link points to a JPEG, PNG, or WebP file, which can be shrunk. */
fun isShrinkableImagePath(path: String): Boolean =
    path.substringBefore('?').substringBefore('#').substringAfterLast('/')
        .substringAfterLast('.', "").lowercase() in SHRINKABLE_IMAGE_EXTENSIONS

private val SHRINKABLE_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** Returns a link from a note category to the account's top-level media folder. */
fun mediaImagePath(category: String, fileName: String): String =
    noteMediaPath(category, fileName, "image/jpeg")

/** Images belong in media; audio and other attachments belong in attachments. */
fun noteMediaFolder(mimeType: String): String =
    if (mimeType.startsWith("image/", ignoreCase = true)) "media" else "attachments"

/** Returns a category-relative link to the same top-level folder used for uploads. */
fun noteMediaPath(category: String, fileName: String, mimeType: String): String {
    require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName)
    val parentTraversal = "../".repeat(category.split('/').count(String::isNotBlank))
    return "$parentTraversal${noteMediaFolder(mimeType)}/$fileName"
}

/** Shared names are untrusted. Keep the extension, but use only URL- and Markdown-safe ASCII. */
fun sharedAttachmentFileName(displayName: String, id: UUID): String {
    val extension = displayName.substringAfterLast('.', "")
        .takeIf { it.matches(Regex("[a-zA-Z0-9]{1,16}")) && !displayName.startsWith('.') }
    val base = (if (extension == null) displayName else displayName.substringBeforeLast('.'))
        .replace(Regex("[^a-zA-Z0-9_-]+"), "-").trim('-').take(48).ifBlank { "attachment" }
    return "$base-$id${extension?.let { ".$it" }.orEmpty()}"
}

fun appendSharedAttachment(
    content: String,
    label: String,
    category: String,
    fileName: String,
    mimeType: String
): String {
    val escapedLabel = label.map {
        if (it in "\\`*_{}[]<>#+-.!|&~") "\\$it" else it.toString()
    }.joinToString("")
    val image = mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    val link = "${if (image) "!" else ""}[$escapedLabel](${noteMediaPath(
        category,
        fileName,
        mimeType
    )})"
    if (content.isEmpty()) return "$link\n"
    val separator = when {
        content.endsWith("\n\n") || content.endsWith("\r\n\r\n") -> ""
        content.endsWith('\n') -> "\n"
        else -> "\n\n"
    }
    return "$content$separator$link\n"
}
