package org.qownnotes.mobile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteShare
import org.qownnotes.mobile.core.NoteShareChange
import org.qownnotes.mobile.core.NoteShareStatus
import org.qownnotes.mobile.core.NoteSharing
import org.qownnotes.mobile.core.NoteSharingPolicy
import org.qownnotes.mobile.core.SyncState

/**
 * Creates and manages the public Nextcloud link to a note, like the desktop share dialog. The
 * link is optionally password protected, which, as on the server, can also be changed later.
 * Passwords are only held in memory and never saved with the dialog's state.
 */
@Composable
internal fun NoteShareDialog(component: ApplicationComponent, note: Note, onDismiss: () -> Unit) {
    val localId = note.localId
    val resources = LocalResources.current
    val scope = rememberCoroutineScope { UiDispatcher }
    var status by remember(localId) { mutableStateOf<NoteShareStatus?>(null) }
    var loadError by remember(localId) { mutableStateOf<String?>(null) }
    var loadRequest by remember(localId) { mutableIntStateOf(0) }
    var busy by remember(localId) { mutableStateOf(false) }
    var actionError by remember(localId) { mutableStateOf<String?>(null) }
    var protect by rememberSaveable(localId) { mutableStateOf(false) }
    var password by remember(localId) { mutableStateOf("") }
    var editingPassword by remember(localId) { mutableStateOf(false) }
    var confirmingStop by remember(localId) { mutableStateOf(false) }

    LaunchedEffect(localId, loadRequest) {
        loadError = null
        try {
            status = withContext(UiDispatcher) { component.noteShare(localId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            status = null
            loadError = noteShareErrorMessage(failure, resources, R.string.share_note_load_failed)
        }
    }

    /** Runs a server change, keeping the dialog's input when it fails. */
    fun perform(
        creatingWithoutPassword: Boolean = false,
        changesPassword: Boolean = true,
        action: suspend () -> NoteShare?
    ) {
        val current = status ?: return
        busy = true
        actionError = null
        scope.launch {
            try {
                val share = action()
                status = current.copy(share = share)
                if (changesPassword) {
                    password = ""
                    editingPassword = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = noteShareErrorMessage(failure, resources, R.string.share_note_failed)
                actionError = if (failure is BackendException.Rejected && creatingWithoutPassword) {
                    // The server may enforce passwords without advertising it, as the desktop
                    // dialog also assumes. Offer the password field right away.
                    protect = true
                    message + "\n\n" + resources.getString(R.string.share_note_password_hint)
                } else {
                    message
                }
                if (failure is BackendException.RemoteMissing) loadRequest++
            } finally {
                busy = false
            }
        }
    }

    val current = status
    val share = current?.share
    val policy = current?.policy ?: NoteSharingPolicy(linkSharingEnabled = false)
    val needsPassword = share == null && (protect || policy.passwordEnforced)
    val passwordAcceptable = NoteSharing.isAcceptablePassword(password, policy)
    val createLink = {
        val requested = password.takeIf { needsPassword }
        perform(creatingWithoutPassword = requested == null, action = {
            component.createNoteShare(localId, requested)
        })
    }
    val savePassword = {
        share?.let { existing ->
            perform(action = {
                component.updateNoteShare(localId, existing, NoteShareChange.Password(password))
            })
        }
        Unit
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
        title = { Text(stringResource(R.string.share_note)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize()
            ) {
                when {
                    loadError != null -> DialogErrorPanel(
                        message = loadError.orEmpty(),
                        messageTag = "note-share-load-error",
                        onRetry = { loadRequest++ },
                        retryTag = "retry-note-share"
                    )
                    current == null -> DialogLoadingIndicator("note-share-loading")
                    share == null && !policy.linkSharingEnabled -> DialogEmptyState(
                        Icons.Filled.LinkOff,
                        stringResource(R.string.share_note_links_disabled),
                        Modifier.testTag("note-share-disabled")
                    )
                    share == null -> {
                        Text(
                            stringResource(R.string.share_note_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ShareOptionRow(
                            icon = Icons.Filled.Lock,
                            title = stringResource(R.string.share_note_protect),
                            summary = stringResource(
                                if (policy.passwordEnforced) {
                                    R.string.share_note_password_required
                                } else {
                                    R.string.share_note_protect_summary
                                }
                            ),
                            checked = needsPassword,
                            enabled = !busy && !policy.passwordEnforced,
                            onCheckedChange = { protect = it },
                            testTag = "note-share-protect"
                        )
                        AnimatedVisibility(visible = needsPassword) {
                            SharePasswordField(
                                value = password,
                                onValueChange = { password = it },
                                policy = policy,
                                enabled = !busy,
                                onDone = { if (passwordAcceptable && !busy) createLink() }
                            )
                        }
                    }
                    else -> {
                        ShareLinkCard(share, note.title)
                        ShareOptionRow(
                            icon = Icons.Filled.Edit,
                            title = stringResource(R.string.share_note_allow_editing),
                            summary = stringResource(R.string.share_note_allow_editing_summary),
                            checked = share.editAllowed,
                            enabled = !busy,
                            onCheckedChange = { allowed ->
                                perform(changesPassword = false, action = {
                                    component.updateNoteShare(
                                        localId,
                                        share,
                                        NoteShareChange.EditAllowed(allowed)
                                    )
                                })
                            },
                            testTag = "note-share-allow-editing"
                        )
                        ShareOptionRow(
                            icon = if (share.passwordProtected) {
                                Icons.Filled.Lock
                            } else {
                                Icons.Filled.LockOpen
                            },
                            title = stringResource(R.string.share_note_password_protection),
                            summary = stringResource(
                                when {
                                    share.passwordProtected && policy.passwordEnforced ->
                                        R.string.share_note_password_required
                                    share.passwordProtected ->
                                        R.string.share_note_password_set_summary
                                    else -> R.string.share_note_password_unset_summary
                                }
                            ),
                            checked = share.passwordProtected || editingPassword,
                            enabled = !busy &&
                                !(share.passwordProtected && policy.passwordEnforced),
                            onCheckedChange = { enabled ->
                                when {
                                    enabled -> editingPassword = true
                                    !share.passwordProtected -> {
                                        editingPassword = false
                                        password = ""
                                    }
                                    else -> perform(action = {
                                        component.updateNoteShare(
                                            localId,
                                            share,
                                            NoteShareChange.RemovePassword
                                        )
                                    })
                                }
                            },
                            testTag = "note-share-password-protection"
                        )
                        if (share.passwordProtected && !editingPassword) {
                            // Aligns the label with the option text, past the icon column.
                            TextButton(
                                onClick = { editingPassword = true },
                                enabled = !busy,
                                modifier = Modifier.padding(start = 28.dp)
                                    .testTag("note-share-change-password")
                            ) { Text(stringResource(R.string.share_note_change_password)) }
                        }
                        AnimatedVisibility(visible = editingPassword) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SharePasswordField(
                                    value = password,
                                    onValueChange = { password = it },
                                    policy = policy,
                                    enabled = !busy,
                                    onDone = { if (passwordAcceptable && !busy) savePassword() }
                                )
                                Row(
                                    horizontalArrangement =
                                    Arrangement.spacedBy(8.dp, Alignment.End),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        onClick = {
                                            editingPassword = false
                                            password = ""
                                        },
                                        enabled = !busy
                                    ) { Text(stringResource(R.string.ui_cancel)) }
                                    FilledTonalButton(
                                        onClick = savePassword,
                                        enabled = !busy && passwordAcceptable,
                                        modifier = Modifier.testTag("note-share-save-password")
                                    ) { Text(stringResource(R.string.share_note_save_password)) }
                                }
                            }
                        }
                        share.expiresOn?.let { date ->
                            ShareInfoRow(
                                Icons.Filled.Event,
                                stringResource(R.string.share_note_expires, formatShareDate(date))
                            )
                        }
                    }
                }
                if (current != null && note.syncState != SyncState.SYNCHRONIZED) {
                    DialogNotice(stringResource(R.string.share_note_unsynced))
                }
                actionError?.let {
                    DialogErrorPanel(message = it, messageTag = "note-share-error")
                }
            }
        },
        confirmButton = {
            when {
                current != null && share == null && policy.linkSharingEnabled -> TextButton(
                    onClick = createLink,
                    enabled = !busy && (!needsPassword || passwordAcceptable),
                    modifier = Modifier.testTag("note-share-create")
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.share_note_creating))
                    } else {
                        IconLabel(
                            Icons.Filled.AddLink,
                            stringResource(R.string.share_note_create_link)
                        )
                    }
                }
                else -> TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.testTag("note-share-done")
                ) { Text(stringResource(R.string.ui_done)) }
            }
        },
        dismissButton = {
            when {
                share != null -> TextButton(
                    onClick = { confirmingStop = true },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("note-share-stop")
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.share_note_stop))
                    } else {
                        IconLabel(Icons.Filled.LinkOff, stringResource(R.string.share_note_stop))
                    }
                }
                current != null && policy.linkSharingEnabled -> TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.testTag("note-share-cancel")
                ) { Text(stringResource(R.string.ui_cancel)) }
            }
        },
        modifier = Modifier.testTag("note-share-dialog")
    )

    if (confirmingStop && share != null) {
        AlertDialog(
            onDismissRequest = { confirmingStop = false },
            icon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
            title = { Text(stringResource(R.string.share_note_stop_title)) },
            text = { Text(stringResource(R.string.share_note_stop_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingStop = false
                        protect = false
                        perform(action = {
                            component.deleteNoteShare(localId, share)
                            null
                        })
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("note-share-confirm-stop")
                ) { Text(stringResource(R.string.share_note_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStop = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
}

/** The link on a tinted card with copy, send, and open actions. */
@Composable
private fun ShareLinkCard(share: NoteShare, noteTitle: String) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var copied by remember(share.url) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (share.passwordProtected) Icons.Filled.Lock else Icons.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    stringResource(
                        if (share.passwordProtected) {
                            R.string.share_note_public_link_protected
                        } else {
                            R.string.share_note_public_link
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TooltipIconButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    description = stringResource(R.string.share_note_open_link),
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(share.url))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                            )
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.share_note_no_browser),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    testTag = "note-share-open"
                )
            }
            SelectionContainer {
                Text(
                    share.url,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("note-share-url")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                            ClipData.newPlainText(noteTitle, share.url)
                        )
                        copied = true
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f).testTag("note-share-copy")
                ) {
                    IconLabel(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        stringResource(
                            if (copied) {
                                R.string.share_note_link_copied
                            } else {
                                R.string.share_note_copy_link
                            }
                        )
                    )
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                            .putExtra(Intent.EXTRA_TEXT, share.url)
                        context.startActivity(Intent.createChooser(send, noteTitle))
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f).testTag("note-share-send")
                ) {
                    IconLabel(
                        Icons.AutoMirrored.Filled.Send,
                        stringResource(R.string.share_note_send_link)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ShareInfoRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SharePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    policy: NoteSharingPolicy,
    enabled: Boolean,
    onDone: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val minLength = policy.passwordMinLength
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filterNot { it == '\r' || it == '\n' }) },
        label = { Text(stringResource(R.string.share_note_password)) },
        singleLine = true,
        enabled = enabled,
        isError = value.isNotEmpty() && !NoteSharing.isAcceptablePassword(value, policy),
        supportingText = minLength?.let {
            { Text(stringResource(R.string.share_note_password_min_length, it)) }
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            Row {
                TooltipIconButton(
                    icon = Icons.Filled.AutoFixHigh,
                    description = stringResource(R.string.share_note_generate_password),
                    onClick = {
                        onValueChange(generateSharePassword(maxOf(minLength ?: 0, 16)))
                        visible = true
                    },
                    enabled = enabled,
                    testTag = "note-share-generate-password"
                )
                TooltipIconButton(
                    icon = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    description = stringResource(
                        if (visible) {
                            R.string.share_note_hide_password
                        } else {
                            R.string.share_note_show_password
                        }
                    ),
                    onClick = { visible = !visible },
                    testTag = "note-share-toggle-password"
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("note-share-password")
    )
}

/**
 * A random password with lower and upper case letters, digits, and symbols, so that it also
 * satisfies the character class rules Nextcloud's password policy can require.
 */
internal fun generateSharePassword(length: Int, random: SecureRandom = SecureRandom()): String {
    val classes = listOf(
        "abcdefghijkmnopqrstuvwxyz",
        "ABCDEFGHJKLMNPQRSTUVWXYZ",
        "23456789",
        "-_.!?#%+="
    )
    val all = classes.joinToString("")
    val characters = classes.map { it[random.nextInt(it.length)] } +
        List(maxOf(length, classes.size) - classes.size) { all[random.nextInt(all.length)] }
    return characters.shuffled(random).joinToString("")
}

private fun formatShareDate(date: String): String = try {
    LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: RuntimeException) {
    date
}

internal fun noteShareErrorMessage(error: Throwable, resources: Resources, fallback: Int): String =
    when (error) {
        is BackendException.Rejected ->
            error.serverMessage
                ?.let { resources.getString(R.string.share_note_rejected, it) }
                ?: resources.getString(R.string.share_note_rejected_generic)
        is BackendException.Permission -> resources.getString(R.string.share_note_permission_denied)
        is BackendException.RemoteMissing -> resources.getString(R.string.share_note_missing)
        is BackendException.Retryable -> resources.getString(R.string.share_note_offline)
        else -> error.message ?: resources.getString(fallback)
    }
