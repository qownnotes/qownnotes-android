package org.qownnotes.mobile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.qownnotes.mobile.core.apertureLabel
import org.qownnotes.mobile.core.aspectRatioLabel
import org.qownnotes.mobile.core.coordinatesLabel
import org.qownnotes.mobile.core.exposureTimeLabel
import org.qownnotes.mobile.core.focalLengthLabel
import org.qownnotes.mobile.core.imageLinkFileName
import org.qownnotes.mobile.core.megapixelsLabel
import org.qownnotes.mobile.markdown.NoteImage

/** Where a viewed image comes from in its note, and how to read its original file's details. */
internal class NoteImageInfoSource(
    /** The image's link as written in the note. */
    val link: String?,
    /** Details of the original file, or `null` when it cannot be read. Absent for web images. */
    val loadDetails: (suspend () -> ImageFileDetails?)? = null
)

/**
 * A panel at the bottom of the image viewer describing the shown image: its file, format, size,
 * resolution and aspect ratio, what the camera recorded, and how the note refers to it.
 */
@Composable
internal fun ImageInfoPanel(
    image: NoteImage,
    source: NoteImageInfoSource?,
    details: ImageFileDetails?,
    loading: Boolean,
    onShrink: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val loader = source?.loadDetails
    BackHandler { onClose() }

    ViewerPanel(
        icon = Icons.Outlined.Info,
        title = stringResource(R.string.image_info),
        onClose = onClose,
        testTag = "image-info",
        modifier = modifier
    ) {
        val isLoading = loading
        val fileDetails = details
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.animateContentSize()
        ) {
            FileHeader(image, source?.link, fileDetails, isLoading)
            SizeTiles(image, fileDetails)
            if (isLoading) {
                PanelLoading(stringResource(R.string.image_info_loading))
            } else if (fileDetails == null) {
                PanelMessage(
                    stringResource(
                        if (loader == null && source?.link != null) {
                            R.string.image_info_web_image
                        } else {
                            R.string.image_info_unavailable
                        }
                    ),
                    error = false,
                    tag = "image-info-unavailable"
                )
            }
            if (fileDetails?.hasPhotoDetails == true) PhotoSection(fileDetails)
            NoteSection(image.description, source?.link)
        }
        if (onShrink != null) {
            OutlinedButton(
                onClick = onShrink,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.fillMaxWidth().testTag("image-info-shrink")
            ) {
                Icon(
                    Icons.Filled.PhotoSizeSelectSmall,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(
                    stringResource(R.string.image_shrink),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                )
            }
        }
    }
}

