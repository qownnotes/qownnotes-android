package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
    var choosingTarget by remember { mutableStateOf(false) }
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
        title = { Text(stringResource(R.string.deck_cards)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    boardError != null -> Text(
                        boardError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("deck-browser-boards-error")
                    )
                    boards == null -> CircularProgressIndicator()
                    stack == null -> Text(stringResource(R.string.deck_no_lists))
                    else -> {
                        Box {
                            OutlinedButton(
                                onClick = { choosingTarget = true },
                                modifier = Modifier.fillMaxWidth().testTag("deck-browser-target")
                            ) {
                                Text("${board.title} / ${stack.title}")
                            }
                            DropdownMenu(
                                expanded = choosingTarget,
                                onDismissRequest = { choosingTarget = false }
                            ) {
                                boards.orEmpty().forEach { optionBoard ->
                                    optionBoard.stacks.forEach { optionStack ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${optionBoard.title} / ${optionStack.title}")
                                            },
                                            onClick = {
                                                boardId = optionBoard.id
                                                stackId = optionStack.id
                                                component.settings.setNextcloudDeckTarget(
                                                    account.id,
                                                    DeckStackTarget(optionBoard.id, optionStack.id)
                                                )
                                                choosingTarget = false
                                            },
                                            modifier = Modifier.testTag(
                                                "deck-browser-target-${optionBoard.id}-${optionStack.id}"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.deck_search_cards)) },
                            modifier = Modifier.fillMaxWidth().testTag("deck-browser-search")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = includeArchived,
                                role = Role.Checkbox,
                                onValueChange = { includeArchived = it }
                            ).testTag("deck-browser-show-archived")
                        ) {
                            Checkbox(checked = includeArchived, onCheckedChange = null)
                            Text(stringResource(R.string.deck_show_archived))
                        }
                        when {
                            cardError != null -> {
                                Text(
                                    cardError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("deck-browser-cards-error")
                                )
                                TextButton(onClick = { cardsRequest++ }) {
                                    Text(stringResource(R.string.ui_retry))
                                }
                            }
                            cards == null -> CircularProgressIndicator(
                                modifier = Modifier.testTag("deck-browser-loading")
                            )
                            filtered.isEmpty() -> Text(
                                stringResource(R.string.deck_no_cards),
                                modifier = Modifier.testTag("deck-browser-empty")
                            )
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                            ) {
                                items(filtered, key = DeckCardDetails::id) { card ->
                                    TextButton(
                                        onClick = { editingCardId = card.id },
                                        modifier = Modifier.fillMaxWidth().testTag(
                                            "deck-browser-card-${card.id}"
                                        )
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(card.title)
                                            if (card.archived) {
                                                Text(
                                                    stringResource(R.string.deck_archived)
                                                )
                                            }
                                            card.dueAtEpochSeconds?.let { dueAt ->
                                                val overdue = dueAt < Instant.now().epochSecond
                                                Text(
                                                    Instant.ofEpochSecond(
                                                        dueAt
                                                    ).atZone(ZoneId.systemDefault())
                                                        .format(
                                                            DateTimeFormatter.ofLocalizedDateTime(
                                                                FormatStyle.SHORT
                                                            )
                                                        ),
                                                    color = if (overdue) {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                if (overdue) {
                                                    Text(
                                                        stringResource(R.string.deck_overdue),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (onInsert != null) {
                                        TextButton(
                                            onClick = {
                                                onInsert(
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
                                            },
                                            modifier = Modifier.testTag(
                                                "deck-browser-insert-${card.id}"
                                            )
                                        ) {
                                            Text(stringResource(R.string.deck_insert_existing_link))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { boardsRequest++ },
                    modifier = Modifier.testTag("deck-browser-refresh")
                ) {
                    Text(stringResource(R.string.deck_refresh))
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
