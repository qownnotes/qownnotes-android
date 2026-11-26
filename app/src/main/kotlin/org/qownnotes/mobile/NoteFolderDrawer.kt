package org.qownnotes.mobile

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.qownnotes.mobile.core.NoteFolder
import org.qownnotes.mobile.core.NoteFolderScope
import org.qownnotes.mobile.core.NoteFolderTree
import org.qownnotes.mobile.core.NoteFolders

/**
 * Folder navigation of one account: the notes root, the folder tree derived from its cached notes
 * with a note count per folder, and the QOwnNotes "show notes from subfolders" choice. A long
 * press on a folder offers creating a subfolder in it, renaming it, and deleting it.
 */
@Composable
internal fun NoteFolderDrawerSheet(
    accountName: String,
    tree: NoteFolderTree,
    scope: NoteFolderScope,
    nested: Boolean,
    /** Folder keys, as [NoteFolders.key], whose children are shown. */
    expanded: Set<String>,
    onToggleExpanded: (NoteFolder) -> Unit,
    onSelect: (String) -> Unit,
    onIncludeSubfoldersChange: (Boolean) -> Unit,
    /** Creates a folder in the given parent folder, where the empty string is the root. */
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (NoteFolder) -> Unit = {},
    onDeleteFolder: (NoteFolder) -> Unit = {},
    /**
     * Whether the drawer is open or opening. The closed drawer keeps only its empty sheet, so the
     * folder names do not join the note list's accessibility tree while it is hidden.
     */
    visible: Boolean = true
) {
    val selectedKey = NoteFolders.key(scope.path)
    val countsSubfolders = nested && scope.includeSubfolders
    val newSubfolder = stringResource(R.string.category_new)
    val rename = stringResource(R.string.action_rename)
    val delete = stringResource(R.string.folders_action_delete)
    ModalDrawerSheet(modifier = Modifier.testTag("folder-drawer")) {
        if (!visible) return@ModalDrawerSheet
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 28.dp, end = 12.dp, top = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    stringResource(R.string.folders_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { onCreateFolder("") },
                modifier = Modifier.testTag("create-folder")
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = newSubfolder)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .toggleable(
                    value = scope.includeSubfolders,
                    role = Role.Switch,
                    onValueChange = onIncludeSubfoldersChange
                )
                .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .testTag("folder-show-subfolders"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.folders_show_subfolders),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = scope.includeSubfolders, onCheckedChange = null)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(modifier = Modifier.padding(vertical = 8.dp).testTag("folder-list")) {
            item(key = "root") {
                FolderRow(
                    name = stringResource(R.string.folders_root),
                    count = tree.count(NoteFolderScope("", scope.includeSubfolders)),
                    depth = 0,
                    selected = scope.isRoot,
                    expandable = false,
                    expanded = false,
                    onToggleExpanded = {},
                    onClick = { onSelect("") },
                    testTag = "folder-root",
                    actions = listOf(
                        FolderAction(newSubfolder, Icons.Filled.CreateNewFolder, "new") {
                            onCreateFolder("")
                        }
                    )
                )
            }
            items(
                tree.visible { NoteFolders.key(it.path) in expanded },
                key = { "folder:" + NoteFolders.key(it.path) }
            ) { folder ->
                FolderRow(
                    name = folder.name,
                    count = if (countsSubfolders) folder.subtreeNoteCount else folder.noteCount,
                    depth = folder.depth,
                    selected = NoteFolders.key(folder.path) == selectedKey,
                    expandable = folder.children.isNotEmpty(),
                    expanded = NoteFolders.key(folder.path) in expanded,
                    onToggleExpanded = { onToggleExpanded(folder) },
                    onClick = { onSelect(folder.path) },
                    testTag = "folder-${folder.path}",
                    // Without nesting a subfolder would only become a sibling with a slash.
                    actions = listOfNotNull(
                        FolderAction(newSubfolder, Icons.Filled.CreateNewFolder, "new") {
                            onCreateFolder(folder.path)
                        }.takeIf { nested },
                        FolderAction(rename, Icons.Filled.DriveFileRenameOutline, "rename") {
                            onRenameFolder(folder)
                        },
                        FolderAction(delete, Icons.Filled.DeleteOutline, "delete", true) {
                            onDeleteFolder(folder)
                        }
                    )
                )
            }
        }
    }
}

private class FolderAction(
    val label: String,
    val icon: ImageVector,
    /** Suffix of the menu item's test tag. */
    val tag: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    name: String,
    count: Int,
    depth: Int,
    selected: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClick: () -> Unit,
    testTag: String,
    actions: List<FolderAction> = emptyList()
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp + (depth * 16).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (expandable) {
            IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("$testTag-expand")) {
                Icon(
                    if (expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = stringResource(
                        if (expanded) R.string.folders_collapse else R.string.folders_expand,
                        name
                    )
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            val actionsLabel = stringResource(R.string.folders_actions)
            // Laid out like a navigation drawer item, which offers no long press of its own.
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                contentColor = contentColor,
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        role = Role.Tab,
                        onClick = onClick,
                        onLongClickLabel = actionsLabel.takeIf { actions.isNotEmpty() },
                        onLongClick = if (actions.isEmpty()) {
                            null
                        } else {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            }
                        }
                    )
                    .semantics {
                        this.selected = selected
                        if (actions.isNotEmpty()) {
                            customActions = actions.map { action ->
                                CustomAccessibilityAction(action.label) {
                                    action.onClick()
                                    true
                                }
                            }
                        }
                    }
                    .testTag(testTag)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 24.dp)
                ) {
                    Icon(
                        when {
                            selected -> Icons.Filled.FolderOpen
                            // A folder without notes of its own or below it, such as a new one.
                            count == 0 && !expandable -> Icons.Outlined.Folder
                            else -> Icons.Filled.Folder
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag("$testTag-count")
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.testTag("$testTag-menu")
            ) {
                actions.forEach { action ->
                    val color = if (action.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    DropdownMenuItem(
                        text = { Text(action.label, color = color) },
                        leadingIcon = {
                            Icon(action.icon, contentDescription = null, tint = color)
                        },
                        onClick = {
                            menuOpen = false
                            action.onClick()
                        },
                        modifier = Modifier.testTag("$testTag-${action.tag}")
                    )
                }
            }
        }
    }
}

/** Short description of a folder scope for the note-list menu and header. */
internal object NoteFolderLabels {
    fun title(context: Context, scope: NoteFolderScope): String = when {
        scope.isWholeAccount -> context.getString(R.string.widget_all_notes)
        scope.isRoot -> context.getString(R.string.widget_uncategorized)
        else -> scope.path
    }

    fun menu(context: Context, scope: NoteFolderScope): String = context.getString(
        R.string.folders_menu_label,
        if (scope.isRoot) context.getString(R.string.folders_root) else scope.path
    )
}
