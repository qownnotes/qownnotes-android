package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.qownnotes.mobile.core.NoteTag
import org.qownnotes.mobile.core.NoteTagAvailability
import org.qownnotes.mobile.core.NoteTagState
import org.qownnotes.mobile.core.NoteTags

/** A note's tags as one compact line of `#name` labels. */
@Composable
internal fun NoteTagLine(tags: List<NoteTag>, testTag: String, color: Color? = null) {
    if (tags.isEmpty()) return
    Text(
        tags.joinToString("  ") { "#${it.name}" },
        style = MaterialTheme.typography.labelMedium,
        color = color ?: MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
internal fun tagFilterLabel(state: NoteTagState, selected: List<String>): String {
    val names = NoteTags.withPaths(state.tags)
        .filter { (_, path) -> NoteTags.pathKey(path) in selected }
        .map { (tag, _) -> tag.name }
    return if (names.isEmpty()) {
        stringResource(R.string.tags_filter_all)
    } else {
        stringResource(R.string.tags_filter_selected, names.joinToString(", "))
    }
}

/** Chooses tags that a listed note must all carry. Selections are tag path keys. */
@Composable
internal fun NoteTagFilterDialog(
    state: NoteTagState,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val tags = remember(state.tags) { NoteTags.withPaths(state.tags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_filter_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            ) {
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.tags_filter_no_tags))
                }
                tags.forEach { (tag, path) ->
                    val key = NoteTags.pathKey(path)
                    TagCheckboxRow(
                        label = NoteTags.displayPath(path),
                        checked = key in selected,
                        enabled = true,
                        onCheckedChange = { checked ->
                            onChange(if (checked) selected + key else selected - key)
                        },
                        testTag = "tag-filter-option-${tag.name}"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-tag-filter")) {
                Text(stringResource(R.string.ui_done))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onChange(emptyList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.testTag("clear-tag-filter")
            ) { Text(stringResource(R.string.ui_clear)) }
        },
        modifier = Modifier.testTag("tag-filter-dialog")
    )
}

/**
 * Adds and removes tags on one note. Every change is saved immediately and written to
 * `notes.sqlite` by the next synchronization.
 */
@Composable
internal fun NoteTagsDialog(
    state: NoteTagState,
    noteTagIds: Set<Long>,
    onToggle: (path: List<String>, linked: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val tags = remember(state.tags) { NoteTags.withPaths(state.tags) }
    val matchingTags = remember(tags, searchQuery) {
        tags.filter { (_, path) ->
            NoteTags.displayPath(path).contains(searchQuery.trim(), ignoreCase = true)
        }
    }
    val normalizedNewTag = NoteTags.normalizeName(newTag)
    val addNewTag = {
        normalizedNewTag?.let { onToggle(listOf(it), true) }
        newTag = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.availability == NoteTagAvailability.AVAILABLE) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.tags_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("search-note-tags")
                    )
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        state.availability == NoteTagAvailability.UNKNOWN -> Text(
                            stringResource(R.string.tags_after_next_sync),
                            modifier = Modifier.testTag("note-tags-unavailable")
                        )
                        state.availability != NoteTagAvailability.AVAILABLE -> Text(
                            state.message ?: stringResource(R.string.tags_unavailable),
                            modifier = Modifier.testTag("note-tags-unavailable")
                        )
                        else -> {
                            if (!state.writable) {
                                Text(
                                    stringResource(R.string.tags_read_only),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("note-tags-read-only")
                                )
                            }
                            state.message?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (tags.isEmpty()) {
                                Text(stringResource(R.string.tags_none))
                            } else if (matchingTags.isEmpty()) {
                                Text(stringResource(R.string.tags_no_matches))
                            }
                            matchingTags.forEach { (tag, path) ->
                                TagCheckboxRow(
                                    label = NoteTags.displayPath(path),
                                    checked = tag.id in noteTagIds,
                                    enabled = state.editable,
                                    onCheckedChange = { onToggle(path, it) },
                                    testTag = "note-tag-option-${tag.name}"
                                )
                            }
                            if (state.editable) {
                                OutlinedTextField(
                                    value = newTag,
                                    onValueChange = { newTag = it },
                                    label = { Text(stringResource(R.string.tags_new_tag)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { addNewTag() }),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        .testTag("new-note-tag")
                                )
                                TextButton(
                                    onClick = addNewTag,
                                    enabled = normalizedNewTag != null,
                                    modifier = Modifier.testTag("add-note-tag")
                                ) { Text(stringResource(R.string.tags_add_tag)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-note-tags")) {
                Text(stringResource(R.string.ui_done))
            }
        },
        modifier = Modifier.testTag("note-tags-dialog")
    )
}

@Composable
private fun TagCheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
