package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.TrashedNote

@Composable
internal fun TrashedNotesDialog(
    notes: List<TrashedNote>,
    onDismiss: () -> Unit,
    onRestore: (TrashedNote) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredNotes = remember(notes, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            notes
        } else {
            notes.filter {
                it.name.contains(trimmed, ignoreCase = true)
            }
        }
    }
    var selected by remember(filteredNotes) { mutableStateOf(filteredNotes.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_trash)) },
        text = {
            if (notes.isEmpty()) {
                ArchiveEmptyState(Icons.Filled.DeleteOutline, stringResource(R.string.trash_empty))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 560.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.trash_search_note_name)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.testTag("clear-trash-search")
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(
                                            R.string.action_clear_search
                                        )
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().testTag("trash-search")
                    )
                    if (filteredNotes.isEmpty()) {
                        ArchiveEmptyState(
                            Icons.Filled.SearchOff,
                            stringResource(R.string.trash_search_empty)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            filteredNotes.forEach { note ->
                                ArchiveEntryRow(
                                    icon = Icons.AutoMirrored.Filled.Article,
                                    title = note.name,
                                    subtitle = note.displayTimestamp,
                                    selected = note == selected,
                                    onClick = { selected = note },
                                    modifier = Modifier.testTag("trashed-note-${note.timestamp}")
                                )
                            }
                        }
                        ArchivePreview(selected?.content.orEmpty(), "trashed-note-preview")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onRestore) },
                enabled = selected != null,
                modifier = Modifier.testTag("restore-trashed-note")
            ) { ArchiveRestoreLabel(Icons.Filled.RestoreFromTrash) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
internal fun NoteVersionsDialog(
    versions: List<RemoteNoteVersion>,
    restoreEnabled: Boolean,
    onDismiss: () -> Unit,
    onRestore: (RemoteNoteVersion) -> Unit
) {
    var selected by remember(versions) { mutableStateOf(versions.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_versions)) },
        text = {
            if (versions.isEmpty()) {
                ArchiveEmptyState(
                    Icons.Filled.History,
                    stringResource(R.string.note_versions_empty)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 560.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        versions.forEach { version ->
                            ArchiveEntryRow(
                                icon = Icons.Filled.History,
                                title = version.displayTimestamp,
                                subtitle = null,
                                selected = version == selected,
                                onClick = { selected = version },
                                modifier = Modifier.testTag("note-version-${version.timestamp}")
                            )
                        }
                    }
                    ArchivePreview(selected?.content.orEmpty(), "note-version-preview")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onRestore) },
                enabled = restoreEnabled && selected != null,
                modifier = Modifier.testTag("restore-note-version")
            ) { ArchiveRestoreLabel(Icons.Filled.Restore) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun ArchiveRestoreLabel(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(stringResource(R.string.action_restore), modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun ArchivePreview(content: String, tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag(tag)
        )
    }
}

@Composable
private fun ArchiveEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveEmptyState(icon: ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
