package org.qownnotes.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.NoteFolderCandidates
import org.qownnotes.mobile.core.RemoteFolderListing
import org.qownnotes.mobile.core.RemoteNoteFolder

/** Note count, subfolders, and QOwnNotes desktop use of a folder, for one line below its name. */
@Composable
internal fun noteFolderSummary(folder: RemoteNoteFolder?): String {
    if (folder == null) return stringResource(R.string.note_folder_does_not_exist)
    val parts = buildList {
        add(
            if (folder.noteCount == 0) {
                stringResource(R.string.note_folder_no_notes)
            } else {
                pluralStringResource(
                    R.plurals.note_folder_notes,
                    folder.noteCount,
                    folder.noteCount
                )
            }
        )
        if (folder.subfolderCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.note_folder_subfolders,
                    folder.subfolderCount,
                    folder.subfolderCount
                )
            )
        }
        if (folder.hasTagDatabase) add(stringResource(R.string.note_folder_desktop))
    }
    return parts.joinToString(stringResource(R.string.note_folder_summary_separator))
}

/**
 * A folder as a selectable card: its name, its full path when nested, and what it holds. QOwnNotes
 * desktop folders get their own icon, because they are the strongest hint where the notes are.
 */
@Composable
private fun NoteFolderOption(
    path: String,
    folder: RemoteNoteFolder?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val secondaryText = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                when {
                    folder == null -> Icons.Outlined.FolderOff
                    folder.hasTagDatabase -> Icons.Filled.FolderSpecial
                    else -> Icons.Filled.Folder
                },
                contentDescription = null,
                tint = colors.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if ('/' in path) {
                    Text(
                        path,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                }
                Text(
                    noteFolderSummary(folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Other folders on the server that already hold notes, offered next to the typed folder. Tapping
 * one only fills in the folder; nothing changes until the settings are saved.
 */
@Composable
internal fun NoteFolderSuggestions(
    candidates: List<RemoteNoteFolder>,
    selectedPath: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    if (candidates.isEmpty()) return
    val selected = NoteFolderCandidates.normalize(selectedPath)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("note-folder-suggestions")
    ) {
        Text(
            stringResource(R.string.note_folder_suggestions_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        candidates.forEach { candidate ->
            val description = stringResource(R.string.note_folder_suggestion_use, candidate.path)
            NoteFolderOption(
                path = candidate.path,
                folder = candidate,
                selected = candidate.path == selected,
                enabled = enabled,
                onClick = { onSelect(candidate.path) },
                modifier = Modifier
                    .semantics { contentDescription = description }
                    .testTag("note-folder-suggestion-${candidate.path}")
            )
        }
    }
}

/**
 * Browses the account's files so the note folder can be chosen instead of typed. Opens at
 * [initialPath], or at the nearest folder above it that exists.
 */
@Composable
internal fun NoteFolderPickerDialog(
    component: ApplicationComponent,
    accountId: String,
    initialPath: String,
    fileSuffix: String,
    onSelect: (String, RemoteNoteFolder) -> Unit,
    onDismiss: () -> Unit
) {
    var path by rememberSaveable(accountId) {
        mutableStateOf(NoteFolderCandidates.normalize(initialPath))
    }
    var listing by remember { mutableStateOf<RemoteFolderListing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    val loadFailed = stringResource(R.string.note_folder_picker_load_failed)

    LaunchedEffect(path, request) {
        listing = null
        error = null
        try {
            val loaded = withContext(UiDispatcher) {
                component.browseNoteFolder(accountId, path, fileSuffix)
            }
            if (loaded == null && path.isNotEmpty()) {
                // A typed folder that does not exist yet: show where it would be created.
                missing = true
                path = NoteFolderCandidates.parent(path)
            } else {
                listing =
                    loaded ?: RemoteFolderListing(RemoteNoteFolder("", 0, 0, false), emptyList())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: loadFailed
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
        title = { Text(stringResource(R.string.note_folder_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NoteFolderBreadcrumb(
                    path = path,
                    onUp = {
                        missing = false
                        path = NoteFolderCandidates.parent(path)
                    }
                )
                if (missing) {
                    DialogNotice(stringResource(R.string.note_folder_picker_missing))
                }
                val loaded = listing
                when {
                    error != null -> DialogErrorPanel(
                        message = error.orEmpty(),
                        messageTag = "note-folder-picker-error",
                        onRetry = { request++ },
                        retryTag = "note-folder-picker-retry"
                    )
                    loaded == null -> DialogLoadingIndicator("note-folder-picker-loading")
                    else -> {
                        if (path.isEmpty()) {
                            Text(
                                stringResource(R.string.note_folder_picker_root_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (loaded.folder.hasTagDatabase) {
                                        Icons.Filled.FolderSpecial
                                    } else {
                                        Icons.Filled.Folder
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    noteFolderSummary(loaded.folder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("note-folder-picker-summary")
                                )
                            }
                        }
                        NoteFolderList(
                            subfolders = loaded.subfolders,
                            onOpen = { name ->
                                missing = false
                                path = NoteFolderCandidates.child(path, name)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { listing?.let { onSelect(path, it.folder) } },
                enabled = path.isNotEmpty() && listing != null,
                modifier = Modifier.testTag("note-folder-picker-select")
            ) { Text(stringResource(R.string.note_folder_picker_select)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        modifier = Modifier.testTag("note-folder-picker")
    )
}

/** The open folder as a path from the files root, with a button that goes up one level. */
@Composable
private fun NoteFolderBreadcrumb(path: String, onUp: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onUp,
                enabled = path.isNotEmpty(),
                modifier = Modifier.testTag("note-folder-picker-up")
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.note_folder_picker_up)
                )
            }
            val root = stringResource(R.string.note_folder_picker_root)
            val segments = listOf(root) + path.split('/').filter(String::isNotEmpty)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                    .padding(end = 12.dp)
                    .testTag("note-folder-picker-path")
            ) {
                segments.forEachIndexed { index, segment ->
                    if (index > 0) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        segment,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == segments.lastIndex) FontWeight.SemiBold else null,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteFolderList(subfolders: List<String>, onOpen: (String) -> Unit) {
    if (subfolders.isEmpty()) {
        DialogEmptyState(
            Icons.Outlined.FolderOff,
            stringResource(R.string.note_folder_picker_no_subfolders),
            Modifier.testTag("note-folder-picker-empty")
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
    ) {
        items(subfolders, key = { it }) { name ->
            val description = stringResource(R.string.note_folder_picker_open, name)
            Surface(
                onClick = { onOpen(name) },
                shape = MaterialTheme.shapes.small,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description }
                    .testTag("note-folder-picker-folder-$name")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Asks once, after a Nextcloud account was added, whether its note folder is the right one, when
 * that folder is empty or other folders already hold notes. Keeping the folder is the default;
 * another folder is only used after the user chooses it.
 */
@Composable
internal fun NoteFolderReviewDialog(
    component: ApplicationComponent,
    review: NoteFolderReview,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope { UiDispatcher }
    val currentPath = review.discovery.currentPath
    var selectedPath by remember(review.accountId) { mutableStateOf(currentPath) }
    var picked by remember(review.accountId) { mutableStateOf<RemoteNoteFolder?>(null) }
    var picking by rememberSaveable(review.accountId) { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val switchFailed = stringResource(R.string.note_folder_switch_failed)
    val candidates = review.discovery.candidates
    val currentEmpty = review.discovery.current?.isEmpty ?: true

    AlertDialog(
        onDismissRequest = { if (!switching) onDismiss() },
        icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
        title = { Text(stringResource(R.string.note_folder_review_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.note_folder_review_current),
                    style = MaterialTheme.typography.bodyMedium
                )
                NoteFolderOption(
                    path = currentPath,
                    folder = review.discovery.current,
                    selected = selectedPath == currentPath,
                    enabled = !switching,
                    onClick = { selectedPath = currentPath },
                    modifier = Modifier.testTag("note-folder-review-current")
                )
                if (candidates.isNotEmpty() || picked != null) {
                    if (candidates.isNotEmpty()) {
                        Text(
                            stringResource(R.string.note_folder_review_candidates),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    candidates.forEach { candidate ->
                        NoteFolderOption(
                            path = candidate.path,
                            folder = candidate,
                            selected = selectedPath == candidate.path,
                            enabled = !switching,
                            onClick = { selectedPath = candidate.path },
                            modifier = Modifier.testTag("note-folder-review-${candidate.path}")
                        )
                    }
                    picked?.takeIf { folder ->
                        folder.path != currentPath && candidates.none { it.path == folder.path }
                    }?.let { folder ->
                        NoteFolderOption(
                            path = folder.path,
                            folder = folder,
                            selected = selectedPath == folder.path,
                            enabled = !switching,
                            onClick = { selectedPath = folder.path },
                            modifier = Modifier.testTag("note-folder-review-picked")
                        )
                    }
                } else if (currentEmpty) {
                    Text(
                        stringResource(R.string.note_folder_review_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = { picking = true },
                    enabled = !switching,
                    modifier = Modifier.fillMaxWidth().testTag("note-folder-review-browse")
                ) {
                    IconLabel(
                        Icons.Filled.FolderOpen,
                        stringResource(R.string.note_folder_review_choose_other)
                    )
                }
                if (selectedPath != currentPath) {
                    DialogNotice(stringResource(R.string.note_settings_folder_warning))
                }
                if (switching) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            stringResource(R.string.note_folder_review_switching, selectedPath),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                error?.let {
                    DialogErrorPanel(message = it, messageTag = "note-folder-review-error")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedPath == currentPath) {
                        onDismiss()
                        return@TextButton
                    }
                    switching = true
                    error = null
                    scope.launch {
                        try {
                            component.updateNoteSettings(
                                review.accountId,
                                review.settings,
                                selectedPath,
                                review.settings.fileSuffix
                            )
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: switchFailed
                            switching = false
                        }
                    }
                },
                enabled = !switching,
                modifier = Modifier.testTag("note-folder-review-confirm")
            ) {
                Text(
                    if (selectedPath == currentPath) {
                        stringResource(R.string.note_folder_review_keep)
                    } else {
                        stringResource(R.string.note_folder_picker_select)
                    }
                )
            }
        },
        modifier = Modifier.testTag("note-folder-review")
    )

    if (picking) {
        NoteFolderPickerDialog(
            component = component,
            accountId = review.accountId,
            initialPath = selectedPath,
            fileSuffix = review.settings.fileSuffix,
            onSelect = { path, folder ->
                picking = false
                picked = folder
                selectedPath = path
            },
            onDismiss = { picking = false }
        )
    }
}
