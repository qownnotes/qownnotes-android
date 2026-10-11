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
