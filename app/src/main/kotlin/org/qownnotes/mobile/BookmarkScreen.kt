package org.qownnotes.mobile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("bookmarks-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                contentDescription = "Open bookmarks note"
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("bookmarks-search"),
                placeholder = { Text("Search bookmarks") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )
            if (availableTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).testTag("bookmarks-filters"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
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
        sourceState is BookmarkSourceState.Loading -> BookmarkMessage("Loading bookmarks...")
        sourceState is BookmarkSourceState.InvalidPath -> BookmarkMessage(
            "The bookmarks path is invalid. Choose a relative Markdown path in Settings."
        )
        sourceState is BookmarkSourceState.Missing -> BookmarkMessage(
            "The bookmarks source note was not found."
        )
        encrypted -> BookmarkMessage("Encrypted bookmark notes cannot be displayed.")
        bookmarks.isEmpty() -> BookmarkMessage("No bookmarks were found in this note.")
        visibleBookmarks.isEmpty() && hasFilters -> BookmarkMessage("No matching bookmarks.")
        else -> {
            val context = LocalContext.current
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("bookmarks-list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleBookmarks, key = Bookmark::url) { bookmark ->
                    BookmarkRow(bookmark, onOpen = { openSafeExternalUrl(context, bookmark.url) })
                }
            }
        }
    }
}

@Composable
private fun BookmarkMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("bookmarks-message"),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit) {
    val safe = isSafeExternalUrl(bookmark.url)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = safe, onClick = onOpen)
            .padding(vertical = 10.dp)
            .testTag("bookmark-row")
    ) {
        Text(
            bookmark.name.ifBlank { bookmark.url },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            bookmark.url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (bookmark.description.isNotBlank()) {
            Text(
                bookmark.description,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (bookmark.tags.isNotEmpty()) {
            Text(
                bookmark.tags.joinToString(" ") { "#$it" },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
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
