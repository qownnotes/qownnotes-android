package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.NextcloudDeck

private fun List<DeckBoard>.targets(): List<DeckStackTarget> = flatMap { board ->
    board.stacks.map { DeckStackTarget(board.id, it.id) }
}

/**
 * Creates a Nextcloud Deck card in a chosen board list and hands its Markdown link to
 * [onCreated]. The dialog stays open with its input when loading or creating fails.
 */
@Composable
internal fun NextcloudDeckCardDialog(
    component: ApplicationComponent,
    accountId: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onCreated: (markdownLink: String) -> Unit
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope { UiDispatcher }
    var boards by remember(accountId) { mutableStateOf<List<DeckBoard>?>(null) }
    var loadError by remember(accountId) { mutableStateOf<String?>(null) }
    var loadRequest by remember(accountId) { mutableIntStateOf(0) }
    var boardId by rememberSaveable(accountId) { mutableStateOf<Long?>(null) }
    var stackId by rememberSaveable(accountId) { mutableStateOf<Long?>(null) }
    var title by rememberSaveable(accountId) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(accountId) { mutableStateOf("") }
    var dueAt by rememberSaveable(accountId) { mutableStateOf<Long?>(null) }
    var creating by remember(accountId) { mutableStateOf(false) }
    var createError by remember(accountId) { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId, loadRequest) {
        loadError = null
        try {
            val loaded = withContext(UiDispatcher) { component.deckBoards(accountId) }
            val targets = loaded.targets()
            if (DeckStackTarget(boardId ?: 0, stackId ?: 0) !in targets) {
                val remembered = component.settings.nextcloudDeckTarget(accountId)
                val selected = targets.firstOrNull { it == remembered } ?: targets.firstOrNull()
                boardId = selected?.boardId
                stackId = selected?.stackId
            }
            boards = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.message ?: resources.getString(R.string.deck_load_boards_failed)
        }
    }

    val selected = remember(boards, boardId, stackId) {
        DeckStackTarget(boardId ?: 0, stackId ?: 0).takeIf { it in boards.orEmpty().targets() }
    }
    val titleValid = NextcloudDeck.isValidCardTitle(title)

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text(stringResource(R.string.deck_new_card_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())
            ) {
                when {
                    loadError != null -> DeckErrorPanel(
                        message = loadError.orEmpty(),
                        messageTag = "deck-boards-error",
                        onRetry = { loadRequest++ },
                        retryTag = "retry-deck-boards"
                    )
                    boards == null -> DeckLoadingIndicator("deck-boards-loading")
                    boards.orEmpty().targets().isEmpty() -> DeckEmptyState(
                        stringResource(R.string.deck_no_lists),
                        Modifier.testTag("deck-no-lists")
                    )
                    else -> DeckTargetPicker(
                        boards = boards.orEmpty(),
                        boardId = boardId,
                        stackId = stackId,
                        fieldTag = "deck-card-target",
                        optionTagPrefix = "deck-target",
                        onSelect = { target ->
                            boardId = target.boardId
                            stackId = target.stackId
                        },
                        enabled = !creating
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.deck_card_title_label)) },
                        singleLine = true,
                        enabled = !creating,
                        isError = title.isNotEmpty() && !titleValid,
                        supportingText = if (title.length > NextcloudDeck.MAX_CARD_TITLE_LENGTH) {
                            {
                                Text(
                                    stringResource(
                                        R.string.deck_card_title_too_long,
                                        NextcloudDeck.MAX_CARD_TITLE_LENGTH
                                    )
                                )
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("deck-card-title")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.deck_card_description_label)) },
                        minLines = 4,
                        maxLines = 10,
                        enabled = !creating,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("deck-card-description")
                    )
                }
                DeckDueDateFields(dueAt, !creating, "deck-card", { dueAt = it })
                createError?.let {
                    DeckErrorPanel(message = it, messageTag = "deck-card-error")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = selected ?: return@TextButton
                    creating = true
                    createError = null
                    scope.launch {
                        try {
                            val link = component.createDeckCard(
                                accountId,
                                target,
                                DeckCardDraft(
                                    title = title,
                                    description = description,
                                    dueAtEpochSeconds = dueAt
                                )
                            )
                            onCreated(link)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            createError = failure.message
                                ?: resources.getString(R.string.deck_create_card_failed)
                            creating = false
                        }
                    }
                },
                enabled = titleValid && selected != null && !creating,
                modifier = Modifier.testTag("create-deck-card")
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    stringResource(
                        if (creating) R.string.deck_creating else R.string.deck_create_and_link
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !creating,
                modifier = Modifier.testTag("cancel-deck-card")
            ) { Text(stringResource(R.string.ui_cancel)) }
        },
        modifier = Modifier.testTag("deck-card-dialog")
    )
}

/** The selected source text as a card title, or an empty title when nothing usable is selected. */
internal fun deckCardTitleFromSelection(text: CharSequence?, start: Int, end: Int): String {
    if (text == null) return ""
    val from = minOf(start, end).coerceIn(0, text.length)
    val to = maxOf(start, end).coerceIn(from, text.length)
    return NextcloudDeck.cardTitle(text.substring(from, to))
        ?.take(NextcloudDeck.MAX_CARD_TITLE_LENGTH)
        .orEmpty()
}
