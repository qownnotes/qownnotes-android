package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.markdown.FetchedLink
import org.qownnotes.mobile.markdown.LinkTitleFetcher
import org.qownnotes.mobile.markdown.canonicalSafeWebUrl
import org.qownnotes.mobile.markdown.markdownLink

/** A title lookup only fills an automatic label; it never replaces the writer's own text. */
@Composable
internal fun InsertLinkDialog(
    initialUrl: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit,
    fetchTitle: suspend (String) -> FetchedLink = { url ->
        withContext(Dispatchers.IO) { LinkTitleFetcher().fetch(url) }
    }
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var manualTitle by rememberSaveable { mutableStateOf(initialTitle.isNotBlank()) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var lookupFailed by remember { mutableStateOf(false) }
    val safeUrl = canonicalSafeWebUrl(url)

    LaunchedEffect(safeUrl, refreshRequest) {
        loading = false
        lookupFailed = false
        if (safeUrl == null || manualTitle) return@LaunchedEffect
        val requestedUrl = url
        val requestedRefresh = refreshRequest
        fun isCurrentRequest() = url == requestedUrl && refreshRequest == requestedRefresh
        loading = true
        try {
            // Avoid requesting a page for every character while the URL is being typed.
            delay(400)
            val fetched = fetchTitle(safeUrl)
            if (isCurrentRequest() && !manualTitle) title = fetched.title
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isCurrentRequest()) lookupFailed = true
        } finally {
            if (isCurrentRequest()) loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.format_link)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        if (!manualTitle) title = ""
                    },
                    label = { Text(stringResource(R.string.link_url)) },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    isError = url.isNotBlank() && safeUrl == null,
                    supportingText = if (url.isNotBlank() && safeUrl == null) {
                        { Text(stringResource(R.string.link_invalid_url)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("link-url")
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        manualTitle = true
                    },
                    label = { Text(stringResource(R.string.link_text)) },
                    leadingIcon = { Icon(Icons.Filled.Title, contentDescription = null) },
                    trailingIcon = {
                        if (loading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp).testTag("link-title-loading")
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    manualTitle = false
                                    title = ""
                                    refreshRequest++
                                },
                                enabled = safeUrl != null,
                                modifier = Modifier.testTag("link-fetch-title")
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.link_fetch_title)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("link-text")
                )
                if (loading) {
                    Text(
                        stringResource(R.string.link_fetching_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (lookupFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp).testTag("link-title-error")
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            stringResource(R.string.link_title_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    safeUrl?.let { onInsert(markdownLink(title.trim().ifBlank { it }, it)) }
                },
                enabled = safeUrl != null && (!loading || title.isNotBlank()),
                modifier = Modifier.testTag("insert-link-confirm")
            ) {
                Icon(
                    Icons.Filled.AddLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    stringResource(R.string.link_insert),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
