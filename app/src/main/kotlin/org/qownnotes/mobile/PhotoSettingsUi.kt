package org.qownnotes.mobile

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.qownnotes.mobile.core.PhotoResolution
import org.qownnotes.mobile.core.PhotoSize

/**
 * Whether photos taken in the application are scaled down, and to which size. The chosen size is
 * kept while reducing is off, so turning it back on restores it.
 */
@Composable
internal fun PhotoResolutionSettings(
    resolution: PhotoResolution,
    onChange: (PhotoResolution) -> Unit
) {
    val context = LocalContext.current
    Column {
        SettingsToggle(
            label = stringResource(R.string.settings_reduce_photo_resolution),
            description = stringResource(R.string.settings_reduce_photo_resolution_description),
            checked = resolution.reduce,
            onCheckedChange = { onChange(resolution.copy(reduce = it)) },
            testTag = "toggle-reduce-photo-resolution"
        )
        AnimatedVisibility(
            visible = resolution.reduce,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().testTag("photo-size")
                ) {
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
                        NumberFormat.getIntegerInstance().format(resolution.size.maxDimension),
                        Formatter.formatShortFileSize(context, resolution.size.typicalBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("photo-size-summary")
                )
            }
        }
    }
}

@get:StringRes
private val PhotoSize.label: Int
    get() = when (this) {
        PhotoSize.SMALL -> R.string.photo_size_small
        PhotoSize.MEDIUM -> R.string.photo_size_medium
        PhotoSize.LARGE -> R.string.photo_size_large
    }

/** A rough size of a typical 4:3 photo reduced to this size and saved as a JPEG. */
private val PhotoSize.typicalBytes: Long
    get() = when (this) {
        PhotoSize.SMALL -> 700_000L
        PhotoSize.MEDIUM -> 1_200_000L
        PhotoSize.LARGE -> 2_700_000L
    }
