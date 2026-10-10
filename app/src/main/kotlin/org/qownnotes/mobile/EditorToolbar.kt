package org.qownnotes.mobile

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Related editor tools, shown together and separated from the other groups. */
internal enum class EditorToolGroup(@StringRes val title: Int) {
    HISTORY(R.string.toolbar_group_history),
    TEXT(R.string.toolbar_group_text),
    LISTS(R.string.toolbar_group_lists),
    INSERT(R.string.toolbar_group_insert),
    DECK(R.string.toolbar_group_deck)
}

/**
 * Every editor toolbar action in display order. The toolbar, its tooltips, its optional labels,
 * and the tools help dialog all read from here, so they cannot disagree about an icon's meaning.
 */
internal enum class EditorTool(
    val group: EditorToolGroup,
    val icon: ImageVector,
    /** Full name, used for tooltips, screen readers, and the help dialog. */
    @StringRes val description: Int,
    /** Short name shown under the icon when toolbar labels are enabled. */
    @StringRes val label: Int,
    @StringRes val help: Int,
    val testTag: String
) {
    UNDO(
        EditorToolGroup.HISTORY,
        Icons.AutoMirrored.Filled.Undo,
        R.string.format_undo,
        R.string.toolbar_label_undo,
        R.string.toolbar_help_undo,
        "undo-edit"
    ),
    REDO(
        EditorToolGroup.HISTORY,
        Icons.AutoMirrored.Filled.Redo,
        R.string.format_redo,
        R.string.toolbar_label_redo,
        R.string.toolbar_help_redo,
        "redo-edit"
    ),
    HEADING(
        EditorToolGroup.TEXT,
        HeadingIcon,
        R.string.format_heading,
        R.string.toolbar_label_heading,
        R.string.toolbar_help_heading,
        "format-heading"
    ),
    BOLD(
        EditorToolGroup.TEXT,
        Icons.Filled.FormatBold,
        R.string.format_bold,
        R.string.toolbar_label_bold,
        R.string.toolbar_help_bold,
        "format-bold"
    ),
    ITALIC(
        EditorToolGroup.TEXT,
        Icons.Filled.FormatItalic,
        R.string.format_italic,
        R.string.toolbar_label_italic,
        R.string.toolbar_help_italic,
        "format-italic"
    ),
    STRIKETHROUGH(
        EditorToolGroup.TEXT,
        Icons.Filled.StrikethroughS,
        R.string.format_strikethrough,
        R.string.toolbar_label_strikethrough,
        R.string.toolbar_help_strikethrough,
        "format-strikethrough"
    ),
    CODE(
        EditorToolGroup.TEXT,
        Icons.Filled.Code,
        R.string.format_code,
        R.string.toolbar_label_code,
        R.string.toolbar_help_code,
        "format-code"
    ),
    QUOTE(
        EditorToolGroup.TEXT,
        Icons.Filled.FormatQuote,
        R.string.format_quote,
        R.string.toolbar_label_quote,
        R.string.toolbar_help_quote,
        "format-quote"
    ),
    BULLET_LIST(
        EditorToolGroup.LISTS,
        Icons.AutoMirrored.Filled.FormatListBulleted,
        R.string.format_list,
        R.string.toolbar_label_list,
        R.string.toolbar_help_list,
        "format-list"
    ),
    NUMBERED_LIST(
        EditorToolGroup.LISTS,
        Icons.Filled.FormatListNumbered,
        R.string.format_numbered_list,
        R.string.toolbar_label_numbered_list,
        R.string.toolbar_help_numbered_list,
        "format-numbered-list"
    ),
    CHECKBOX_LIST(
        EditorToolGroup.LISTS,
        Icons.Filled.Checklist,
        R.string.format_checkbox_list,
        R.string.toolbar_label_checkbox_list,
        R.string.toolbar_help_checkbox_list,
        "format-checkbox-list"
    ),
    INDENT(
        EditorToolGroup.LISTS,
        Icons.AutoMirrored.Filled.FormatIndentIncrease,
        R.string.format_indent,
        R.string.toolbar_label_indent,
        R.string.toolbar_help_indent,
        "format-indent"
    ),
    OUTDENT(
        EditorToolGroup.LISTS,
        Icons.AutoMirrored.Filled.FormatIndentDecrease,
        R.string.format_outdent,
        R.string.toolbar_label_outdent,
        R.string.toolbar_help_outdent,
        "format-outdent"
    ),
    LINK(
        EditorToolGroup.INSERT,
        Icons.Filled.Link,
        R.string.format_link,
        R.string.toolbar_label_link,
        R.string.toolbar_help_link,
        "format-link"
    ),
    IMAGE(
        EditorToolGroup.INSERT,
        Icons.Filled.AddPhotoAlternate,
        R.string.insert_image,
        R.string.toolbar_label_image,
        R.string.toolbar_help_image,
        "insert-image"
    ),
    DATE(
        EditorToolGroup.INSERT,
        Icons.Filled.Today,
        R.string.insert_date,
        R.string.toolbar_label_date,
        R.string.toolbar_help_date,
        "insert-date"
    ),
    DATE_TIME(
        EditorToolGroup.INSERT,
        Icons.Filled.MoreTime,
        R.string.insert_date_time,
        R.string.toolbar_label_date_time,
        R.string.toolbar_help_date_time,
        "insert-datetime"
    ),
    CREATE_DECK_CARD(
        EditorToolGroup.DECK,
        Icons.Filled.PostAdd,
        R.string.create_deck_card,
        R.string.toolbar_label_create_deck_card,
        R.string.toolbar_help_create_deck_card,
        "create-deck-card-link"
    ),
    BROWSE_DECK_CARDS(
        EditorToolGroup.DECK,
        Icons.Filled.ViewKanban,
        R.string.deck_cards,
        R.string.toolbar_label_deck_cards,
        R.string.toolbar_help_deck_cards,
        "browse-editor-deck-cards"
    )
}

