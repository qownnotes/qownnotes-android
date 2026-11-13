package org.qownnotes.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.markdown.NoteImage

/**
 * Shows a tapped note image full screen. Pinch or double-tap zooms around the fingers, dragging
 * pans within the image's edges, a tap shows or hides the controls, and swiping a fitted image
 * up or down closes the viewer.
 */
@Composable
internal fun NoteImageViewer(
    image: NoteImage,
    onSaveAnnotation: (suspend (annotated: Bitmap, replace: Boolean) -> Unit)? = null,
    shrinkActions: NoteImageShrinkActions? = null,
    infoSource: NoteImageInfoSource? = null,
    onDismiss: () -> Unit
) {
    val bitmap = remember(image) { image.bitmap.asImageBitmap() }
    val scope = rememberCoroutineScope()
    val appearance = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var copying by remember { mutableStateOf(false) }
    var annotating by remember { mutableStateOf(false) }
    var shrinking by remember { mutableStateOf(false) }
    var savingShrunk by remember { mutableStateOf(false) }
    var showingInfo by remember { mutableStateOf(false) }
    val panelOpen = shrinking || showingInfo
    // Read once for both the bar at the bottom and the details panel.
    val detailsLoader = infoSource?.loadDetails
    var detailsLoading by remember(infoSource) { mutableStateOf(detailsLoader != null) }
    var details by remember(infoSource) { mutableStateOf<ImageFileDetails?>(null) }
    LaunchedEffect(infoSource) {
        if (detailsLoader == null) return@LaunchedEffect
        details = try {
            detailsLoader()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        detailsLoading = false
    }
    val context = LocalContext.current
    val zoomAnimation = remember { arrayOfNulls<Job>(1) }

    fun close(slideTo: Float = dismissDrag) {
        if (closing) return
        closing = true
        scope.launch {
            launch {
                animate(dismissDrag, slideTo, animationSpec = tween(CLOSE_MILLIS)) { value, _ ->
                    dismissDrag = value
                }
            }
            appearance.animateTo(0f, tween(CLOSE_MILLIS, easing = FastOutLinearInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        appearance.animateTo(1f, tween(OPEN_MILLIS, easing = LinearOutSlowInEasing))
    }

    Dialog(
        // While annotating or a panel is open, back is handled there; it may be saving.
        onDismissRequest = { if (!annotating && !panelOpen) close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ImmersiveViewerWindow(systemBarsVisible = controlsVisible || annotating || panelOpen)
        BoxWithConstraints(Modifier.fillMaxSize().testTag("image-viewer")) {
            val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val center = Offset(viewport.width / 2f, viewport.height / 2f)
            val fitted = fittedImageSize(bitmap.width, bitmap.height, viewport)

            fun bounded(candidate: Offset, atScale: Float): Offset {
                val maxX = ((fitted.width * atScale - viewport.width) / 2f).coerceAtLeast(0f)
                val maxY = ((fitted.height * atScale - viewport.height) / 2f).coerceAtLeast(0f)
                return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
            }

            fun animateZoom(targetScale: Float, focus: Offset = center) {
                val startScale = scale
                val startOffset = offset
                val focusFromCenter = focus - center
                val targetOffset = bounded(
                    focusFromCenter - (focusFromCenter - startOffset) * (targetScale / startScale),
                    targetScale
                )
                zoomAnimation[0]?.cancel()
                zoomAnimation[0] = scope.launch {
                    animate(0f, 1f, animationSpec = spring(stiffness = ZOOM_STIFFNESS)) {
                            progress,
                            _
                        ->
                        scale = lerp(startScale, targetScale, progress)
                        offset = lerp(startOffset, targetOffset, progress)
                    }
                }
            }

            val zoomed = scale > ZOOMED_THRESHOLD
            val dragProgress = (abs(dismissDrag) / (viewport.height / 3f)).coerceIn(0f, 1f)
            val description = image.description
            val zoomInLabel = stringResource(R.string.image_viewer_zoom_in)
            val fitLabel = stringResource(R.string.image_viewer_fit)
            val zoomState = stringResource(
                R.string.image_viewer_zoom_level,
                (scale * 100).roundToInt()
            )

            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = appearance.value * (1f - dragProgress * 0.75f) }
                    .background(Color.Black)
            )
            Image(
                bitmap = bitmap,
                contentDescription = description.ifBlank {
                    stringResource(R.string.image_viewer_image)
                },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .semantics {
                        stateDescription = zoomState
                        customActions = listOf(
                            CustomAccessibilityAction(if (zoomed) fitLabel else zoomInLabel) {
                                animateZoom(if (zoomed) 1f else DOUBLE_TAP_ZOOM)
                                true
                            }
                        )
                    }
                    .pointerInput(viewport, fitted) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { tap ->
                                animateZoom(
                                    if (scale >
                                        ZOOMED_THRESHOLD
                                    ) {
                                        1f
                                    } else {
                                        DOUBLE_TAP_ZOOM
                                    },
                                    tap
                                )
                            }
                        )
                    }
                    .pointerInput(viewport, fitted) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            zoomAnimation[0]?.cancel()
                            val velocity = VelocityTracker()
                            velocity.addPosition(down.uptimeMillis, down.position)
                            val slop = viewConfiguration.touchSlop
                            var pastSlop = false
                            var dismissing = false
                            var totalZoom = 1f
                            var totalPan = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.id == down.id }?.let {
                                    velocity.addPosition(it.uptimeMillis, it.position)
                                }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (!pastSlop) {
                                    totalZoom *= zoom
                                    totalPan += pan
                                    val zoomMotion =
                                        abs(1f - totalZoom) * event.calculateCentroidSize(false)
                                    if (zoomMotion > slop || totalPan.getDistance() > slop) {
                                        pastSlop = true
                                        dismissing = event.changes.size == 1 &&
                                            scale <= ZOOMED_THRESHOLD &&
                                            abs(totalPan.y) > abs(totalPan.x)
                                    }
                                }
                                if (pastSlop) {
                                    if (dismissing) {
                                        dismissDrag += pan.y
                                    } else {
                                        val newScale = (scale * zoom).coerceIn(MIN_PINCH, MAX_ZOOM)
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        val moved = if (centroid.isSpecified) {
                                            val focus = centroid - center
                                            focus - (focus - offset) * (newScale / scale) + pan
                                        } else {
                                            offset + pan
                                        }
                                        scale = newScale
                                        offset = bounded(moved, scale.coerceAtLeast(1f))
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (dismissing) {
                                val flingVelocity = velocity.calculateVelocity().y
                                val flung = abs(flingVelocity) > DISMISS_VELOCITY.dp.toPx() &&
                                    flingVelocity * dismissDrag > 0f
                                if (flung ||
                                    abs(dismissDrag) > viewport.height * DISMISS_FRACTION
                                ) {
                                    close(slideTo = viewport.height / 2f * sign(dismissDrag))
                                } else {
                                    scope.launch {
                                        animate(dismissDrag, 0f, animationSpec = spring()) {
                                                value,
                                                _
                                            ->
                                            dismissDrag = value
                                        }
                                    }
                                }
                            } else if (scale < 1f) {
                                animateZoom(1f)
                            }
                        }
                    }
                    .graphicsLayer {
                        val entrance = lerp(ENTRANCE_SCALE, 1f, appearance.value)
                        val dragShrink = 1f - dragProgress * 0.15f
                        scaleX = scale * entrance * dragShrink
                        scaleY = scale * entrance * dragShrink
                        translationX = offset.x
                        translationY = offset.y + dismissDrag
                        alpha = appearance.value
                    }
                    .testTag("image-viewer-image")
            )

            val controlsShown = controlsVisible && !closing && dismissDrag == 0f && !panelOpen
            AnimatedVisibility(
                visible = controlsShown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    IconButton(
                        onClick = { close() },
                        modifier = Modifier.testTag("image-viewer-close")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.image_viewer_close),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = description,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            .testTag("image-viewer-title")
                    )
                    AnimatedVisibility(
                        visible = zoomed,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        IconButton(
                            onClick = { animateZoom(1f) },
                            modifier = Modifier.testTag("image-viewer-fit")
                        ) {
                            Icon(
                                Icons.Filled.FitScreen,
                                contentDescription = fitLabel,
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = { annotating = true },
                        modifier = Modifier.testTag("image-viewer-annotate")
                    ) {
                        Icon(
                            Icons.Filled.Draw,
                            contentDescription = stringResource(R.string.image_annotate),
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { showingInfo = true },
                        modifier = Modifier.testTag("image-viewer-info")
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.image_info),
                            tint = Color.White
                        )
                    }
                    if (shrinkActions != null) {
                        IconButton(
                            onClick = { shrinking = true },
                            modifier = Modifier.testTag("image-viewer-shrink")
                        ) {
                            Icon(
                                Icons.Filled.PhotoSizeSelectSmall,
                                contentDescription = stringResource(R.string.image_shrink),
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            copying = true
                            scope.launch {
                                copyImageWithFeedback(context, image)
                                copying = false
                            }
                        },
                        enabled = !copying,
                        modifier = Modifier.testTag("image-viewer-copy")
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.image_viewer_copy),
                            tint = Color.White.copy(alpha = if (copying) 0.5f else 1f)
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = controlsShown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 20.dp)
                ) {
                    AnimatedVisibility(
                        visible = zoomed,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E2E2E).copy(alpha = 0.88f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = zoomState,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("image-viewer-zoom")
                            )
                        }
                    }
                    ImageMetadataBar(
                        image = image,
                        link = infoSource?.link,
                        details = details,
                        loading = detailsLoading,
                        onClick = { showingInfo = true }
                    )
                }
            }
            AnimatedVisibility(
                visible = panelOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                showingInfo = false
                                if (!savingShrunk) shrinking = false
                            }
                        }
                )
            }
            AnimatedVisibility(
                visible = showingInfo,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ImageInfoPanel(
                    image = image,
                    source = infoSource,
                    details = details,
                    loading = detailsLoading,
                    onShrink = shrinkActions?.let {
                        {
                            showingInfo = false
                            shrinking = true
                        }
                    },
                    onClose = { showingInfo = false }
                )
            }
            AnimatedVisibility(
                visible = shrinking && shrinkActions != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                shrinkActions?.let { actions ->
                    ImageShrinkPanel(
                        actions = actions,
                        onSaved = {
                            shrinking = false
                            close()
                        },
                        onClose = { shrinking = false },
                        onSavingChange = { savingShrunk = it }
                    )
                }
            }
            AnimatedVisibility(
                visible = annotating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ImageAnnotationEditor(
                    image = image,
                    onSave = onSaveAnnotation,
                    onSaved = {
                        annotating = false
                        close()
                    },
                    onClose = { annotating = false }
                )
            }
        }
    }
}

