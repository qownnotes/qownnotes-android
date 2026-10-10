package org.qownnotes.mobile

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.deck_open_card_question))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(
                                value = rememberChoice,
                                role = Role.Checkbox,
                                onValueChange = { rememberChoice = it }
                            )
                            .padding(vertical = 8.dp)
                            .testTag("deck-remember-opening")
                    ) {
                        Checkbox(checked = rememberChoice, onCheckedChange = null)
                        Text(stringResource(R.string.deck_remember_choice))
                    }
                    FilledTonalButton(
                        onClick = {
                            if (rememberChoice) {
                                component.settings.setDeckLinkOpening(
                                    account.id,
                                    DeckLinkOpening.QOWNNOTES
                                )
                            }
                            editing = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .testTag("deck-open-in-app")
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.deck_edit_in_app))
                    }
                    OutlinedButton(
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
                        modifier = Modifier.fillMaxWidth().testTag("deck-open-external")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.deck_open_external))
                    }
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.deck_edit_card), modifier = Modifier.weight(1f))
                IconButton(
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
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.deck_open_external)
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())
            ) {
                if (current == null) {
                    if (loadError == null) {
                        DialogLoadingIndicator("deck-edit-loading")
                    } else {
                        DialogErrorPanel(
                            message = loadError.orEmpty(),
                            messageTag = "deck-edit-load-error",
                            onRetry = { loadRequest++ },
                            retryTag = "deck-edit-retry"
                        )
                    }
                } else {
                    if (!current.editable || current.archived) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (current.archived) {
                                DeckBadge(
                                    icon = Icons.Filled.Archive,
                                    text = stringResource(R.string.deck_archived),
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!current.editable) {
                                DeckBadge(
                                    icon = Icons.Filled.Lock,
                                    text = stringResource(R.string.deck_read_only),
                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                    content = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    val editable = current.editable && !saving
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.deck_card_title_label)) },
                            singleLine = true,
                            enabled = editable,
                            isError = !NextcloudDeck.isValidCardTitle(title),
                            supportingText =
                            if (title.length > NextcloudDeck.MAX_CARD_TITLE_LENGTH) {
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
                            modifier = Modifier.fillMaxWidth().testTag("deck-edit-title")
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(R.string.deck_card_description_label)) },
                            minLines = 4,
                            maxLines = 10,
                            enabled = editable,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("deck-edit-description")
                        )
                    }
                    DeckDueDateFields(dueAt, editable, "deck-edit", { dueAt = it })
                    saveError?.let {
                        DialogErrorPanel(
                            message = it,
                            messageTag = "deck-edit-error",
                            onRetry = { reloading = true },
                            retryTag = "deck-edit-reload",
                            retryLabel = stringResource(R.string.deck_reload_card),
                            retryEnabled = !saving
                        )
                    }
                }
                if (current != null && current.editable && !current.archived) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { confirmingArchive = true },
                        enabled = !saving,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.testTag("deck-edit-archive")
                    ) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.deck_archive_card))
                    }
                }
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
            icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
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
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            title = { Text(stringResource(R.string.deck_reload_card)) },
            text = { Text(stringResource(R.string.deck_reload_discard)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reloading = false
                        original = null
                        saveError = null
                        loadRequest++
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("deck-edit-confirm-reload")
                ) {
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

@Composable
private fun DeckDueButton(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(
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
                )
                .padding(vertical = 8.dp)
                .testTag("$tag-due-toggle")
        ) {
            Checkbox(checked = dueAt != null, onCheckedChange = null, enabled = enabled)
            Text(
                stringResource(R.string.deck_due_date),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (dueAt != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DeckDueButton(
                    icon = Icons.Filled.Event,
                    text = dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    enabled = enabled,
                    onClick = { choosingDate = true },
                    modifier = Modifier.weight(1f).testTag("$tag-due-date")
                )
                DeckDueButton(
                    icon = Icons.Filled.Schedule,
                    text = dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                    enabled = enabled,
                    onClick = { choosingTime = true },
                    modifier = Modifier.weight(1f).testTag("$tag-due-time")
                )
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
