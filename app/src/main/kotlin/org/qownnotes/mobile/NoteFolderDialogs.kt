package org.qownnotes.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.qownnotes.mobile.core.NoteCategories
import org.qownnotes.mobile.core.NoteFolder
import org.qownnotes.mobile.core.NoteFolderTree
import org.qownnotes.mobile.core.NoteFolders

/**
 * Asks for the name of a new folder inside [parent], where the empty string is the root. A `/` in
 * the name creates nested folders at once.
 */
@Composable
internal fun NewFolderDialog(
    parent: String,
    tree: NoteFolderTree,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(parent) { mutableStateOf("") }
    val relative = NoteCategories.normalize(name)
    val path = NoteFolders.join(parent, relative)
    val reserved = relative.isNotEmpty() && NoteCategories.isInternal(path)
    val exists = relative.isNotEmpty() && tree.find(path) != null
    val valid = relative.isNotEmpty() && !reserved && !exists
    val location = if (parent.isEmpty()) {
        stringResource(R.string.folders_new_location_root)
    } else {
        stringResource(R.string.folders_new_location, parent)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
        title = { Text(stringResource(R.string.category_new)) },
        text = {
            FolderNameField(
                value = remember { TextFieldValue(name, selection = TextRange(name.length)) },
                onValueChange = { name = it },
                isError = reserved || exists,
                supportingText = when {
                    reserved -> stringResource(R.string.category_new_path_reserved)
                    exists -> stringResource(R.string.folders_exists)
                    relative.isNotEmpty() && relative != name.trim() ->
                        stringResource(R.string.category_new_path_normalized, relative)
                    else -> location
                },
                onDone = { if (valid) onConfirm(path) },
                testTag = "new-folder-name"
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path) },
                enabled = valid,
                modifier = Modifier.testTag("confirm-new-folder")
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        modifier = Modifier.testTag("new-folder-dialog")
    )
}

/**
 * Asks for a new name of [folder], which stays in its parent. [noteCount] notes at and below it
 * move with it.
 */
@Composable
internal fun RenameFolderDialog(
    folder: NoteFolder,
    tree: NoteFolderTree,
    noteCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(folder.path) { mutableStateOf(folder.name) }
    val normalized = NoteCategories.normalize(name)
    val target = NoteFolders.join(NoteFolders.parent(folder.path), normalized)
    val slash = '/' in name
    val unchanged = NoteFolders.key(target) == NoteFolders.key(folder.path)
    val reserved = normalized.isNotEmpty() && NoteCategories.isInternal(target)
    val exists = normalized.isNotEmpty() && !unchanged && tree.find(target) != null
    val valid = normalized.isNotEmpty() && !slash && !unchanged && !reserved && !exists
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.folders_rename_title)) },
        text = {
            FolderNameField(
                value = remember { TextFieldValue(name, selection = TextRange(0, name.length)) },
                onValueChange = { name = it },
                isError = slash || reserved || exists,
                supportingText = when {
                    slash -> stringResource(R.string.folders_name_slash)
                    reserved -> stringResource(R.string.category_new_path_reserved)
                    exists -> stringResource(R.string.folders_exists)
                    normalized.isNotEmpty() && normalized != name.trim() ->
                        stringResource(R.string.category_new_path_normalized, normalized)
                    noteCount > 0 ->
                        pluralStringResource(R.plurals.folders_rename_notes, noteCount, noteCount)
                    else -> null
                },
                onDone = { if (valid) onConfirm(normalized) },
                testTag = "rename-folder-name"
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalized) },
                enabled = valid,
                modifier = Modifier.testTag("confirm-rename-folder")
            ) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        modifier = Modifier.testTag("rename-folder-dialog")
    )
}

/**
 * Confirms deleting [folder]. [noteCount] notes at and below it move to the trash, and
 * [unsynchronizedCount] of them have changes the trash would not keep.
 */
@Composable
internal fun DeleteFolderDialog(
    folder: NoteFolder,
    noteCount: Int,
    unsynchronizedCount: Int,
    /** How the account's trash works, as the note deletion dialogs explain it. */
    trashMessage: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FolderDelete, contentDescription = null) },
        title = {
            Text(
                stringResource(R.string.folders_delete_title, folder.name),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (noteCount == 0) {
                    Text(stringResource(R.string.folders_delete_empty))
                } else {
                    Text(pluralStringResource(R.plurals.folders_delete_notes, noteCount, noteCount))
                    Text(
                        trashMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unsynchronizedCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.shapes.medium
                            )
                            .padding(12.dp)
                            .testTag("delete-folder-unsynchronized")
                    ) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.folders_delete_unsynchronized,
                                unsynchronizedCount,
                                unsynchronizedCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("confirm-delete-folder")
            ) { Text(stringResource(R.string.folders_action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        modifier = Modifier.testTag("delete-folder-dialog")
    )
}

/** Explains why a folder kept its name. */
@Composable
internal fun FolderRenameFailureDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(stringResource(R.string.folders_rename_failed), textAlign = TextAlign.Center)
        },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close-folder-rename-failure")
            ) { Text(stringResource(R.string.action_close)) }
        },
        modifier = Modifier.testTag("folder-rename-failure")
    )
}

@Composable
private fun FolderNameField(
    value: TextFieldValue,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supportingText: String?,
    onDone: () -> Unit,
    testTag: String
) {
    var fieldValue by remember { mutableStateOf(value) }
    val focusRequester = remember { FocusRequester() }
    // As in the note dialogs, the focus is taken once the field is placed in the dialog's window.
    var fieldPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(fieldPlaced) {
        if (fieldPlaced) focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        label = { Text(stringResource(R.string.folders_name)) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            .onPlaced { fieldPlaced = true }
            .testTag(testTag)
    )
}
