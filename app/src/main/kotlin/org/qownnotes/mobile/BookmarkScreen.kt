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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.qownnotes.mobile.core.Bookmark
import org.qownnotes.mobile.core.BookmarksSource
import org.qownnotes.mobile.core.Note
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
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("bookmarks-page"),
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
                hasFilters = query.isNotBlank() || selectedTags.isNotEmpty()
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
    hasFilters: Boolean
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
                    BookmarkRow(bookmark, onOpen = { openSafeExternalUrl(context, bookmark.url) })
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
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit) {
    val safe = isSafeExternalUrl(bookmark.url)
    Card(
        onClick = onOpen,
        enabled = safe,
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
        }
    }
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