/** Light system-bar icons over the black backdrop, and the bars follow the viewer controls. */
@Composable
private fun ImmersiveViewerWindow(systemBarsVisible: Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        window.setDimAmount(0f)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { }
    }
    LaunchedEffect(window, systemBarsVisible) {
        WindowCompat.getInsetsController(window, view).apply {
            if (systemBarsVisible) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** Copies [image] and says so where Android does not already confirm clipboard changes. */
internal suspend fun copyImageWithFeedback(context: Context, image: NoteImage) {
    val copied = copyImageToClipboard(context, image)
    // Android 13 and newer confirm clipboard changes themselves.
    if (!copied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(
            context,
            if (copied) R.string.image_viewer_copied else R.string.image_viewer_copy_failed,
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Puts [image] on the clipboard as a PNG that other apps can paste. Only the newest copy is kept
 * in the app cache, and the clipboard grants readers temporary access to it.
 */
internal suspend fun copyImageToClipboard(context: Context, image: NoteImage): Boolean {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, COPIED_IMAGES_DIRECTORY).apply {
                deleteRecursively()
                mkdirs()
            }
            val file = File(directory, "image-${UUID.randomUUID()}.png")
            val written = file.outputStream().use {
                image.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            check(written) { "The image could not be encoded" }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    } ?: return false
    return runCatching {
        val label = image.description.ifBlank { context.getString(R.string.image_viewer_image) }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: error("No clipboard")
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
    }.isSuccess
}

private const val COPIED_IMAGES_DIRECTORY = "copied-images"

internal fun fittedImageSize(width: Int, height: Int, viewport: Size): Size {
    if (width <= 0 || height <= 0 || viewport.width <= 0f || viewport.height <= 0f) {
        return Size.Zero
    }
    val fit = minOf(viewport.width / width, viewport.height / height)
    return Size(width * fit, height * fit)
}

private const val OPEN_MILLIS = 220
private const val CLOSE_MILLIS = 160
private const val ENTRANCE_SCALE = 0.92f
private const val ZOOMED_THRESHOLD = 1.01f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_PINCH = 0.8f
private const val MAX_ZOOM = 6f
private const val DISMISS_FRACTION = 0.12f
private const val DISMISS_VELOCITY = 600
private const val ZOOM_STIFFNESS = 400f
