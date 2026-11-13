package org.qownnotes.mobile

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.qownnotes.mobile.core.ImageOrigin
import org.qownnotes.mobile.core.ImageShrinkSettings
import org.qownnotes.mobile.core.PhotoResolution
import org.qownnotes.mobile.core.PhotoSize
import org.qownnotes.mobile.core.TYPICAL_CAMERA_PHOTO_BYTES
import org.qownnotes.mobile.core.savedPercent
import org.qownnotes.mobile.core.typicalShrunkPhotoBytes

/**
 * How images are shrunk, a preview of what that saves on a typical photo, and which ways of
 * adding images use it. The chosen size is kept while reducing is off, so turning it back on
 * restores it.
 */
@Composable
internal fun ImageShrinkSettingsSection(
    shrink: ImageShrinkSettings,
    resolution: PhotoResolution,
    onShrinkChange: (ImageShrinkSettings) -> Unit,
    onResolutionChange: (PhotoResolution) -> Unit
) {
    Column {
        SettingsToggle(
            label = stringResource(R.string.settings_save_as_webp),
            description = stringResource(R.string.settings_save_as_webp_description),
            checked = shrink.saveAsWebp,
            onCheckedChange = { onShrinkChange(shrink.copy(saveAsWebp = it)) },
            testTag = "toggle-save-as-webp"
        )
        SettingsToggle(
            label = stringResource(R.string.settings_reduce_photo_resolution),
            description = stringResource(R.string.settings_reduce_photo_resolution_description),
            checked = resolution.reduce,
            onCheckedChange = { onResolutionChange(resolution.copy(reduce = it)) },
            testTag = "toggle-reduce-photo-resolution"
        )
        AnimatedVisibility(
            visible = resolution.reduce,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            PhotoSizeChoice(resolution, onResolutionChange)
        }
        ShrinkEstimateCard(
            shrink = shrink,
            resolution = resolution,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        Text(
            stringResource(R.string.settings_shrink_images_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
        )
        IMAGE_ORIGINS.forEach { origin ->
            SettingsToggle(
                label = stringResource(origin.label),
                description = stringResource(origin.description),
                icon = origin.icon,
                checked = shrink.appliesTo(origin),
                onCheckedChange = { onShrinkChange(shrink.with(origin, it)) },
                testTag = "toggle-shrink-${origin.name.lowercase()}"
            )
        }
    }
}

@Composable
private fun PhotoSizeChoice(resolution: PhotoResolution, onChange: (PhotoResolution) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("photo-size")) {
            PhotoSize.entries.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = size == resolution.size,
                    onClick = { onChange(resolution.copy(size = size)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = PhotoSize.entries.size
                    ),
                    label = { Text(stringResource(size.label), maxLines = 1) },
                    modifier = Modifier.testTag("photo-size-${size.name.lowercase()}")
                )
            }
        }
        Text(
            stringResource(
                if (resolution.size == PhotoSize.DEFAULT) {
                    R.string.settings_photo_size_summary_default
                } else {
                    R.string.settings_photo_size_summary
                },
                NumberFormat.getIntegerInstance().format(resolution.size.maxDimension)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("photo-size-summary")
        )
    }
}

/** What the current settings make of a typical camera photo, with a bar comparing both sizes. */
@Composable
private fun ShrinkEstimateCard(
    shrink: ImageShrinkSettings,
    resolution: PhotoResolution,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val anyOrigin = ImageOrigin.entries.any(shrink::appliesTo)
    val shrunkBytes = if (anyOrigin) {
        typicalShrunkPhotoBytes(resolution, shrink.saveAsWebp)
    } else {
        TYPICAL_CAMERA_PHOTO_BYTES
    }
    val saved = savedPercent(TYPICAL_CAMERA_PHOTO_BYTES, shrunkBytes)
    val fraction by animateFloatAsState(
        shrunkBytes.toFloat() / TYPICAL_CAMERA_PHOTO_BYTES,
        label = "shrunk-share"
    )
    val original = Formatter.formatShortFileSize(context, TYPICAL_CAMERA_PHOTO_BYTES)
    val shrunk = Formatter.formatShortFileSize(context, shrunkBytes)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth().testTag("image-shrink-estimate")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            ) {
                Icon(
                    if (anyOrigin) Icons.Filled.PhotoSizeSelectSmall else Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_image_estimate_title),
                    style = MaterialTheme.typography.labelMedium
                )
                AnimatedContent(
                    targetState = Triple(anyOrigin, saved, shrunk),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "shrink-estimate"
                ) { (applies, percent, shrunkSize) ->
                    Text(
                        when {
                            !applies -> stringResource(R.string.settings_image_estimate_off)
                            percent == 0 -> stringResource(
                                R.string.settings_image_estimate_unchanged,
                                original
                            )
                            else -> stringResource(
                                R.string.settings_image_estimate,
                                original,
                                shrunkSize
                            )
                        },
                        style = if (applies && percent > 0) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        modifier = Modifier.testTag("image-shrink-estimate-text")
                    )
                }
                AnimatedVisibility(visible = anyOrigin && saved > 0) {
                    SizeComparisonBar(fraction, Modifier.padding(top = 4.dp))
                }
            }
            AnimatedVisibility(
                visible = anyOrigin && saved > 0,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                SavingsBadge(saved, Modifier.testTag("image-shrink-saved"))
            }
        }
    }
}

/** A bar for the original size with the shrunk size filled in. */
@Composable
internal fun SizeComparisonBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** A pill with the share of space saved, such as "−79 %". */
@Composable
internal fun SavingsBadge(percent: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(
            stringResource(
                R.string.settings_image_saved_percent,
                NumberFormat.getPercentInstance().format(percent / 100.0)
            ),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private val IMAGE_ORIGINS = listOf(ImageOrigin.CAMERA, ImageOrigin.CHOSEN, ImageOrigin.ANNOTATED)

@get:StringRes
private val ImageOrigin.label: Int
    get() = when (this) {
        ImageOrigin.CAMERA -> R.string.settings_shrink_camera_photos
        ImageOrigin.CHOSEN -> R.string.settings_shrink_chosen_images
        ImageOrigin.ANNOTATED -> R.string.settings_shrink_annotated_images
    }

@get:StringRes
private val ImageOrigin.description: Int
    get() = when (this) {
        ImageOrigin.CAMERA -> R.string.settings_shrink_camera_photos_description
        ImageOrigin.CHOSEN -> R.string.settings_shrink_chosen_images_description
        ImageOrigin.ANNOTATED -> R.string.settings_shrink_annotated_images_description
    }

private val ImageOrigin.icon: ImageVector
    get() = when (this) {
        ImageOrigin.CAMERA -> Icons.Filled.PhotoCamera
        ImageOrigin.CHOSEN -> Icons.Filled.PhotoLibrary
        ImageOrigin.ANNOTATED -> Icons.Filled.Draw
    }

@get:StringRes
internal val PhotoSize.label: Int
    get() = when (this) {
        PhotoSize.SMALL -> R.string.photo_size_small
        PhotoSize.MEDIUM -> R.string.photo_size_medium
        PhotoSize.LARGE -> R.string.photo_size_large
    }
