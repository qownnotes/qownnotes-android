package org.qownnotes.mobile

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.NoteListItem
import org.qownnotes.mobile.core.NoteNames

@Composable
internal fun FileShareDialog(
    component: ApplicationComponent,
    share: FileShareState,
    accounts: List<Account>?,
    activeAccountId: String?
) {
    val close = component::dismissFileShare
    when {
        share is FileShareState.Unavailable -> {
            FileShareNotice(share.message, close)
        }
        accounts == null || share is FileShareState.Loading -> {
            AlertDialog(
                onDismissRequest = close,
                title = { Text(stringResource(R.string.shared_file_title)) },
                text = { ShareProgress(R.string.shared_file_preparing) },
                confirmButton = {
                    TextButton(onClick = close) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
        share is FileShareState.Ready -> key(share.id) {
            val destinations = accounts.filter(component::supportsSharedFiles)
            if (destinations.isEmpty()) {
                FileShareNotice(R.string.shared_file_no_account, close)
            } else {
                FileDestinationDialog(component, share, destinations, activeAccountId)
            }
        }
    }
}

@Composable
private fun FileShareNotice(message: Int, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
        title = { Text(stringResource(R.string.shared_file_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(android.R.string.ok)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDestinationDialog(
    component: ApplicationComponent,
    share: FileShareState.Ready,
    accounts: List<Account>,
    activeAccountId: String?
) {
    var accountId by rememberSaveable {
        mutableStateOf(accounts.firstOrNull { it.id == activeAccountId }?.id ?: accounts.first().id)
    }
    val account = accounts.firstOrNull { it.id == accountId } ?: accounts.first()
    var existing by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable {
        mutableStateOf(component.defaultNoteName(share.file.name.substringBeforeLast('.')))
    }
    var selectedId by rememberSaveable(account.id) { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable(account.id) { mutableStateOf<String?>(null) }
    val busy = share.saving
    val close = component::dismissFileShare
    val valid = if (existing) selectedId != null else NoteNames.isValid(title)
    AlertDialog(
        onDismissRequest = close,
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(stringResource(R.string.shared_file_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SharedFileCard(share.file)
                if (accounts.size > 1) {
                    ShareAccountPicker(accounts, account, !busy) { accountId = it }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(R.string.share_to_new_note, R.string.shared_file_existing_note)
                        .forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = existing == (index == 1),
                                onClick = { existing = index == 1 },
                                enabled = !busy,
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                modifier = Modifier.testTag("share-destination-$index"),
                                label = { Text(stringResource(label)) }
                            )
                        }
                }
                if (existing) {
                    ShareNotePicker(
                        component,
                        account.id,
                        selectedId,
                        selectedTitle,
                        !busy
                    ) { note ->
                        selectedId = note?.localId
                        selectedTitle = note?.title
                    }
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        enabled = !busy,
                        label = { Text(stringResource(R.string.shared_file_note_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("share-note-title")
                    )
                }
                if (busy) {
                    ShareProgress(R.string.shared_file_saving)
                } else if (share.error != null) {
                    Text(
                        stringResource(share.error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    component.saveFileShare(account.id, selectedId.takeIf { existing }, title)
                },
                enabled = valid && !busy,
                modifier = Modifier.testTag("save-shared-file")
            ) {
                Text(
                    stringResource(
                        if (existing) R.string.shared_file_attach else R.string.shared_file_create
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun SharedFileCard(file: SharedFile) {
    val context = LocalContext.current
    val isImage = file.mimeType.startsWith("image/")
    val kind = stringResource(
        if (isImage) R.string.shared_file_image else R.string.shared_file_kind
    )
    val icon = if (isImage) Icons.Filled.Image else Icons.AutoMirrored.Filled.InsertDriveFile
    val size = file.size?.let { Formatter.formatShortFileSize(context, it) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (size == null) {
                        kind
                    } else {
                        stringResource(R.string.shared_file_kind_size, kind, size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShareAccountPicker(
    accounts: List<Account>,
    account: Account,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.shared_file_account, account.displayName),
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(item.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareNotePicker(
    component: ApplicationComponent,
    accountId: String,
    selectedId: String?,
    selectedTitle: String?,
    enabled: Boolean,
    onSelect: (NoteListItem?) -> Unit
) {
    var query by rememberSaveable(accountId) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val flow = remember(component, accountId) { component.noteRepository.observeNotes(accountId) }
    val notes by flow.collectAsStateWithLifecycle(initialValue = null, context = UiDispatcher)
    val results = remember(notes, query) {
        val search = query.trim()
        notes?.filter {
            !it.readOnly && it.syncState in SHARED_FILE_WRITABLE_STATES &&
                (
                    it.title.contains(search, ignoreCase = true) ||
                        it.category.contains(search, ignoreCase = true)
                    )
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            onSelect(null)
        },
        enabled = enabled,
        label = { Text(stringResource(R.string.shared_file_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = {
                    query = ""
                    onSelect(null)
                }, enabled = enabled) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("share-search-notes")
    )
    when {
        results == null -> ShareProgress(R.string.shared_file_loading_notes)
        results.isEmpty() -> Text(
            stringResource(
                if (query.isBlank()) {
                    R.string.shared_file_no_notes
                } else {
                    R.string.shared_file_no_matches
                }
            ),
            style = MaterialTheme.typography.bodySmall
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 176.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(results, key = NoteListItem::localId) { note ->
                val selected = selectedId == note.localId
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected, enabled, Role.RadioButton) {
                                focus.clearFocus()
                                onSelect(note)
                            }
                            .testTag("share-target-${note.localId}")
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = enabled)
                        Column {
                            Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (note.category.isNotEmpty()) {
                                Text(
                                    note.category,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (selectedTitle != null) {
        Text(
            stringResource(R.string.shared_file_selected, selectedTitle),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShareProgress(message: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(message))
    }
}
