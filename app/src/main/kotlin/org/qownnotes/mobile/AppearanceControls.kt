package org.qownnotes.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

/** Test tag suffix for a palette color, such as `light-blue`. */
internal fun swatchTag(prefix: String, color: NamedColor?): String =
    "$prefix-" + (color?.name?.lowercase()?.replace(' ', '-') ?: "default")

/**
 * A labeled row of color swatches. The first swatch means "no custom color" and shows
 * [defaultColor] with a reset icon. Selection is exposed to accessibility services.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorChoice(
    label: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    testTag: String,
    defaultLabel: String,
    defaultColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Swatch(
                color = defaultColor,
                description = defaultLabel,
                selected = selected == null,
                reset = true,
                testTag = swatchTag(testTag, null),
                onClick = { onSelect(null) }
            )
            AppearanceColors.palette.forEach { color ->
                Swatch(
                    color = Color(color.argb),
                    description = stringResource(color.label),
                    selected = selected == color.argb,
                    reset = false,
                    testTag = swatchTag(testTag, color),
                    onClick = { onSelect(color.argb) }
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    description: String,
    selected: Boolean,
    reset: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val content = Color(AppearanceColors.contentColor(color.toArgb()))
    Box(
        modifier = Modifier.size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = content)
            reset -> Icon(
                Icons.Filled.FormatColorReset,
                contentDescription = null,
                tint = content.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Editor for the note list's header, row, and category colors. */