internal fun availableEditorTools(deckAvailable: Boolean): List<EditorTool> =
    EditorTool.entries.filter { deckAvailable || it.group != EditorToolGroup.DECK }

/**
 * The editor's formatting and insert actions as one scrollable row, grouped by purpose and ending
 * with a help button that explains every tool.
 */
@Composable
internal fun EditorToolbar(
    deckAvailable: Boolean,
    showLabels: Boolean,
    isEnabled: (EditorTool) -> Boolean,
    onTool: (EditorTool) -> Unit,
    onHelp: () -> Unit
) {
    val tools = availableEditorTools(deckAvailable)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .testTag("format-toolbar")
    ) {
        tools.forEachIndexed { index, tool ->
            if (index > 0 && tools[index - 1].group != tool.group) ToolbarGroupDivider(showLabels)
            EditorToolbarButton(
                icon = tool.icon,
                description = stringResource(tool.description),
                label = stringResource(tool.label),
                showLabel = showLabels,
                enabled = isEnabled(tool),
                testTag = tool.testTag,
                onClick = { onTool(tool) }
            )
        }
        ToolbarGroupDivider(showLabels)
        EditorToolbarButton(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            description = stringResource(R.string.toolbar_help_title),
            label = stringResource(R.string.toolbar_label_help),
            showLabel = showLabels,
            enabled = true,
            testTag = "editor-toolbar-help",
            onClick = onHelp
        )
    }
}

@Composable
private fun ToolbarGroupDivider(showLabels: Boolean) {
    VerticalDivider(
        modifier = Modifier.padding(horizontal = 4.dp).height(if (showLabels) 32.dp else 24.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** An icon button whose name appears when it is long-pressed, and optionally below the icon. */
@Composable
private fun EditorToolbarButton(
    icon: ImageVector,
    description: String,
    label: String,
    showLabel: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    WithTooltip(description, above = true) {
        if (showLabel) {
            val contentColor = LocalContentColor.current.let {
                if (enabled) it else it.copy(alpha = DISABLED_CONTENT_ALPHA)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    // Screen readers announce the full name rather than the short label.
                    .semantics { contentDescription = description }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag(testTag)
            ) {
                Icon(icon, contentDescription = null, tint = contentColor)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(testTag)) {
                Icon(icon, contentDescription = description)
            }
        }
    }
}

/** A one-time hint explaining how to find out what the toolbar icons do. */
@Composable
internal fun EditorToolbarHint(onShowHelp: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("editor-toolbar-hint")
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp)
                )
                Text(
                    stringResource(R.string.toolbar_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(
                    onClick = onShowHelp,
                    modifier = Modifier.testTag("editor-toolbar-hint-help")
                ) { Text(stringResource(R.string.toolbar_hint_show_tools)) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss-editor-toolbar-hint")
                ) { Text(stringResource(R.string.toolbar_hint_dismiss)) }
            }
        }
    }
}

/** Lists every available toolbar action with its icon, name, and what it does. */
@Composable
internal fun EditorToolbarHelpDialog(
    deckAvailable: Boolean,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val tools = availableEditorTools(deckAvailable)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.toolbar_help_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                    .testTag("editor-toolbar-help-dialog")
            ) {
                Text(
                    stringResource(R.string.toolbar_help_long_press),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsToggle(
                    label = stringResource(R.string.settings_show_toolbar_labels),
                    checked = showLabels,
                    onCheckedChange = onShowLabelsChange,
                    testTag = "editor-toolbar-help-labels"
                )
                tools.groupBy { it.group }.forEach { (group, groupTools) ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSectionHeader(stringResource(group.title))
                    groupTools.forEach { tool -> ToolHelpRow(tool) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close-editor-toolbar-help")
            ) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun ToolHelpRow(tool: EditorTool) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .testTag("editor-tool-help-${tool.testTag}")
    ) {
        Icon(
            tool.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(24.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(tool.description), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(tool.help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val DISABLED_CONTENT_ALPHA = 0.38f

/** A bold "H", which reads as "heading" more directly than Material's text-title icon. */
private val HeadingIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Heading",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 4f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(6.5f)
        horizontalLineToRelative(8f)
        verticalLineTo(4f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(16f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(-6.5f)
        horizontalLineTo(8f)
        verticalLineTo(20f)
        horizontalLineTo(5f)
        close()
    }.build()
}
