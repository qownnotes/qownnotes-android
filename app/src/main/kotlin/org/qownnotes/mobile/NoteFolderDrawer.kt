package org.qownnotes.mobile

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.qownnotes.mobile.core.NoteFolder
import org.qownnotes.mobile.core.NoteFolderScope
import org.qownnotes.mobile.core.NoteFolderTree
import org.qownnotes.mobile.core.NoteFolders

/**
 * Folder navigation of one account: the notes root, the folder tree derived from its cached notes
 * with a note count per folder, and the QOwnNotes "show notes from subfolders" choice.
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
    /**
     * Whether the drawer is open or opening. The closed drawer keeps only its empty sheet, so the
     * folder names do not join the note list's accessibility tree while it is hidden.
     */
    visible: Boolean = true
) {
    val selectedKey = NoteFolders.key(scope.path)
    val countsSubfolders = nested && scope.includeSubfolders
    ModalDrawerSheet(modifier = Modifier.testTag("folder-drawer")) {
        if (!visible) return@ModalDrawerSheet
        Column(modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 16.dp)) {
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
                    testTag = "folder-root"
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
                    testTag = "folder-${folder.path}"
                )
            }
        }
    }
}

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
    testTag: String
) {
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
        NavigationDrawerItem(
            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = {
                Icon(
                    if (selected) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null
                )
            },
            badge = { Text(count.toString(), modifier = Modifier.testTag("$testTag-count")) },
            selected = selected,
            onClick = onClick,
            modifier = Modifier.weight(1f).testTag(testTag)
        )
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
