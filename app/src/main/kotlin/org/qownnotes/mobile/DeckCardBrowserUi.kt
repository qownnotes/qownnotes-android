package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCard
import org.qownnotes.mobile.core.DeckCardDetails
import org.qownnotes.mobile.core.DeckCardLink
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.NextcloudDeck

/** On-demand browser. Changing lists cancels the previous request and never mixes results. */
@Composable
internal fun DeckCardBrowserDialog(
    component: ApplicationComponent,
    account: Account,
    onDismiss: () -> Unit,
    onInsert: ((String) -> Unit)? = null
) {
    val resources = LocalResources.current
    var boards by remember(account.id) { mutableStateOf<List<DeckBoard>?>(null) }
    var boardError by remember { mutableStateOf<String?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }
    var boardsRequest by remember { mutableIntStateOf(0) }
    var cardsRequest by remember { mutableIntStateOf(0) }
    var cards by remember { mutableStateOf<List<DeckCardDetails>?>(null) }
    var boardId by rememberSaveable(account.id) { mutableStateOf<Long?>(null) }
    var stackId by rememberSaveable(account.id) { mutableStateOf<Long?>(null) }
    var query by rememberSaveable(account.id) { mutableStateOf("") }
    var includeArchived by rememberSaveable(account.id) { mutableStateOf(false) }
    var editingCardId by rememberSaveable(account.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(account.id, boardsRequest) {
        boardError = null
        try {
            val loaded = component.deckBrowseBoards(account.id)
            val targets = loaded.flatMap { board ->
                board.stacks.map { DeckStackTarget(board.id, it.id) }
            }
            if (DeckStackTarget(boardId ?: 0, stackId ?: 0) !in targets) {
                val remembered = component.settings.nextcloudDeckTarget(account.id)
                val selected = targets.firstOrNull { it == remembered } ?: targets.firstOrNull()
                boardId = selected?.boardId
                stackId = selected?.stackId
            }
            boards = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            boardError = deckErrorMessage(error, resources)
        }
    }
    LaunchedEffect(account.id, boardId, stackId, includeArchived, cardsRequest, boardsRequest) {
        cards = null
        cardError = null
        val target = DeckStackTarget(
            boardId ?: return@LaunchedEffect,
            stackId ?: return@LaunchedEffect
        )
        try {
            cards = component.deckCards(account.id, target, includeArchived)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            cardError = deckErrorMessage(error, resources)
        }
    }
    val board = boards.orEmpty().firstOrNull { it.id == boardId }
    val stack = board?.stacks?.firstOrNull { it.id == stackId }
    val filtered = cards.orEmpty().filter {
        query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) ||
            it.description.contains(query.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.deck_cards), modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { boardsRequest++ },
                    modifier = Modifier.testTag("deck-browser-refresh")
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.deck_refresh)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    boardError != null -> DialogErrorPanel(
                        message = boardError.orEmpty(),
                        messageTag = "deck-browser-boards-error",
                        onRetry = { boardsRequest++ },
                        retryTag = "deck-browser-retry-boards"
                    )
                    boards == null -> DialogLoadingIndicator()
                    stack == null -> DeckEmptyState(stringResource(R.string.deck_no_lists))
                    else -> {
                        DeckTargetPicker(
                            boards = boards.orEmpty(),
                            boardId = boardId,
                            stackId = stackId,
                            fieldTag = "deck-browser-target",
                            optionTagPrefix = "deck-browser-target",
                            onSelect = { target ->
                                boardId = target.boardId
                                stackId = target.stackId
                                component.settings.setNextcloudDeckTarget(account.id, target)
                            }
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.deck_search_cards)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = stringResource(
                                                R.string.ui_clear_search
                                            )
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.fillMaxWidth().testTag("deck-browser-search")
                        )
                        FilterChip(
                            selected = includeArchived,
                            onClick = { includeArchived = !includeArchived },
                            label = { Text(stringResource(R.string.deck_show_archived)) },
                            leadingIcon = {
                                Icon(
                                    if (includeArchived) {
                                        Icons.Filled.Check
                                    } else {
                                        Icons.Filled.Archive
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            modifier = Modifier.testTag("deck-browser-show-archived")
                        )
                        when {
                            cardError != null -> DialogErrorPanel(
                                message = cardError.orEmpty(),
                                messageTag = "deck-browser-cards-error",
                                onRetry = { cardsRequest++ }
                            )
                            cards == null -> DialogLoadingIndicator("deck-browser-loading")
                            filtered.isEmpty() -> DeckEmptyState(
                                stringResource(R.string.deck_no_cards),
                                Modifier.testTag("deck-browser-empty")
                            )
                            else -> LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                            ) {
                                items(filtered, key = DeckCardDetails::id) { card ->
                                    DeckCardListItem(
                                        card = card,
                                        onOpen = { editingCardId = card.id },
                                        onInsert = onInsert?.let { insert ->
                                            {
                                                insert(
                                                    NextcloudDeck.cardMarkdownLink(
                                                        account.serverUrl,
                                                        DeckCard(
                                                            card.id,
                                                            card.boardId,
                                                            card.stackId,
                                                            card.title
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("deck-browser-close")) {
                Text(stringResource(R.string.action_close))
            }
        },
        modifier = Modifier.testTag("deck-browser-dialog")
    )
    editingCardId?.let { cardId ->
        val currentBoardId = boardId ?: return@let
        DeckCardEditorDialog(
            component,
            account,
            DeckCardLink(currentBoardId, cardId),
            onDismiss = { editingCardId = null },
            onSaved = {
                editingCardId = null
                cardsRequest++
            }
        )
    }
}

/** A board and list selector that groups each board's lists in its dropdown. */
@Composable
internal fun DeckTargetPicker(
    boards: List<DeckBoard>,
    boardId: Long?,
    stackId: Long?,
    fieldTag: String,
    optionTagPrefix: String,
    onSelect: (DeckStackTarget) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val board = boards.firstOrNull { it.id == boardId }
    val stack = board?.stacks?.firstOrNull { it.id == stackId }
    Box {
        OutlinedCard(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(fieldTag)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        board?.title ?: stringResource(R.string.deck_board_and_list),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stack?.title ?: stringResource(R.string.deck_choose_list),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            boards.filter { it.stacks.isNotEmpty() }.forEachIndexed { index, optionBoard ->
                if (index > 0) HorizontalDivider()
                Text(
                    optionBoard.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 4.dp
                    )
                )
                optionBoard.stacks.forEach { optionStack ->
                    val chosen = optionBoard.id == boardId && optionStack.id == stackId
                    DropdownMenuItem(
                        text = { Text(optionStack.title) },
                        trailingIcon = if (chosen) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSelect(DeckStackTarget(optionBoard.id, optionStack.id))
                        },
                        modifier = Modifier.testTag(
                            "$optionTagPrefix-${optionBoard.id}-${optionStack.id}"
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckCardListItem(card: DeckCardDetails, onOpen: () -> Unit, onInsert: (() -> Unit)?) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        modifier = Modifier.fillMaxWidth().testTag("deck-browser-card-${card.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 16.dp,
                end = if (onInsert == null) 16.dp else 4.dp,
                top = 12.dp,
                bottom = 12.dp
            )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (card.archived) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = card.description.lineSequence().map(String::trim)
                    .filter(String::isNotEmpty).joinToString(" ")
                if (preview.isNotEmpty()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (card.dueAtEpochSeconds != null || card.archived) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        card.dueAtEpochSeconds?.let { dueAt ->
                            val overdue = !card.archived && dueAt < Instant.now().epochSecond
                            val date = Instant.ofEpochSecond(dueAt)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                            DeckBadge(
                                icon = Icons.Filled.Schedule,
                                text = if (overdue) {
                                    "${stringResource(R.string.deck_overdue)} · $date"
                                } else {
                                    date
                                },
                                container = if (overdue) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                content = if (overdue) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                        if (card.archived) {
                            DeckBadge(
                                icon = Icons.Filled.Archive,
                                text = stringResource(R.string.deck_archived),
                                container = MaterialTheme.colorScheme.surfaceVariant,
                                content = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (onInsert != null) {
                IconButton(
                    onClick = onInsert,
                    modifier = Modifier.testTag("deck-browser-insert-${card.id}")
                ) {
                    Icon(
                        Icons.Filled.AddLink,
                        contentDescription = stringResource(R.string.deck_insert_existing_link),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeckBadge(icon: ImageVector, text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun DeckEmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Icon(
            Icons.Filled.ViewKanban,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }
}
