package org.qownnotes.mobile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.TrashedNote
import org.qownnotes.mobile.core.findTextMatches
import org.qownnotes.mobile.core.textMatchSnippet

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
                DialogEmptyState(Icons.Filled.DeleteOutline, stringResource(R.string.trash_empty))
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
                        DialogEmptyState(
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
            ) { IconLabel(Icons.Filled.RestoreFromTrash, stringResource(R.string.action_restore)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

/** A note version together with every place the current search query occurs in it. */
private data class NoteVersionSearchResult(
    val version: RemoteNoteVersion,
    val matches: List<IntRange>
)

@Composable
internal fun NoteVersionsDialog(
    versions: List<RemoteNoteVersion>,
    restoreEnabled: Boolean,
    onDismiss: () -> Unit,
    onRestore: (RemoteNoteVersion) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchQuery = query.trim()
    val searching = searchQuery.isNotEmpty()
    val results = remember(versions, searchQuery) {
        versions.map { NoteVersionSearchResult(it, findTextMatches(it.content, searchQuery)) }
            .filter { !searching || it.matches.isNotEmpty() }
    }
    var chosen by remember(versions) { mutableStateOf(versions.firstOrNull()) }
    // Keep the chosen version while the search still finds it, otherwise show the first match.
    val selected = results.firstOrNull { it.version == chosen } ?: results.firstOrNull()
    // Remember the shown version, so clearing the search keeps the reader where they were.
    SideEffect { if (selected != null) chosen = selected.version }
    var currentMatch by remember(selected?.version, searchQuery) { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_versions)) },
        text = {
            if (versions.isEmpty()) {
                DialogEmptyState(
                    Icons.Filled.History,
                    stringResource(R.string.note_versions_empty)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 560.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.note_versions_search)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.testTag("clear-note-version-search")
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
                        modifier = Modifier.fillMaxWidth().testTag("note-version-search")
                    )
                    if (results.isEmpty()) {
                        DialogEmptyState(
                            Icons.Filled.SearchOff,
                            stringResource(R.string.note_versions_search_empty, searchQuery),
                            Modifier.testTag("note-version-search-empty")
                        )
                    } else {
                        if (searching) {
                            NoteVersionSearchSummary(
                                matchCount = results.sumOf { it.matches.size },
                                versionCount = results.size,
                                totalVersions = versions.size
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            results.forEach { result ->
                                val version = result.version
                                ArchiveEntryRow(
                                    icon = Icons.Filled.History,
                                    title = version.displayTimestamp,
                                    subtitle = null,
                                    selected = result == selected,
                                    onClick = { chosen = version },
                                    snippet = result.matches.firstOrNull()?.let {
                                        matchSnippet(version.content, it)
                                    },
                                    badge = if (searching) result.matches.size else null,
                                    modifier = Modifier.testTag("note-version-${version.timestamp}")
                                )
                            }
                        }
                        ArchivePreview(
                            content = selected?.version?.content.orEmpty(),
                            tag = "note-version-preview",
                            matches = selected?.matches.orEmpty(),
                            currentMatch = currentMatch,
                            onCurrentMatchChange = { currentMatch = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.version?.let(onRestore) },
                enabled = restoreEnabled && selected != null,
                modifier = Modifier.testTag("restore-note-version")
            ) { IconLabel(Icons.Filled.Restore, stringResource(R.string.action_restore)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun NoteVersionSearchSummary(matchCount: Int, versionCount: Int, totalVersions: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ManageSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            stringResource(
                R.string.note_versions_search_summary,
                pluralStringResource(
                    R.plurals.note_versions_search_matches,
                    matchCount,
                    matchCount
                ),
                pluralStringResource(
                    R.plurals.note_versions_search_versions,
                    totalVersions,
                    versionCount,
                    totalVersions
                )
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("note-version-search-summary")
        )
    }
}

@Composable
private fun matchSnippet(content: String, match: IntRange): AnnotatedString {
    val snippet = remember(content, match) { textMatchSnippet(content, match) }
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        fontWeight = FontWeight.SemiBold
    )
    return buildAnnotatedString {
        append(snippet.text)
        addStyle(highlight, snippet.match.first, snippet.match.last + 1)
    }
}

@Composable
private fun ArchivePreview(
    content: String,
    tag: String,
    matches: List<IntRange> = emptyList(),
    currentMatch: Int = -1,
    onCurrentMatchChange: (Int) -> Unit = {}
) {
    val matchStyle = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer
    )
    val currentStyle = SpanStyle(
        background = MaterialTheme.colorScheme.primary,
        color = MaterialTheme.colorScheme.onPrimary
    )
    val text = remember(content, matches, currentMatch, matchStyle, currentStyle) {
        buildAnnotatedString {
            append(content)
            matches.forEachIndexed { index, match ->
                addStyle(
                    if (index == currentMatch) currentStyle else matchStyle,
                    match.first,
                    match.last + 1
                )
            }
        }
    }
    val scrollState = remember(content) { ScrollState(0) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val padding = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(content, matches, currentMatch, layout) {
        val textLayout = layout?.takeIf { it.layoutInput.text.text == content }
            ?: return@LaunchedEffect
        val match = matches.getOrNull(currentMatch) ?: return@LaunchedEffect
        val line = textLayout.getLineForOffset(match.first)
        val lineCenter = (textLayout.getLineTop(line) + textLayout.getLineBottom(line)) / 2
        // Center the current match so the text around it stays readable.
        scrollState.animateScrollTo(
            (padding + lineCenter - scrollState.viewportSize / 2f).roundToInt().coerceAtLeast(0)
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (matches.isNotEmpty()) {
                PreviewMatchNavigator(
                    position = currentMatch.coerceIn(matches.indices),
                    count = matches.size,
                    onPositionChange = onCurrentMatchChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .testTag(tag)
            )
        }
    }
}

@Composable
private fun PreviewMatchNavigator(position: Int, count: Int, onPositionChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
    ) {
        Text(
            stringResource(R.string.find_match_position, position + 1, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag("note-version-match-position")
        )
        IconButton(
            onClick = { onPositionChange((position - 1 + count) % count) },
            enabled = count > 1,
            modifier = Modifier.testTag("note-version-previous-match")
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.find_previous_match)
            )
        }
        IconButton(
            onClick = { onPositionChange((position + 1) % count) },
            enabled = count > 1,
            modifier = Modifier.testTag("note-version-next-match")
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.find_next_match)
            )
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    snippet: AnnotatedString? = null,
    badge: Int? = null
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
                if (snippet != null) {
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (badge != null) {
                val description = pluralStringResource(
                    R.plurals.note_versions_search_matches,
                    badge,
                    badge
                )
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.semantics { contentDescription = description }
                ) {
                    Text(
                        badge.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.defaultMinSize(minWidth = 24.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clearAndSetSemantics { }
                    )
                }
            }
        }
    }
}