@Composable
internal fun AppAppearanceEditor(
    appearance: AppAppearance,
    onChange: (AppAppearance) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SwitchRow(
            label = stringResource(R.string.appearance_show_list_header),
            checked = appearance.showListHeader,
            onCheckedChange = { onChange(appearance.copy(showListHeader = it)) },
            testTag = "toggle-list-header"
        )
        Text(
            stringResource(R.string.appearance_show_list_header_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow(
            label = stringResource(R.string.appearance_note_cards),
            checked = appearance.noteCards,
            onCheckedChange = { onChange(appearance.copy(noteCards = it)) },
            testTag = "toggle-note-cards"
        )
        Text(
            stringResource(R.string.appearance_note_cards_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColorChoice(
            label = stringResource(R.string.appearance_header_color),
            selected = appearance.headerColor,
            onSelect = { onChange(appearance.copy(headerColor = it)) },
            testTag = "app-header",
            defaultLabel = stringResource(R.string.appearance_default_header),
            defaultColor = MaterialTheme.colorScheme.surface
        )
        ColorChoice(
            label = stringResource(R.string.appearance_note_background),
            selected = appearance.noteBackground,
            onSelect = { onChange(appearance.copy(noteBackground = it)) },
            testTag = "app-note-background",
            defaultLabel = stringResource(R.string.appearance_default_note_background),
            defaultColor = MaterialTheme.colorScheme.surface
        )
        SwitchRow(
            label = stringResource(R.string.appearance_highlight_categories),
            checked = appearance.highlightCategories,
            onCheckedChange = { onChange(appearance.copy(highlightCategories = it)) },
            testTag = "toggle-highlight-categories"
        )
        Text(
            stringResource(R.string.appearance_highlight_categories_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (appearance.highlightCategories) {
            ColorChoice(
                label = stringResource(R.string.appearance_category_highlight_color),
                selected = appearance.categoryHighlight,
                onSelect = { onChange(appearance.copy(categoryHighlight = it)) },
                testTag = "app-category-highlight",
                defaultLabel = stringResource(R.string.appearance_default_category_highlight),
                defaultColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
        Text(
            stringResource(R.string.appearance_custom_colors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * A note's category, either as plain text or as a tinted label. The label uses
 * [highlightColor] when set and the theme's secondary container otherwise.
 */
@Composable
internal fun NoteCategoryLabel(
    category: String,
    highlight: Boolean,
    highlightColor: Int?,
    testTag: String
) {
    if (!highlight) {
        Text(
            category,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(testTag)
        )
        return
    }
    val background = highlightColor?.let(::Color) ?: MaterialTheme.colorScheme.secondaryContainer
    val content = highlightColor?.let { Color(AppearanceColors.contentColor(it)) }
        ?: MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        category,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .testTag(testTag)
    )
}

/** Optional two-line header under the search bar: what is listed, then whose notes they are. */
@Composable
internal fun NoteListHeader(
    title: String,
    accountName: String,
    contentColor: Color,
    secondaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, bottom = 8.dp)
            .testTag("note-list-header")
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("note-list-header-title")
        )
        Text(
            accountName,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("note-list-header-account")
        )
    }
}

/**
 * Keeps status bar icons readable over a custom header color and restores the theme default when
 * the screen leaves or the color is cleared.
 */
@Composable
internal fun StatusBarIconsFor(headerColor: Int?) {
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(view, headerColor, darkTheme) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = headerColor
            ?.let { AppearanceColors.contentColor(it) == AppearanceColors.DARK_CONTENT }
            ?: !darkTheme
        onDispose { controller?.isAppearanceLightStatusBars = !darkTheme }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Editor for one widget's colors, opacity, and frame, with a live approximation above it. */
@Composable
internal fun WidgetAppearanceEditor(
    appearance: WidgetAppearance,
    singleNote: Boolean,
    onChange: (WidgetAppearance) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultBackground = colorResource(R.color.widget_background)
    val defaultRow = colorResource(R.color.widget_item_background)
    val defaultFrame = colorResource(R.color.widget_frame)
    Column(modifier = modifier.fillMaxWidth()) {
        WidgetAppearancePreview(appearance, singleNote)
        ColorChoice(
            label = stringResource(R.string.widget_appearance_background),
            selected = appearance.background,
            onSelect = { onChange(appearance.copy(background = it)) },
            testTag = "widget-background",
            defaultLabel = stringResource(R.string.widget_appearance_default_background),
            defaultColor = defaultBackground
        )
        Text(
            stringResource(
                R.string.widget_appearance_background_opacity,
                appearance.backgroundOpacityPercent
            ),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Slider(
            value = appearance.backgroundOpacityPercent.toFloat(),
            onValueChange = {
                onChange(
                    appearance.copy(
                        backgroundOpacityPercent = AppearanceColors.coerceOpacity(
                            it.roundToInt()
                        )
                    )
                )
            },
            valueRange = OPACITY_RANGE,
            steps = OPACITY_STEPS,
            modifier = Modifier.testTag("widget-opacity")
        )
        ColorChoice(
            label = stringResource(R.string.widget_appearance_header),
            selected = appearance.header,
            onSelect = { onChange(appearance.copy(header = it)) },
            testTag = "widget-header",
            defaultLabel = stringResource(R.string.widget_appearance_no_header_color),
            defaultColor = defaultBackground
        )
        if (!singleNote) {
            ColorChoice(
                label = stringResource(R.string.widget_appearance_note_rows),
                selected = appearance.row,
                onSelect = { onChange(appearance.copy(row = it)) },
                testTag = "widget-row",
                defaultLabel = stringResource(R.string.widget_appearance_default_note_rows),
                defaultColor = defaultRow
            )
        }
        SwitchRow(
            label = stringResource(R.string.widget_appearance_frame),
            checked = appearance.frame,
            onCheckedChange = { onChange(appearance.copy(frame = it)) },
            testTag = "widget-frame"
        )
        if (appearance.frame) {
            ColorChoice(
                label = stringResource(R.string.widget_appearance_frame_color),
                selected = appearance.frameColor,
                onSelect = { onChange(appearance.copy(frameColor = it)) },
                testTag = "widget-frame-color",
                defaultLabel = stringResource(R.string.widget_appearance_default_frame),
                defaultColor = defaultFrame
            )
        }
    }
}

/** Compose approximation of the RemoteViews widget; it mirrors the same color policy. */
@Composable
private fun WidgetAppearancePreview(appearance: WidgetAppearance, singleNote: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val background = appearance.background?.let(::Color) ?: colorResource(R.color.widget_background)
    val defaultText = colorResource(R.color.widget_text)
    val defaultSecondary = colorResource(R.color.widget_text_secondary)
    val headerSurface = WidgetAppearanceViews.headerSurface(appearance)
    fun primary(surface: Int?) = surface?.let { Color(AppearanceColors.contentColor(it)) }
        ?: defaultText
    fun secondary(surface: Int?) =
        surface?.let { Color(AppearanceColors.secondaryContentColor(it)) } ?: defaultSecondary
    Box(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(shape)
            // A checkerboard would be more precise; a neutral backdrop shows the opacity well enough.
            .background(Color(0xFF8E8E8E))
            .testTag("widget-appearance-preview")
    ) {
        Box(
            modifier = Modifier.matchParentSize()
                .background(background.copy(alpha = appearance.backgroundOpacityPercent / 100f))
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(
                    if (appearance.frame) {
                        Modifier.border(
                            2.dp,
                            appearance.frameColor?.let(::Color)
                                ?: colorResource(R.color.widget_frame),
                            shape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.header?.let(::Color) ?: Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (singleNote) {
                        stringResource(R.string.widget_preview_shopping_list)
                    } else {
                        stringResource(R.string.widget_preview_notes)
                    },
                    color = primary(headerSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (!singleNote) {
                    Text(
                        stringResource(R.string.widget_preview_personal),
                        color = secondary(headerSurface),
                        fontSize = 12.sp
                    )
                }
            }
            if (singleNote) {
                listOf(
                    stringResource(R.string.widget_preview_milk),
                    stringResource(R.string.widget_preview_bread)
                ).forEach {
                    Text(
                        it,
                        color = secondary(appearance.background),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            } else {
                val rowColor = appearance.row?.let(::Color)
                    ?: colorResource(R.color.widget_item_background)
                listOf(
                    stringResource(R.string.widget_preview_meeting_notes) to
                        stringResource(R.string.widget_preview_meeting_notes_excerpt),
                    stringResource(R.string.widget_preview_ideas) to
                        stringResource(R.string.widget_preview_ideas_excerpt)
                ).forEach { (title, excerpt) ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowColor)
                            .padding(10.dp)
                    ) {
                        Text(
                            title,
                            color = primary(appearance.row),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(excerpt, color = secondary(appearance.row), fontSize = 13.sp)
                    }
                }
            }
            Box(modifier = Modifier.height(4.dp))
        }
    }
}

private const val OPACITY_STEP = 5
private val OPACITY_RANGE =
    AppearanceColors.MIN_OPACITY_PERCENT.toFloat()..AppearanceColors.MAX_OPACITY_PERCENT.toFloat()

// Slider steps count the stops between the ends: 20, 25, ..., 100 has 15 of them.
private const val OPACITY_STEPS =
    (AppearanceColors.MAX_OPACITY_PERCENT - AppearanceColors.MIN_OPACITY_PERCENT) /
        OPACITY_STEP - 1
