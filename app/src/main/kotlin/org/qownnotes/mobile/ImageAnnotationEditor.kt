package org.qownnotes.mobile

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.markdown.NoteImage

/**
 * Draws on a note image: pen, highlighter, arrows, rectangles, and text, with an eraser and
 * undo/redo. One finger draws with the selected tool and two fingers zoom and pan. Marks are kept
 * in the image's own pixels and drawn by the same code on screen and into the saved copy, so the
 * saved image looks exactly like the editor.
 *
 * [onSave] adds the annotated copy after the original (`replace = false`) or replaces the original
 * with it; null when the image cannot be changed in its note. Copying is always offered.
 */
@Composable
internal fun ImageAnnotationEditor(
    image: NoteImage,
    onSave: (suspend (annotated: Bitmap, replace: Boolean) -> Unit)?,
    onSaved: () -> Unit,
    onClose: () -> Unit
) {
    val bitmap = remember(image) { image.bitmap.asImageBitmap() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val history = remember { AnnotationHistory() }
    var tool by remember { mutableStateOf(AnnotationTool.PEN) }
    val toolColors = remember { mutableStateMapOf<AnnotationTool, Int>() }
    val toolSizes = remember { mutableStateMapOf<AnnotationTool, Int>() }
    val color = toolColors[tool] ?: if (tool == AnnotationTool.HIGHLIGHTER) YELLOW else RED
    val sizeIndex = toolSizes[tool] ?: 1
    var draft by remember { mutableStateOf<ImageAnnotation?>(null) }
    var working by remember { mutableStateOf<List<ImageAnnotation>?>(null) }
    var eraserAt by remember { mutableStateOf<Offset?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var textEdit by remember { mutableStateOf<TextEdit?>(null) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    var choosingSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val annotations = working ?: history.annotations

    fun requestClose() {
        if (history.annotations.isEmpty()) onClose() else confirmingDiscard = true
    }
    BackHandler(enabled = !saving) { requestClose() }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).testTag("image-annotation-editor")
    ) {
        val density = LocalDensity.current
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val fitted = fittedImageSize(bitmap.width, bitmap.height, viewport)
        val fitScale = if (bitmap.width > 0) fitted.width / bitmap.width else 1f

        // These read the zoom when called, so a gesture in progress follows it as it changes.
        fun totalScale() = fitScale * scale
        fun imageTopLeft() = center + offset -
            Offset(bitmap.width * totalScale() / 2f, bitmap.height * totalScale() / 2f)
        fun toImage(point: Offset) = (point - imageTopLeft()) / totalScale()
        fun screenSize(dp: Float) = with(density) { dp.dp.toPx() } / totalScale()
        fun bounded(candidate: Offset, atScale: Float): Offset {
            val maxX = ((fitted.width * atScale - viewport.width) / 2f).coerceAtLeast(0f)
            val maxY = ((fitted.height * atScale - viewport.height) / 2f).coerceAtLeast(0f)
            return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
        }

        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = image.description }
                .pointerInput(viewport, fitted, tool, color, sizeIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        val start = toImage(down.position)
                        val width = screenSize(TOOL_SIZES.getValue(tool)[sizeIndex])
                        var transforming = false
                        var drawing = false
                        var moved = false
                        val draggedLabel = if (tool == AnnotationTool.TEXT) {
                            history.annotations.indexOfLast {
                                it is ImageAnnotation.Label && it.isHit(start, screenSize(8f))
                            }.takeIf { it >= 0 }
                        } else {
                            null
                        }

                        fun erase(at: Offset) {
                            val current = working ?: history.annotations
                            val radius = screenSize(ERASER_RADIUS)
                            val kept = current.filterNot { it.isHit(at, radius) }
                            if (kept.size != current.size) working = kept
                        }

                        when (tool) {
                            AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER ->
                                draft =
                                    ImageAnnotation.Stroke(
                                        listOf(start),
                                        color,
                                        width,
                                        highlighter = tool == AnnotationTool.HIGHLIGHTER
                                    )
                            AnnotationTool.ERASER -> {
                                eraserAt = down.position
                                erase(start)
                            }
                            else -> Unit
                        }

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            // A second finger before the drawing really started means zooming.
                            if (!drawing && pressed >= 2 && !transforming) {
                                transforming = true
                                draft = null
                                working = null
                                eraserAt = null
                            }
                            if (transforming) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val newScale = (scale * zoom).coerceIn(1f, MAX_ANNOTATION_ZOOM)
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val moving = if (centroid.isSpecified) {
                                    val focus = centroid - center
                                    focus - (focus - offset) * (newScale / scale) + pan
                                } else {
                                    offset + pan
                                }
                                scale = newScale
                                offset = bounded(moving, scale)
                            } else {
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null && change.pressed) {
                                    if (!moved &&
                                        (change.position - down.position).getDistance() > slop
                                    ) {
                                        moved = true
                                    }
                                    val point = toImage(change.position)
                                    when (tool) {
                                        AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
                                            (draft as? ImageAnnotation.Stroke)?.let { stroke ->
                                                draft = stroke.copy(
                                                    points = stroke.points.plusPoint(
                                                        point,
                                                        screenSize(1.5f)
                                                    )
                                                )
                                            }
                                            if (moved) drawing = true
                                        }
                                        AnnotationTool.ARROW -> if (moved) {
                                            drawing = true
                                            draft =
                                                ImageAnnotation.Arrow(start, point, color, width)
                                        }
                                        AnnotationTool.RECTANGLE -> if (moved) {
                                            drawing = true
                                            draft = ImageAnnotation.Rectangle(
                                                start,
                                                point,
                                                color,
                                                width
                                            )
                                        }
                                        AnnotationTool.ERASER -> {
                                            eraserAt = change.position
                                            erase(point)
                                            if (moved) drawing = true
                                        }
                                        AnnotationTool.TEXT -> if (moved && draggedLabel != null) {
                                            drawing = true
                                            val label = history.annotations[draggedLabel]
                                                as ImageAnnotation.Label
                                            working = history.annotations.toMutableList().apply {
                                                set(
                                                    draggedLabel,
                                                    label.copy(
                                                        position = label.position + point - start
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })

                        if (!transforming) {
                            when (tool) {
                                AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER,
                                AnnotationTool.ARROW, AnnotationTool.RECTANGLE ->
                                    draft?.let { history.commit(history.annotations + it) }
                                AnnotationTool.ERASER, AnnotationTool.TEXT ->
                                    working?.let(history::commit)
                            }
                            if (tool == AnnotationTool.TEXT && !moved) {
                                textEdit = if (draggedLabel != null) {
                                    val label = history.annotations[draggedLabel]
                                        as ImageAnnotation.Label
                                    TextEdit(draggedLabel, label.position, label.text)
                                } else {
                                    TextEdit(null, start, "")
                                }
                            }
                        }
                        draft = null
                        working = null
                        eraserAt = null
                    }
                }
                .testTag("image-annotation-canvas")
        ) {
            val topLeft = imageTopLeft()
            val drawScale = totalScale()
            withTransform({
                translate(topLeft.x, topLeft.y)
                scale(drawScale, drawScale, pivot = Offset.Zero)
            }) {
                drawImage(bitmap)
                drawIntoCanvas { canvas ->
                    annotations.forEach { canvas.nativeCanvas.drawAnnotation(it) }
                    draft?.let { canvas.nativeCanvas.drawAnnotation(it) }
                }
            }
            eraserAt?.let {
                val radius = ERASER_RADIUS.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.35f), radius, it, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, radius, it, style = Stroke(1.5.dp.toPx()))
            }
        }

        AnnotationTopBar(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            canFinish = history.annotations.isNotEmpty(),
            onClose = ::requestClose,
            onUndo = history::undo,
            onRedo = history::redo,
            onDone = { choosingSave = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        AnnotationToolbar(
            tool = tool,
            color = color,
            sizeIndex = sizeIndex,
            onTool = { tool = it },
            onColor = { toolColors[tool] = it },
            onSize = { toolSizes[tool] = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        textEdit?.let { edit ->
            AnnotationTextDialog(
                edit = edit,
                onDismiss = { textEdit = null },
                onDone = { text ->
                    textEdit = null
                    val labels = history.annotations.toMutableList()
                    if (edit.index != null) {
                        val label = labels[edit.index] as ImageAnnotation.Label
                        if (text.isBlank()) {
                            labels.removeAt(edit.index)
                        } else {
                            labels[edit.index] = label.copy(text = text)
                        }
                    } else if (text.isNotBlank()) {
                        val size = screenSize(TOOL_SIZES.getValue(AnnotationTool.TEXT)[sizeIndex])
                        val label = ImageAnnotation.Label(Offset.Zero, text, color, size)
                        val bounds = labelBounds(label)
                        // The tap marks the middle of the new text.
                        labels += label.copy(
                            position = edit.position -
                                Offset(bounds.width() / 2f, bounds.height() / 2f)
                        )
                    }
                    history.commit(labels)
                }
            )
        }
        if (confirmingDiscard) {
            AlertDialog(
                onDismissRequest = { confirmingDiscard = false },
                title = { Text(stringResource(R.string.image_annotation_discard_title)) },
                text = { Text(stringResource(R.string.image_annotation_discard_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmingDiscard = false
                            onClose()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("image-annotation-discard")
                    ) { Text(stringResource(R.string.image_annotation_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDiscard = false }) {
                        Text(stringResource(R.string.image_annotation_keep))
                    }
                }
            )
        }
        if (choosingSave) {
            AnnotationSaveDialog(
                canSaveToNote = onSave != null,
                saving = saving,
                onDismiss = { if (!saving) choosingSave = false },
                onCopy = {
                    choosingSave = false
                    scope.launch(Dispatchers.Main.immediate) {
                        val annotated = withContext(Dispatchers.Default) {
                            renderAnnotatedImage(image.bitmap, history.annotations)
                        }
                        copyImageWithFeedback(context, NoteImage(annotated, image.description))
                    }
                },
                onSave = { replace ->
                    val save = onSave ?: return@AnnotationSaveDialog
                    saving = true
                    // Toasts need the main thread whichever thread resumes after the save.
                    scope.launch(Dispatchers.Main.immediate) {
                        try {
                            val annotated = withContext(Dispatchers.Default) {
                                renderAnnotatedImage(image.bitmap, history.annotations)
                            }
                            save(annotated, replace)
                            Toast.makeText(
                                context,
                                if (replace) {
                                    R.string.image_annotation_replaced
                                } else {
                                    R.string.image_annotation_added
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                            choosingSave = false
                            onSaved()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                error.message
                                    ?: resources.getString(R.string.image_annotation_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            saving = false
                        }
                    }
                }
            )
        }
    }
}

private data class TextEdit(val index: Int?, val position: Offset, val text: String)

@Composable
private fun AnnotationTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    canFinish: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.35f)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
            )
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 20.dp)
    ) {
        IconButton(
            onClick = onClose,
            colors = iconColors,
            modifier = Modifier.testTag("image-annotation-close")
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.image_annotation_close)
            )
        }
        Text(
            text = stringResource(R.string.image_annotation_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            colors = iconColors,
            modifier = Modifier.testTag("image-annotation-undo")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.image_annotation_undo)
            )
        }
        IconButton(
            onClick = onRedo,
            enabled = canRedo,
            colors = iconColors,
            modifier = Modifier.testTag("image-annotation-redo")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.image_annotation_redo)
            )
        }
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onDone,
            enabled = canFinish,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            ),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.testTag("image-annotation-done")
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.image_annotation_done))
        }
    }
}

