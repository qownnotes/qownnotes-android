package org.qownnotes.mobile

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.DeckCardDetails
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckCardLink
import org.qownnotes.mobile.core.NextcloudDeck

/** The same external route as before, including the browser fallback when Deck is not installed. */
internal fun openDeckCardExternally(
    context: android.content.Context,
    component: ApplicationComponent,
    account: Account,
    url: String
) {
    if (!component.deckCardOpener.open(url, account)) {
        openSafeExternalUrl(context, url)
    }
}

@Composable
internal fun DeckCardLinkDialog(
    component: ApplicationComponent,
    account: Account,
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val link = NextcloudDeck.parseCardLink(url, account.serverUrl) ?: return
    val remembered = remember(account.id, url) { component.settings.deckLinkOpening(account.id) }
    var editing by rememberSaveable(account.id, url) {
        mutableStateOf(remembered == DeckLinkOpening.QOWNNOTES)
    }
    var rememberChoice by rememberSaveable(account.id, url) { mutableStateOf(false) }
    LaunchedEffect(account.id, url) {
        if (remembered == DeckLinkOpening.DECK) {
            openDeckCardExternally(context, component, account, url)
            onDismiss()
        }
    }
    if (editing) {
        DeckCardEditorDialog(component, account, link, onDismiss = onDismiss)
    } else if (remembered != DeckLinkOpening.DECK) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.deck_open_card)) },
            text = {
                Column {
                    Text(stringResource(R.string.deck_open_card_question))
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = rememberChoice,
                            role = Role.Checkbox,
                            onValueChange = { rememberChoice = it }
                        ).testTag("deck-remember-opening")
                    ) {
                        Checkbox(checked = rememberChoice, onCheckedChange = null)
                        Text(stringResource(R.string.deck_remember_choice))
                    }
                    TextButton(
                        onClick = {
                            if (rememberChoice) {
                                component.settings.setDeckLinkOpening(
                                    account.id,
                                    DeckLinkOpening.QOWNNOTES
                                )
                            }
                            editing = true
                        },
                        modifier = Modifier.testTag("deck-open-in-app")
                    ) { Text(stringResource(R.string.deck_edit_in_app)) }
                    TextButton(
                        onClick = {
                            if (rememberChoice) {
                                component.settings.setDeckLinkOpening(
                                    account.id,
                                    DeckLinkOpening.DECK
                                )
                            }
                            openDeckCardExternally(context, component, account, url)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("deck-open-external")
                    ) { Text(stringResource(R.string.deck_open_external)) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            },
            modifier = Modifier.testTag("deck-opening-dialog")
        )
    }
}

