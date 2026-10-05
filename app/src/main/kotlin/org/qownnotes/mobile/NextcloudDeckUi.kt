package org.qownnotes.mobile

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.NextcloudDeck

private data class DeckTargetOption(val target: DeckStackTarget, val label: String)

private fun List<DeckBoard>.targetOptions(): List<DeckTargetOption> = flatMap { board ->
    board.stacks.map { stack ->
        DeckTargetOption(DeckStackTarget(board.id, stack.id), "${board.title} / ${stack.title}")
    }
}

/** The default due date of the desktop application: one hour from now, on the minute. */
private fun defaultDeckDueAt(): Long =
    Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).epochSecond

/**
 * Creates a Nextcloud Deck card in a chosen board list and hands its Markdown link to
 * [onCreated]. The dialog stays open with its input when loading or creating fails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NextcloudDeckCardDialog(
    component: ApplicationComponent,
    accountId: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onCreated: (markdownLink: String) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope { UiDispatcher }
    var boards by remember(accountId) { mutableStateOf<List<DeckBoard>?>(null) }
    var loadError by remember(accountId) { mutableStateOf<String?>(null) }
    var loadRequest by remember(accountId) { mutableIntStateOf(0) }
    var boardId by rememberSaveable(accountId) { mutableStateOf<Long?>(null) }
    var stackId by rememberSaveable(accountId) { mutableStateOf<Long?>(null) }
    var title by rememberSaveable(accountId) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(accountId) { mutableStateOf("") }
    var hasDueDate by rememberSaveable(accountId) { mutableStateOf(false) }
    var dueAt by rememberSaveable(accountId) { mutableStateOf(defaultDeckDueAt()) }
    var choosingTarget by remember { mutableStateOf(false) }
    var choosingDate by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    var creating by remember(accountId) { mutableStateOf(false) }
    var createError by remember(accountId) { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId, loadRequest) {
        loadError = null
        try {
            val loaded = withContext(UiDispatcher) { component.deckBoards(accountId) }
            val options = loaded.targetOptions()
            val current = DeckStackTarget(boardId ?: 0, stackId ?: 0)
            if (options.none { it.target == current }) {
                val remembered = component.settings.nextcloudDeckTarget(accountId)
                val selected = options.firstOrNull { it.target == remembered }
                    ?: options.firstOrNull()
                boardId = selected?.target?.boardId
                stackId = selected?.target?.stackId
            }
            boards = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError = failure.message ?: resources.getString(R.string.deck_load_boards_failed)
        }
    }

    val options = remember(boards) { boards.orEmpty().targetOptions() }
    val selected = options.firstOrNull {
        it.target.boardId == boardId && it.target.stackId == stackId
    }
    val titleValid = NextcloudDeck.isValidCardTitle(title)
    val zone = ZoneId.systemDefault()
    val dueDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(dueAt), zone)

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text(stringResource(R.string.deck_new_card_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            ) {
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
                    minLines = 2,
                    maxLines = 6,
                    enabled = !creating,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("deck-card-description")
                )
                when {
                    loadError != null -> Column {
                        Text(
                            loadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("deck-boards-error")
                        )
                        TextButton(
                            onClick = { loadRequest++ },
                            modifier = Modifier.testTag("retry-deck-boards")
                        ) { Text(stringResource(R.string.ui_retry)) }
                    }
                    boards == null -> CircularProgressIndicator(
                        modifier = Modifier.testTag("deck-boards-loading")
                    )
                    options.isEmpty() -> Text(
                        stringResource(R.string.deck_no_lists),
                        modifier = Modifier.testTag("deck-no-lists")
                    )
                    else -> Column {
                        Text(
                            stringResource(R.string.deck_board_and_list),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Box {
                            OutlinedButton(
                                onClick = { choosingTarget = true },
                                enabled = !creating,
                                modifier = Modifier.fillMaxWidth().testTag("deck-card-target")
                            ) {
                                Text(
                                    selected?.label ?: stringResource(R.string.deck_choose_list),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = choosingTarget,
                                onDismissRequest = { choosingTarget = false }
                            ) {
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            boardId = option.target.boardId
                                            stackId = option.target.stackId
                                            choosingTarget = false
                                        },
                                        modifier = Modifier.testTag(
                                            "deck-target-${option.target.boardId}-" +
                                                "${option.target.stackId}"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(
                            value = hasDueDate,
                            enabled = !creating,
                            role = Role.Checkbox,
                            onValueChange = { hasDueDate = it }
                        )
                        .testTag("deck-card-due-toggle")
                ) {
                    Checkbox(checked = hasDueDate, onCheckedChange = null, enabled = !creating)
                    Text(stringResource(R.string.deck_due_date))
                }
                if (hasDueDate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { choosingDate = true },
                            enabled = !creating,
                            modifier = Modifier.testTag("deck-card-due-date")
                        ) {
                            Text(
                                dueDateTime.format(
                                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                )
                            )
                        }
                        OutlinedButton(
                            onClick = { choosingTime = true },
                            enabled = !creating,
                            modifier = Modifier.testTag("deck-card-due-time")
                        ) {
                            Text(
                                dueDateTime.format(
                                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                )
                            )
                        }
                    }
                }
                createError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("deck-card-error")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = selected?.target ?: return@TextButton
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
                                    dueAtEpochSeconds = dueAt.takeIf { hasDueDate }
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
                Text(
                    stringResource(
                        if (creating) R.string.deck_creating else R.string.deck_create_and_link
                    )
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

    if (choosingDate) {
        // The date picker works with midnight UTC of the chosen day.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            dueAt = dueDateTime.with(date).toEpochSecond()
                        }
                        choosingDate = false
                    },
                    modifier = Modifier.testTag("confirm-deck-due-date")
                ) { Text(stringResource(R.string.ui_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { choosingDate = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (choosingTime) {
        val pickerState = rememberTimePickerState(
            initialHour = dueDateTime.hour,
            initialMinute = dueDateTime.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { choosingTime = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dueAt = dueDateTime
                            .withHour(pickerState.hour)
                            .withMinute(pickerState.minute)
                            .withSecond(0)
                            .toEpochSecond()
                        choosingTime = false
                    },
                    modifier = Modifier.testTag("confirm-deck-due-time")
                ) { Text(stringResource(R.string.ui_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { choosingTime = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
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