@Composable
private fun AnnotationToolbar(
    tool: AnnotationTool,
    color: Int,
    sizeIndex: Int,
    onTool: (AnnotationTool) -> Unit,
    onColor: (Int) -> Unit,
    onSize: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
            )
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1C1C1E).copy(alpha = 0.94f),
            contentColor = Color.White,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                AnimatedVisibility(
                    visible = tool != AnnotationTool.ERASER,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        PALETTE.forEach { (swatch, name) ->
                            ColorSwatch(
                                color = swatch,
                                name = stringResource(name),
                                selected = swatch == color,
                                onClick = { onColor(swatch) }
                            )
                        }
                        VerticalDivider(
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.height(24.dp)
                        )
                        SizeButton(
                            sizeIndex = sizeIndex,
                            color = color,
                            onClick = { onSize((sizeIndex + 1) % SIZE_NAMES.size) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnnotationTool.entries.forEach { entry ->
                        val (icon, label) = TOOL_ICONS.getValue(entry)
                        IconToggleButton(
                            checked = tool == entry,
                            onCheckedChange = { onTool(entry) },
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                contentColor = Color.White.copy(alpha = 0.8f),
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("image-annotation-tool-${entry.tag}")
                        ) {
                            Icon(
                                icon,
                                contentDescription = stringResource(label)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(34.dp)
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = name }
    ) {
        Box(
            Modifier.size(if (selected) 28.dp else 22.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.35f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun SizeButton(sizeIndex: Int, color: Int, onClick: () -> Unit) {
    val description = stringResource(
        R.string.image_annotation_size,
        stringResource(SIZE_NAMES[sizeIndex])
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description }
            .testTag("image-annotation-size")
    ) {
        Box(
            Modifier.size(SIZE_DOTS[sizeIndex].dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
    }
}

@Composable
private fun AnnotationTextDialog(edit: TextEdit, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember(edit) { mutableStateOf(edit.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (edit.index == null) {
                        R.string.image_annotation_text_add
                    } else {
                        R.string.image_annotation_text_edit
                    }
                )
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.image_annotation_text_hint)) },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .testTag("image-annotation-text-field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(text.trimEnd()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("image-annotation-text-done")
            ) { Text(stringResource(R.string.image_annotation_done)) }
        },
        dismissButton = {
            Row {
                if (edit.index != null) {
                    TextButton(
                        onClick = { onDone("") },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.image_annotation_text_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@Composable
private fun AnnotationSaveDialog(
    canSaveToNote: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onSave: (replace: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_annotation_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SaveOption(
                    icon = Icons.Filled.AddPhotoAlternate,
                    title = stringResource(R.string.image_annotation_add),
                    summary = stringResource(
                        if (canSaveToNote) {
                            R.string.image_annotation_add_summary
                        } else {
                            R.string.image_annotation_unavailable
                        }
                    ),
                    enabled = canSaveToNote && !saving,
                    tag = "image-annotation-add",
                    onClick = { onSave(false) }
                )
                SaveOption(
                    icon = Icons.Filled.FindReplace,
                    title = stringResource(R.string.image_annotation_replace),
                    summary = stringResource(
                        if (canSaveToNote) {
                            R.string.image_annotation_replace_summary
                        } else {
                            R.string.image_annotation_unavailable
                        }
                    ),
                    enabled = canSaveToNote && !saving,
                    tag = "image-annotation-replace",
                    onClick = { onSave(true) }
                )
                SaveOption(
                    icon = Icons.Filled.ContentCopy,
                    title = stringResource(R.string.image_viewer_copy),
                    summary = stringResource(R.string.image_annotation_copy_summary),
                    enabled = !saving,
                    tag = "image-annotation-copy",
                    onClick = onCopy
                )
                AnimatedVisibility(visible = saving) {
                    Column(Modifier.padding(top = 8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.image_annotation_saving),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun SaveOption(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val AnnotationTool.tag get() = name.lowercase()

/** A tilted eraser; the icon sets have none. */
private val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2.5f, 14.8f)
            lineTo(7.0f, 10.3f)
            lineTo(12.7f, 16.0f)
            lineTo(8.2f, 20.5f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19.5f, 9.2f)
            lineTo(13.8f, 3.5f)
            lineTo(2.5f, 14.8f)
            lineTo(8.2f, 20.5f)
            close()
            moveTo(12.5f, 21f)
            lineTo(21f, 21f)
        }
    }.build()
}

/** A tilted marker with a chisel tip; the icon sets' highlight icon is a flashlight. */
private val HighlighterIcon: ImageVector by lazy {
    ImageVector.Builder("Highlighter", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.7f, 11.7f)
            lineTo(11.3f, 15.3f)
            lineTo(8.3f, 18.3f)
            lineTo(4.7f, 14.7f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20.3f, 6.3f)
            lineTo(16.7f, 2.7f)
            lineTo(7.7f, 11.7f)
            lineTo(11.3f, 15.3f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(3.5f, 21f)
            lineTo(20.5f, 21f)
        }
    }.build()
}

private val TOOL_ICONS: Map<AnnotationTool, Pair<ImageVector, Int>> by lazy {
    mapOf(
        AnnotationTool.PEN to (Icons.Filled.Edit to R.string.image_annotation_tool_pen),
        AnnotationTool.HIGHLIGHTER to
            (HighlighterIcon to R.string.image_annotation_tool_highlighter),
        AnnotationTool.ARROW to (Icons.Filled.NorthEast to R.string.image_annotation_tool_arrow),
        AnnotationTool.RECTANGLE to
            (Icons.Outlined.CropSquare to R.string.image_annotation_tool_rectangle),
        AnnotationTool.TEXT to (Icons.Filled.TextFields to R.string.image_annotation_tool_text),
        AnnotationTool.ERASER to (EraserIcon to R.string.image_annotation_tool_eraser)
    )
}

/** Small, medium, and large sizes in dp on screen at the current zoom; text sizes are in dp too. */
private val TOOL_SIZES = mapOf(
    AnnotationTool.PEN to listOf(2.5f, 5f, 10f),
    AnnotationTool.HIGHLIGHTER to listOf(12f, 20f, 32f),
    AnnotationTool.ARROW to listOf(3f, 5f, 8f),
    AnnotationTool.RECTANGLE to listOf(3f, 5f, 8f),
    AnnotationTool.TEXT to listOf(18f, 28f, 44f),
    AnnotationTool.ERASER to listOf(0f, 0f, 0f)
)
private val SIZE_DOTS = listOf(8, 13, 20)
private val SIZE_NAMES = listOf(
    R.string.image_annotation_size_small,
    R.string.image_annotation_size_medium,
    R.string.image_annotation_size_large
)

private const val RED = 0xFFE53935.toInt()
private const val YELLOW = 0xFFFDD835.toInt()
private val PALETTE = listOf(
    RED to R.string.image_annotation_color_red,
    0xFFFB8C00.toInt() to R.string.image_annotation_color_orange,
    YELLOW to R.string.image_annotation_color_yellow,
    0xFF43A047.toInt() to R.string.image_annotation_color_green,
    0xFF1E88E5.toInt() to R.string.image_annotation_color_blue,
    0xFF8E24AA.toInt() to R.string.image_annotation_color_purple,
    0xFFFFFFFF.toInt() to R.string.image_annotation_color_white,
    0xFF000000.toInt() to R.string.image_annotation_color_black
)
private const val ERASER_RADIUS = 14f
private const val MAX_ANNOTATION_ZOOM = 6f