@Composable
internal fun DeckCardEditorDialog(
    component: ApplicationComponent,
    account: Account,
    link: DeckCardLink,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope { UiDispatcher }
    var original by rememberSaveable(account.id, link.idKey(), stateSaver = deckCardSnapshotSaver) {
        mutableStateOf<DeckCardDetails?>(null)
    }
    var loadRequest by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reloading by remember { mutableStateOf(false) }
    var confirmingArchive by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var dueAt by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(account.id, link, loadRequest) {
        // Retain the reviewed snapshot as well as the input across recreation. Loading a newer
        // baseline underneath restored input would incorrectly authorize overwriting that version.
        if (original != null) return@LaunchedEffect
        loadError = null
        original = null
        try {
            val loaded = component.deckCard(account.id, link)
            original = loaded
            title = loaded.title
            description = loaded.description
            dueAt = loaded.dueAtEpochSeconds
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = deckErrorMessage(error, resources)
        }
    }
    val current = original
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.deck_edit_card)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            ) {
                if (current == null) {
                    if (loadError == null) {
                        CircularProgressIndicator(modifier = Modifier.testTag("deck-edit-loading"))
                    } else {
                        Text(
                            loadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("deck-edit-load-error")
                        )
                        TextButton(onClick = {
                            loadRequest++
                        }, modifier = Modifier.testTag("deck-edit-retry")) {
                            Text(stringResource(R.string.ui_retry))
                        }
                    }
                } else {
                    if (!current.editable) Text(stringResource(R.string.deck_read_only))
                    if (current.archived) Text(stringResource(R.string.deck_archived))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.deck_card_title_label)) },
                        singleLine = true,
                        enabled = current.editable && !saving,
                        isError = !NextcloudDeck.isValidCardTitle(title),
                        modifier = Modifier.fillMaxWidth().testTag("deck-edit-title")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.deck_card_description_label)) },
                        minLines = 3,
                        maxLines = 8,
                        enabled = current.editable && !saving,
                        modifier = Modifier.fillMaxWidth().testTag("deck-edit-description")
                    )
                    DeckDueDateFields(dueAt, current.editable && !saving, "deck-edit", {
                        dueAt = it
                    })
                    if (current.editable && !current.archived) {
                        TextButton(
                            onClick = { confirmingArchive = true },
                            enabled = !saving,
                            modifier = Modifier.testTag("deck-edit-archive")
                        ) {
                            Text(stringResource(R.string.deck_archive_card))
                        }
                    }
                    saveError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("deck-edit-error")
                        )
                        TextButton(
                            onClick = { reloading = true },
                            enabled = !saving,
                            modifier = Modifier.testTag("deck-edit-reload")
                        ) {
                            Text(stringResource(R.string.deck_reload_card))
                        }
                    }
                }
                TextButton(
                    onClick = {
                        openDeckCardExternally(
                            context,
                            component,
                            account,
                            NextcloudDeck.cardUrl(account.serverUrl, link.boardId, link.cardId)
                        )
                    },
                    enabled = !saving,
                    modifier = Modifier.testTag("deck-edit-external")
                ) { Text(stringResource(R.string.deck_open_external)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val snapshot = original ?: return@TextButton
                    saving = true
                    saveError = null
                    scope.launch {
                        try {
                            component.updateDeckCard(
                                account.id,
                                snapshot,
                                DeckCardDraft(title, description, dueAt)
                            )
                            onSaved()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            saveError = deckErrorMessage(error, resources)
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled =
                current?.editable == true && !saving && NextcloudDeck.isValidCardTitle(title),
                modifier = Modifier.testTag("deck-edit-save")
            ) { Text(stringResource(if (saving) R.string.deck_saving else R.string.deck_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !saving,
                modifier = Modifier.testTag("deck-edit-close")
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
        modifier = Modifier.testTag("deck-edit-dialog")
    )
    if (confirmingArchive && current != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirmingArchive = false },
            title = { Text(stringResource(R.string.deck_archive_card)) },
            text = { Text(stringResource(R.string.deck_archive_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    saving = true
                    saveError = null
                    scope.launch {
                        try {
                            component.archiveDeckCard(account.id, current)
                            confirmingArchive = false
                            onSaved()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            confirmingArchive = false
                            saveError = deckErrorMessage(error, resources)
                        } finally {
                            saving = false
                        }
                    }
                }, enabled = !saving, modifier = Modifier.testTag("deck-confirm-archive")) {
                    Text(stringResource(R.string.deck_archive_card))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingArchive = false },
                    enabled = !saving,
                    modifier = Modifier.testTag("deck-cancel-archive")
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
    if (reloading) {
        AlertDialog(
            onDismissRequest = { reloading = false },
            title = { Text(stringResource(R.string.deck_reload_card)) },
            text = { Text(stringResource(R.string.deck_reload_discard)) },
            confirmButton = {
                TextButton(onClick = {
                    reloading = false
                    original = null
                    saveError = null
                    loadRequest++
                }, modifier = Modifier.testTag("deck-edit-confirm-reload")) {
                    Text(stringResource(R.string.ui_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    reloading = false
                }) { Text(stringResource(R.string.ui_cancel)) }
            }
        )
    }
}

private fun DeckCardLink.idKey() = "$boardId/$cardId"

private val deckCardSnapshotSaver = listSaver<DeckCardDetails?, Any>(
    save = { card ->
        if (card == null) {
            emptyList()
        } else {
            listOf(
                card.id, card.boardId, card.stackId, card.title, card.description,
                card.dueAtEpochSeconds ?: Long.MIN_VALUE, card.owner, card.order, card.type,
                card.archived, card.startDate.orEmpty(), card.etag.orEmpty(), card.editable
            )
        }
    },
    restore = { values ->
        if (values.isEmpty()) {
            null
        } else {
            DeckCardDetails(
                values[0] as Long, values[1] as Long, values[2] as Long,
                values[3] as String, values[4] as String,
                (values[5] as Long).takeUnless { it == Long.MIN_VALUE },
                values[6] as String, values[7] as Int, values[8] as String,
                values[9] as Boolean, (values[10] as String).ifEmpty { null },
                (values[11] as String).ifEmpty { null }, values[12] as Boolean
            )
        }
    }
)

internal fun deckErrorMessage(error: Exception, resources: android.content.res.Resources): String =
    when (error) {
        is BackendException.Conflict -> resources.getString(R.string.deck_card_changed)
        is BackendException.RemoteMissing -> resources.getString(R.string.deck_card_missing)
        else -> error.message ?: resources.getString(R.string.deck_card_request_failed)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckDueDateFields(
    dueAt: Long?,
    enabled: Boolean,
    tag: String,
    onChange: (Long?) -> Unit
) {
    val context = LocalContext.current
    var fallbackDueAt by rememberSaveable {
        mutableStateOf(Instant.now().plusSeconds(3600).epochSecond / 60 * 60)
    }
    var choosingDate by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    val dateTime = Instant.ofEpochSecond(dueAt ?: fallbackDueAt).atZone(ZoneId.systemDefault())
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = dueAt != null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { checked ->
                if (checked) {
                    onChange(fallbackDueAt)
                } else {
                    fallbackDueAt = dueAt ?: fallbackDueAt
                    onChange(null)
                }
            }
        ).testTag("$tag-due-toggle")
    ) {
        Checkbox(checked = dueAt != null, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.deck_due_date))
    }
    if (dueAt != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { choosingDate = true },
                enabled = enabled,
                modifier = Modifier.testTag("$tag-due-date")
            ) {
                Text(dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
            OutlinedButton(
                onClick = { choosingTime = true },
                enabled = enabled,
                modifier = Modifier.testTag("$tag-due-time")
            ) {
                Text(dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
            }
        }
    }
    if (choosingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(
                            dateTime.with(
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            ).toEpochSecond()
                        )
                    }
                    choosingDate = false
                }) { Text(stringResource(R.string.ui_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingDate = false
                }) { Text(stringResource(R.string.ui_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
    if (choosingTime) {
        val state =
            rememberTimePickerState(
                dateTime.hour,
                dateTime.minute,
                DateFormat.is24HourFormat(context)
            )
        AlertDialog(
            onDismissRequest = { choosingTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        dateTime.withHour(
                            state.hour
                        ).withMinute(state.minute).withSecond(0).toEpochSecond()
                    )
                    choosingTime = false
                }) { Text(stringResource(R.string.ui_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingTime = false
                }) { Text(stringResource(R.string.ui_cancel)) }
            }
        )
    }
}
