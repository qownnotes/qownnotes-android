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
                    description = color.name,
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
            label = "Show list header",
            checked = appearance.showListHeader,
            onCheckedChange = { onChange(appearance.copy(showListHeader = it)) },
            testTag = "toggle-list-header"
        )
        Text(
            "Shows the listed category and the account name below the search bar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow(
            label = "Show notes as cards",
            checked = appearance.noteCards,
            onCheckedChange = { onChange(appearance.copy(noteCards = it)) },
            testTag = "toggle-note-cards"
        )
        Text(
            "Draws a border around each note with a small gap between notes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColorChoice(
            label = "Header color",
            selected = appearance.headerColor,
            onSelect = { onChange(appearance.copy(headerColor = it)) },
            testTag = "app-header",
            defaultLabel = "Default header",
            defaultColor = MaterialTheme.colorScheme.surface
        )
        ColorChoice(
            label = "Note background",
            selected = appearance.noteBackground,
            onSelect = { onChange(appearance.copy(noteBackground = it)) },
            testTag = "app-note-background",
            defaultLabel = "Default note background",
            defaultColor = MaterialTheme.colorScheme.surface
        )
        SwitchRow(
            label = "Highlight categories",
            checked = appearance.highlightCategories,
            onCheckedChange = { onChange(appearance.copy(highlightCategories = it)) },
            testTag = "toggle-highlight-categories"
        )
        Text(
            "Applies when \"Show category\" is on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (appearance.highlightCategories) {
            ColorChoice(
                label = "Category highlight color",
                selected = appearance.categoryHighlight,
                onSelect = { onChange(appearance.copy(categoryHighlight = it)) },
                testTag = "app-category-highlight",
                defaultLabel = "Default category highlight",
                defaultColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
        Text(
            "Custom colors stay the same in light and dark mode. Text color adjusts " +
                "automatically for readability.",
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
            label = "Background",
            selected = appearance.background,
            onSelect = { onChange(appearance.copy(background = it)) },
            testTag = "widget-background",
            defaultLabel = "Default background",
            defaultColor = defaultBackground
        )
        Text(
            "Background opacity: ${appearance.backgroundOpacityPercent}%",
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
            label = "Header",
            selected = appearance.header,
            onSelect = { onChange(appearance.copy(header = it)) },
            testTag = "widget-header",
            defaultLabel = "No header color",
            defaultColor = defaultBackground
        )
        if (!singleNote) {
            ColorChoice(
                label = "Note rows",
                selected = appearance.row,
                onSelect = { onChange(appearance.copy(row = it)) },
                testTag = "widget-row",
                defaultLabel = "Default note rows",
                defaultColor = defaultRow
            )
        }
        SwitchRow(
            label = "Frame",
            checked = appearance.frame,
            onCheckedChange = { onChange(appearance.copy(frame = it)) },
            testTag = "widget-frame"
        )
        if (appearance.frame) {
            ColorChoice(
                label = "Frame color",
                selected = appearance.frameColor,
                onSelect = { onChange(appearance.copy(frameColor = it)) },
                testTag = "widget-frame-color",
                defaultLabel = "Default frame",
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
                    if (singleNote) "Shopping list" else "Notes",
                    color = primary(headerSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (!singleNote) {
                    Text("Personal", color = secondary(headerSurface), fontSize = 12.sp)
                }
            }
            if (singleNote) {
                listOf("- Milk", "- Bread").forEach {
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
                listOf("Meeting notes" to "Agenda and decisions", "Ideas" to "Weekend project")
                    .forEach { (title, excerpt) ->
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
