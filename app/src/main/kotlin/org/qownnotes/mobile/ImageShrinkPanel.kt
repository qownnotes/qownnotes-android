package org.qownnotes.mobile

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.ImageShrinking
import org.qownnotes.mobile.core.PhotoSize
import org.qownnotes.mobile.core.savedPercent

/** What the image viewer needs to shrink a note image and save the smaller copy to the note. */
internal class NoteImageShrinkActions(
    /** The size and format the image settings suggest. */
    val defaults: ImageShrinking,
    /** A private copy of the image's original file. */
    val loadOriginal: suspend () -> File,
    /** A shrunk copy in the cache, or `null` when it would not be smaller. */
    val shrink: suspend (original: File, shrinking: ImageShrinking) -> ShrunkNoteImage?,
    /** Attaches the copy and points the image at it, or adds it after the image. */
    val save: suspend (shrunk: ShrunkNoteImage, replace: Boolean) -> Unit
)

/**
 * A panel at the bottom of the image viewer that shrinks the shown image: it loads the original
 * file, lets the reader pick a resolution and WebP, and compares the current size with the size
 * of an actual shrunk copy before that copy replaces the image or is added after it.
 */
@Composable
internal fun ImageShrinkPanel(
    actions: NoteImageShrinkActions,
    onSaved: () -> Unit,
    onClose: () -> Unit,
    onSavingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Pair<File, ImageFileInfo>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var maxDimension by remember { mutableStateOf(actions.defaults.maxDimension) }
    var webp by remember { mutableStateOf(actions.defaults.webp) }
    var preview by remember { mutableStateOf<ShrunkNoteImage?>(null) }
    var computing by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val latestPreview by rememberUpdatedState(preview)

    BackHandler(enabled = !saving) { onClose() }
    LaunchedEffect(saving) { onSavingChange(saving) }
    DisposableEffect(Unit) { onDispose { latestPreview?.file?.delete() } }

    LaunchedEffect(Unit) {
        try {
            val file = actions.loadOriginal()
            val info = withContext(Dispatchers.IO) { ImageShrinker.inspect(file) }
                ?: error(resources.getString(R.string.image_shrink_original_unavailable))
            // Sizes the image already fits in would not change it, so they start out unselected.
            if (maxDimension?.let { it >= maxOf(info.width, info.height) } == true) {
                maxDimension = null
            }
            original = file to info
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadError = error.message ?: resources.getString(R.string.image_shrink_failed)
        }
    }
    LaunchedEffect(original, maxDimension, webp) {
        val (file, _) = original ?: return@LaunchedEffect
        computing = true
        val previous = preview
        preview = try {
            actions.shrink(file, ImageShrinking(maxDimension, webp))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        withContext(Dispatchers.IO) { previous?.file?.delete() }
        computing = false
    }

    fun save(replace: Boolean) {
        val shrunk = preview ?: return
        saving = true
        scope.launch(Dispatchers.Main.immediate) {
            try {
                actions.save(shrunk, replace)
                Toast.makeText(
                    context,
                    if (replace) R.string.image_shrink_replaced else R.string.image_shrink_added,
                    Toast.LENGTH_SHORT
                ).show()
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: resources.getString(R.string.image_shrink_failed),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                saving = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("image-shrink-panel")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(end = 12.dp)
                    .width(32.dp).height(4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(
                        Icons.Filled.PhotoSizeSelectSmall,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    stringResource(R.string.image_shrink),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                IconButton(
                    onClick = onClose,
                    enabled = !saving,
                    modifier = Modifier.testTag("image-shrink-close")
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.image_shrink_close)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                val loaded = original
                when {
                    loadError != null -> PanelMessage(
                        loadError.orEmpty(),
                        error = true,
                        tag = "image-shrink-error"
                    )
                    loaded == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.image_shrink_loading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> ShrinkOptions(
                        info = loaded.second,
                        preview = preview,
                        computing = computing,
                        saving = saving,
                        maxDimension = maxDimension,
                        onMaxDimension = { maxDimension = it },
                        webp = webp,
                        onWebp = { webp = it },
                        onSave = ::save
                    )
                }
            }
        }
    }
}

@Composable
private fun ShrinkOptions(
    info: ImageFileInfo,
    preview: ShrunkNoteImage?,
    computing: Boolean,
    saving: Boolean,
    maxDimension: Int?,
    onMaxDimension: (Int?) -> Unit,
    webp: Boolean,
    onWebp: (Boolean) -> Unit,
    onSave: (replace: Boolean) -> Unit
) {
    val context = LocalContext.current
    val longest = maxOf(info.width, info.height)
    val shrunk = preview?.info
    Row(verticalAlignment = Alignment.CenterVertically) {
        SizeSummary(
            label = stringResource(R.string.image_shrink_now),
            size = Formatter.formatShortFileSize(context, info.bytes),
            details = info.details(),
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
        )
        AnimatedContent(
            targetState = if (computing) null else shrunk,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.CenterEnd,
            label = "shrunk-size",
            modifier = Modifier.weight(1f)
        ) { result ->
            SizeSummary(
                label = stringResource(R.string.image_shrink_after),
                size = when {
                    computing -> "…"
                    result == null -> "–"
                    else -> Formatter.formatShortFileSize(context, result.bytes)
                },
                details = when {
                    computing -> stringResource(R.string.image_shrink_calculating)
                    result == null -> " "
                    else -> result.details()
                },
                highlighted = result != null,
                alignEnd = true,
                modifier = Modifier.testTag("image-shrink-result")
            )
        }
    }
    val saved = shrunk?.let { savedPercent(info.bytes, it.bytes) } ?: 0
    val fraction by animateFloatAsState(
        if (shrunk == null) 1f else shrunk.bytes.toFloat() / info.bytes,
        label = "shrunk-fraction"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SizeComparisonBar(fraction, Modifier.weight(1f))
        AnimatedVisibility(
            visible = saved > 0 && !computing,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            SavingsBadge(saved, Modifier.testTag("image-shrink-saved"))
        }
    }
    AnimatedVisibility(
        visible = !computing && shrunk == null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        PanelMessage(
            stringResource(R.string.image_shrink_no_savings),
            error = false,
            tag = "image-shrink-no-savings"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.image_shrink_resolution),
            style = MaterialTheme.typography.labelLarge
        )
        val choices = listOf<Pair<Int?, String>>(
            null to stringResource(R.string.image_shrink_original)
        ) + PhotoSize.entries.reversed().map { it.maxDimension to stringResource(it.label) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (dimension, label) ->
                SegmentedButton(
                    selected = dimension == maxDimension,
                    onClick = { onMaxDimension(dimension) },
                    enabled = !saving && (dimension == null || dimension < longest),
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("image-shrink-size-${dimension ?: "original"}")
                )
            }
        }
        Text(
            maxDimension?.let {
                stringResource(
                    R.string.settings_photo_size_summary,
                    NumberFormat.getIntegerInstance().format(it)
                )
            } ?: info.details(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (info.mimeType == ImageShrinker.WEBP) {
        Text(
            stringResource(R.string.image_shrink_already_webp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        SettingsToggle(
            label = stringResource(R.string.image_shrink_webp),
            description = stringResource(R.string.image_shrink_webp_description),
            checked = webp,
            onCheckedChange = { if (!saving) onWebp(it) },
            testTag = "image-shrink-webp"
        )
    }
    Text(
        stringResource(R.string.image_shrink_keeps_original),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AnimatedVisibility(visible = saving) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    val canSave = shrunk != null && !computing && !saving
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onSave(false) },
            enabled = canSave,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.weight(1f).testTag("image-shrink-add")
        ) {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Text(
                stringResource(R.string.image_shrink_add),
                maxLines = 1,
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
            )
        }
        Button(
            onClick = { onSave(true) },
            enabled = canSave,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.weight(1f).testTag("image-shrink-replace")
        ) {
            Icon(
                Icons.Filled.FindReplace,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Text(
                stringResource(R.string.image_shrink_replace),
                maxLines = 1,
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
            )
        }
    }
}

@Composable
private fun SizeSummary(
    label: String,
    size: String,
    details: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    alignEnd: Boolean = false
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            size,
            style = MaterialTheme.typography.headlineSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 1
        )
    }
}

@Composable
private fun PanelMessage(text: String, error: Boolean, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(
                if (error) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag)
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun ImageFileInfo.details(): String = stringResource(
    R.string.image_shrink_details,
    NumberFormat.getIntegerInstance().format(width),
    NumberFormat.getIntegerInstance().format(height),
    formatName(mimeType)
)

private fun formatName(mimeType: String): String = when (mimeType) {
    ImageShrinker.JPEG -> "JPEG"
    ImageShrinker.PNG -> "PNG"
    ImageShrinker.WEBP -> "WebP"
    else -> mimeType.substringAfter('/').uppercase()
}