@Composable
private fun FileHeader(
    image: NoteImage,
    link: String?,
    details: ImageFileDetails?,
    loading: Boolean
) {
    val context = LocalContext.current
    val format = imageFormatLabel(details, link)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("image-info-file")
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    format ?: "IMG",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                link?.let(::imageLinkFileName)
                    ?: image.description.ifBlank { stringResource(R.string.image_viewer_image) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis
            )
            Text(
                when {
                    details != null -> stringResource(
                        R.string.image_shrink_details,
                        integer(details.info.width),
                        integer(details.info.height),
                        Formatter.formatShortFileSize(context, details.info.bytes)
                    )
                    loading -> stringResource(R.string.image_info_loading)
                    else -> stringResource(
                        R.string.image_info_shown_at,
                        integer(image.bitmap.width),
                        integer(image.bitmap.height)
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SizeTiles(image: NoteImage, details: ImageFileDetails?) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val width = details?.info?.width ?: image.bitmap.width
    val height = details?.info?.height ?: image.bitmap.height
    if (width <= 0 || height <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoTile(
            label = stringResource(R.string.image_info_resolution),
            value = megapixelsLabel(width, height, locale),
            detail = "${integer(width)} × ${integer(height)}",
            tag = "image-info-resolution",
            modifier = Modifier.weight(1f)
        )
        InfoTile(
            label = stringResource(R.string.image_info_aspect_ratio),
            value = aspectRatioLabel(width, height, locale),
            detail = null,
            tag = "image-info-aspect-ratio",
            modifier = Modifier.weight(1f)
        )
        if (details != null) {
            InfoTile(
                label = stringResource(R.string.image_info_file_size),
                value = Formatter.formatShortFileSize(context, details.info.bytes),
                detail = imageFormatName(details.info.mimeType),
                tag = "image-info-file-size",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    detail: String?,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics(mergeDescendants = true) {}.testTag(tag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                detail ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PhotoSection(details: ImageFileDetails) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val exposure = listOfNotNull(
        details.fNumber?.let { apertureLabel(it, locale) },
        details.exposureSeconds?.let { exposureTimeLabel(it, locale) },
        details.iso?.let { stringResource(R.string.image_info_iso, integer(it)) },
        details.focalLength?.let { focalLengthLabel(it, locale) }
    ).joinToString(" · ").takeIf(String::isNotEmpty)
    val noMapApp = stringResource(R.string.image_info_no_map_app)
    InfoSection(stringResource(R.string.image_info_section_photo)) {
        val rows = buildList<@Composable () -> Unit> {
            details.takenAt?.let { takenAt ->
                add {
                    InfoRow(
                        Icons.Filled.Event,
                        stringResource(R.string.image_info_taken),
                        takenAt.format(
                            DateTimeFormatter.ofLocalizedDateTime(
                                FormatStyle.MEDIUM,
                                FormatStyle.SHORT
                            ).withLocale(locale)
                        ),
                        "image-info-taken"
                    )
                }
            }
            details.camera?.let {
                add {
                    InfoRow(
                        Icons.Filled.PhotoCamera,
                        stringResource(R.string.image_info_camera),
                        it,
                        "image-info-camera"
                    )
                }
            }
            details.lens?.let {
                add {
                    InfoRow(
                        Icons.Filled.Camera,
                        stringResource(R.string.image_info_lens),
                        it,
                        "image-info-lens"
                    )
                }
            }
            exposure?.let {
                add {
                    InfoRow(
                        Icons.Filled.Exposure,
                        stringResource(R.string.image_info_exposure),
                        it,
                        "image-info-exposure"
                    )
                }
            }
            val latitude = details.latitude
            val longitude = details.longitude
            if (latitude != null && longitude != null) {
                add {
                    InfoRow(
                        Icons.Filled.Place,
                        stringResource(R.string.image_info_location),
                        coordinatesLabel(latitude, longitude, locale),
                        "image-info-location"
                    ) {
                        TextButton(
                            onClick = {
                                val position = String.format(
                                    Locale.ROOT,
                                    "%.6f,%.6f",
                                    latitude,
                                    longitude
                                )
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("geo:$position?q=$position")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, noMapApp, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("image-info-show-on-map")
                        ) { Text(stringResource(R.string.image_info_show_on_map)) }
                    }
                }
            }
            details.software?.let {
                add {
                    InfoRow(
                        Icons.Filled.Brush,
                        stringResource(R.string.image_info_software),
                        it,
                        "image-info-software"
                    )
                }
            }
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) InfoDivider()
            row()
        }
    }
}

@Composable
private fun NoteSection(description: String, link: String?) {
    val context = LocalContext.current
    val copied = stringResource(R.string.image_info_link_copied)
    InfoSection(stringResource(R.string.image_info_section_note)) {
        InfoRow(
            Icons.AutoMirrored.Filled.ShortText,
            stringResource(R.string.image_info_description),
            description.ifBlank { stringResource(R.string.image_info_no_description) },
            "image-info-description",
            muted = description.isBlank()
        )
        if (link != null) {
            InfoDivider()
            InfoRow(
                Icons.Filled.Link,
                stringResource(R.string.image_info_link),
                link,
                "image-info-link",
                monospace = true
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(link, link))
                        // Android 13 and newer confirm clipboard changes themselves.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("image-info-copy-link")
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.image_info_copy_link),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    muted: Boolean = false,
    monospace: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp)
            .padding(vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag(tag)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                    color = if (muted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 52.dp)
    )
}

/**
 * The file type, resolution, and file size of the viewed image, shown at the bottom of the viewer
 * with its other controls. Tapping it opens the image details.
 */
@Composable
internal fun ImageMetadataBar(
    image: NoteImage,
    link: String?,
    details: ImageFileDetails?,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val format = imageFormatLabel(details, link)
    val width = details?.info?.width ?: image.bitmap.width
    val height = details?.info?.height ?: image.bitmap.height
    val summary = listOfNotNull(
        stringResource(R.string.image_info_pixels, integer(width), integer(height)),
        details?.info?.bytes?.let { Formatter.formatShortFileSize(context, it) }
    ).joinToString(" · ")
    val label = stringResource(R.string.image_info)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF2E2E2E).copy(alpha = 0.88f),
        contentColor = Color.White,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: ${listOfNotNull(format, summary).joinToString(", ")}"
        }.testTag("image-viewer-metadata")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                .animateContentSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    format ?: "IMG",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(summary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            AnimatedVisibility(visible = loading) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/** The file type from the file's content, or else from the link's extension. */
private fun imageFormatLabel(details: ImageFileDetails?, link: String?): String? =
    details?.info?.mimeType?.let(::imageFormatName)
        ?: link?.substringBefore('?')?.substringBefore('#')?.substringAfterLast('/')
            ?.substringAfterLast('.', "")?.uppercase()
            ?.let {
                if (it == "JPG") {
                    "JPEG"
                } else if (it == "WEBP") {
                    "WebP"
                } else {
                    it
                }
            }
            ?.takeIf { it.length in 2..4 }

private fun integer(value: Int): String = NumberFormat.getIntegerInstance().format(value)
