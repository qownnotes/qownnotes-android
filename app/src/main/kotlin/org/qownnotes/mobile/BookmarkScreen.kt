package org.qownnotes.mobile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.qownnotes.mobile.core.Bookmark
import org.qownnotes.mobile.core.BookmarksSource
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.core.filterBookmarks
import org.qownnotes.mobile.core.isSafeExternalUrl
import org.qownnotes.mobile.core.parseBookmarks
import org.qownnotes.mobile.core.parseBookmarksSource

private sealed interface BookmarkSourceState {
    data object Loading : BookmarkSourceState

    data class InvalidPath(val path: String) : BookmarkSourceState

    data class Missing(val source: BookmarksSource) : BookmarkSourceState

    data class Found(val note: Note) : BookmarkSourceState
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    component: ApplicationComponent,
    accountId: String,
    onBack: () -> Unit,
    onOpenSourceNote: (String) -> Unit
) {
    val sourceStateFlow = remember(component, accountId) {
        component.settings.bookmarksPath(accountId).flatMapLatest { path ->
            val source = parseBookmarksSource(path)
            if (source == null) {
                flowOf<BookmarkSourceState>(BookmarkSourceState.InvalidPath(path))
            } else {
                component.noteRepository.observeNoteAt(accountId, source.category, source.title)
                    .map<Note?, BookmarkSourceState> { note ->
                        note?.let(BookmarkSourceState::Found) ?: BookmarkSourceState.Missing(source)
                    }
            }
        }
    }
    val sourceState by sourceStateFlow.collectAsStateWithLifecycle(
        initialValue = BookmarkSourceState.Loading,
        context = UiDispatcher
    )
    var query by rememberSaveable(accountId) { mutableStateOf("") }
    var selectedTags by rememberSaveable(accountId) { mutableStateOf(emptyList<String>()) }
    val sourceNote = (sourceState as? BookmarkSourceState.Found)?.note
    val encrypted = sourceNote?.let { component.markdownRenderer.hasEncryptedContent(it.content) }
        ?: false
    val bookmarks = remember(sourceNote?.content, encrypted) {
        if (sourceNote == null || encrypted) emptyList() else parseBookmarks(sourceNote.content)
    }
    val availableTags = remember(bookmarks, selectedTags) {
        (bookmarks.flatMap(Bookmark::tags) + selectedTags).distinct().sorted()
    }
    val visibleBookmarks = remember(bookmarks, query, selectedTags) {
        filterBookmarks(bookmarks, query, selectedTags.toSet())
    }
    var pendingDeletion by remember(accountId) { mutableStateOf<Pair<Note, Bookmark>?>(null) }
    var pendingEdit by remember(accountId) { mutableStateOf<Pair<Note, Bookmark>?>(null) }
    var editFailed by remember(accountId) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deleteError = stringResource(R.string.bookmarks_delete_failed)
    val deleteSuccess = stringResource(R.string.bookmarks_deleted)
    val editSuccess = stringResource(R.string.bookmarks_edited)
    val canDelete = sourceNote != null && !sourceNote.readOnly && !encrypted &&
        sourceNote.syncState in setOf(
            SyncState.SYNCHRONIZED,
            SyncState.LOCALLY_CREATED,
            SyncState.LOCALLY_MODIFIED
        )
    pendingEdit?.let { (note, bookmark) ->
        BookmarkEditDialog(
            bookmark = bookmark,
            sourceContent = note.content,
            saving = deleting,
            saveFailed = editFailed,
            onDismiss = { pendingEdit = null },
            onSave = { replacement ->
                deleting = true
                scope.launch {
                    val saved = try {
                        component.editBookmark(
                            note.localId,
                            note.content,
                            bookmark.url,
                            replacement
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    } finally {
                        deleting = false
                    }
                    editFailed = !saved
                    if (saved) {
                        pendingEdit = null
                        snackbar.showSnackbar(editSuccess)
                    }
                }
            }
        )
    }
    pendingDeletion?.let { (note, bookmark) ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDeletion = null },
            icon = {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.bookmarks_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.bookmarks_delete_message))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                bookmark.name.ifBlank { bookmark.url },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                bookmark.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    modifier = Modifier.testTag("bookmark-delete-confirm"),
                    onClick = {
                        deleting = true
                        scope.launch {
                            val saved = try {
                                component.deleteBookmark(note.localId, note.content, bookmark.url)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                false
                            } finally {
                                deleting = false
                                pendingDeletion = null
                            }
                            snackbar.showSnackbar(if (saved) deleteSuccess else deleteError)
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.bookmarks_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("bookmarks-page"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("bookmarks-back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ui_back)
                        )
                    }
                },
                actions = {
                    if (sourceNote != null) {
                        IconButton(
                            onClick = { onOpenSourceNote(sourceNote.localId) },
                            modifier = Modifier.testTag("bookmarks-open-source")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Article,
                                contentDescription = stringResource(R.string.bookmarks_open_source)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    .testTag("bookmarks-search"),
                shape = MaterialTheme.shapes.extraLarge,
                placeholder = { Text(stringResource(R.string.bookmarks_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.ui_clear_search)
                            )
                        }
                    }
                },
                singleLine = true
            )
            if (availableTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                        .testTag("bookmarks-filters"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableTags.forEach { tag ->
                        val tagSelected = tag in selectedTags
                        FilterChip(
                            selected = tagSelected,
                            leadingIcon = if (tagSelected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                selectedTags = if (tag in selectedTags) {
                                    selectedTags - tag
                                } else {
                                    selectedTags + tag
                                }
                            },
                            label = { Text("#$tag") },
                            modifier = Modifier.testTag("bookmarks-filter-$tag")
                        )
                    }
                }
            }
            BookmarkContent(
                sourceState = sourceState,
                encrypted = encrypted,
                bookmarks = bookmarks,
                visibleBookmarks = visibleBookmarks,
                hasFilters = query.isNotBlank() || selectedTags.isNotEmpty(),
                onEdit = if (canDelete) {
                    { bookmark ->
                        editFailed = false
                        pendingEdit = sourceNote to bookmark
                    }
                } else {
                    null
                },
                onDelete = if (canDelete) {
                    { bookmark -> pendingDeletion = sourceNote to bookmark }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun BookmarkContent(
    sourceState: BookmarkSourceState,
    encrypted: Boolean,
    bookmarks: List<Bookmark>,
    visibleBookmarks: List<Bookmark>,
    hasFilters: Boolean,
    onEdit: ((Bookmark) -> Unit)?,
    onDelete: ((Bookmark) -> Unit)?
) {
    when {
        sourceState is BookmarkSourceState.Loading -> BookmarkMessage(
            stringResource(R.string.bookmarks_loading),
            icon = null
        )
        sourceState is BookmarkSourceState.InvalidPath -> BookmarkMessage(
            stringResource(R.string.bookmarks_invalid_path),
            Icons.Filled.ErrorOutline
        )
        sourceState is BookmarkSourceState.Missing -> BookmarkMessage(
            stringResource(R.string.bookmarks_source_missing),
            Icons.AutoMirrored.Filled.Article
        )
        encrypted -> BookmarkMessage(
            stringResource(R.string.bookmarks_encrypted),
            Icons.Filled.Lock
        )
        bookmarks.isEmpty() -> BookmarkMessage(
            stringResource(R.string.bookmarks_none),
            Icons.Filled.BookmarkBorder
        )
        visibleBookmarks.isEmpty() && hasFilters -> BookmarkMessage(
            stringResource(R.string.bookmarks_no_matches),
            Icons.Filled.SearchOff
        )
        else -> {
            val context = LocalContext.current
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("bookmarks-list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleBookmarks, key = Bookmark::url) { bookmark ->
                    BookmarkRow(
                        bookmark,
                        onOpen = { openSafeExternalUrl(context, bookmark.url) },
                        onEdit = onEdit?.let { { it(bookmark) } },
                        onDelete = onDelete?.let { { it(bookmark) } }
                    )
                }
            }
        }
    }
}

/** A centered empty, error, or loading state. A null [icon] shows a progress indicator. */
@Composable
private fun BookmarkMessage(message: String, icon: ImageVector?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        if (icon == null) {
            CircularProgressIndicator()
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("bookmarks-message")
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val safe = isSafeExternalUrl(bookmark.url)
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().testTag("bookmark-row")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (safe) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (safe) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (safe) Icons.Filled.Public else Icons.Filled.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    bookmark.name.ifBlank { bookmark.url },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (bookmark.name.isNotBlank()) {
                    Text(
                        bookmark.url.substringAfter("://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (safe) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (bookmark.description.isNotBlank()) {
                    Text(
                        bookmark.description,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (bookmark.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        bookmark.tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Column {
                if (onEdit != null) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("bookmark-edit-${bookmark.url}")
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(
                                R.string.bookmarks_edit_named,
                                bookmark.name.ifBlank { bookmark.url }
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("bookmark-delete-${bookmark.url}")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(
                                R.string.bookmarks_delete_named,
                                bookmark.name.ifBlank { bookmark.url }
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkEditDialog(
    bookmark: Bookmark,
    sourceContent: String,
    saving: Boolean,
    saveFailed: Boolean,
    onDismiss: () -> Unit,
    onSave: (Bookmark) -> Unit
) {
    var name by rememberSaveable(bookmark.url) { mutableStateOf(bookmark.name) }
    var url by rememberSaveable(bookmark.url) { mutableStateOf(bookmark.url) }
    var tags by rememberSaveable(bookmark.url) {
        mutableStateOf(bookmark.tags.joinToString(" ") { "#$it" })
    }
    var description by rememberSaveable(bookmark.url) { mutableStateOf(bookmark.description) }
    val replacement = Bookmark(
        url = url.trim(),
        name = name.trim(),
        tags = tags.split(Regex("\\s+")).map {
            it.removePrefix("#")
        }.filter { it.isNotEmpty() }.distinct(),
        description = description.trim()
    )
    val updated = org.qownnotes.mobile.core.editBookmark(sourceContent, bookmark.url, replacement)
    val focusManager = LocalFocusManager.current
    val nextField = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val canSave = !saving && updated != null && updated != sourceContent
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = null)
                }
            }
        },
        title = { Text(stringResource(R.string.bookmarks_edit_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                bookmark.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                bookmark.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.bookmarks_edit_name)) },
                        leadingIcon = { Icon(Icons.Filled.Title, contentDescription = null) },
                        isError = name.isBlank(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = nextField,
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("bookmark-edit-name")
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.bookmarks_edit_url)) },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = nextField,
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("bookmark-edit-url")
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text(stringResource(R.string.bookmarks_edit_tags)) },
                        leadingIcon = { Icon(Icons.Filled.Sell, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = nextField,
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("bookmark-edit-tags")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.bookmarks_edit_description)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("bookmark-edit-description")
                    )
                }
                if (updated == null) {
                    DialogErrorPanel(
                        message = stringResource(R.string.bookmarks_edit_invalid),
                        messageTag = "bookmark-edit-validation"
                    )
                }
                if (saveFailed) {
                    DialogErrorPanel(
                        message = stringResource(R.string.bookmarks_edit_failed),
                        messageTag = "bookmark-edit-error"
                    )
                }
                DialogNotice(stringResource(R.string.bookmarks_edit_message))
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(replacement) },
                modifier = Modifier.testTag("bookmark-edit-save")
            ) {
                if (saving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(stringResource(R.string.bookmarks_edit_save))
                    }
                } else {
                    IconLabel(Icons.Filled.Save, stringResource(R.string.bookmarks_edit_save))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(R.string.ui_cancel))
            }
        }
    )
}

internal fun openSafeExternalUrl(context: Context, url: String): Boolean {
    if (!isSafeExternalUrl(url)) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
